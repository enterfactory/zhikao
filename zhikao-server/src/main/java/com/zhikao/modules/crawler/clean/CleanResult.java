package com.zhikao.modules.crawler.clean;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 清洗产物：归一化后的题目字段 + 质量判定结果。
 *
 * <p>passed=false 表示有结构性硬伤（如题干过短），流水线不会为它生成待审记录；
 * qualityFlags 里的 DUPLICATE / OPTIONS_MISMATCH 只作标记，记录仍保留给人复核。
 */
@Data
public class CleanResult {

    private Integer questionType;
    private String content;
    private Map<String, String> options = new LinkedHashMap<>();
    private String answer;
    private String analysis;

    /** 归一化内容的 SHA-256（去重键） */
    private String contentHash;

    /** 质量标记：SHORT_CONTENT / OPTIONS_MISMATCH / DUPLICATE */
    private List<String> qualityFlags = new ArrayList<>();

    /** 是否通过结构性硬校验（题干非空且足够长） */
    private boolean passed;
}
