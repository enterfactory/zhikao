package com.zhikao.modules.exam;

import com.zhikao.common.ImportResultVO;
import com.zhikao.common.PageResult;
import com.zhikao.modules.exam.dto.QuestionQueryDTO;
import com.zhikao.modules.exam.dto.QuestionSaveDTO;
import com.zhikao.modules.exam.vo.QuestionSimilarVO;
import com.zhikao.modules.exam.vo.QuestionVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 题库服务：题目维护、筛选、批量导入。
 */
public interface QuestionService {

    /** 分页查询题目（科目 / 题型 / 难度 / 关键字） */
    PageResult<QuestionVO> page(QuestionQueryDTO query);

    /** 题目详情 */
    QuestionVO detail(Long id);

    /** 新增或修改题目 */
    void save(QuestionSaveDTO dto);

    /**
     * 新增题目并返回主库生成的题目 id（供外部链路回填关联，如爬虫清洗入库后回写 promoted_question_id）。
     *
     * <p>与 {@link #save} 走<b>完全相同</b>的校验与写入路径，只是额外把自增主键返回——
     * 不存在「另一条更宽松的入库通道」。仅用于新增场景（dto.id 必须为空）。
     */
    Long saveReturningId(QuestionSaveDTO dto);

    /**
     * 批量新增题目。
     *
     * <p>用途主要是「AI 出题草稿勾选后一键入库」，但实现上**不带任何 AI 相关逻辑**——
     * 它就是一个普通的多题写入接口，走的也是与单题新建完全相同的 {@link #save} 校验。
     * 这样设计的理由：AI 只负责生成草稿，落库路径和人工新建是同一条，
     * 所以不存在「AI 专用写入通道绕过了校验」这种隐患。
     *
     * <p>逐条独立处理：某一条失败只记录原因，其余照常入库。
     *
     * @return 成功 / 失败条数与失败明细
     */
    ImportResultVO saveBatch(List<QuestionSaveDTO> items);

    /** 删除题目 */
    void remove(Long id);

    /** 从 Excel 批量导入题目到指定科目（每行独立处理） */
    ImportResultVO importQuestions(Long subjectId, MultipartFile file);

    /**
     * 从 Excel 批量导入题目。
     *
     * @param preview true 表示**只校验不落库**：走的是与入库完全相同的校验链路
     *                （{@code validate()}），只是跳过写库，因此预览结论与真实入库一致。
     *                这是「先看结果再决定要不要写」的体验，也是 AI/批量操作的一贯边界。
     */
    ImportResultVO importQuestions(Long subjectId, MultipartFile file, boolean preview);

    /** 生成题目导入模板（xlsx 字节） */
    byte[] importTemplate();

    /**
     * 查重：找出与给定题目字面上高度相似已有题目。
     *
     * <p>主要用于 AI 出题草稿入库前：模型不知道库里有什么，很容易出一道
     * 「换个说法但考同一个点」的题。让教师在勾选前看到「第 12 题与已有题目重合 86%」，
     * 由人决定要不要留——AI 只负责提醒，不负责替人决定。
     */
    List<QuestionSimilarVO> findSimilar(QuestionSaveDTO dto);
}
