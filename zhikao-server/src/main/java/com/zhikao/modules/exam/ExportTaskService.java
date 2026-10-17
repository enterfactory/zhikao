package com.zhikao.modules.exam;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.BusinessException;
import com.zhikao.common.DataScope;
import com.zhikao.common.LoginUtil;
import com.zhikao.config.AsyncConfig;
import com.zhikao.entity.Exam;
import com.zhikao.entity.SysExportTask;
import com.zhikao.mapper.ExamMapper;
import com.zhikao.mapper.SysExportTaskMapper;
import com.zhikao.modules.file.FileStorageService;
import com.zhikao.modules.exam.vo.ExportTaskVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步导出任务服务。
 *
 * <p><b>为什么改异步：</b>导出在请求线程里生成 xlsx 时，人数一多就是一条慢请求——
 * 它占着 Tomcat 线程、前端转圈、网关侧还会被当成超时。改成异步后请求立即返回，
 * 前端轮询任务状态，生成完再下载。
 *
 * <p><b>为什么任务状态存库而不是存内存：</b>内存 Map 在进程重启后清空，
 * 前端会一直轮询一个永远不会完成的任务；存库则天然可恢复、可排查、可被定时任务清理。
 *
 * <p><b>为什么同步导出接口还留着：</b>小规模导出（几十人）走同步链路更简单，
 * 也没有「点了还要等轮询」的体验损失。两条链路共用同一个生成方法
 * （{@code GradeService.exportExamGrade}），不存在两套口径。
 */
@Service
public class ExportTaskService {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskService.class);

    /** 业务类型：考试成绩导出 */
    public static final String BIZ_TYPE_GRADE = "exam-grade";

    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILED = 2;

    private final SysExportTaskMapper taskMapper;
    private final ExamMapper examMapper;
    private final ExportWorker worker;
    private final DataScope dataScope;

    public ExportTaskService(SysExportTaskMapper taskMapper, ExamMapper examMapper,
                             ExportWorker worker,
                             DataScope dataScope) {
        this.taskMapper = taskMapper;
        this.examMapper = examMapper;
        this.worker = worker;
        this.dataScope = dataScope;
    }

    /**
     * 提交一个成绩导出任务。
     *
     * <p>归属校验放在**请求线程**里做：异步线程虽然有登录态快照（由线程池装饰器带入），
     * 但「越权请求」应该在最早的地方被拒绝——不该为了一次非法请求还去跑一遍导出。
     */
    public ExportTaskVO submitGradeExport(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        dataScope.requireOwner(exam.getCreateBy(), "考试");

        SysExportTask task = SysExportTask.builder()
                .bizType(BIZ_TYPE_GRADE)
                .bizId(examId)
                .fileName(exam.getExamName() + "-成绩.xlsx")
                .status(STATUS_RUNNING)
                .createBy(LoginUtil.username())
                .createTime(LocalDateTime.now())
                .build();
        taskMapper.insert(task);
        try { worker.generate(task.getId()); }
        catch (org.springframework.core.task.TaskRejectedException e) { log.info("导出已排队，等待恢复调度 #{}", task.getId()); }
        return toVO(task);
    }

    /** 查询任务状态（只查自己发起的） */
    public ExportTaskVO task(Long taskId) {
        SysExportTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("导出任务不存在或已被清理");
        }
        if (!StringUtils.hasText(task.getCreateBy())
                || !task.getCreateBy().equals(LoginUtil.username())) {
            throw new BusinessException("只能查看自己发起的导出任务");
        }
        return toVO(task);
    }

    /** 我最近发起的导出任务（便于「下载过的文件」回看） */
    public List<ExportTaskVO> myTasks() {
        String owner = LoginUtil.username();
        if (!StringUtils.hasText(owner)) {
            return List.of();
        }
        return taskMapper.selectList(Wrappers.<SysExportTask>lambdaQuery()
                        .eq(SysExportTask::getCreateBy, owner)
                        .orderByDesc(SysExportTask::getId)
                        .last("limit 20"))
                .stream().map(this::toVO).toList();
    }

    private ExportTaskVO toVO(SysExportTask task) {
        ExportTaskVO vo = new ExportTaskVO();
        vo.setId(task.getId());
        vo.setStatus(task.getStatus());
        vo.setStatusName(statusName(task.getStatus()));
        vo.setFileName(task.getFileName());
        vo.setFileId(task.getFileId());
        vo.setDownloadUrl(task.getFileId() == null ? null : "/api/file/view/" + task.getFileId());
        vo.setErrorMsg(task.getErrorMsg());
        vo.setCreateTime(task.getCreateTime());
        vo.setFinishTime(task.getFinishTime());
        return vo;
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_SUCCESS -> "已完成";
            case STATUS_FAILED -> "生成失败";
            default -> "生成中";
        };
    }
}
