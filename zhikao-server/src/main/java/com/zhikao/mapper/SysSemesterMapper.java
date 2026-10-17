package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.SysSemester;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 学期 Mapper。
 */
@Mapper
public interface SysSemesterMapper extends BaseMapper<SysSemester> {

    /** 切换当前学期前先把旧的当前位清零，保证全表至多一个 is_current=1 */
    @Update("UPDATE sys_semester SET is_current = 0 WHERE is_current = 1")
    int clearCurrent();
}
