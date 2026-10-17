package com.zhikao.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户新增 / 修改参数。
 * id 为空表示新增，不为空表示修改。
 */
@Data
public class UserSaveDTO {

    /** 主键：为空表示新增，不为空表示修改 */
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过 50")
    private String username;

    /** 密码：新增时留空则使用默认密码；修改时留空表示不修改密码 */
    @Size(min = 6, max = 32, message = "密码长度需为 6~32 位")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名长度不能超过 50")
    private String realName;

    @NotNull(message = "请选择角色")
    private Integer userType;

    /**
     * 可选：RBAC 细粒度角色码（sys_role.role_code）。
     * 传了则 persona（userType）按该角色的 persona 同步，并重建该用户的角色关联；
     * 不传则沿用 userType 既有逻辑，仅在用户没有任何关联时补默认角色。
     */
    @Size(max = 30, message = "角色码长度不能超过 30")
    private String roleCode;

    /** 学生归属班级（角色为学生时必填，由 Service 校验） */
    private Long classId;

    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    /** 状态：1启用 0禁用；为空时按启用处理 */
    private Integer status;
}
