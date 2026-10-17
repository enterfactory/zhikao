package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper：查询走 BaseMapper + Wrapper，无需自定义 SQL。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
