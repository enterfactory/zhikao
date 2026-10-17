package com.zhikao.modules.learn;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.common.JsonHelper;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.PageResult;
import com.zhikao.common.QuestionTypeEnum;
import com.zhikao.entity.Question;
import com.zhikao.mapper.ExamAnswerMapper;
import com.zhikao.mapper.QuestionMapper;
import com.zhikao.modules.ai.vo.KnowledgeStatVO;
import com.zhikao.modules.exam.vo.QuestionVO;
import com.zhikao.modules.learn.vo.WrongBookSummaryVO;
import com.zhikao.modules.learn.vo.WrongQuestionVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 错题本与复习建议服务。
 *
 * 与 Agent 的联动点：{@code weakPoints()} 输出的薄弱知识点，
 * 既是页面上「该复习什么」的依据，也是 AnalyticsAgent 生成学习建议时的事实输入——
 * 两处共用同一份 SQL 聚合，避免「AI 说一套、页面显示另一套」。
 */
@Service
public class WrongBookService {

    /** 薄弱判定阈值：正确率低于 60% */
    private static final BigDecimal WEAK_ACCURACY = new BigDecimal("60");

    /** 推荐练习题数量上限 */
    private static final int RECOMMEND_LIMIT = 10;

    private final ExamAnswerMapper examAnswerMapper;
    private final QuestionMapper questionMapper;

    public WrongBookService(ExamAnswerMapper examAnswerMapper, QuestionMapper questionMapper) {
        this.examAnswerMapper = examAnswerMapper;
        this.questionMapper = questionMapper;
    }

    /** 错题分页（当前登录学生本人） */
    public PageResult<WrongQuestionVO> page(int pageNum, int pageSize, String knowledgePoint) {
        long safeNum = pageNum < 1 ? 1L : pageNum;
        long safeSize = pageSize < 1 ? 10L : Math.min(pageSize, 100);
        IPage<WrongQuestionVO> page = examAnswerMapper.selectWrongQuestions(
                new Page<>(safeNum, safeSize), LoginUtil.userId(),
                StringUtils.hasText(knowledgePoint) ? knowledgePoint : null);

        PageResult<WrongQuestionVO> result = PageResult.of(page);
        for (WrongQuestionVO item : result.getRecords()) {
            item.setTypeName(QuestionTypeEnum.labelOf(item.getType()));
            item.setOptionMap(JsonHelper.toOptionMap(item.getOptions()));
        }
        return result;
    }

    /** 错题概览：总数 / 涉及知识点 / 最薄弱知识点 */
    public WrongBookSummaryVO summary() {
        List<KnowledgeStatVO> stats = weakPoints();
        WrongBookSummaryVO vo = new WrongBookSummaryVO();
        vo.setKnowledgeCount(stats.size());
        vo.setTotal(stats.stream()
                .mapToLong(stat -> {
                    int total = stat.getTotal() == null ? 0 : stat.getTotal();
                    int correct = stat.getCorrectCount() == null ? 0 : stat.getCorrectCount();
                    return total - correct;
                })
                .sum());
        if (!stats.isEmpty()) {
            KnowledgeStatVO weakest = stats.get(0);
            vo.setWeakestPoint(weakest.getKnowledgePoint());
            vo.setWeakestAccuracy(weakest.getAccuracy());
        }
        return vo;
    }

    /** 知识点掌握度（按正确率升序，最薄弱的在前） */
    public List<KnowledgeStatVO> weakPoints() {
        List<KnowledgeStatVO> stats = examAnswerMapper.selectStudentKnowledgeStats(LoginUtil.userId());
        List<KnowledgeStatVO> result = new ArrayList<>();
        for (KnowledgeStatVO stat : stats) {
            int total = stat.getTotal() == null ? 0 : stat.getTotal();
            int correct = stat.getCorrectCount() == null ? 0 : stat.getCorrectCount();
            stat.setAccuracy(total == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(correct * 100.0 / total).setScale(2, RoundingMode.HALF_UP));
            result.add(stat);
        }
        return result;
    }

    /**
     * 针对某个知识点推荐练习题（不出答案，用于自测）。
     * 从题库按知识点检索，而不是「推荐错题原题」——原题做过的记忆会干扰练习效果。
     */
    public List<QuestionVO> recommend(String knowledgePoint) {
        if ("student".equals(LoginUtil.roleCode())) return List.of();
        if (!StringUtils.hasText(knowledgePoint)) {
            return List.of();
        }
        List<Question> questions = questionMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<Question>lambdaQuery()
                        .eq(Question::getKnowledgePoint, knowledgePoint)
                        .orderByAsc(Question::getDifficulty)
                        .last("limit " + RECOMMEND_LIMIT));
        List<QuestionVO> result = new ArrayList<>();
        for (Question question : questions) {
            QuestionVO vo = new QuestionVO();
            vo.setId(question.getId());
            vo.setSubjectId(question.getSubjectId());
            vo.setType(question.getType());
            vo.setTypeName(QuestionTypeEnum.labelOf(question.getType()));
            vo.setContent(question.getContent());
            vo.setOptions(JsonHelper.toOptionMap(question.getOptions()));
            vo.setDifficulty(question.getDifficulty());
            vo.setKnowledgePoint(question.getKnowledgePoint());
            vo.setScore(question.getScore());
            // 刻意不下发 answer / analysis：练习场景需要先自己作答
            result.add(vo);
        }
        return result;
    }

    /** 是否属于「薄弱」知识点（供其他服务复用同一口径） */
    public static boolean isWeak(BigDecimal accuracy) {
        return accuracy != null && accuracy.compareTo(WEAK_ACCURACY) < 0;
    }
}
