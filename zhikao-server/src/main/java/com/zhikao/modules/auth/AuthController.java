package com.zhikao.modules.auth;

import com.zhikao.common.R;
import com.zhikao.common.RateLimit;
import com.zhikao.modules.auth.dto.ChangePasswordDTO;
import com.zhikao.modules.auth.dto.LoginDTO;
import com.zhikao.modules.auth.vo.LoginVO;
import com.zhikao.modules.auth.vo.SecurityActivityVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录：返回 token 与用户信息。
     *
     * 限流口径说明：**防爆破不靠这个令牌桶**，而是靠 {@link LoginGuard} 的
     * 「按账号连续失败 5 次锁定 + 按 IP 累计失败 20 次限制」——后者只统计失败，
     * 不会误伤正常登录。这里的令牌桶只用于挡住「单 IP 高频硬打」这类 DoS，
     * 因此容量给得比较宽：给紧会先把批量导入后逐个登录的压测脚本打死，
     * 而真正的暴力破解仍会在 LoginGuard 那里被拦下。
     */
    @PostMapping("/login")
    @RateLimit(key = "login", capacity = 300, refillPerSecond = 10.0,
            message = "登录过于频繁，请稍后再试")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /** 获取当前登录用户信息（需登录，由 Sa-Token 拦截器保障） */
    @GetMapping("/info")
    public R<LoginVO> info() {
        return R.ok(authService.info());
    }

    /** 登出：注销当前 token */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    /**
     * 本人账号的安全活动（登录 / 登录失败 / 登出 / 改密），个人设置页「账号与安全」用。
     * 只查当前登录账号自己的记录，所以任何已登录角色都能调，不需要操作日志的管理权限。
     */
    @GetMapping("/security-activity")
    public R<List<SecurityActivityVO>> securityActivity(
            @RequestParam(required = false) Integer limit) {
        return R.ok(authService.securityActivity(limit));
    }

    /**
     * 修改自己的密码（任何已登录角色都可用）。
     * 必须提供旧密码：token 被盗用不等于账号可被接管。
     */
    @PostMapping("/password/change")
    @RateLimit(key = "changePwd", capacity = 5, refillPerSecond = 0.05,
            message = "修改密码过于频繁，请稍后再试")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(dto);
        return R.ok();
    }
}
