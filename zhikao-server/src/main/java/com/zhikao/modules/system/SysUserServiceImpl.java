package com.zhikao.modules.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.common.BusinessException;
import com.zhikao.common.ExcelHelper;
import com.zhikao.common.ImportResultVO;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.PageResult;
import com.zhikao.common.PasswordPolicy;
import com.zhikao.common.UserTypeEnum;
import com.zhikao.config.SecurityProperties;
import com.zhikao.entity.SysClass;
import com.zhikao.entity.SysRole;
import com.zhikao.entity.SysUser;
import com.zhikao.entity.SysUserRole;
import com.zhikao.mapper.SysClassMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.mapper.SysUserRoleMapper;
import com.zhikao.modules.system.dto.UserQueryDTO;
import com.zhikao.modules.system.dto.UserSaveDTO;
import com.zhikao.modules.system.vo.UserVO;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现。
 */
@Service
public class SysUserServiceImpl implements SysUserService {

    /** 导入模板列顺序：用户名 | 真实姓名 | 角色 | 班级 | 手机号 */
    private static final List<String> IMPORT_HEADERS = List.of("用户名", "真实姓名", "角色", "班级", "手机号", "初始密码");

    private final SysUserMapper userMapper;
    private final com.zhikao.mapper.HistoricalReferencesMapper history;
    private final com.zhikao.modules.file.FileBlobStore fileBlobs;
    private final SysClassMapper classMapper;
    private final PasswordPolicy passwordPolicy;
    private final SecurityProperties security;
    private final RbacService rbacService;
    private final SysUserRoleMapper userRoleMapper;
    private final com.zhikao.modules.auth.SessionRevocation sessions;
    private final org.springframework.transaction.support.TransactionTemplate transactions;

    public SysUserServiceImpl(SysUserMapper userMapper, SysClassMapper classMapper,
                              PasswordPolicy passwordPolicy, SecurityProperties security,
                              RbacService rbacService, SysUserRoleMapper userRoleMapper,
                              com.zhikao.modules.auth.SessionRevocation sessions, org.springframework.transaction.PlatformTransactionManager manager,com.zhikao.mapper.HistoricalReferencesMapper history,com.zhikao.modules.file.FileBlobStore fileBlobs) {
        this.history=history;this.fileBlobs=fileBlobs;
        this.userMapper = userMapper;
        this.classMapper = classMapper;
        this.passwordPolicy = passwordPolicy;
        this.security = security;
        this.rbacService = rbacService;
        this.userRoleMapper = userRoleMapper;
        this.sessions = sessions;
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(manager);
    }

