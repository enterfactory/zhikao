package com.zhikao.modules.system;

import com.zhikao.entity.SysRole;

import java.util.List;

/**
 * 角色服务：RBAC 改造后角色是真实的能力集合，本服务提供角色目录查询。
 */
public interface SysRoleService {

    /** 全部启用角色（按 sort 升序），供前端角色下拉 */
    List<SysRole> list();
}
