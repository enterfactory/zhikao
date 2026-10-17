package com.zhikao.modules.exam;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.zhikao.common.OperLog;
import com.zhikao.common.PageResult;
import com.zhikao.common.R;
import com.zhikao.entity.ExamAnnouncement;
import com.zhikao.modules.exam.dto.AnnouncementSaveDTO;
import com.zhikao.modules.exam.dto.ExamQueryDTO;
import com.zhikao.modules.exam.dto.ExamSaveDTO;
import com.zhikao.modules.exam.vo.AnnouncementVO;
import com.zhikao.modules.exam.vo.ExamMonitorVO;
import com.zhikao.modules.exam.vo.ExamVO;
import com.zhikao.modules.exam.vo.StudentExamVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 考试管理接口。
 *
 * <p>RBAC 改造后的权限口径：管理动作按 exam:manage / exam:publish 两个权限点收敛——
 * 教务管理员与学科负责人都能管考试（exam:manage），发布是独立的高危动作（exam:publish），
 * 教师个人可管自己的考试但发布权限按角色配置；学生侧接口保留 @SaCheckRole("student")，
 * 因为「我的考试」不是 console 权限点，而是本人数据的入口。
 */
@RestController
@RequestMapping("/exam")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    /** 分页查询考试 */
    @GetMapping("/page")
    @SaCheckPermission("exam:manage")
    public R<PageResult<ExamVO>> page(ExamQueryDTO query) {
        return R.ok(examService.page(query));
    }

    /** 学生视角：本人所在班级的已发布考试 */
    @GetMapping("/mine")
    @SaCheckRole("student")
    public R<List<StudentExamVO>> mine() {
        return R.ok(examService.studentExamList());
    }

    /** 考试详情 */
    @GetMapping("/{id}")
    @SaCheckPermission("exam:manage")
    public R<ExamVO> detail(@PathVariable Long id) {
        return R.ok(examService.detail(id));
    }

    /** 创建考试 */
    @PostMapping
    @SaCheckPermission("exam:manage")
    @OperLog(module = "考试管理", action = "创建考试")
    public R<Long> create(@Valid @RequestBody ExamSaveDTO dto) {
        return R.ok(examService.create(dto));
    }

    /** 发布考试：独立权限点，「提前让全班看到卷面」必须单独授权 */
    @PostMapping("/{id}/publish")
    @SaCheckPermission("exam:publish")
    @OperLog(module = "考试管理", action = "发布考试")
    public R<Void> publish(@PathVariable Long id) {
        examService.publish(id);
        return R.ok();
    }

    /** 提前结束考试 */
    @PostMapping("/{id}/finish")
    @SaCheckPermission("exam:manage")
    @OperLog(module = "考试管理", action = "提前结束考试")
    public R<Void> finish(@PathVariable Long id) {
        examService.finish(id);
        return R.ok();
    }

    /** 实时监控 */
    @GetMapping("/{id}/monitor")
    @SaCheckPermission("exam:manage")
    public R<ExamMonitorVO> monitor(@PathVariable Long id) {
        return R.ok(examService.monitor(id));
    }

    /** 删除考试 */
    @DeleteMapping("/{id}")
    @SaCheckPermission("exam:manage")
    @OperLog(module = "考试管理", action = "删除考试")
    public R<Void> remove(@PathVariable Long id) {
        examService.remove(id);
        return R.ok();
    }

    // ==================== 考试公告 ====================

    /** 某场考试的公告列表（学生也能看，不挂权限点；对外只给 VO，不外放 create_by） */
    @GetMapping("/{id}/announcement")
    public R<List<AnnouncementVO>> announcements(@PathVariable Long id) {
        return R.ok(examService.announcements(id).stream().map(this::toVO).toList());
    }

    /** 新增考试公告 */
    @PostMapping("/{id}/announcement")
    @SaCheckPermission("exam:manage")
    @OperLog(module = "考试管理", action = "发布考试公告")
    public R<Long> addAnnouncement(@PathVariable Long id,
                                   @Valid @RequestBody AnnouncementSaveDTO dto) {
        return R.ok(examService.addAnnouncement(id, dto.getContent(),
                dto.getPush() == null || dto.getPush() == 1));
    }

    /** 删除考试公告 */
    @DeleteMapping("/announcement/{announcementId}")
    @SaCheckPermission("exam:manage")
    @OperLog(module = "考试管理", action = "删除考试公告")
    public R<Void> removeAnnouncement(@PathVariable Long announcementId) {
        examService.removeAnnouncement(announcementId);
        return R.ok();
    }

    /** 实体 -> 对外视图：create_by 属内部审计列，学生端公告只需要内容与时间 */
    private AnnouncementVO toVO(ExamAnnouncement entity) {
        AnnouncementVO vo = new AnnouncementVO();
        vo.setId(entity.getId());
        vo.setExamId(entity.getExamId());
        vo.setContent(entity.getContent());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
