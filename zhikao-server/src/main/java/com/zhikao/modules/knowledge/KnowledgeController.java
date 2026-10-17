package com.zhikao.modules.knowledge;

import com.zhikao.common.R;
import com.zhikao.modules.knowledge.vo.KnowledgeNodeVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识点接口（P3 地基）：树形查询，登录即可读——
 * 学生筛选错题、教师组卷选题都要用同一棵树，它是指南针而不是操作对象。
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /** 知识点树；subjectId 为空返回全部科目 */
    @GetMapping("/tree")
    public R<List<KnowledgeNodeVO>> tree(@RequestParam(required = false) Long subjectId) {
        return R.ok(knowledgeService.tree(subjectId));
    }
}
