package com.zhikao.modules.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.entity.Question;
import com.zhikao.mapper.QuestionMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public curriculum retrieval: ECS uses DashScope embeddings and an isolated Qdrant collection.
 * The explicit non-vector mode retains the original keyword scoring for callers outside ECS.
 * No unpublished exam answers or student records are sent to the vector collection.
 */
@Component
public class KnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);

    /** 知识源文件 */
    private static final String SOURCE = "ai-knowledge/knowledge-outline.json";

    /** 单次检索最多返回的条目数 */
    private static final int MAX_HITS = 3;

    /** 每个条目附带的历史题目数上限（避免把上下文撑爆） */
    private static final int SAMPLE_QUESTION_LIMIT = 3;

    /** 关键词命中权重：知识点名 > 考法 > 定义 > 常见错误 */
    private static final int WEIGHT_POINT = 10;
    private static final int WEIGHT_TIPS = 4;
    private static final int WEIGHT_DEFINITION = 3;
    private static final int WEIGHT_ERRORS = 2;

    private final QuestionMapper questionMapper;
    private final ObjectMapper objectMapper;
    private final com.zhikao.modules.ai.VectorKnowledgeStore vectors;

    /** 内存中的知识条目（启动时加载一次；内容只读，无需考虑并发写） */
    private final List<KnowledgeEntry> entries = new ArrayList<>();

    public KnowledgeBase(QuestionMapper questionMapper, ObjectMapper objectMapper, com.zhikao.modules.ai.VectorKnowledgeStore vectors) {
        this.questionMapper = questionMapper;
        this.objectMapper = objectMapper;
        this.vectors = vectors;
    }

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(SOURCE).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            for (JsonNode node : root.path("entries")) {
                entries.add(new KnowledgeEntry(
                        node.path("subject").asText(""),
                        node.path("point").asText(""),
                        readList(node.path("prerequisites")),
                        node.path("definition").asText(""),
                        node.path("examTips").asText(""),
                        node.path("commonErrors").asText("")));
            }
            log.info("知识库加载完成：{} 个知识点条目（来源 {}）", entries.size(), SOURCE);
        } catch (Exception e) {
            // 知识库是「增强」而非「必需」：加载失败时退化为空库，不影响系统其他功能
            log.warn("知识库加载失败，检索将返回空结果：{}", e.getMessage());
        }
    }

    /** 全部知识点名（供工具列举，按科目分组） */
    public Map<String, List<String>> outline() {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (KnowledgeEntry entry : entries) {
            grouped.computeIfAbsent(entry.subject, key -> new ArrayList<>()).add(entry.point);
        }
        return grouped;
    }

    /**
     * 检索：先按科目结构化过滤，再按关键词打分排序。
     *
     * @param query   自然语言查询（如「Java 的并发」「索引为什么失效」）
     * @param subject 可选；给出时只在该科目内检索
     * @return Markdown 形式的检索结果，直接可拼进提示词
     */
    public String retrieve(String query, String subject) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        if (vectors.enabled()) return vectors.retrieve(query, subject);
        List<Scored> scored = new ArrayList<>();
        for (KnowledgeEntry entry : entries) {
            if (StringUtils.hasText(subject) && !entry.subject.contains(subject)) {
                continue;
            }
            int score = score(entry, query);
            if (score > 0) {
                scored.add(new Scored(entry, score));
            }
        }
        if (scored.isEmpty()) {
            return "";
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed());

        StringBuilder builder = new StringBuilder();
        builder.append("【知识库检索结果】（来源：本地考纲整理，非向量检索）\n");
        int count = 0;
        for (Scored item : scored) {
            if (count++ >= MAX_HITS) {
                break;
            }
            KnowledgeEntry entry = item.entry();
            builder.append("\n■ ").append(entry.subject).append(" / ").append(entry.point).append("\n");
            if (!entry.prerequisites.isEmpty()) {
                builder.append("  先修：").append(String.join("、", entry.prerequisites)).append("\n");
            }
            builder.append("  定义：").append(entry.definition).append("\n");
            builder.append("  典型考法：").append(entry.examTips).append("\n");
            builder.append("  常见错误：").append(entry.commonErrors).append("\n");

            List<String> samples = sampleQuestions(entry.subject, entry.point);
            if (!samples.isEmpty()) {
                builder.append("  题库中的同类题（请避免重复出题）：\n");
                for (String sample : samples) {
                    builder.append("    - ").append(sample).append("\n");
                }
            }
        }
        return builder.toString();
    }

    /** 命中关键词的权重累加 */
    private int score(KnowledgeEntry entry, String query) {
        int score = 0;
        score += hits(entry.point, query) * WEIGHT_POINT;
        score += hits(entry.examTips, query) * WEIGHT_TIPS;
        score += hits(entry.definition, query) * WEIGHT_DEFINITION;
        score += hits(entry.commonErrors, query) * WEIGHT_ERRORS;
        // 科目名直接出现在查询里时给一点加成（「Java 的集合」应更偏向 Java 科目）
        if (query.contains(entry.subject)) {
            score += 5;
        }
        return score;
    }

    /**
     * 关键词命中次数。
     * 切词策略刻意简单：按非中文/非字母数字切分，再对中文做「按知识点名整体包含」判断——
     * 中文不引入分词器，避免为一个小知识库背上词典依赖。
     */
    private int hits(String text, String query) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int count = 0;
        for (String token : query.split("[\\s,，、。;；:：/\\\\|()（）\\[\\]【】]+")) {
            if (token.length() >= 2 && text.contains(token)) {
                count++;
            }
        }
        // 中文长查询：整体包含也计一次
        if (query.length() >= 2 && text.contains(query.trim())) {
            count++;
        }
        return count;
    }

    /** 从题库取该知识点下的样例题干（截断），用于查重与风格对齐 */
    private List<String> sampleQuestions(String subject, String point) {
        try {
            IPage<Question> page = questionMapper.selectPage(new Page<>(1, SAMPLE_QUESTION_LIMIT),
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<Question>lambdaQuery()
                            .eq(Question::getKnowledgePoint, point)
                            .orderByDesc(Question::getId));
            List<String> result = new ArrayList<>();
            for (Question question : page.getRecords()) {
                String content = question.getContent() == null ? "" : question.getContent();
                if (content.length() > 60) {
                    content = content.substring(0, 60) + "...";
                }
                result.add(content);
            }
            return result;
        } catch (Exception e) {
            // 检索增强失败不影响主流程
            log.debug("知识库附带样例题失败：{}", e.getMessage());
            return List.of();
        }
    }

    private List<String> readList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> result.add(item.asText()));
        }
        return result;
    }

    /** 一条知识条目 */
    private record KnowledgeEntry(String subject, String point, List<String> prerequisites,
                                  String definition, String examTips, String commonErrors) {
    }

    /** 带分数的检索结果 */
    private record Scored(KnowledgeEntry entry, int score) {
    }
}
