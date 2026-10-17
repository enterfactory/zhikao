package com.zhikao.modules.portal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 排行榜条目。
 *
 * <p>计分口径（近 7 天）：score = 该生近 7 天「已阅卷」考试总分之和
 * （exam_student.status=3 且 submit_time 在窗口内，SUM(total_score)）；
 * 名次按 score 降序，同分先交卷者靠前；当前用户不在前 10 时也会附带本人名次。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryVO {

    private Long userId;

    /** 学生真实姓名 */
    private String name;

    /** 近 7 天得分合计 */
    private BigDecimal score;

    /** 名次，从 1 开始 */
    private Integer rank;
}
