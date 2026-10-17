package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生本人的卷面明细（查分 + 错题解析）。
 * 说明：标准答案与解析仅在此处下发，且必须是已交卷的学生本人查询。
 */
@Data
public class MyPaperVO {

    private Long examId;

    private String examName;

    private String paperName;

    private BigDecimal paperTotalScore;

    private BigDecimal objectiveScore;

    private BigDecimal subjectiveScore;

    private BigDecimal totalScore;

    /** 每题作答与判分情况 */
    private List<MyAnswerVO> questions = new ArrayList<>();

    /** 单题作答明细 */
    @Data
    public static class MyAnswerVO {

        private Long paperQuestionId;

        private Integer sort;

        private Integer type;

        private String typeName;

        /** 是否客观题 */
        private Boolean objective;

        private String content;

        private Map<String, String> options = new LinkedHashMap<>();

        /** 本题满分 */
        private BigDecimal fullScore;

        /** 我的作答 */
        private String myAnswer;

        /** 标准答案 */
        private String standardAnswer;

        /** 解析 */
        private String analysis;

        /** 本题得分（主观题未批改时为 null） */
        private BigDecimal gotScore;

        /** 1对 0错 null未判（主观题未批改时为 null） */
        private Integer correct;

        /** 是否已批改（主观题） */
        private Integer marked;
    }
}
