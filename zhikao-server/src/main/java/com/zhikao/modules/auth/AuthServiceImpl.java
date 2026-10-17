package com.zhikao.modules.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.BusinessException;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.OperLogRecorder;
import com.zhikao.common.PasswordPolicy;
import com.zhikao.common.UserTypeEnum;
import com.zhikao.common.WebUtil;
import com.zhikao.config.StpInterfaceImpl;
import com.zhikao.entity.SysOperLog;
import com.zhikao.entity.SysUser;
import com.zhikao.mapper.SysOperLogMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.modules.auth.dto.ChangePasswordDTO;
import com.zhikao.modules.auth.dto.LoginDTO;
import com.zhikao.modules.auth.vo.LoginVO;
import com.zhikao.modules.auth.vo.SecurityActivityVO;
import com.zhikao.modules.auth.vo.UserInfoVO;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 认证服务实现：登录校验 + Sa-Token 签发 token + 登录审计。
 *
 * 安全加固（二期）：
 * 1. 登录前先过 {@link LoginGuard}，被锁定/被限制的请求不查库、不做 BCrypt 比对（省 CPU）；
 * 2. 登录成功与失败都落操作日志——**失败的记录同样重要**，它是排查撞库的唯一线索；
 * 3. 失败日志里不记明文密码（切面统一脱敏，此处直接不传密码）。
 *
 * RBAC 改造：登录成功（以及改密后重新登录）把细粒度角色码与权限码写入会话，
 * 数据口径与 StpInterfaceImpl 完全一致（复用同一实现，避免「登录返回的权限」与
 * 「鉴权时生效的权限」两套算法日后各自漂移）。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    /** 安全活动读的是操作日志表：登录/登出/改密本来就落在这里，不另起一张表就是两套真相 */
    private final SysOperLogMapper operLogMapper;
    private final LoginGuard loginGuard;
    private final OperLogRecorder operLogRecorder;
    private final PasswordPolicy passwordPolicy;
    /** Sa-Token 角色/权限数据源：装载并缓存 roleCodes / permissions。
     *  注入具体实现而非 StpInterface 接口——starter 的默认实现也在容器里，按接口注入会有两个候选 */
    private final StpInterfaceImpl stpInterface;
    private final SessionRevocation sessions;

    public AuthServiceImpl(SysUserMapper userMapper, SysOperLogMapper operLogMapper,
                           LoginGuard loginGuard,
                           OperLogRecorder operLogRecorder, PasswordPolicy passwordPolicy,
                           StpInterfaceImpl stpInterface, SessionRevocation sessions) {
        this.userMapper = userMapper;
        this.operLogMapper = operLogMapper;
        this.loginGuard = loginGuard;
        this.operLogRecorder = operLogRecorder;
        this.passwordPolicy = passwordPolicy;
        this.stpInterface = stpInterface;
        this.sessions = sessions;
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        String ip = WebUtil.clientIp();
        // 1. 前置检查：账号或 IP 已被锁定时直接拒绝
        loginGuard.check(dto.getUsername(), ip);

        // 2. 按用户名查询用户
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || user.getStatus() == 0) {
            onLoginFailure(dto.getUsername(), ip, "用户不存在或已禁用");
            throw new BusinessException("用户不存在或已禁用");
        }
        // 3. 校验密码（BCrypt 比对）
        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            onLoginFailure(dto.getUsername(), ip, "密码错误");
            throw new BusinessException("用户名或密码错误");
        }

        if (Integer.valueOf(1).equals(user.getPasswordChangeRequired())
                && (user.getPasswordExpiresAt() == null || LocalDateTime.now().isAfter(user.getPasswordExpiresAt()))) {
            throw new BusinessException("初始口令已过期，请联系管理员重置");
        }
        // 4. 登录成功：清除失败计数 + 落审计日志
        loginGuard.onSuccess(dto.getUsername());
        StpUtil.login(user.getId());
        StpUtil.getSession().set(LoginUtil.SESSION_USERNAME, user.getUsername());
        StpUtil.getTokenSession().set("authVersion", user.getAuthVersion() == null ? 0L : user.getAuthVersion());
        StpUtil.getSession().set(LoginUtil.SESSION_USER_TYPE, user.getUserType());
        StpUtil.getSession().delete(LoginUtil.SESSION_ROLE_CODES);
        StpUtil.getSession().delete(LoginUtil.SESSION_PERMISSIONS);
        List<String> roleCodes = stpInterface.getRoleList(user.getId(), "login");
        List<String> permissions = stpInterface.getPermissionList(user.getId(), "login");
        saveOperLog("登录", "登录成功", "/auth/login", user.getUsername(),
                UserTypeEnum.roleOf(user.getUserType()), ip, 1, null);

        return buildLoginVO(user, roleCodes, permissions);
    }

    @Override
    public LoginVO info() {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("当前登录用户不存在或已被删除");
        }
        // 会话里有登录时写入的快照（StpInterfaceImpl 内部优先读会话缓存），
        // 缓存缺失时这里会顺带回查数据库并补写会话——同一份口径，两处都拿到全量数据。
        List<String> roleCodes = stpInterface.getRoleList(userId, "login");
        List<String> permissions = stpInterface.getPermissionList(userId, "login");
        return buildLoginVO(user, roleCodes, permissions);
    }

    @Override
    public void logout() {
        // 先取会话信息（注销后就取不到了），再记审计、再注销 token
        String username = LoginUtil.username();
        String role = LoginUtil.roleCode();
        StpUtil.logout();
        saveOperLog("登出", "退出登录", "/auth/logout", username, role, WebUtil.clientIp(), 1, null);
    }

    // ==================== 内部方法 ====================

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("当前登录用户不存在或已被删除");
        }
        // 1. 必须先验证旧密码：token 泄露不等于账号可被接管
        if (!BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("当前密码不正确");
        }
        // 2. 新密码不能与旧密码相同（否则「修改成功」其实什么也没改）
        if (BCrypt.checkpw(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        // 3. 强度校验（与管理员重置密码走同一套规则）
        passwordPolicy.validate(dto.getNewPassword());

        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(BCrypt.hashpw(dto.getNewPassword(), BCrypt.gensalt()));
        update.setPasswordChangeRequired(0);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);

        sessions.afterCommit(userId);

        String ip = WebUtil.clientIp();
        saveOperLog("个人设置", "修改密码", "/auth/password/change", user.getUsername(),
                UserTypeEnum.roleOf(user.getUserType()), ip, 1, null);
    }

    /** 登录失败处理：累计失败次数 + 落审计日志 */
    private void onLoginFailure(String username, String ip, String reason) {
        loginGuard.onFailure(username, ip);
        saveOperLog("登录", "登录失败", "/auth/login", username, "", ip, 0, reason);
    }

    /** 异步写一条登录相关操作日志（密码等敏感信息不参与） */
    private void saveOperLog(String module, String action, String requestUri, String operName,
                             String operRole, String ip, int status, String errorMsg) {
        operLogRecorder.record(SysOperLog.builder()
                .module(module)
                .action(action)
                .method("POST")
                .requestUri(requestUri)
                .operName(operName)
                .operRole(operRole)
                .operIp(ip)
                .resultStatus(status)
                .errorMsg(errorMsg)
                .costMs(0L)
                .operTime(LocalDateTime.now())
                .build());
    }

    // ==================== 本人安全活动（个人设置页）====================

    /**
     * 与「账号安全」有关的事件类型：登录（含失败）、登出、改密。
     * 这三类是 {@link #saveOperLog} 直接写的，模块名固定，不用配置也不用猜。
     */
    private static final List<String> SECURITY_MODULES = List.of("登录", "登出", "个人设置");

    private static final int SECURITY_ACTIVITY_DEFAULT = 12;
    private static final int SECURITY_ACTIVITY_MAX = 30;

    @Override
    public List<SecurityActivityVO> securityActivity(Integer limit) {
        String username = LoginUtil.username();
        if (!StringUtils.hasText(username)) {
            // 取不到当前账号就返回空而不是全部：这个接口的语义是「我的」
            return List.of();
        }
        int size = limit == null || limit <= 0
                ? SECURITY_ACTIVITY_DEFAULT
                : Math.min(SECURITY_ACTIVITY_MAX, limit);
        List<SysOperLog> rows = operLogMapper.selectList(Wrappers.<SysOperLog>lambdaQuery()
                .eq(SysOperLog::getOperName, username)
                .in(SysOperLog::getModule, SECURITY_MODULES)
                .orderByDesc(SysOperLog::getId)
                // size 由上面夹紧成 1~30 的整数，不是外部字符串，不存在注入
                .last("limit " + size));
        return rows.stream().map(AuthServiceImpl::toActivity).collect(Collectors.toList());
    }

    private static SecurityActivityVO toActivity(SysOperLog row) {
        SecurityActivityVO vo = new SecurityActivityVO();
        vo.setOperTime(row.getOperTime());
        vo.setTitle(row.getAction());
        vo.setIp(row.getOperIp());
        vo.setResultStatus(row.getResultStatus());
        vo.setErrorMsg(row.getErrorMsg());
        vo.setKind(activityKind(row));
        return vo;
    }

    /** 类型码只用来让前端挑图标与配色；登录成功与失败同属「登录」模块，所以判 action 而不判 module */
    private static String activityKind(SysOperLog row) {
        String action = row.getAction() == null ? "" : row.getAction();
        if (action.contains("失败")) {
            return "LOGIN_FAILED";
        }
        if (action.contains("登录成功")) {
            return "LOGIN";
        }
        if (action.contains("退出")) {
            return "LOGOUT";
        }
        if (action.contains("密码")) {
            return "PASSWORD";
        }
        return "OTHER";
    }

    /** 组装登录返回（token + 用户信息） */
    private LoginVO buildLoginVO(SysUser user, List<String> roleCodes, List<String> permissions) {
        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());

        UserInfoVO info = new UserInfoVO();
        info.setPasswordChangeRequired(Integer.valueOf(1).equals(user.getPasswordChangeRequired()));
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setRealName(user.getRealName());
        info.setUserType(user.getUserType());
        // role 是 persona 码（admin/teacher/student）：前端与既有后端逻辑都按它分流，
        // 细粒度语义由 roleCodes/permissions 承载，二者并存、各司其职。
        info.setRole(UserTypeEnum.roleOf(user.getUserType()));
        info.setRoleCodes(roleCodes);
        info.setPermissions(permissions);
        info.setAvatar(user.getAvatar());
        vo.setUserInfo(info);
        return vo;
    }
}
