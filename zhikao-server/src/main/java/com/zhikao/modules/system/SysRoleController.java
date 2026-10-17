package com.zhikao.modules.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zhikao.common.R;
import com.zhikao.common.BusinessException;
import com.zhikao.entity.SysRole;
import com.zhikao.modules.system.vo.RoleVO;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口：RBAC 改造后角色不再是「仅展示用的字典」——
 * 提供真实角色目录（id/code/name/persona）供用户表单的角色下拉与角色配置页使用。
 * 按 system:role:view 授权（五个角色除学生外都持有，下拉对管理侧与教学侧都可用）。
 */
@RestController
@RequestMapping("/role")
@SaCheckPermission("system:role:view")
public class SysRoleController {

    private final SysRoleService roleService;
    private final RbacService rbacService;

    public SysRoleController(SysRoleService roleService, RbacService rbacService) {
        this.roleService = roleService;
        this.rbacService = rbacService;
    }

    /** 角色列表（启用角色，按 sort 升序） */
    @GetMapping("/list")
    public R<List<RoleVO>> list() {
        return R.ok(roleService.list().stream().map(this::toVO).toList());
    }

    /** Read-only permission matrix, using the same policy as authentication. */
    @GetMapping("/{id}/permissions")
    public R<List<String>> permissions(@PathVariable Long id) {
        SysRole role = roleService.list().stream()
                .filter(item -> id.equals(item.getId())).findFirst()
                .orElseThrow(() -> new BusinessException("角色不存在"));
        return R.ok(rbacService.permissionCodesOf(List.of(role.getRoleCode())));
    }

    /** 实体 -> 轻量视图：接口契约只暴露前端选择器需要的四个字段 */
    private RoleVO toVO(SysRole entity) {
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
