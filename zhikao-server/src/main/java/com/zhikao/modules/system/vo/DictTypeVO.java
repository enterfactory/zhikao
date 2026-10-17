package com.zhikao.modules.system.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 字典类型返回结构（含字典项数量，便于管理页展示）。
 */
@Data
public class DictTypeVO {

    private Long id;

    private String dictName;

    private String dictType;

    private String remark;

    private Integer status;

    private LocalDateTime createTime;

    /** 该类型下的字典项数量 */
    private Integer itemCount;

    /** 该类型下的字典项（管理页展开时使用，列表查询时为空） */
    private List<DictItemVO> items;
}
