package com.zhikao.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一接口返回结构。
 * 所有 Controller 返回值统一包装为 R，前端按 code 判断业务结果（200 成功）。
 *
 * @param <T> 业务数据类型
 */
@Data
public class R<T> implements Serializable {

    /** 业务码：200 成功，非 200 为失败 */
    private int code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private T data;

    /** 成功（带数据） */
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 200;
        r.msg = "success";
        r.data = data;
        return r;
    }

    /** 成功（无数据） */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /** 失败（默认码 500） */
    public static <T> R<T> fail(String msg) {
        return fail(500, msg);
    }

    /** 失败（指定码与信息） */
    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
