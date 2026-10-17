package com.zhikao.modules.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhikao.common.QuestionTypeEnum;
import com.zhikao.modules.ai.vo.QuestionDraftVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 把大模型输出的「题目 JSON」解析成结构化草稿。
 *
 * <p><b>为什么要单独写一个容错解析器，而不是直接 {@code objectMapper.readValue}？</b>
 * 因为实测下来模型的输出几乎不会百分百符合要求，常见的偏差有：
 * <ul>
 *   <li>包了 Markdown 代码块（{@code ```json ... ```}），哪怕提示词里明确说了不要；</li>
 *   <li>前后多一段「好的，以下是题目：」的寒暄；</li>
 *   <li>把数组包在 {@code {"questions": [...]}} 里；</li>
 *   <li>选项写成数组 {@code ["A. xx", "B. yy"]} 而不是对象；</li>
 *   <li>难度写成字符串 {@code "3"}；</li>
 *   <li>答案写成 {@code "A、B"} 或 {@code "A,B"} 而不是 {@code "AB"}。</li>
 * </ul>
 * 直接反序列化会在第一种情况下就整批失败。这个解析器逐层降级：
 * 先剥壳，再定位 JSON，再逐字段宽松取值；单条题目失败只跳过那一条，
 * 不影响整批——「10 道里解析出 8 道」比「一道没有」有用得多。
 *
 * <p><b>这里同时承担了「入库前校验」。</b>校验放在解析阶段而不是入库阶段，
 * 是为了让教师在勾选前就看到「这道题少了选项」，而不是点了导入才收到一条报错。
 */
@Component
public class AiQuestionDraftParser {

    private static final Logger log = LoggerFactory.getLogger(AiQuestionDraftParser.class);

    /** 单选题至少要有这么多选项 */
    private static final int MIN_OPTIONS = 2;

    /** 题型未给出时，按提示词里请求的题型兜底 */
    private final ObjectMapper objectMapper;

    public AiQuestionDraftParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 解析结果：草稿列表 + 一句人话说明 */
    public record Parsed(List<QuestionDraftVO> drafts, String note) {
    }

    /**
     * @param raw         模型原始输出
     * @param fallbackType 请求时指定的题型（模型没写 type 时用它兜底）
     * @param fallbackScore 请求时指定的分值
     */
    public Parsed parse(String raw, Integer fallbackType, BigDecimal fallbackScore) {
        if (!StringUtils.hasText(raw)) {
            return new Parsed(List.of(), "模型没有返回内容");
        }
        JsonNode array = locateArray(raw);
        if (array == null || !array.isArray() || array.isEmpty()) {
            return new Parsed(List.of(), "模型返回的内容不是 JSON 数组，已按原文展示，可手动整理后新建题目");
        }

        List<QuestionDraftVO> drafts = new ArrayList<>();
        int skipped = 0;
        for (JsonNode node : array) {
            QuestionDraftVO draft = toDraft(node, fallbackType, fallbackScore);
            if (draft == null) {
                skipped++;
                continue;
            }
            drafts.add(draft);
        }

        String note = "共解析出 %d 道题目".formatted(drafts.size());
        if (skipped > 0) {
            note += "，另有 %d 条因缺少题干被跳过".formatted(skipped);
        }
        return new Parsed(drafts, note);
    }

    // ==================== 定位 JSON ====================

    /**
     * 在任意文本中定位题目数组。
     * 逐层降级：直接 parse → 剥代码块 → 截取首个 '[' 到末个 ']' → 找对象里的第一个数组字段。
     */
    private JsonNode locateArray(String raw) {
        String text = raw.trim();

        JsonNode direct = tryRead(text);
        if (direct != null) {
            JsonNode array = unwrap(direct);
            if (array != null) {
                return array;
            }
        }

        String stripped = stripCodeFence(text);
        if (!stripped.equals(text)) {
            JsonNode node = tryRead(stripped);
            if (node != null) {
                JsonNode array = unwrap(node);
                if (array != null) {
                    return array;
                }
            }
        }

        // 从两端向中间收缩，截出最外层的 []。比正则可靠，且能正确处理嵌套对象
        int start = stripped.indexOf('[');
        int end = stripped.lastIndexOf(']');
        if (start >= 0 && end > start) {
            JsonNode node = tryRead(stripped.substring(start, end + 1));
            if (node != null && node.isArray()) {
                return node;
            }
        }
        log.debug("模型输出未能定位到 JSON 数组，前 200 字符：{}",
                stripped.length() > 200 ? stripped.substring(0, 200) : stripped);
        return null;
    }

