package com.zhikao.modules.exam.dto;

import lombok.Data;

/**
 * 作弊上报入参。
 *
 * <p>只传一个「我切屏了」不够用：教师做处置决定时要看的是
 * 「什么时候切的」「切到第几题了」，所以这两项一并上报。
 */
@Data
public class CheatReportDTO {

    /** 异常类型：blur（失焦/切屏）/ copy / paste / fullscreen_exit；为空按 blur 处理 */
    private String type;

    /** 发生时的题目快照 id，可为空（学生可能还没点开题目） */
    private Long paperQuestionId;

    /** 补充说明，可为空 */
    private String remark;
}
