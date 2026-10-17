package com.zhikao.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhikao.entity.SysOperLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志切面：拦截 {@link OperLog} 标注的方法，采集「谁、什么时候、对什么模块做了什么、结果如何、耗时多少」。
 *
 * 三个刻意的设计：
 * 1. **不吞异常**：切面记录失败后原样抛出，业务语义不变（审计不该改变业务行为）；
 * 2. **参数摘要化 + 强制脱敏**：只记业务参数，文件与 Servlet 对象跳过，密码类字段替换为 ***；
 * 3. **落库异步**：切面只做廉价的字符串拼装，真正的 INSERT 交给有界线程池，不占用业务响应时间。
 */
@Aspect
@Component
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);

    /** 参数摘要最大长度：审计关心「做了什么」，不关心完整报文 */
    private static final int PARAMS_MAX_LENGTH = 2000;

    /** 错误信息最大长度 */
    private static final int ERROR_MAX_LENGTH = 500;

    private final OperLogRecorder recorder;
    private final ObjectMapper objectMapper;

    public OperLogAspect(OperLogRecorder recorder, ObjectMapper objectMapper) {
        this.recorder = recorder;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            save(joinPoint, operLog, start, 1, null);
            return result;
        } catch (Throwable e) {
            // 先记录审计，再把异常原样抛出，保证业务行为不被切面改变
            save(joinPoint, operLog, start, 0, e.getMessage());
            throw e;
        }
    }

    private void save(ProceedingJoinPoint joinPoint, OperLog operLog,
                      long start, int status, String errorMsg) {
        try {
            HttpServletRequest request = WebUtil.currentRequest();
            SysOperLog.SysOperLogBuilder builder = SysOperLog.builder()
                    .module(operLog.module())
                    .action(operLog.action())
                    .method(request == null ? "" : request.getMethod())
                    .requestUri(request == null ? "" : request.getRequestURI())
                    .operName(LoginUtil.username())
                    .operRole(LoginUtil.roleCode())
                    .operIp(WebUtil.clientIp())
                    .resultStatus(status)
                    .errorMsg(truncate(errorMsg, ERROR_MAX_LENGTH))
                    .costMs(System.currentTimeMillis() - start)
                    .operTime(LocalDateTime.now());

            if (operLog.saveParams()) {
                builder.params(truncate(serializeArgs(joinPoint.getArgs()), PARAMS_MAX_LENGTH));
            }
            recorder.record(builder.build());
        } catch (Exception e) {
            // 审计采集自身出错不能影响业务
            log.warn("操作日志采集失败：{}", e.getMessage());
        }
    }

    /** 序列化业务参数：跳过文件与 Servlet 对象，随后统一脱敏 */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        List<Object> businessArgs = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof MultipartFile || arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse) {
                businessArgs.add("[已忽略：" + arg.getClass().getSimpleName() + "]");
                continue;
            }
            businessArgs.add(arg);
        }
        try {
            return SensitiveMasker.mask(objectMapper.writeValueAsString(businessArgs));
        } catch (Exception e) {
            return "[参数序列化失败：" + e.getMessage() + "]";
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...(已截断)";
    }
}
