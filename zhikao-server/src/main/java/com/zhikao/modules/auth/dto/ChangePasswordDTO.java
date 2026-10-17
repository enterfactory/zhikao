package com.zhikao.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改自己的密码。
 *
 * <p>必须同时提供旧密码：即使 token 被盗用，攻击者也无法在不掌握原密码的情况下
 * 直接把账号「接管」过去——这是改密接口最容易被忽略的一道防线。
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "请输入当前密码")
    private String oldPassword;

    @NotBlank(message = "请输入新密码")
    private String newPassword;
}
