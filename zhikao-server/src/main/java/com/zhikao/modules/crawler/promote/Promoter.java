package com.zhikao.modules.crawler.promote;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.JsonHelper;
import com.zhikao.entity.CrawlQuestion;
import com.zhikao.entity.KnowledgePoint;
import com.zhikao.entity.QuestionKnowledge;
import com.zhikao.mapper.CrawlQuestionMapper;
import com.zhikao.mapper.KnowledgePointMapper;
import com.zhikao.mapper.QuestionKnowledgeMapper;
import com.zhikao.modules.exam.QuestionService;
import com.zhikao.modules.exam.dto.QuestionSaveDTO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 入库器（六期）：人审通过（PROMOTE）后，把待审题写进正式题库 question 主表。
 *
 * <p><b>复用现有录入通道</b>：走 {@link QuestionService#saveReturningId}，与人工新建、批量导入同一条校验路径，
 * 不自建第二套写入逻辑；科目/选项/答案合法性由它统一把关。拿到主库 id 后回填 promoted_question_id，
 * 并把 knowledge_tags 解析成 {@code question_knowledge} 关联（知识点节点已存在才挂，挂不到不报错）。
 *
 * <p>单条一个事务：某条解析/校验失败只影响它自己（调用方把原因写进 review_note，
 * 阶段退回 REVIEWED 可再审），不牵连同批次其它已通过审核的题。
 */
@Component
public class Promoter {

    /** 爬虫入库题默认分值，与主库录入默认值一致 */
    private static final BigDecimal DEFAULT_SCORE = new BigDecimal("5.00");

    private final QuestionService questionService;
    private final CrawlQuestionMapper crawlQuestionMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionKnowledgeMapper questionKnowledgeMapper;

    public Promoter(QuestionService questionService, CrawlQuestionMapper crawlQuestionMapper,
                    KnowledgePointMapper knowledgePointMapper, QuestionKnowledgeMapper questionKnowledgeMapper) {
        this.questionService = questionService;
        this.crawlQuestionMapper = crawlQuestionMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.questionKnowledgeMapper = questionKnowledgeMapper;
    }

    /**
     * 把一条待审题升入主库。
     *
     * <p><b>失败直接抛，不在这里 catch</b>：本方法自己就是事务边界，事务里捕获异常
     * 「吞掉再写失败原因」是行不通的——内层 {@code QuestionService} 一抛，这条事务就被标成
     * rollback-only，后面的 review_note 回写会一起被回滚，调用方最后只会拿到一个
     * UnexpectedRollbackException（前端表现为「系统异常」，整批审核中断）。
     * 逐条兜底与失败原因回写交给不带事务的调用方（{@code CrawlerServiceImpl#review}）。
     *
     * @return 主库 question.id
     * @throws com.zhikao.common.BusinessException 校验不通过（如未指定科目）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long promote(CrawlQuestion crawl) {
        CrawlQuestion latest = crawlQuestionMapper.selectOne(Wrappers.<CrawlQuestion>lambdaQuery()
                .eq(CrawlQuestion::getId, crawl.getId()).last("FOR UPDATE"));
        if (latest == null) throw new com.zhikao.common.BusinessException("候选题不存在");
        if (latest.getPromotedQuestionId() != null) return latest.getPromotedQuestionId();
        if (!java.util.Objects.equals(latest.getRevision(), crawl.getRevision()))
            throw new com.zhikao.common.BusinessException(40901,"候选题版本已变化，请重新复核");
        if (!Integer.valueOf(1).equals(latest.getFormulaConfirmed()))
            throw new com.zhikao.common.BusinessException("请在题目复核页核对原文与公式并保存");
        QuestionSaveDTO dto = toSaveDto(crawl);
        Long questionId = questionService.saveReturningId(dto);

        // 回填 promoted_question_id + 阶段推进为 PROMOTED
        crawl.setPromotedQuestionId(questionId);
        crawl.setStage("PROMOTED");
        crawlQuestionMapper.updateById(crawl);

        // 知识点关联：knowledge_tags 逐个映射到已存在的 knowledge_point 节点
        // QuestionService binds exact labels within the selected subject.
        return questionId;
    }

    /** 待审记录 → 主库录入 DTO（字段语义对齐 question 表） */
    private QuestionSaveDTO toSaveDto(CrawlQuestion crawl) {
        QuestionSaveDTO dto = new QuestionSaveDTO();
        dto.setId(null);
        dto.setSubjectId(crawl.getSubjectId());
        dto.setType(crawl.getQuestionType());
        dto.setContent(crawl.getContent());
        Map<String, String> options = JsonHelper.toOptionMap(crawl.getOptions());
        dto.setOptions(options);
        dto.setAnswer(crawl.getAnswer());
        dto.setAnalysis(crawl.getAnalysis());
        dto.setDifficulty(crawl.getDifficulty());
        // 知识点：优先用打标命中的标签文本，主库 knowledge_point 列沿用自由文本
        dto.setKnowledgePoint(StringUtils.hasText(crawl.getKnowledgeTags())
                ? crawl.getKnowledgeTags() : crawl.getGradeLevel());
        dto.setScore(DEFAULT_SCORE);
        return dto;
    }

    /** 标签串（逗号分隔）解析为知识点关联；仅挂到名称能对上号的 knowledge_point 节点 */
    /** 记录清洗/审核时间戳的小工具，供服务层调用 */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
