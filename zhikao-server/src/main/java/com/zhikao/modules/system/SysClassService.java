package com.zhikao.modules.system;

import com.zhikao.entity.SysClass;
import com.zhikao.modules.system.dto.ClassSaveDTO;

import java.util.List;

/**
 * 班级管理服务。
 * 班级数量少且要用于下拉选择，故列表接口不分页。
 */
public interface SysClassService {

    /** 查询全部班级（按 id 倒序，新建的排在前面） */
    List<SysClass> list();

    /** 新增或修改班级 */
    void save(ClassSaveDTO dto);

    /** 删除班级 */
    void remove(Long id);
}
