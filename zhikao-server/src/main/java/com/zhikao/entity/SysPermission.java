package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限点（RBAC 改造新增）。
 * perm_code 即 @SaCheckPermission 的取值，命名「模块[:子域]:动作」；
 * 目录由 DataInitializer 按固定清单 seed，不在页面随意增删——权限点与代码注解必须一一对应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限标识，如 question:edit */
    private String permCode;

    /** 中文名，供角色配置页展示 */
    private String permName;

    /** 所属模块：system / question / exam / ... 分组渲染用 */
    private String module;

    private Integer sort;
}
