package com.zhikao.modules.exam;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.zhikao.common.OperLog;
import com.zhikao.common.R;
import com.zhikao.modules.exam.vo.ExamGradeVO;
import com.zhikao.modules.exam.vo.ExportTaskVO;
import com.zhikao.modules.exam.vo.MyPaperVO;
import com.zhikao.modules.exam.vo.StudentGradeVO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 成绩接口。
 * 教师侧：按考试统计与导出；学生侧：本人成绩与卷面明细。
 */
@RestController
@RequestMapping("/grade")
public class GradeController {

    private final GradeService gradeService;
    private final ExportTaskService exportTaskService;

    public GradeController(GradeService gradeService, ExportTaskService exportTaskService) {
        this.gradeService = gradeService;
        this.exportTaskService = exportTaskService;
    }

    /** 教师视角：某场考试成绩统计（含排名与分布） */
    @GetMapping("/exam/{examId}")
    @SaCheckPermission("grade:view")
    public R<ExamGradeVO> examGrade(@PathVariable Long examId) {
        return R.ok(gradeService.examGrade(examId));
    }

    /** 教师视角：导出成绩为 Excel（导出即数据出系统，单独一个权限点 grade:export） */
    @GetMapping("/exam/{examId}/export")
    @SaCheckPermission("grade:export")
    public ResponseEntity<byte[]> export(@PathVariable Long examId) {
        byte[] body = gradeService.exportExamGrade(examId);
        String filename = URLEncoder.encode("考试成绩.xlsx", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    /**
     * 异步导出：立即返回任务 id，后端生成完再下载。
     * 适用人数较多的场次——避免一条慢请求一直占着连接。
     */
    @PostMapping("/exam/{examId}/export/task")
    @SaCheckPermission("grade:export")
    @OperLog(module = "成绩管理", action = "异步导出成绩")
    public R<ExportTaskVO> submitExportTask(@PathVariable Long examId) {
        return R.ok(exportTaskService.submitGradeExport(examId));
    }

    /** 查询导出任务状态（生成完成后带下载地址） */
    @GetMapping("/export/task/{taskId}")
    @SaCheckPermission("grade:view")
    public R<ExportTaskVO> exportTask(@PathVariable Long taskId) {
        return R.ok(exportTaskService.task(taskId));
    }

    /** 我最近发起的导出任务 */
    @GetMapping("/export/tasks")
    @SaCheckPermission("grade:view")
    public R<List<ExportTaskVO>> myExportTasks() {
        return R.ok(exportTaskService.myTasks());
    }

    /** 学生视角：本人全部成绩 */
    @GetMapping("/mine")
    @SaCheckRole("student")
    public R<List<StudentGradeVO>> mine() {
        return R.ok(gradeService.myGrades());
    }

    /** 学生视角：本人某场考试的卷面明细（含答案与错题解析） */
    @GetMapping("/mine/{examId}")
    @SaCheckRole("student")
    public R<MyPaperVO> myPaper(@PathVariable Long examId) {
        return R.ok(gradeService.myPaper(examId));
    }
}
