package com.zhikao.modules.exam;

import com.zhikao.common.PageResult;
import com.zhikao.modules.exam.dto.PaperManualDTO;
import com.zhikao.modules.exam.dto.PaperQueryDTO;
import com.zhikao.modules.exam.dto.PaperRandomDTO;
import com.zhikao.modules.exam.vo.PaperDetailVO;
import com.zhikao.modules.exam.vo.PaperQuestionVO;
import com.zhikao.modules.exam.vo.PaperVO;

import java.util.List;

/**
 * 试卷服务：手工 / 随机组卷、试卷预览与启停。
 */
public interface PaperService {

    /** 分页查询试卷 */
    PageResult<PaperVO> page(PaperQueryDTO query);

    /** 全部试卷（供创建考试时选择，试卷数量可控故不分页） */
    List<PaperVO> list();

    /** 试卷详情（含题目快照） */
    PaperDetailVO detail(Long id);

    /**
     * 取某份试卷的题目快照列表（按 sort 升序）。
     *
     * @param withAnswer 是否携带标准答案与解析：
     *                   教师预览 / 阅卷传 true；学生取卷必须传 false，否则答案会下发给考生
     */
    List<PaperQuestionVO> listQuestions(Long paperId, boolean withAnswer);

    /** 手工组卷，返回试卷 id */
    Long genManual(PaperManualDTO dto);

    /** 随机组卷，返回试卷 id */
    Long genRandom(PaperRandomDTO dto);

    /** 发布 / 撤回试卷（status：0草稿 1已发布） */
    void updateStatus(Long id, Integer status);

    /** 删除试卷（被考试引用时禁止删除） */
    void remove(Long id);
}
