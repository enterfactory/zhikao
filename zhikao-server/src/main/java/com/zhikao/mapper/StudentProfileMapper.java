package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.StudentProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生画像（长期记忆）Mapper。
 */
@Mapper
public interface StudentProfileMapper extends BaseMapper<StudentProfile> {
}
