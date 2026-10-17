package com.zhikao.modules.crawler.classify;

import com.zhikao.entity.KnowledgePoint;
import com.zhikao.entity.Subject;
import com.zhikao.mapper.KnowledgePointMapper;
import com.zhikao.mapper.SubjectMapper;
import com.zhikao.modules.crawler.clean.CleanResult;
import com.zhikao.modules.crawler.parse.ParsedQuestion;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 归类打标器（六期）：给清洗后的题目补「学段 / 年级 / 科目 / 知识点标签 / 难度」。
 *
 * <p>全部是<b>关键词规则</b>，不调用模型、零外部依赖：
 * <ul>
 *   <li>学段（幼小初高）：命中「幼儿园/小学/初中/高中」及年级别称即归类；</li>
 *   <li>年级：识别「一年级…六年级 / 初一…初三·七~九年级 / 高一…高三」；</li>
 *   <li>科目：拿题干+解析文本与 {@code subject.subject_name} 做包含匹配（现有 sys_subject 科目名，本表即 subject）；
 *       源里自带的 subject 名也参与匹配；匹配不到则回退来源默认科目；</li>
 *   <li>知识点标签：与 {@code knowledge_point.name} 包含式匹配，命中的知识点名逗号拼接；</li>
 *   <li>难度：由题型、选项数、题干长度的启发式给出 1-5。</li>
 * </ul>
 *
 * <p>它是服务层流水线协作者，按分层约定可直接依赖 Mapper（Controller 才禁止直连 Mapper）。
 */
@Component
public class ClassifierTagger {

    /** 年级别称 → 学段（顺序即优先级，先长后短避免「初中」被子串误命中） */
    private static final List<String> SENIOR_HIGH = List.of("高一", "高二", "高三", "高中");
    private static final List<String> JUNIOR_HIGH = List.of("初一", "初二", "初三", "七年级", "八年级", "九年级", "初中");
    private static final List<String> PRIMARY = List.of("一年级", "二年级", "三年级", "四年级", "五年级", "六年级", "小学");
    private static final List<String> PRESCHOOL = List.of("幼儿园", "幼小", "学前");

    /** 常见学科关键词别名：subject_name 未直接出现在题干时，用学科特征词兜底匹配（K-12 演示友好） */
    private static final Map<String, List<String>> SUBJECT_ALIAS = Map.of(
            "数学", List.of("计算", "加", "减", "乘", "除", "等于", "长方形", "正方形", "三角形", "周长",
                    "面积", "公因数", "公倍数", "分数", "小数", "方程", "勾股", "斜边", "倍"),
            "语文", List.of("语病", "成语", "古诗", "作文", "阅读", "汉字", "拼音", "课文", "标点", "句子", "小说", "散文"),
            "英语", List.of("单词", "语法", "翻译", "选择", "完成句子", "英译汉"));

    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;

    public ClassifierTagger(SubjectMapper subjectMapper, KnowledgePointMapper knowledgePointMapper) {
        this.subjectMapper = subjectMapper;
        this.knowledgePointMapper = knowledgePointMapper;
    }

    /**
     * 打标结果载体。
     */
    public record Tags(String grade, String gradeLevel, Long subjectId, String knowledgeTags, Integer difficulty) {
    }

    /**
     * 对一条清洗产物打标。
     *
     * @param clean            清洗结果（提供题型/选项数/题干）
     * @param parsed           解析产物（可能自带 grade/subject 名，参与匹配）
     * @param defaultSubjectId 来源默认科目（全部匹配不上时兜底，可空）
     */
    public Tags tag(CleanResult clean, ParsedQuestion parsed, Long defaultSubjectId) {
        // 打标语料：题干 + 解析 + 源自带年级/科目名，一起参与关键词匹配
        String corpus = buildCorpus(clean, parsed);

        String grade = detectGrade(corpus);
        String gradeLevel = detectGradeLevel(corpus);
        Long subjectId = matchSubject(corpus, parsed == null ? null : parsed.getSubjectName(), defaultSubjectId);
        String knowledgeTags = matchKnowledgeTags(corpus, subjectId);
        Integer difficulty = inferDifficulty(clean);

        return new Tags(grade, gradeLevel, subjectId, knowledgeTags, difficulty);
    }

