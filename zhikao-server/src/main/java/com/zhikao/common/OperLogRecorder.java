package com.zhikao.common;

import com.zhikao.entity.SysOperLog;
import com.zhikao.mapper.SysOperLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 操作日志异步落库器。
 *
 * 为什么单独成类：Spring 的 @Async 依赖代理，同类内部自调用不会走异步。
 * 单独抽成一个 Bean，由切面注入调用，才能真正异步。
 */
@Component
public class OperLogRecorder {

    private static final Logger log = LoggerFactory.getLogger(OperLogRecorder.class);

    private final SysOperLogMapper operLogMapper;

    public OperLogRecorder(SysOperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    /**
     * 异步写入一条操作日志。
     * 写入失败只告警、不抛异常——审计日志不能反过来把业务搞挂。
     */
    @Async("operLogExecutor")
    public void record(SysOperLog operLog) {
        try {
            operLogMapper.insert(operLog);
        } catch (Exception e) {
            log.warn("操作日志写入失败：module={}, action={}, err={}",
                    operLog.getModule(), operLog.getAction(), e.getMessage());
        }
    }
}
