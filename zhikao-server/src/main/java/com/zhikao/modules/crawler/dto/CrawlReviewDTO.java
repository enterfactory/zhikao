package com.zhikao.modules.crawler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 待审题批量审核参数。
 * action：PROMOTE（入库，走 Promoter 写 question 主表）| REJECT（退回）。
 */
@Data
public class CrawlReviewDTO {

    /** 待处理的 crawl_question.id 列表 */
    @NotEmpty(message = "请至少选择一条待审记录")
    private List<Long> ids;

    /** 动作：PROMOTE | REJECT */
    @NotBlank(message = "审核动作不能为空")
    private String action;

    /** 审核备注 */
    private String note;

    /**
     * 科目兜底。OCR 出来的文档大多认不出属于哪门科目（crawl_question.subject_id 为空），
     * 而主库录入强制要求科目，否则入库校验会挡下。审核时由审核人指定一个科目，
     * 只用于补上那些自己没带科目的记录，已有 subject_id 的记录不受影响。
     */
    private Long subjectId;
}
