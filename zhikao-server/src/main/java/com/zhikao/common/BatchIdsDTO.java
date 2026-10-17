package com.zhikao.common;

import lombok.Data;

import java.util.List;

/**
 * 批量操作的通用入参：一组主键 ID。
 * 抽成公共 DTO 而不是每个模块各建一个，避免十几个近乎相同的类。
 */
@Data
public class BatchIdsDTO {

    /** 待操作的主键集合 */
    private List<Long> ids;
}
