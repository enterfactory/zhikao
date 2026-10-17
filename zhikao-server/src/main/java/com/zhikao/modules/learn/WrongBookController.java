package com.zhikao.modules.learn;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.zhikao.common.PageResult;
import com.zhikao.common.R;
import com.zhikao.modules.ai.vo.KnowledgeStatVO;
import com.zhikao.modules.exam.vo.QuestionVO;
import com.zhikao.modules.learn.vo.WrongBookSummaryVO;
import com.zhikao.modules.learn.vo.WrongQuestionVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 错题本与复习建议接口（学生本人）。
 */
@RestController
@RequestMapping("/learn")
@SaCheckRole("student")
public class WrongBookController {

    private final WrongBookService wrongBookService;

    public WrongBookController(WrongBookService wrongBookService) {
        this.wrongBookService = wrongBookService;
    }

    /** 错题分页（可按知识点筛选） */
    @GetMapping("/wrong/page")
    public R<PageResult<WrongQuestionVO>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) String knowledgePoint) {
        return R.ok(wrongBookService.page(pageNum, pageSize, knowledgePoint));
    }

    /** 错题概览 */
    @GetMapping("/wrong/summary")
    public R<WrongBookSummaryVO> summary() {
        return R.ok(wrongBookService.summary());
    }

    /** 知识点掌握度（按正确率升序） */
    @GetMapping("/wrong/knowledge")
    public R<List<KnowledgeStatVO>> knowledge() {
        return R.ok(wrongBookService.weakPoints());
    }

    /** 按知识点推荐练习题（不含答案，用于自测） */
    @GetMapping("/recommend")
    public R<List<QuestionVO>> recommend(@RequestParam String knowledgePoint) {
        return R.ok(wrongBookService.recommend(knowledgePoint));
    }
}
