package com.zhikao.common;

import lombok.Data;

/**
 * 分页查询基类：所有分页查询 DTO 继承本类，统一 pageNum / pageSize 入参。
 */
@Data
public class PageQuery {

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 规范化页码：至少为 1 */
    public long safePageNum() {
        return pageNum == null || pageNum < 1 ? 1L : pageNum;
    }

    /** 规范化每页条数：默认 10，上限 100（避免前端传入超大值拖垮数据库） */
    public long safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, 100);
    }
}
