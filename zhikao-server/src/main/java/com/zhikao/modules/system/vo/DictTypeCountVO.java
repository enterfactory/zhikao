package com.zhikao.modules.system.vo;

import lombok.Data;

/**
 * 「字典类型 -> 字典项数量」的聚合结果。
 */
@Data
public class DictTypeCountVO {

    private String dictType;

    private Long total;
}
