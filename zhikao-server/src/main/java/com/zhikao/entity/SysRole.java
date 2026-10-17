package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色（RBAC 改造后为 5 个细粒度内置角色）。
 * role_code 即 Sa-Token 角色标识；persona 是粗粒度身份（与 sys_user.user_type 同义），
 * DataScope / AI 配额等既有逻辑仍按 persona 分流，故两者并存。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Sa-Token 角色标识：super_admin / edu_admin / subject_lead / teacher / student */
    private String roleCode;

    private String roleName;

    /** 粗粒度身份：1管理 2教师 3学生 */
    private Integer persona;

    /** 展示顺序，越小越靠前 */
    private Integer sort;
}
