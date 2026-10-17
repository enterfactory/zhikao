package com.zhikao.modules.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.BusinessException;
import com.zhikao.entity.SysClass;
import com.zhikao.entity.SysUser;
import com.zhikao.mapper.SysClassMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.modules.system.dto.ClassSaveDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 班级管理服务实现。
 */
@Service
public class SysClassServiceImpl implements SysClassService {
    @org.springframework.beans.factory.annotation.Autowired private com.zhikao.mapper.HistoricalReferencesMapper history;

    private final SysClassMapper classMapper;
    private final SysUserMapper userMapper;

    public SysClassServiceImpl(SysClassMapper classMapper, SysUserMapper userMapper) {
        this.classMapper = classMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<SysClass> list() {
        return classMapper.selectList(Wrappers.<SysClass>lambdaQuery().orderByDesc(SysClass::getId));
    }

    @Override
    public void save(ClassSaveDTO dto) {
        // 班级名称唯一：同名班级会让「导入用户 / 选择班级」产生歧义
        Long duplicated = classMapper.selectCount(Wrappers.<SysClass>lambdaQuery()
                .eq(SysClass::getClassName, dto.getClassName())
                .ne(dto.getId() != null, SysClass::getId, dto.getId()));
        if (duplicated > 0) {
            throw new BusinessException("班级已存在：" + dto.getClassName());
        }

        SysClass entity = new SysClass();
        entity.setId(dto.getId());
        entity.setClassName(dto.getClassName());
        entity.setGrade(dto.getGrade());
        entity.setMajor(dto.getMajor());
        if (dto.getId() == null) {
            entity.setCreateTime(LocalDateTime.now());
            classMapper.insert(entity);
        } else {
            classMapper.updateById(entity);
        }
    }

    @Override
    public void remove(Long id) {
        // 班级下仍有学生时禁止删除，避免出现「无班级学生」导致成绩无法按班统计
        Long students = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getClassId, id));
        if (students > 0) {
            throw new BusinessException("该班级下还有 " + students + " 名学生，请先调整这些学生的班级");
        }
        if(history.classReferences(id)>0)throw new BusinessException("该班级已有考试历史，不能删除");
        classMapper.deleteById(id);
    }
}
