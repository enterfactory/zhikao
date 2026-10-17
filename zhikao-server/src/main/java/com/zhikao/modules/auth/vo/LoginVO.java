package com.zhikao.modules.auth.vo;

import lombok.Data;

/**
 * 登录返回：token + 用户信息。
 */
@Data
public class LoginVO {

    private String token;
    private UserInfoVO userInfo;
}