    private String buildCorpus(CleanResult clean, ParsedQuestion parsed) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, clean.getContent());
        appendIfPresent(sb, clean.getAnalysis());
        if (parsed != null) {
            appendIfPresent(sb, parsed.getGrade());
            appendIfPresent(sb, parsed.getGradeLevel());
            appendIfPresent(sb, parsed.getSubjectName());
            appendIfPresent(sb, parsed.getKnowledgeHint());
        }
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String text) {
        if (StringUtils.hasText(text)) {
            sb.append(text).append(' ');
        }
    }

    /** 学段判定：高中 > 初中 > 小学 > 学前（从严到宽，先命中先归类） */
    private String detectGrade(String corpus) {
        if (containsAny(corpus, SENIOR_HIGH)) {
            return "高中";
        }
        if (containsAny(corpus, JUNIOR_HIGH)) {
            return "初中";
        }
        if (containsAny(corpus, PRIMARY)) {
            return "小学";
        }
        if (containsAny(corpus, PRESCHOOL)) {
            return "幼小";
        }
        return null;
    }

    /** 年级：命中原词（如「三年级」「高一」）即回填 */
    private String detectGradeLevel(String corpus) {
        List<String> all = new ArrayList<>();
        all.addAll(SENIOR_HIGH);
        all.addAll(JUNIOR_HIGH);
        all.addAll(PRIMARY);
        for (String token : all) {
            // 排除纯学段词（高中/初中/小学），它们不是「年级」
            if (token.equals("高中") || token.equals("初中") || token.equals("小学")) {
                continue;
            }
            if (corpus.contains(token)) {
                return token;
            }
        }
        return null;
    }

    /**
     * 科目标题匹配：先按源自带 subject 名精确/包含匹配 sys_subject，
     * 再拿打标语料做包含匹配；都命中不了则回退来源默认科目。
     */
    private Long matchSubject(String corpus, String declaredSubject, Long defaultSubjectId) {
        List<Subject> subjects = subjectMapper.selectList(null);
        // 1) 源显式声明的科目名优先（如 JSON 的 "subject":"数学"）
        if (StringUtils.hasText(declaredSubject)) {
            for (Subject subject : subjects) {
                if (subject.getSubjectName().equals(declaredSubject)
                        || subject.getSubjectName().contains(declaredSubject)
                        || declaredSubject.contains(subject.getSubjectName())) {
                    return subject.getId();
                }
            }
        }
        // 2) 语料包含科目名，或命中该科目的学科特征词别名
        for (Subject subject : subjects) {
            if (corpus.contains(subject.getSubjectName()) || matchesAlias(corpus, subject.getSubjectName())) {
                return subject.getId();
            }
        }
        // 3) 兜底：来源默认科目
        return defaultSubjectId;
    }

    /** 学科别名匹配：科目名在别名表里且语料命中任一特征词 */
    private boolean matchesAlias(String corpus, String subjectName) {
        List<String> aliases = SUBJECT_ALIAS.get(subjectName);
        if (aliases == null) {
            return false;
        }
        for (String alias : aliases) {
            if (corpus.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 知识点包含式匹配：knowledge_point.name 出现在语料里即命中，逗号拼接去重。
     * 若已定位到科目，优先在该科目的知识点里匹配；匹配不到再放宽到全库。
     */
    private String matchKnowledgeTags(String corpus, Long subjectId) {
        List<KnowledgePoint> points = knowledgePointMapper.selectList(null);
        Set<String> matched = new LinkedHashSet<>();
        // 先本科目
        if (subjectId != null) {
            for (KnowledgePoint point : points) {
                if (subjectId.equals(point.getSubjectId()) && corpus.contains(point.getName())) {
                    matched.add(point.getName());
                }
            }
        }
        // 本科目没命中则放宽全库（避免知识点未建科目归属时全漏）
        if (matched.isEmpty()) {
            for (KnowledgePoint point : points) {
                if (corpus.contains(point.getName())) {
                    matched.add(point.getName());
                }
            }
        }
        return matched.isEmpty() ? null : String.join(",", matched);
    }

    /**
     * 难度启发（1-5）：
     * 简答/多选偏难、判断/单选偏易；选项越多、题干越长适度加难；结果夹在 [1,5]。
     */
    private Integer inferDifficulty(CleanResult clean) {
        int score = 3;
        Integer type = clean.getQuestionType();
        if (type != null) {
            score += switch (type) {
                case 5 -> 2;  // 简答
                case 2 -> 1;  // 多选
                case 4 -> 1;  // 填空
                case 3 -> -1; // 判断
                case 1 -> 0;  // 单选
                default -> 0;
            };
        }
        int optionCount = clean.getOptions() == null ? 0 : clean.getOptions().size();
        if (optionCount >= 5) {
            score += 1;
        }
        int length = clean.getContent() == null ? 0 : clean.getContent().length();
        if (length > 80) {
            score += 1;
        } else if (length < 15) {
            score -= 1;
        }
        return Math.max(1, Math.min(5, score));
    }

    private boolean containsAny(String text, List<String> tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