    /** 数组本身就是答案；若是对象，取其中第一个数组型字段（兼容 {"questions": [...]}） */
    private JsonNode unwrap(JsonNode node) {
        if (node.isArray()) {
            return node;
        }
        if (node.isObject()) {
            for (JsonNode child : node) {
                if (child.isArray()) {
                    return child;
                }
            }
        }
        return null;
    }

    /** 去掉 ```json ... ``` 外壳 */
    private String stripCodeFence(String text) {
        if (!text.startsWith("```")) {
            // 也可能是「寒暄\n```json\n[...]\n```」，这里找出代码块内容
            int fence = text.indexOf("```");
            if (fence < 0) {
                return text;
            }
            return extractFenceBody(text, fence);
        }
        return extractFenceBody(text, 0);
    }

    private String extractFenceBody(String text, int fenceStart) {
        int bodyStart = text.indexOf('\n', fenceStart);
        if (bodyStart < 0) {
            return text;
        }
        int bodyEnd = text.lastIndexOf("```");
        if (bodyEnd <= bodyStart) {
            return text.substring(bodyStart + 1);
        }
        return text.substring(bodyStart + 1, bodyEnd);
    }

    private JsonNode tryRead(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 单条题目 ====================

    private QuestionDraftVO toDraft(JsonNode node, Integer fallbackType, BigDecimal fallbackScore) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String content = text(node, "content", "题干", "question", "title");
        if (!StringUtils.hasText(content)) {
            return null;
        }

        QuestionDraftVO draft = new QuestionDraftVO();
        draft.setContent(content.trim());
        draft.setAnswer(text(node, "answer", "答案", "correctAnswer", "standardAnswer"));
        draft.setAnalysis(text(node, "analysis", "解析", "explanation"));
        draft.setKnowledgePoint(text(node, "knowledgePoint", "知识点", "point"));

        Integer type = intValue(node, "type", "题型");
        draft.setType(QuestionTypeEnum.of(type) != null ? type : fallbackType);

        Integer difficulty = intValue(node, "difficulty", "难度");
        draft.setDifficulty(difficulty == null ? 3 : Math.min(5, Math.max(1, difficulty)));

        BigDecimal score = decimalValue(node, "score", "分值");
        draft.setScore(score != null ? score : fallbackScore);

        draft.setOptions(optionsOf(node));

        validate(draft);
        return draft;
    }

    /** 选项：既支持 {"A":"xx"}，也支持 ["A. xx", "B. yy"] 或 ["xx","yy"] */
    private Map<String, String> optionsOf(JsonNode node) {
        JsonNode options = node.has("options") ? node.get("options")
                : node.has("选项") ? node.get("选项") : null;
        Map<String, String> result = new LinkedHashMap<>();
        if (options == null || options.isNull()) {
            return result;
        }

        if (options.isObject()) {
            // 统一成有序的 A/B/C/D：模型偶尔会乱序输出，选项顺序对作答体验有影响
            TreeSet<String> keys = new TreeSet<>();
            options.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                String value = options.get(key).asText("");
                if (StringUtils.hasText(value)) {
                    result.put(normalizeLabel(key), value.trim());
                }
            }
            return result;
        }

