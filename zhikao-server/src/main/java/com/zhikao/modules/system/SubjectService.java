package com.zhikao.modules.system;

import com.zhikao.entity.Subject;
import com.zhikao.modules.system.dto.SubjectSaveDTO;

import java.util.List;

/**
 * 科目管理服务。
 */
public interface SubjectService {

    /** 查询全部科目（题目录入、组卷的下拉选项） */
    List<Subject> list();

    /**
     * 新增或修改科目。
     *
     * @return 科目 ID。创建类接口回 ID 而不是只回 200，调用方（如「建科目 → 立刻建题」）
     *         就不必为了拿 ID 再查一次列表——顺带也避免了「按名称反查」这种脆弱写法。
     */
    Long save(SubjectSaveDTO dto);

    /** 删除科目（已被题目或试卷引用时拒绝，避免留下孤儿数据） */
    void remove(Long id);
}
