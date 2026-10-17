package com.zhikao.modules.system;

import com.zhikao.common.PageResult;
import com.zhikao.modules.system.dto.OperLogQueryDTO;
import com.zhikao.modules.system.vo.OperLogVO;

import java.util.List;

/**
 * 操作日志服务。
 */
public interface SysOperLogService {

    /** 分页查询操作日志（仅管理员） */
    PageResult<OperLogVO> page(OperLogQueryDTO query);

    /** 可选的模块列表，供页面下拉筛选 */
    List<String> modules();
}
