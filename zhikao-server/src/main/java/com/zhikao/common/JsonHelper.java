package com.zhikao.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 轻量 JSON 工具：用于题目选项等「结构化字段存字符串」的序列化 / 反序列化。
 * 选项在库中以 JSON 字符串存储（H2 无 JSON 类型，生产 MySQL 也为兼容保留字符串）。
 */
public final class JsonHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonHelper() {
    }

    /** 对象转 JSON 字符串；失败返回 null（不抛出，避免影响主流程） */
    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** JSON 字符串转「有序 String -> String」映射；空值或解析失败返回空 Map */
    public static Map<String, String> toOptionMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
