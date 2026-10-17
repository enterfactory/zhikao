package com.zhikao.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一分页返回结构，前端约定字段：total（总条数）、records（当前页数据）。
 *
 * @param <T> 每行数据类型
 */
@Data
public class PageResult<T> {

    /** 总条数 */
    private long total;

    /** 当前页数据 */
    private List<T> records;

    /** 直接转换 MyBatis-Plus 分页对象 */
    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.total = page.getTotal();
        result.records = page.getRecords();
        return result;
    }

    /**
     * 实体分页转 VO 分页，用于隐藏敏感字段（如密码）或补充展示字段（如班级名称）。
     *
     * @param page   实体分页结果
     * @param mapper 单行转换函数
     */
    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        PageResult<T> result = new PageResult<>();
        result.total = page.getTotal();
        result.records = page.getRecords().stream().map(mapper).collect(Collectors.toList());
        return result;
    }
}
