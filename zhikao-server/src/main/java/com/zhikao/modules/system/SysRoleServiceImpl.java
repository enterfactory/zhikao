package com.zhikao.modules.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.entity.SysRole;
import com.zhikao.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色服务实现。
 */
@Service
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper roleMapper;

    public SysRoleServiceImpl(SysRoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public List<SysRole> list() {
        // sys_role 是内置角色表（无启停列），按 sort 升序即「启用角色」目录；
        // 若将来需要角色启停，加 status 列并在此处过滤即可，调用方契约不变。
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getSort));
    }
}