        if (options.isArray()) {
            char label = 'A';
            for (JsonNode item : options) {
                String value = item.asText("");
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                String trimmed = value.trim();
                // "A. 选项内容" / "A、选项内容" → 抽出真实标签，丢掉重复的前缀
                String detected = leadingLabel(trimmed);
                String finalLabel;
                String finalText;
                if (detected != null) {
                    finalLabel = detected;
                    finalText = trimmed.substring(1).replaceFirst("^[.、,，:：)\\s]+", "").trim();
                } else {
                    finalLabel = String.valueOf(label);
                    finalText = trimmed;
                }
                result.put(finalLabel, finalText);
                label++;
            }
        }
        return result;
    }

    /** 识别 "A. xxx" 这类前缀里的标签 */
    private String leadingLabel(String text) {
        if (text.length() >= 2 && Character.isUpperCase(text.charAt(0))
                && ".、,，:：)".indexOf(text.charAt(1)) >= 0) {
            return String.valueOf(text.charAt(0));
        }
        return null;
    }

    private String normalizeLabel(String key) {
        String value = key.trim();
        return value.isEmpty() ? "" : value.substring(0, 1).toUpperCase();
    }

    // ==================== 校验 ====================

    /**
     * 入库前的校验。这里的判断刻意比手工新建更严：
     * 人工录入时填错字段会立刻看到提示，而 AI 草稿是批量入库的，
     * 放进去一道「没有选项的单选题」会直接坏掉一场考试。
     */
    private void validate(QuestionDraftVO draft) {
        QuestionTypeEnum type = QuestionTypeEnum.of(draft.getType());

        if (!StringUtils.hasText(draft.getAnswer())) {
            draft.invalidate("缺少参考答案");
        }
        if (!StringUtils.hasText(draft.getKnowledgePoint())) {
            draft.warn("未标注知识点，学情分析里会归到「未分类」");
        }
        if (draft.getScore() == null || draft.getScore().compareTo(BigDecimal.ZERO) <= 0) {
            draft.invalidate("缺少分值");
        }
        if (type == null) {
            draft.invalidate("题型无法识别");
            return;
        }

        switch (type) {
            case SINGLE -> {
                if (draft.getOptions().size() < MIN_OPTIONS) {
                    draft.invalidate("单选题至少需要 2 个选项，当前只有 " + draft.getOptions().size() + " 个");
                } else if (!draft.getOptions().containsKey(draft.getAnswer())) {
                    draft.invalidate("答案「" + draft.getAnswer() + "」不在选项中");
                }
            }
            case MULTIPLE -> {
                if (draft.getOptions().size() < MIN_OPTIONS) {
                    draft.invalidate("多选题至少需要 2 个选项");
                    break;
                }
                // "A、B" / "A,B" / "ab" 统一成 "AB" 再校验
                String normalized = draft.getAnswer().replaceAll("[^A-Za-z]", "").toUpperCase();
                if (normalized.length() < 2) {
                    draft.invalidate("多选题答案应包含 2 个及以上选项，当前为「" + draft.getAnswer() + "」");
                    break;
                }
                for (char c : normalized.toCharArray()) {
                    if (!draft.getOptions().containsKey(String.valueOf(c))) {
                        draft.invalidate("答案包含选项「" + c + "」，但该选项不存在");
                        break;
                    }
                }
                draft.setAnswer(normalized);
            }
            case JUDGE -> {
                String answer = draft.getAnswer().trim();
                if (!answer.equals("对") && !answer.equals("错")
                        && !answer.equals("正确") && !answer.equals("错误")
                        && !answer.equalsIgnoreCase("true") && !answer.equalsIgnoreCase("false")) {
                    draft.invalidate("判断题答案应为「对」或「错」，当前为「" + answer + "」");
                } else {
                    draft.setAnswer(answer.startsWith("对") || answer.equalsIgnoreCase("true") ? "对" : "错");
                }
            }
            case BLANK -> {
                if (!StringUtils.hasText(draft.getAnswer())) {
                    draft.invalidate("填空题缺少参考答案");
                } else if (!draft.getAnswer().contains("|") && !draft.getAnswer().contains(";")) {
                    draft.warn("多个空请用 ; 分隔、同一空多个可接受答案用 | 分隔，否则只能整串精确匹配");
                }
            }
            case ESSAY -> {
                // 主观题答案由教师按要点给分，这里只提醒参考答案偏短
                if (draft.getAnswer().length() < 10) {
                    draft.warn("主观题参考答案偏短，建议补充评分要点");
                }
            }
            default -> {
            }
        }
    }

    // ==================== 宽松取值 ====================

    /** 依次尝试多个字段名（中英文都试），返回第一个非空文本 */
    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                String text = value.asText("");
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    /** 取整数，兼容 "3" / 3.0 这类写法 */
    private Integer intValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asInt();
            }
            String text = value.asText("").trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                return new BigDecimal(text).intValue();
            } catch (NumberFormatException e) {
                // 试下一个字段名
            }
        }
        return null;
    }

    private BigDecimal decimalValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.decimalValue();
            }
            String text = value.asText("").trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                // 试下一个字段名
            }
        }
        return null;
    }
}
