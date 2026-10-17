package com.zhikao.modules.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.BusinessException;
import com.zhikao.entity.SysSemester;
import com.zhikao.mapper.SysSemesterMapper;
import com.zhikao.modules.system.vo.SemesterVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 学期服务：只读列表 + 当前学期切换（供管理端后续接入）。
 */
@Service
public class SemesterService {

    private final SysSemesterMapper semesterMapper;

    public SemesterService(SysSemesterMapper semesterMapper) {
        this.semesterMapper = semesterMapper;
    }

    /** 全部学期，起始日期倒序（最新学期在最前）；登录即可查——建考试选学期人人要用 */
    public List<SemesterVO> list() {
        return semesterMapper.selectList(Wrappers.<SysSemester>lambdaQuery()
                        .orderByDesc(SysSemester::getStartDate)
                        .orderByDesc(SysSemester::getTermNo))
                .stream().map(this::toVO).toList();
    }

    /** 切换当前学期：先全表清零再置目标行为当前，避免同时存在两个「当前学期」 */
    @Transactional(rollbackFor = Exception.class)
    public void makeCurrent(Long id) {
        SysSemester semester = semesterMapper.selectById(id);
        if (semester == null) {
            throw new BusinessException("学期不存在");
        }
        semesterMapper.clearCurrent();
        semester.setIsCurrent(1);
        semesterMapper.updateById(semester);
    }

    private SemesterVO toVO(SysSemester entity) {
        SemesterVO vo = new SemesterVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
