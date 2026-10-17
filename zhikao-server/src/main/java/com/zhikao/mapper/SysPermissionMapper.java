package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限点 Mapper。
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /** 全部权限码：super_admin 的短路返回（权限点目录不大，一次取全后缓存进会话） */
    @Select("SELECT perm_code FROM sys_permission ORDER BY sort, id")
    List<String> selectAllPermCodes();

    /**
     * 按角色码集合查权限码（去重）。
     * 用 <script> + foreach 生成 IN 列表，值仍是 #{} 绑定，不做字符串拼接。
     * 去重用 GROUP BY 而不是 DISTINCT —— MySQL 下 DISTINCT 无法按未投影的 p.sort 排序。
     */
    @Select("<script>SELECT p.perm_code FROM sys_permission p "
            + "INNER JOIN sys_role_permission rp ON rp.perm_id = p.id "
            + "INNER JOIN sys_role r ON r.id = rp.role_id "
            + "WHERE r.role_code IN "
            + "<foreach item='code' collection='roleCodes' open='(' separator=',' close=')'>#{code}</foreach>"
            + " GROUP BY p.perm_code ORDER BY MIN(p.sort), MIN(p.id)</script>")
    List<String> selectPermCodesByRoleCodes(@Param("roleCodes") List<String> roleCodes);
}
