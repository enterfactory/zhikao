package com.zhikao.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 Controller 的「写操作」方法上，由 {@code OperLogAspect} 切入采集。
 *
 * 用法：
 * <pre>
 *   &#64;OperLog(module = "用户管理", action = "新增用户")
 *   &#64;PostMapping
 *   public R&lt;Void&gt; save(...) { ... }
 * </pre>
 *
 * 设计取舍：用注解显式声明模块与动作，而不是从方法名反射猜测——
 * 猜出来的「save / update」对审计毫无意义，人工声明的中文语义才是能查到东西的。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 业务模块，如「用户管理」 */
    String module();

    /** 操作动作，如「新增用户」 */
    String action();

    /** 是否记录请求参数摘要（默认记录，敏感字段会被强制脱敏） */
    boolean saveParams() default true;
}
