package com.zhikao.common;

import java.util.regex.Pattern;

/**
 * 敏感信息脱敏工具。
 *
 * 用途：操作日志落库前，把请求参数中的密码类字段替换为 ***。
 * 这一层是「强制」的——不依赖每个埋点的人记得脱敏，而是在统一切面里做掉。
 */
public final class SensitiveMasker {

    /**
     * 匹配 JSON 中的敏感字段：password / pwd / token / apiKey 等，
     * 结构形如 "password":"明文" ，替换为 "password":"***"。
     */
    private static final Pattern SENSITIVE_JSON = Pattern.compile(
            "(\"(?:password|pwd|passwd|oldPassword|newPassword|token|apiKey|api_key|secret)\"\\s*:\\s*\")[^\"]*(\")",
            Pattern.CASE_INSENSITIVE);

    /** 匹配 URL 查询串中的敏感参数，形如 &password=明文 */
    private static final Pattern SENSITIVE_QUERY = Pattern.compile(
            "((?:password|pwd|token|apiKey|secret)=)[^&\\s]*",
            Pattern.CASE_INSENSITIVE);

    private SensitiveMasker() {
    }

    /** 对文本做整体脱敏；入参为 null 时原样返回 */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = SENSITIVE_JSON.matcher(text).replaceAll("$1***$2");
        return SENSITIVE_QUERY.matcher(masked).replaceAll("$1***");
    }
}
