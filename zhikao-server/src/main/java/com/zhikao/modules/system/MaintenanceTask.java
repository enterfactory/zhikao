package com.zhikao.modules.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.entity.AiTask;
import com.zhikao.entity.SysExportTask;
import com.zhikao.entity.SysOperLog;
import com.zhikao.mapper.AiTaskMapper;
import com.zhikao.mapper.SysExportTaskMapper;
import com.zhikao.mapper.SysOperLogMapper;
import com.zhikao.modules.file.FileCategory;
import com.zhikao.modules.file.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据维护定时任务：给「只增不减」的表装上出口。
 *
 * <p>没有这些清理时，sys_oper_log、ai_task、sys_file 三张表在演示环境看不出问题，
 * 跑上几个月就是「磁盘不知不觉满了、列表查询越来越慢」，而且没人知道是哪天开始的。
 * 清理策略按数据性质区分保留期，而不是一刀切：
 *
 * <ul>
 *   <li><b>操作日志 90 天</b>：审计价值随时间衰减，但要覆盖一个学期的追溯需求。</li>
 *   <li><b>AI 任务 90 天</b>：成本核算通常按月/季度看，保留一个季度足够。</li>
 *   <li><b>导出产物 7 天</b>：导出文件是临时物，用户需要的是「当时能下载」，
 *       过期后文件本身没有保留价值，且它最容易占磁盘。</li>
 *   <li><b>批改留痕与作弊证据不清理</b>：这是成绩争议时的凭据，
 *       数据量也远小于上面三类，保留下来收益大于成本。</li>
 * </ul>
 */
@Component
public class MaintenanceTask {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceTask.class);

    private final SysOperLogMapper operLogMapper;
    private final AiTaskMapper aiTaskMapper;
    private final SysExportTaskMapper exportTaskMapper;
    private final FileStorageService fileStorage;

    private final int operLogDays;
    private final int aiTaskDays;
    private final int exportFileDays;

    public MaintenanceTask(SysOperLogMapper operLogMapper, AiTaskMapper aiTaskMapper,
                           SysExportTaskMapper exportTaskMapper,
                           FileStorageService fileStorage,
                           @Value("${zhikao.maintain.oper-log-days:90}") int operLogDays,
                           @Value("${zhikao.maintain.ai-task-days:90}") int aiTaskDays,
                           @Value("${zhikao.maintain.export-file-days:7}") int exportFileDays) {
        this.operLogMapper = operLogMapper;
        this.aiTaskMapper = aiTaskMapper;
        this.exportTaskMapper = exportTaskMapper;
        this.fileStorage = fileStorage;
        this.operLogDays = operLogDays;
        this.aiTaskDays = aiTaskDays;
        this.exportFileDays = exportFileDays;
    }

    /** 每天凌晨 3:15 执行：业务低峰，且不与定时收卷争抢连接 */
    @Scheduled(cron = "${zhikao.maintain.cron:0 15 3 * * *}")
    public void run() {
        try {
            cleanOperLog();
            cleanAiTask();
            cleanExportFiles();
        } catch (Exception e) {
            log.warn("数据维护任务执行失败：{}", e.getMessage());
        }
    }

    private void cleanOperLog() {
        LocalDateTime before = LocalDateTime.now().minusDays(operLogDays);
        int rows = operLogMapper.delete(Wrappers.<SysOperLog>lambdaQuery()
                .lt(SysOperLog::getOperTime, before));
        if (rows > 0) {
            log.info("清理 {} 天前操作日志 {} 条", operLogDays, rows);
        }
    }

    private void cleanAiTask() {
        LocalDateTime before = LocalDateTime.now().minusDays(aiTaskDays);
        int rows = aiTaskMapper.delete(Wrappers.<AiTask>lambdaQuery()
                .lt(AiTask::getCreateTime, before));
        if (rows > 0) {
            log.info("清理 {} 天前 AI 任务记录 {} 条", aiTaskDays, rows);
        }
    }

    private void cleanExportFiles() {
        LocalDateTime before = LocalDateTime.now().minusDays(exportFileDays);
        int files = fileStorage.purge(FileCategory.EXPORT.getCode(), before);
        int tasks = exportTaskMapper.delete(Wrappers.<SysExportTask>lambdaQuery()
                .lt(SysExportTask::getCreateTime, before));
        if (files > 0 || tasks > 0) {
            log.info("清理 {} 天前导出文件 {} 个、导出任务 {} 条", exportFileDays, files, tasks);
        }
    }
}
