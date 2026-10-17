package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @org.apache.ibatis.annotations.Update("UPDATE sys_user SET auth_version=auth_version+1 WHERE id=#{id}")
    int advanceAuthVersion(Long id);
}
