package com.zhikao.modules.auth;

import com.zhikao.cache.CacheService;
import com.zhikao.common.BusinessException;
import com.zhikao.common.ErrorCode;
import com.zhikao.config.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录防爆破守卫。
 *
 * 两条独立规则：
 * 1. **按账号**：连续失败 {@code loginMaxFailPerAccount} 次 → 锁定 {@code loginLockMinutes} 分钟；
 * 2. **按 IP**：连续失败 {@code loginMaxFailPerIp} 次 → 限制该 IP 登录，防止「撞库」遍历大量账号。
 *
 * <p><b>为什么现在必须放进共享存储：</b>
 * 计数原本在 JVM 内存里。单实例下没问题，但两台实例各自计数时，
 * 攻击者只要在两台之间轮换请求，就能让两边都不曾达到阈值——
 * 防护规则<b>形同虚设</b>，而这恰恰是它唯一要防的场景。
 * 这类"安全限制"一旦可以被横向绕过，比没有更危险：它会给出错误的安全感。
 *
 * <p><b>持久化边界：</b>
 * 计数允许 Redis 丢失后重新从零开始——它不是业务数据，丢了大不了重新计数。
 * 这条判断与交卷幂等正好相反：交卷状态丢了就是真的错。
 * 「按丢失后的后果决定是否必须持久化」，而不是"一律都存"。
 */
@Component
public class LoginGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginGuard.class);

    /** 失败计数前缀 */
    private static final String FAIL_KEY = "login:fail:";
    /** 锁定标记前缀 */
    private static final String LOCK_KEY = "login:lock:";

    private final SecurityProperties security;
    private final CacheService cacheService;

    public LoginGuard(SecurityProperties security, CacheService cacheService) {
        this.security = security;
        this.cacheService = cacheService;
    }

    /**
     * 登录前置检查：账号或 IP 被锁定时直接拒绝（不查库、不比对密码，省下 BCrypt 的开销）。
     */
    public void check(String username, String ip) {
        if (cacheService.get(LOCK_KEY + "acct:" + username) != null) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "账号连续登录失败已被锁定，请 " + security.getLoginLockMinutes() + " 分钟后重试");
        }
        if (cacheService.get(LOCK_KEY + "ip:" + ip) != null) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "当前网络登录失败次数过多，请稍后再试");
        }
    }

    /** 登录成功：清除该账号的失败计数与锁定（IP 计数保留，避免同一 IP 反复试错不同账号） */
    public void onSuccess(String username) {
        cacheService.delete(FAIL_KEY + "acct:" + username);
        cacheService.delete(LOCK_KEY + "acct:" + username);
    }

    /**
     * 登录失败：累计账号与 IP 的失败次数，达到阈值即锁定。
     *
     * <p>失败计数的 TTL 取锁定时长的 2 倍而不是永不过期：
     * 一个 15 分钟前试过两次密码的人，没必要让他的计数跟着账号一辈子，
     * 让过期时间承担清理职责，比自己写回收逻辑更不容易漏。
     */
    public void onFailure(String username, String ip) {
        Duration window = Duration.ofMinutes(security.getLoginLockMinutes() * 2L);
        Duration lockTtl = Duration.ofMinutes(security.getLoginLockMinutes());

        long accountCount = cacheService.increment(FAIL_KEY + "acct:" + username, window);
        if (accountCount >= security.getLoginMaxFailPerAccount()) {
            cacheService.set(LOCK_KEY + "acct:" + username, "1", lockTtl);
            cacheService.delete(FAIL_KEY + "acct:" + username);
            log.warn("账号 {} 连续登录失败 {} 次，已锁定 {} 分钟",
                    username, accountCount, security.getLoginLockMinutes());
        }

        long ipCount = cacheService.increment(FAIL_KEY + "ip:" + ip, window);
        if (ipCount >= security.getLoginMaxFailPerIp()) {
            cacheService.set(LOCK_KEY + "ip:" + ip, "1", lockTtl);
            cacheService.delete(FAIL_KEY + "ip:" + ip);
            log.warn("IP {} 连续登录失败 {} 次，已限制 {} 分钟",
                    ip, ipCount, security.getLoginLockMinutes());
        }
    }
}
