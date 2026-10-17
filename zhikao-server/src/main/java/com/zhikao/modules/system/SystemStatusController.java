package com.zhikao.modules.system;

import com.zhikao.cache.CacheService;
import com.zhikao.common.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行状态查询。
 *
 * <p>存在的理由只有一个：<b>部署后要能确认 Redis 到底用上没有，而不是靠猜。</b>
 * 共享存储是「连不上就回落」的设计，回落过程不报错——这本意是让本机好跑，
 * 但也意味着「配错了 Redis 地址」和「Redis 用上了」在界面上长得一模一样。
 * 没有一个明确的查询口，线上就只能靠翻日志判断。
 *
 * <p>只读、不含任何凭证信息，因此对管理员与教师开放；学生没必要看这些。
 */
@RestController
@RequestMapping("/system")
public class SystemStatusController {

    private final CacheService cacheService;

    @Value("${zhikao.db-type:H2}")
    private String dbType;

    public SystemStatusController(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /** 运行期状态快照 */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cacheMode", cacheService.mode());
        data.put("redisAlive", cacheService.redisAlive());
        data.put("dbType", dbType);
        data.put("serverTime", LocalDateTime.now().toString());
        return R.ok(data);
    }
}
