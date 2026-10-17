package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.util.List;

/**
 * 试卷详情：试卷信息 + 题目快照列表。
 */
@Data
public class PaperDetailVO {

    private PaperVO paper;

    /** 题目列表（按 sort 升序） */
    private List<PaperQuestionVO> questions;
}
