package com.zhikao.modules.auth;

import com.zhikao.modules.auth.dto.ChangePasswordDTO;
import com.zhikao.modules.auth.dto.LoginDTO;
import com.zhikao.modules.auth.vo.LoginVO;
import com.zhikao.modules.auth.vo.SecurityActivityVO;

import java.util.List;

/**
 * 认证服务：登录、登出、获取当前用户信息。
 */
public interface AuthService {

    /** 登录：校验账号密码并签发 Sa-Token */
    LoginVO login(LoginDTO dto);

    /** 获取当前登录用户信息（含角色） */
    LoginVO info();

    /** 注销当前登录态 */
    void logout();

    /**
     * 修改当前登录用户的密码。
     *
     * <p>校验旧密码 + 新密码强度；改密成功后**强制该账号其他端下线**
     * （只保留当前 token），避免密码已经改了、旧 token 还能继续用。
     */
    void changePassword(ChangePasswordDTO dto);

    /**
     * 本人账号的安全活动（登录/登录失败/登出/改密），供个人设置页「账号与安全」展示。
     *
     * <p>与操作日志分页接口的区别是<b>只查自己</b>：条件里硬性带上当前登录账号，
     * 所以这个接口不需要 system:log:view 那样的管理权限，任何角色都能看自己的登录轨迹。
     * 改密的常见动机就是「怀疑密码泄露」，本人得有一个不用找管理员就能看到
     * 「最近有哪些 IP 登进来过、有没有失败」的入口。
     *
     * @param limit 最多返回几条；空按 12，上限 30
     */
    List<SecurityActivityVO> securityActivity(Integer limit);
}
