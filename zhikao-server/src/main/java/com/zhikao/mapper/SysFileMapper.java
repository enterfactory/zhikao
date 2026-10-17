package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.SysFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件中心 Mapper。查询条件都很简单（按哈希 / 按归属 / 按业务），
 * 全部走 MyBatis-Plus 的 LambdaQuery，无需手写 SQL。
 */
@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {
}
