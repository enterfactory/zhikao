package com.zhikao.modules.system.vo;

import lombok.Data;

/**
 * 角色下拉视图：id / code / name / persona 四个前端选择器需要的字段，
 * 不把实体直接外放到接口契约里。
 */
@Data
public class RoleVO {

    private Long id;

    /** Sa-Token 角色标识，即 sys_role.role_code */
    private String roleCode;

    private String roleName;

    /** 粗粒度身份：1管理 2教师 3学生；前端可据此分组展示 */
    private Integer persona;
}
