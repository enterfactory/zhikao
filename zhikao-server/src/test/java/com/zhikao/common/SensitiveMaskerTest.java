package com.zhikao.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作日志脱敏测试。
 *
 * 这一层是「安全」的兜底：漏一个字段就是明文密码进日志。
 * 所以用测试把常见字段名全部钉死，而不是靠代码评审记得检查。
 */
@DisplayName("敏感信息脱敏")
class SensitiveMaskerTest {

    @Test
    @DisplayName("JSON 中的密码类字段全部替换为 ***")
    void maskJsonFields() {
        String masked = SensitiveMasker.mask(
                "[{\"username\":\"admin\",\"password\":\"123456\",\"newPassword\":\"abc\"}]");
        assertTrue(masked.contains("\"password\":\"***\""), masked);
        assertTrue(masked.contains("\"newPassword\":\"***\""), masked);
        assertTrue(masked.contains("\"username\":\"admin\""), "非敏感字段不应被改动");
    }

    @Test
    @DisplayName("token / apiKey / secret 同样脱敏")
    void maskTokenLikeFields() {
        String masked = SensitiveMasker.mask(
                "{\"token\":\"eyJhbGciOi\",\"apiKey\":\"sk-123\",\"secret\":\"s3cr3t\"}");
        assertTrue(masked.contains("\"token\":\"***\""), masked);
        assertTrue(masked.contains("\"apiKey\":\"***\""), masked);
        assertTrue(masked.contains("\"secret\":\"***\""), masked);
    }

    @Test
    @DisplayName("URL 查询串中的敏感参数也脱敏")
    void maskQueryString() {
        String masked = SensitiveMasker.mask("/auth/login?username=admin&password=123456&remember=1");
        assertTrue(masked.contains("password=***"), masked);
        assertTrue(masked.contains("remember=1"), "普通参数不应被改动");
    }

    @Test
    @DisplayName("大小写不敏感")
    void caseInsensitive() {
        assertTrue(SensitiveMasker.mask("{\"PASSWORD\":\"x\"}").contains("\"***\""));
    }

    @Test
    @DisplayName("无敏感字段与 null 安全返回")
    void safePassThrough() {
        assertEquals("{\"id\":1}", SensitiveMasker.mask("{\"id\":1}"));
        assertEquals(null, SensitiveMasker.mask(null));
        assertEquals("", SensitiveMasker.mask(""));
    }
}
