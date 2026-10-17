package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.SysDictItem;
import com.zhikao.modules.system.vo.DictTypeCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 字典项 Mapper。
 */
@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {

    /**
     * 按字典类型统计字典项数量（管理页列表用）。
     * 用 GROUP BY 下推到数据库聚合，而不是把全部字典项拉回内存再分组。
     */
    @Select("SELECT dict_type AS dict_type, COUNT(*) AS total FROM sys_dict_item GROUP BY dict_type")
    List<DictTypeCountVO> countGroupByType();
}
