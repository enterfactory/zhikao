package com.zhikao.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.NotWebContextException;
import cn.dev33.satoken.exception.SaTokenContextException;
import com.zhikao.config.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：把各类异常统一转换为 {@link R} 结构，
 * 并映射到 {@link ErrorCode}，让前端只依赖一套语义码。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 是否把异常原文返回前端。默认关闭：避免内部术语与 SQL 片段泄露 */
    @Value("${zhikao.error.expose-detail:false}")
    private boolean exposeDetail;

    /** 业务异常：返回明确的业务码与提示 */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 未登录 / 登录过期（Sa-Token 抛出） */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        return R.fail(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMsg());
    }

    /**
     * 当前线程拿不到 Sa-Token 上下文。
     *
     * <p>这类异常的原始信息是「SaTokenContext 上下文尚未初始化」——
     * 对使用者来说完全是噪音，而且容易被误当成系统崩了。
     * 它的真实语义只有一个：<b>这个执行线程上没有登录态</b>，
     * 因此按 40101 返回，让前端走「重新登录」而不是「系统异常」。
     *
     * <p>正常情况下 {@link LoginUtil} 的快照兜底已经把这个分支消掉了，
     * 这里保留处理是为了「万一还有遗漏的线程」，也不至于把内部术语吐给用户。
     */
    @ExceptionHandler({SaTokenContextException.class, NotWebContextException.class})
    public R<Void> handleNoContext(Exception e) {
        log.warn("当前线程缺少 Sa-Token 上下文（{}），按未登录处理；线程={}",
                e.getClass().getSimpleName(), Thread.currentThread().getName());
        return R.fail(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMsg());
    }

    /** 角色或权限不足（Sa-Token 的 @SaCheckRole / @SaCheckPermission 抛出） */
    @ExceptionHandler({NotRoleException.class, NotPermissionException.class})
    public R<Void> handleNoPermission(Exception e) {
        return R.fail(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMsg());
    }

    /** 参数校验失败（@Valid 作用于 @RequestBody 时抛出的异常） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : ErrorCode.PARAM_ERROR.getMsg();
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /** 参数绑定失败（表单 / 查询参数） */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : ErrorCode.PARAM_ERROR.getMsg();
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /** 必填参数缺失 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), "缺少必填参数：" + e.getParameterName());
    }

    /** 参数类型不匹配，如把「abc」当数字传 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), "参数格式不正确：" + e.getName());
    }

    /**
     * 其他未捕获异常。
     *
     * <p>这里**刻意不把异常原文返回给前端**（三期修正）：原来的实现是
     * {@code SYSTEM_ERROR.getMsg() + "：" + e.getMessage()}，于是用户会看到
     * 「系统异常：SaTokenContext 上下文尚未初始化」这种内部术语、
     * 甚至是 SQL 片段——既不专业也是信息泄露。
     *
     * <p>现在返回「系统异常（追踪号 xxx）」：用户把追踪号给到运维，
     * 后端一行 grep 就能定位到完整堆栈。既安全又可排查。
     * 需要临时排障时把 {@code zhikao.error.expose-detail} 打开即可。
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        log.error("系统异常 [traceId={}]", traceId, e);
        if (exposeDetail) {
            return R.fail(ErrorCode.SYSTEM_ERROR.getCode(),
                    ErrorCode.SYSTEM_ERROR.getMsg() + "：" + e.getMessage());
        }
        return R.fail(ErrorCode.SYSTEM_ERROR.getCode(),
                ErrorCode.SYSTEM_ERROR.getMsg() + "（追踪号 " + (traceId == null ? "-" : traceId) + "）");
    }
}