    @Override
    public PageResult<UserVO> page(UserQueryDTO query) {
        // 关键字同时模糊匹配「用户名」与「真实姓名」，用 and(...) 包成一组条件，
        // 避免与其他条件的 or 混淆导致筛选条件失效。
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(SysUser::getUsername, query.getKeyword())
                        .or()
                        .like(SysUser::getRealName, query.getKeyword()))
                .eq(query.getUserType() != null, SysUser::getUserType, query.getUserType())
                .eq(query.getClassId() != null, SysUser::getClassId, query.getClassId())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getId);

        Page<SysUser> page = userMapper.selectPage(
                new Page<>(query.safePageNum(), query.safePageSize()), wrapper);

        // 一次性查出本页涉及的班级名称，避免逐行查库（N+1）
        Map<Long, String> classNames = classNames(page.getRecords());
        // 同理批量查用户角色关联，供前端展示细粒度角色码
        Map<Long, List<String>> roleCodes = rbacService.roleCodesOf(
                page.getRecords().stream().map(SysUser::getId).collect(Collectors.toList()));
        return PageResult.of(page, user -> toVO(user, classNames, roleCodes));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(UserSaveDTO dto) {
        boolean create = dto.getId() == null;

        // 1. 用户名唯一性校验（修改时排除自身）
        Long duplicated = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername())
                .ne(!create, SysUser::getId, dto.getId()));
        if (duplicated > 0) {
            throw new BusinessException("用户名已存在：" + dto.getUsername());
        }

        // 2. 传了 roleCode 时以角色表为准：persona(user_type) 反向同步自 sys_role.persona，
        //    避免前端两处选择（角色下拉 + 用户类型）不一致造成权限与身份错位
        String roleCode = StringUtils.hasText(dto.getRoleCode()) ? dto.getRoleCode().trim() : null;
        if (roleCode != null) {
            SysRole role = rbacService.roleOf(roleCode);
            if (role == null) {
                throw new BusinessException("角色不存在：" + roleCode);
            }
            dto.setUserType(role.getPersona());
        }

        // 3. 学生必须归属班级：成绩需要按班级统计
        boolean student = UserTypeEnum.STUDENT.getCode() == dto.getUserType();
        if (student && dto.getClassId() == null) {
            throw new BusinessException("学生必须选择所属班级");
        }
        if (student) {
            requireClassExists(dto.getClassId());
        }

        SysUser user = new SysUser();
        user.setId(dto.getId());
        user.setUsername(dto.getUsername());
        user.setRealName(dto.getRealName());
        user.setUserType(dto.getUserType());
        // 非学生不保留班级归属，避免角色变更后残留脏数据
        user.setClassId(student ? dto.getClassId() : null);
        user.setPhone(dto.getPhone());
        user.setUpdateTime(LocalDateTime.now());

        if (create) {
            user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
            user.setCreateTime(LocalDateTime.now());
            String rawPassword = StringUtils.hasText(dto.getPassword())
                    ? dto.getPassword()
                    : security.getDefaultPassword();
            passwordPolicy.validate(rawPassword);
            user.setPasswordChangeRequired(1);
            user.setPasswordExpiresAt(LocalDateTime.now().plusHours(24));
            user.setPassword(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
            userMapper.insert(user);
            syncRoleLinks(user.getId(), user.getUserType(), roleCode);
            return;
        }

        // 修改：密码留空表示不变；状态按传入值更新（为空时 MyBatis-Plus 会跳过该字段）
        user.setStatus(dto.getStatus());
        if (StringUtils.hasText(dto.getPassword())) {
            passwordPolicy.validate(dto.getPassword());
            user.setPassword(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
            user.setPasswordChangeRequired(1);
            user.setPasswordExpiresAt(LocalDateTime.now().plusHours(24));
        }
        userMapper.updateById(user);
        syncRoleLinks(user.getId(), user.getUserType(), roleCode);
        sessions.afterCommit(user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请先选择要操作的用户");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值不合法，只能为 1（启用）或 0（禁用）");
        }
        // 批量禁用时把自己排除在外，避免管理员把自己锁在系统外
        List<Long> targets = ids.stream()
                .filter(id -> !(status == 0 && id.equals(LoginUtil.userId())))
                .collect(Collectors.toList());
        if (targets.isEmpty()) {
            throw new BusinessException("不能禁用当前登录账号");
        }

        SysUser update = new SysUser();
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.update(update, Wrappers.<SysUser>lambdaUpdate().in(SysUser::getId, targets));
        targets.forEach(sessions::afterCommit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchRemove(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请先选择要删除的用户");
        }
        if (ids.contains(LoginUtil.userId())) {
            throw new BusinessException("不能删除当前登录账号");
        }
        // 逐条删除而非批量 delete：用户被试卷 / 考试引用时需给出明确提示，不能静默删掉
        for (Long id : ids) {
            requireUserExists(id);
            assertRemovable(id);
            userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId,id));
            userMapper.deleteById(id);
            sessions.afterCommit(id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值不合法，只能为 1（启用）或 0（禁用）");
        }
        // 不允许禁用自己，避免管理员误操作后无法登录
        if (status == 0 && id.equals(LoginUtil.userId())) {
            throw new BusinessException("不能禁用当前登录账号");
        }
        requireUserExists(id);

        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        sessions.afterCommit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(Long id) {
        requireUserExists(id);

        SysUser update = new SysUser();
        update.setId(id);
        String temporary = "Zk9!" + java.util.UUID.randomUUID().toString().replace("-", "");
        update.setPassword(BCrypt.hashpw(temporary, BCrypt.gensalt()));
        update.setPasswordChangeRequired(1);
        update.setPasswordExpiresAt(LocalDateTime.now().plusHours(24));
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        sessions.afterCommit(id);
        return temporary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        // 不允许删除自己，避免管理员把自己删掉后无法登录
        if (id.equals(LoginUtil.userId())) {
            throw new BusinessException("不能删除当前登录账号");
        }
        requireUserExists(id);
        assertRemovable(id);
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId,id));
        userMapper.deleteById(id);
            sessions.afterCommit(id);
    }

    private void assertRemovable(Long id) {
        SysUser user=userMapper.selectById(id);
        if(user!=null && history.userReferences(id,user.getUsername())>0)
            throw new BusinessException("该用户已有教学或文件历史，请使用停用功能保留记录");
    }

    @Override
    public ImportResultVO importUsers(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的 Excel 文件");
        }
        List<List<String>> rows = ExcelHelper.read(file);
        if (rows.size() <= 1) {
            throw new BusinessException("Excel 中没有数据行（第 1 行应为表头）");
        }

        ImportResultVO result = new ImportResultVO();
        // 每行独立导入：单行失败只记录原因，不影响已成功的行
        for (int i = 1; i < rows.size(); i++) {
            int lineNo = i + 1; // 含表头的真实行号，便于用户定位
            try {
                UserSaveDTO row = parseRow(rows.get(i));
                transactions.executeWithoutResult(status -> save(row));
                result.addSuccess();
            } catch (Exception e) {
                result.addError("第 " + lineNo + " 行：" + e.getMessage());
            }
        }
        return result;
    }

    @Override
    public byte[] importTemplate() {
        return ExcelHelper.write("用户导入", IMPORT_HEADERS, List.of(
                List.of("zhangsan", "张三", "学生", "软件工程2301班", "13800000001", "请填写强初始密码"),
                List.of("lisi", "李四", "教师", "", "13800000002", "请填写强初始密码")));
    }

    /** Excel 行 -> 保存参数，并给出明确的校验提示 */
    private UserSaveDTO parseRow(List<String> row) {
        UserSaveDTO dto = new UserSaveDTO();
        dto.setUsername(cell(row, 0));
        dto.setPassword(cell(row, 5));
        dto.setRealName(cell(row, 1));
        dto.setPhone(cell(row, 4));
        dto.setStatus(1);

        if (!StringUtils.hasText(dto.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        if (!StringUtils.hasText(dto.getRealName())) {
            throw new BusinessException("真实姓名不能为空");
        }
        if (StringUtils.hasText(dto.getPhone()) && !dto.getPhone().matches("^1\\d{10}$")) {
            throw new BusinessException("手机号格式不正确：" + dto.getPhone());
        }

        // 角色列：支持 管理员/教师/学生、admin/teacher/student、1/2/3
        String roleText = cell(row, 2);
        Integer userType = UserTypeEnum.parse(roleText);
        if (userType == null) {
            throw new BusinessException("角色无法识别（应为 管理员/教师/学生）：" + roleText);
        }
        dto.setUserType(userType);

        // 班级列按名称匹配；学生必填
        String className = cell(row, 3);
        if (StringUtils.hasText(className)) {
            SysClass clazz = classMapper.selectOne(Wrappers.<SysClass>lambdaQuery()
                    .eq(SysClass::getClassName, className)
                    .last("limit 1"));
            if (clazz == null) {
                throw new BusinessException("班级不存在：" + className);
            }
            dto.setClassId(clazz.getId());
        }
        return dto;
    }

    /** 取指定列的值，列不存在时返回空串 */
    private String cell(List<String> row, int index) {
        return index < row.size() ? row.get(index).trim() : "";
    }

    /**
     * 重建 sys_user_role 关联。
     * 传了 roleCode → 唯一关联该角色；未传 → 沿用 userType 对应的默认角色，
     * 且只在用户当前没有任何关联时补建（避免覆盖历史手工配置）。
     */
    private void syncRoleLinks(Long userId, Integer persona, String roleCode) {
        if (roleCode != null) {
            SysRole role = rbacService.roleOf(roleCode);
            userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
                    .eq(SysUserRole::getUserId, userId));
            userRoleMapper.insert(SysUserRole.builder()
                    .userId(userId).roleId(role.getId()).build());
            return;
        }
        Long linked = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, userId));
        if (linked > 0) {
            return;
        }
        SysRole defaultRole = rbacService.roleOf(RbacService.defaultRoleCodeOf(persona));
        if (defaultRole != null) {
            userRoleMapper.insert(SysUserRole.builder()
                    .userId(userId).roleId(defaultRole.getId()).build());
        }
    }

    /** 批量查询班级名称，key 为班级 id */
    private Map<Long, String> classNames(List<SysUser> users) {
        List<Long> classIds = users.stream()
                .map(SysUser::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return classMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(SysClass::getId, SysClass::getClassName));
    }

    /** 实体 -> VO */
    private UserVO toVO(SysUser user, Map<Long, String> classNames, Map<Long, List<String>> roleCodes) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setUserType(user.getUserType());
        vo.setUserTypeName(UserTypeEnum.labelOf(user.getUserType()));
        vo.setClassId(user.getClassId());
        vo.setClassName(user.getClassId() == null ? null : classNames.get(user.getClassId()));
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setRoleCodes(roleCodes.getOrDefault(user.getId(), List.of()));
        return vo;
    }

    /** 更新当前登录用户的头像地址；传空表示清除（回退到姓名首字展示） */
    @Override
    @Transactional(rollbackFor=Exception.class)
    public void updateMyAvatar(String avatarUrl) {
        // 只接受本系统文件模块产出的地址，避免被塞进任意外链（= 变相 SSRF / 内容注入）
        if (StringUtils.hasText(avatarUrl) && !avatarUrl.startsWith("/api/file/view/")) {
            throw new BusinessException("头像地址不合法：请先通过文件上传接口上传图片");
        }
        fileBlobs.lockReferences(avatarUrl);
        SysUser update = new SysUser();
        update.setId(LoginUtil.userId());
        // 用空串而不是 null：MyBatis-Plus 默认忽略 null 字段，null 清不掉旧头像
        update.setAvatar(StringUtils.hasText(avatarUrl) ? avatarUrl : "");
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
    }

    /** 校验用户存在 */
    private void requireUserExists(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new BusinessException("用户不存在");
        }
    }

    /** 校验班级存在 */
    private void requireClassExists(Long classId) {
        if (classMapper.selectById(classId) == null) {
            throw new BusinessException("所选班级不存在");
        }
    }
}
