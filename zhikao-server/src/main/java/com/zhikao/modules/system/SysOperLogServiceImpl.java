package com.zhikao.modules.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.common.PageResult;
import com.zhikao.common.UserTypeEnum;
import com.zhikao.entity.SysOperLog;
import com.zhikao.mapper.SysOperLogMapper;
import com.zhikao.modules.system.dto.OperLogQueryDTO;
import com.zhikao.modules.system.vo.OperLogVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现。
 */
@Service
public class SysOperLogServiceImpl implements SysOperLogService {

    private final SysOperLogMapper operLogMapper;

    public SysOperLogServiceImpl(SysOperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    @Override
    public PageResult<OperLogVO> page(OperLogQueryDTO query) {
        LambdaQueryWrapper<SysOperLog> wrapper = Wrappers.<SysOperLog>lambdaQuery()
                .like(StringUtils.hasText(query.getModule()), SysOperLog::getModule, query.getModule())
                .like(StringUtils.hasText(query.getOperName()), SysOperLog::getOperName, query.getOperName())
                .eq(query.getResultStatus() != null, SysOperLog::getResultStatus, query.getResultStatus())
                .ge(query.getStartTime() != null, SysOperLog::getOperTime, query.getStartTime())
                .le(query.getEndTime() != null, SysOperLog::getOperTime, query.getEndTime())
                .orderByDesc(SysOperLog::getId);

        Page<SysOperLog> page = operLogMapper.selectPage(
                new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        return PageResult.of(page, this::toVO);
    }

    @Override
    public List<String> modules() {
        return operLogMapper.selectList(Wrappers.<SysOperLog>lambdaQuery()
                        .select(SysOperLog::getModule)
                        .groupBy(SysOperLog::getModule))
                .stream()
                .map(SysOperLog::getModule)
                .filter(StringUtils::hasText)
                .sorted()
                .collect(Collectors.toList());
    }

    private OperLogVO toVO(SysOperLog log) {
        OperLogVO vo = new OperLogVO();
        vo.setId(log.getId());
        vo.setModule(log.getModule());
        vo.setAction(log.getAction());
        vo.setMethod(log.getMethod());
        vo.setRequestUri(log.getRequestUri());
        vo.setOperName(log.getOperName());
        vo.setOperRole(log.getOperRole());
        vo.setOperRoleName(roleName(log.getOperRole()));
        vo.setOperIp(log.getOperIp());
        vo.setParams(log.getParams());
        vo.setResultStatus(log.getResultStatus());
        vo.setErrorMsg(log.getErrorMsg());
        vo.setCostMs(log.getCostMs());
        vo.setOperTime(log.getOperTime());
        return vo;
    }

    /** 角色标识 -> 中文名 */
    private String roleName(String role) {
        if (role == null || role.isBlank()) {
            return "-";
        }
        return switch (role) {
            case "admin" -> UserTypeEnum.ADMIN.getLabel();
            case "teacher" -> UserTypeEnum.TEACHER.getLabel();
            case "student" -> UserTypeEnum.STUDENT.getLabel();
            default -> role;
        };
    }
}
