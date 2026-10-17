package com.zhikao.cache;

import org.springframework.data.redis.serializer.StringRedisSerializer;

/** Keeps sessions and application state separate from other projects on a shared Redis. */
public class NamespacedKeySerializer extends StringRedisSerializer {
    private final String prefix;
    public NamespacedKeySerializer(String prefix) {
        if (prefix == null || !prefix.matches("[a-zA-Z0-9:_-]+:"))
            throw new IllegalArgumentException("Redis namespace 必须是以冒号结尾的字母数字前缀");
        this.prefix = prefix;
    }
    @Override public byte[] serialize(String value) { return value == null ? null : super.serialize(prefix + value); }
    @Override public String deserialize(byte[] bytes) {
        String value=super.deserialize(bytes);
        return value!=null&&value.startsWith(prefix)?value.substring(prefix.length()):value;
    }
}
