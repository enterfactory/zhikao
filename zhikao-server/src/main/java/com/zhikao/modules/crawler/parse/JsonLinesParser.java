package com.zhikao.modules.crawler.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * JSON 行解析器：逐行读取 {@code {type,content,options,answer,analysis,grade,subject}} 直入。
 *
 * <p>适用 .json / .jsonl：一行一道题，是「机器生成的题库」最常见的落盘格式。
 * 单行解析失败只跳过该行（其它行照常），绝不因一行坏数据整篇作废。
 * options 支持对象形态 {@code {"A":"..","B":".."}}，也兼容数组形态 {@code ["甲","乙"]}（自动转成 A/B/…）。
 */
@Component
public class JsonLinesParser implements QuestionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean supports(String content) {
        if (content == null) {
            return false;
        }
        // 首个非空行以 { 开头，认定为 JSON 行格式
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            return trimmed.startsWith("{");
        }
        return false;
    }

    @Override
    public int order() {
        return 10; // 结构化优先于模板解析
    }

    @Override
    public List<ParsedQuestion> parse(String content) {
        List<ParsedQuestion> result = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                JsonNode node = MAPPER.readTree(trimmed);
                result.add(toParsed(node));
            } catch (Exception e) {
                // 坏行跳过，不影响其它行；原文已留在 crawl_raw，可事后追查
            }
        }
        return result;
    }

    private ParsedQuestion toParsed(JsonNode node) {
        ParsedQuestion q = new ParsedQuestion();
        if (node.hasNonNull("type")) {
            q.setQuestionType(node.get("type").asInt());
        } else if (node.hasNonNull("questionType")) {
            q.setQuestionType(node.get("questionType").asInt());
        }
        q.setContent(firstNonNull(text(node, "content"), text(node, "question")));
        q.setAnswer(text(node, "answer"));
        q.setAnalysis(text(node, "analysis"));
        q.setGrade(text(node, "grade"));
        q.setGradeLevel(text(node, "gradeLevel"));
        q.setSubjectName(text(node, "subject"));
        q.setKnowledgeHint(text(node, "knowledge"));
        JsonNode options = node.get("options");
        if (options != null && options.isObject()) {
            Iterator<String> keys = options.fieldNames();
            while (keys.hasNext()) {
                String key = keys.next();
                q.getOptions().put(key.toUpperCase(), options.get(key).asText());
            }
        } else if (options != null && options.isArray()) {
            // 数组形态：按位置映射到 A/B/C/D…
            int index = 0;
            for (JsonNode item : options) {
                q.getOptions().put(String.valueOf((char) ('A' + index++)), item.asText());
            }
        }
        return q;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** opencompass 等公开题库用 question 字段，作为 content 的兜底别名 */
    private String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
