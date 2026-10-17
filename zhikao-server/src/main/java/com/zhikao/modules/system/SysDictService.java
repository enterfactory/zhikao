package com.zhikao.modules.system;

import com.zhikao.common.PageResult;
import com.zhikao.modules.system.dto.DictItemSaveDTO;
import com.zhikao.modules.system.dto.DictTypeSaveDTO;
import com.zhikao.modules.system.vo.DictItemVO;
import com.zhikao.modules.system.vo.DictTypeVO;

import java.util.List;
import java.util.Map;

/**
 * 数据字典服务。
 *
 * 分层原则：字典只提供「展示型」枚举（文案 / 排序 / 颜色），
 * 业务判分仍走 Java 枚举，避免把业务规则搬进数据库导致编译期失去校验。
 */
public interface SysDictService {

    /** 单类型查询（只返回启用的字典项，按 sort 升序） */
    List<DictItemVO> items(String dictType);

    /** 批量查询：前端一次拉全，会话内复用 */
    Map<String, List<DictItemVO>> batch(List<String> types);

    /** 字典类型分页（管理页） */
    PageResult<DictTypeVO> typePage(String keyword, Integer pageNum, Integer pageSize);

    /** 字典项列表（管理页，含停用项） */
    List<DictItemVO> itemList(String dictType);

    /** 新增或修改字典类型 */
    void saveType(DictTypeSaveDTO dto);

    /** 删除字典类型（连同其下字典项，内置字典禁止删除） */
    void removeType(Long id);

    /** 新增或修改字典项 */
    void saveItem(DictItemSaveDTO dto);

    /** 删除字典项 */
    void removeItem(Long id);
}
