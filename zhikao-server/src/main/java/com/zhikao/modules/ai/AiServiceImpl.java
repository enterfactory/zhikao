package com.zhikao.modules.ai;

import com.zhikao.common.BusinessException;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.QuestionTypeEnum;
import com.zhikao.entity.AiTask;
import com.zhikao.entity.Exam;
import com.zhikao.mapper.AiTaskMapper;
import com.zhikao.mapper.ExamAnswerMapper;
import com.zhikao.mapper.ExamMapper;
import com.zhikao.modules.ai.dto.AiAssistMarkDTO;
import com.zhikao.modules.ai.dto.AiGenQuestionDTO;
import com.zhikao.modules.ai.vo.AiAnalysisVO;
import com.zhikao.modules.ai.vo.AiGenQuestionVO;
import com.zhikao.modules.ai.vo.AiMarkSuggestionVO;
import com.zhikao.modules.ai.vo.KnowledgeStatVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 增强服务实现。
 *
 * 说明：AI 结果一律只作为「建议」返回，不写回题库与成绩；
 * 每次调用都会落一条 ai_task 记录，便于排查与成本治理。
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    /** 任务类型：智能出题 */
    private static final String TYPE_GEN_QUESTION = "gen_question";
    /** 任务类型：辅助阅卷 */
    private static final String TYPE_ASSIST_MARK = "assist_mark";
    /** 任务类型：错题分析 */
    private static final String TYPE_ANALYSIS = "analysis";

    /** 正确率低于该阈值视为薄弱知识点 */
    private static final BigDecimal WEAK_ACCURACY = new BigDecimal("60");

    /** 从模型返回中提取建议分数，兼容 {"score": 6} / "score":6 / 分数：6 等情况 */
    private static final Pattern SCORE_PATTERN =
            Pattern.compile("(?:\"score\"|分数)\\s*[:：]\\s*\"?([0-9]+(?:\\.[0-9]+)?)\"?");

    private final AiClient aiClient;
    private final AiTaskMapper aiTaskMapper;
    private final ExamMapper examMapper;
    private final ExamAnswerMapper examAnswerMapper;
    /** 把模型输出解析成结构化题目草稿（含容错与入库前校验） */
    private final AiQuestionDraftParser draftParser;

    public AiServiceImpl(AiClient aiClient, AiTaskMapper aiTaskMapper,
                         ExamMapper examMapper, ExamAnswerMapper examAnswerMapper,
                         AiQuestionDraftParser draftParser) {
        this.aiClient = aiClient;
        this.aiTaskMapper = aiTaskMapper;
        this.examMapper = examMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.draftParser = draftParser;
    }

    @Override
    public AiGenQuestionVO genQuestion(AiGenQuestionDTO dto) {
        QuestionTypeEnum type = QuestionTypeEnum.of(dto.getType());
        if (type == null) {
            throw new BusinessException("题型不合法");
        }
        BigDecimal score = dto.getScore() == null ? new BigDecimal("5") : dto.getScore();
        String prompt = """
                请围绕科目「%s」、知识点「%s」出 %d 道%s题，难度 %d/5，每题 %s 分。
                输出要求：
                1. 直接输出 JSON 数组，不要输出任何解释文字，也不要使用 Markdown 代码块标记；
                2. 每个元素包含字段：content（题干）、options（对象，键为 A/B/C/D，判断/填空/简答题给空对象 {}）、
                   answer（参考答案）、analysis（解析）、difficulty（1-5）、knowledgePoint；
                3. 判断题的 answer 用「对」或「错」；多选题答案形如 "ABD"（只写字母，不要分隔符）；
                   填空题多个可接受答案用 | 分隔，多个空用 ; 分隔；
                4. 单选题必须有 4 个选项，且 answer 必须是其中之一。
                """.formatted(
                defaultText(dto.getSubjectName(), "通用"),
                defaultText(dto.getKnowledgePoint(), "不限"),
                dto.getCount() == null ? 3 : dto.getCount(),
                type.getLabel(),
                dto.getDifficulty() == null ? 3 : dto.getDifficulty(),
                score.toPlainString());

        String content = invoke(TYPE_GEN_QUESTION, null, prompt);

        AiGenQuestionVO vo = new AiGenQuestionVO();
        vo.setContent(content);
        vo.setAiGenerated(true);
        // 解析成结构化草稿：入库由教师在界面上勾选确认，AI 不直接写库
        AiQuestionDraftParser.Parsed parsed = draftParser.parse(content, dto.getType(), score);
        // 把目标科目回填到每道草稿：模型只认科目名，库里认的是 id，
        // 在这里补齐，前端就能做到「勾选 → 一键入库」而不用再查一次科目
        if (dto.getSubjectId() != null) {
            parsed.drafts().forEach(draft -> draft.setSubjectId(dto.getSubjectId()));
        }
        vo.setDrafts(parsed.drafts());
        vo.setParseNote(parsed.note());
        return vo;
    }

    @Override
    public AiMarkSuggestionVO assistMark(AiAssistMarkDTO dto) {
        String prompt = """
                请为下面这道主观题给出评分建议。
                【题目】%s
                【参考答案】%s
                【学生作答】%s
                【本题满分】%s 分
                只输出如下 JSON，不要包含其他文字：{"score": 建议分数, "reason": "简要理由"}
                """.formatted(
                defaultText(dto.getQuestion(), "（未提供）"),
                defaultText(dto.getStandardAnswer(), "（未提供）"),
                defaultText(dto.getStudentAnswer(), "（学生未作答）"),
                dto.getFullScore().toPlainString());

        String raw = invoke(TYPE_ASSIST_MARK, null, prompt);
        AiMarkSuggestionVO vo = new AiMarkSuggestionVO();
        vo.setRaw(raw);
        vo.setAiGenerated(true);

        Matcher matcher = SCORE_PATTERN.matcher(raw == null ? "" : raw);
        if (matcher.find()) {
            BigDecimal score = new BigDecimal(matcher.group(1));
            // 建议分不得超过本题满分，超出视为模型输出异常
            if (score.compareTo(dto.getFullScore()) > 0) {
                score = dto.getFullScore();
            }
            vo.setSuggestScore(score.setScale(2, RoundingMode.HALF_UP));
        }
        vo.setReason(raw);
        return vo;
    }

    @Override
    public AiAnalysisVO analysis(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        List<KnowledgeStatVO> stats = examAnswerMapper.selectKnowledgeStats(examId);
        List<String> weakPoints = new ArrayList<>();
        for (KnowledgeStatVO stat : stats) {
            int total = stat.getTotal() == null ? 0 : stat.getTotal();
            int correct = stat.getCorrectCount() == null ? 0 : stat.getCorrectCount();
            BigDecimal accuracy = total == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(correct * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
            stat.setAccuracy(accuracy);
            if (accuracy.compareTo(WEAK_ACCURACY) < 0) {
                weakPoints.add(stat.getKnowledgePoint());
            }
        }

        AiAnalysisVO vo = new AiAnalysisVO();
        vo.setExamId(exam.getId());
        vo.setExamName(exam.getExamName());
        vo.setKnowledgeStats(stats);
        vo.setWeakPoints(weakPoints);

        if (stats.isEmpty()) {
            vo.setSuggestion("该考试暂无终评且已评分的答题数据，无法进行知识点分析。");
            return vo;
        }
        // 配置了大模型时由模型给出教学建议，否则退回统计结论（明确标注非 AI 生成）
        if (aiClient.available()) {
            String prompt = buildAnalysisPrompt(exam.getExamName(), stats, weakPoints);
            vo.setSuggestion(invoke(TYPE_ANALYSIS, examId, prompt));
            vo.setAiGenerated(true);
        } else {
            vo.setSuggestion(buildRuleSuggestion(stats, weakPoints));
            vo.setAiGenerated(false);
        }
        return vo;
    }

    // ==================== 内部方法 ====================

    /** 统一调用入口：落 ai_task 记录后再返回文本（成功与失败都留痕） */
    private String invoke(String type, Long refId, String prompt) {
        long startedAt = System.currentTimeMillis();
        try {
            String result = aiClient.chat(null, prompt);
            saveTask(type, refId, prompt, result, 1, System.currentTimeMillis() - startedAt, null);
            return result;
        } catch (RuntimeException e) {
            saveTask(type, refId, prompt, null, 2, System.currentTimeMillis() - startedAt, e.getMessage());
            throw e;
        }
    }

    /**
     * 落一条调用留痕。
     *
     * <p>与对话链路共用同一张 ai_task 表、同一套字段口径：
     * 「这个月 AI 花了多少」必须能把智能出题、辅助阅卷、错题分析和对话加在一起算，
     * 各记各的表最后只能靠人工对账。
     */
    private void saveTask(String type, Long refId, String prompt, String result, int status,
                          long costMs, String errorMsg) {
        try {
            Long userId;
            try {
                userId = LoginUtil.userId();
            } catch (Exception e) {
                userId = null; // 定时任务等无请求上下文场景
            }
            aiTaskMapper.insert(AiTask.builder()
                    .type(type)
                    .userId(userId)
                    .refId(refId)
                    .prompt(prompt)
                    .result(result)
                    .status(status)
                    .roleCode(LoginUtil.roleCode())
                    // 出题/阅卷/分析走的是固定 prompt 模板，没有 Agent 编排，这里记的是任务类型
                    .agentType(type)
                    .costMs(costMs)
                    .errorMsg(errorMsg)
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            // 记录失败不影响主流程
            log.warn("保存 ai_task 失败：{}", e.getMessage());
        }
    }

    /** 未配置大模型时的规则化建议：直接基于统计结论给出可执行的复习方向 */
    private String buildRuleSuggestion(List<KnowledgeStatVO> stats, List<String> weakPoints) {
        StringBuilder builder = new StringBuilder();
        builder.append("（本建议由答题统计自动生成，未使用大模型）\n");
        if (weakPoints.isEmpty()) {
            builder.append("各知识点正确率均不低于 ")
                    .append(WEAK_ACCURACY.toPlainString())
                    .append("%，整体掌握情况良好，可适当提高题目难度或补充综合应用题。");
            return builder.toString();
        }
        builder.append("以下知识点正确率偏低，建议安排针对性复习与练习：\n");
        for (KnowledgeStatVO stat : stats) {
            if (weakPoints.contains(stat.getKnowledgePoint())) {
                builder.append("- ")
                        .append(stat.getKnowledgePoint())
                        .append("：正确率 ")
                        .append(stat.getAccuracy().toPlainString())
                        .append("%（")
                        .append(stat.getCorrectCount())
                        .append("/")
                        .append(stat.getTotal())
                        .append("）\n");
            }
        }
        return builder.toString();
    }

    /** 给大模型的分析提示：把统计结果作为事实输入，避免模型凭空编造 */
    private String buildAnalysisPrompt(String examName, List<KnowledgeStatVO> stats, List<String> weakPoints) {
        StringBuilder builder = new StringBuilder();
        builder.append("以下是考试「").append(examName).append("」各知识点的作答正确率统计：\n");
        for (KnowledgeStatVO stat : stats) {
            builder.append("- ")
                    .append(stat.getKnowledgePoint())
                    .append("：正确率 ")
                    .append(stat.getAccuracy().toPlainString())
                    .append("%（")
                    .append(stat.getCorrectCount())
                    .append("/")
                    .append(stat.getTotal())
                    .append("）\n");
        }
        builder.append("\n正确率低于 ").append(WEAK_ACCURACY.toPlainString()).append("% 的知识点有：")
                .append(weakPoints.isEmpty() ? "无" : String.join("、", weakPoints))
                .append("\n请基于以上数据给出 3 条以内的教学建议，直接给出结论，不要复述数据。");
        return builder.toString();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
