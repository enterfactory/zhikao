package com.zhikao.modules.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.UserTypeEnum;
import com.zhikao.entity.SysRole;
import com.zhikao.entity.SysUser;
import com.zhikao.entity.SysUserRole;
import com.zhikao.mapper.SysPermissionMapper;
import com.zhikao.mapper.SysRoleMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 身份装载：把「一个账号能代表谁」算出来的唯一一处。
 *
 * <p>为什么单独成 Service 而不是塞进 StpInterfaceImpl：
 * 登录返回（AuthServiceImpl）、注解鉴权（StpInterfaceImpl）、用户管理展示
 * 都要同一套口径；逻辑写两遍，迟早出现「接口能进、菜单看不到」这类不一致。
 *
 * <p>兜底策略：sys_user_role 没有关联时按 persona（sys_user.user_type）映射默认角色——
 * 这保证「老库没做角色迁移也能登录」，也让「建用户忘配角色」不至于把账号变成废号。
 */
@Service
public class RbacService {

    /** 超级管理员角色码：权限点直接短路返回全表，避免每加一个权限点都要补关联 */
    public static final String SUPER_ADMIN = "super_admin";

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;

    public RbacService(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper,
                       SysUserMapper userMapper, SysUserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /**
     * 用户的细粒度角色码集合。
     * 关联表为空时按 persona 兜底：1→super_admin，2→teacher，3→student。
     * 注意 1 兜底到 super_admin 是演示/迁移场景的保守选择——正式环境应显式分配角色，
     * 真正的防线是 @SaCheckPermission 按权限点收敛，而不是靠角色数量。
     */
    public List<String> roleCodesOf(Long userId) {
        List<String> codes = roleMapper.selectRoleCodesByUserId(userId);
        if (codes != null && !codes.isEmpty()) {
            return codes;
        }
        SysUser user = userMapper.selectById(userId);
        String fallback = defaultRoleCodeOf(user == null ? null : user.getUserType());
        return fallback == null ? Collections.emptyList() : List.of(fallback);
    }

    /**
     * 角色码集合对应的权限码集合。
     * super_admin 短路返回全表（seed 里同时写了全量关联，这里省一次 join，
     * 也保证「新增权限点即刻对超管可用」而不必先补 sys_role_permission）。
     */
    public List<String> permissionCodesOf(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            // 空角色 = 无权限（学生无 console 权限即走这里；未知角色同样按最小权限处理）
            return new ArrayList<>();
        }
        if (roleCodes.contains(SUPER_ADMIN)) {
            return permissionMapper.selectAllPermCodes();
        }
        return permissionMapper.selectPermCodesByRoleCodes(roleCodes);
    }

    /** persona（sys_user.user_type）到默认角色码的映射；未知按最小权限返回 student */
    public static String defaultRoleCodeOf(Integer persona) {
        if (persona == null) {
            return UserTypeEnum.STUDENT.getRole();
        }
        return switch (persona) {
            case 1 -> SUPER_ADMIN;
            case 2 -> UserTypeEnum.TEACHER.getRole();
            default -> UserTypeEnum.STUDENT.getRole();
        };
    }

    /**
     * 批量版角色码：用户列表一页几十个账号，逐个查会放大成 N+1；
     * 这里两条 SQL（关联 + 角色）在内存里装配。
     * 无关联的用户返回空列表——展示层按「未分配角色」处理，不在此兜底默认角色，
     * 免得列表里看到的角色和登录后的真实角色是两套口径。
     */
    public Map<Long, List<String>> roleCodesOf(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRole> links = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .in(SysUserRole::getUserId, userIds));
        if (links.isEmpty()) {
            return userIds.stream().collect(Collectors.toMap(id -> id, id -> List.of()));
        }
        Set<Long> roleIds = links.stream().map(SysUserRole::getRoleId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> codeById = roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleCode));
        Map<Long, List<String>> result = new HashMap<>();
        for (SysUserRole link : links) {
            String code = codeById.get(link.getRoleId());
            if (code != null) {
                result.computeIfAbsent(link.getUserId(), k -> new ArrayList<>()).add(code);
            }
        }
        userIds.forEach(id -> result.putIfAbsent(id, Collections.emptyList()));
        return result;
    }

    /** 角色码对应的角色 id；供用户管理分配角色时换算（角色目录很小，直查即可） */
    public SysRole roleOf(String roleCode) {
        return roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode).last("limit 1"));
    }
}
