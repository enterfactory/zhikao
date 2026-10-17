package com.zhikao.common;

/**
 * 统一错误码。
 *
 * 分段约定：
 * <pre>
 *   200      成功
 *   4xxxx    客户端问题（参数 / 鉴权 / 限流）
 *   5xxxx    服务端问题（业务失败 / 系统异常）
 * </pre>
 *
 * 与 HTTP 状态码的关系：本项目统一返回 HTTP 200，业务结果由 body.code 表达，
 * 这样前端拦截器只在一处判断即可，避免「HTTP 层 + 业务层」两套错误语义打架。
 */
public enum ErrorCode {

    SUCCESS(200, "成功"),

    /** 参数校验失败、参数格式错误 */
    PARAM_ERROR(40001, "参数错误"),

    /** 未登录 / token 过期 */
    UNAUTHORIZED(40101, "未登录或登录已过期"),

    /** 角色或权限不足 */
    FORBIDDEN(40301, "没有操作权限"),

    /** 资源不存在 */
    NOT_FOUND(40401, "资源不存在"),

    /** 触发限流（令牌桶空） */
    RATE_LIMITED(42901, "操作过于频繁，请稍后再试"),

    /** 账号因连续登录失败被临时锁定 */
    ACCOUNT_LOCKED(42902, "账号已被临时锁定，请稍后再试"),

    /** 业务规则拒绝（如重名、状态非法） */
    BUSINESS_ERROR(50001, "业务处理失败"),

    /** 未预期的系统异常 */
    SYSTEM_ERROR(50000, "系统异常");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
