package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 角色 Mapper。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 按用户查细粒度角色码（Sa-Token 角色数据源）。
     * 全部使用 #{} 绑定；关联为内连接——角色被删时结果自然为空，由上层按 persona 兜底。
     */
    @Select("SELECT r.role_code FROM sys_role r "
            + "INNER JOIN sys_user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId} ORDER BY r.sort, r.id")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
