package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生进入考试后返回的答题卷。
 * 说明：题目一律不带标准答案（由 PaperService#listQuestions(paperId, false) 保证）。
 */
@Data
public class EnterExamVO {

    private Long examId;

    private String examName;

    private String paperName;

    /** 试卷总分 */
    private BigDecimal totalScore;

    /** 答题时长（分钟） */
    private Integer duration;

    /** 剩余答题秒数（服务端计算，前端据此倒计时并定期回源校准） */
    private Long remainingSeconds;

    /** 客观题自动阅卷 1开 0关 */
    private Integer autoMark;

    /** 防作弊开关 1开 0关 */
    private Integer cheatCheck;

    /** 本人参与状态 */
    private Integer myStatus;

    private String myStatusName;

    /** 题目列表（按顺序） */
    private List<PaperQuestionVO> questions;

    /** 已保存的作答：paperQuestionId -> 作答内容（支持中途续答） */
    private Map<Long, String> savedAnswers = new LinkedHashMap<>();

    private Map<Long, Integer> answerVersions = new LinkedHashMap<>();
}
