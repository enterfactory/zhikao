package com.zhikao.config;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.zhikao.common.LoginUtil;
import com.zhikao.modules.system.RbacService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限数据源：为 {@code @SaCheckRole} 提供角色码集合、
 * 为 {@code @SaCheckPermission / StpUtil.hasPermission()} 提供权限码集合。
 *
 * <p>RBAC 改造后的取数链路（每次鉴权都走这里，所以只做一次库查询、结果缓存进会话）：
 * <ol>
 *   <li>优先读 Sa-Token Session 缓存（登录时 {@code AuthServiceImpl} 已写入，运行期读取）；</li>
 *   <li>缓存缺失（老会话、会话被清理）时经 {@link RbacService} 回查数据库并写回会话；</li>
 *   <li>sys_user_role 无关联的角色兜底：按 persona（sys_user.user_type）映射默认角色。</li>
 * </ol>
 *
 * <p>为什么缓存进会话而不是本地/Redis 缓存：角色变更本来就要求重新登录才生效，
 * 会话缓存的生命周期与「这次登录是谁」天然一致，不引入额外失效逻辑。
 * {@code SysUserService} 调整角色后旧会话仍持有旧快照，是当前模式（token 会话）的既有语义。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final RbacService rbacService;

    public StpInterfaceImpl(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    /**
     * 权限码集合（按钮/接口级）。
     * super_admin 短路返回全表 perm_code 的实现在 {@link RbacService#permissionCodesOf}；
     * 学生无任何 console 权限，返回空集合是正常的「有身份、无权限」，不是异常。
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId);
        List<String> cached = stringList(session.get(LoginUtil.SESSION_PERMISSIONS));
        if (cached != null) {
            return cached;
        }
        List<String> permissions = rbacService.permissionCodesOf(getRoleList(loginId, loginType));
        session.set(LoginUtil.SESSION_PERMISSIONS, new ArrayList<>(permissions));
        return permissions;
    }

    /** 细粒度角色码集合，例如 ["teacher"]；sys_user_role 缺失时按 persona 兜底 */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SaSession session = StpUtil.getSessionByLoginId(loginId);
        List<String> cached = stringList(session.get(LoginUtil.SESSION_ROLE_CODES));
        if (cached != null) {
            return cached;
        }
        List<String> roleCodes = rbacService.roleCodesOf(Long.valueOf(loginId.toString()));
        session.set(LoginUtil.SESSION_ROLE_CODES, new ArrayList<>(roleCodes));
        return roleCodes;
    }

    /**
     * 会话值 -> 字符串列表。
     * 注意 Redis 序列化回读后可能是 List 也可能是其他形态：非 List 一律按「缓存缺失」处理，
     * 触发重新装载而不是抛 ClassCastException——鉴权路径上宁可多查一次库。
     */
    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(item == null ? "" : item.toString());
        }
        return result;
    }
}
