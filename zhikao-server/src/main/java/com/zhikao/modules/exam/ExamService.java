package com.zhikao.modules.exam;

import com.zhikao.common.PageResult;
import com.zhikao.entity.ExamAnnouncement;
import com.zhikao.modules.exam.dto.ExamQueryDTO;
import com.zhikao.modules.exam.dto.ExamSaveDTO;
import com.zhikao.modules.exam.vo.ExamMonitorVO;
import com.zhikao.modules.exam.vo.ExamVO;
import com.zhikao.modules.exam.vo.StudentExamVO;

import java.util.List;

/**
 * 考试服务：创建、发布、状态推进、监控，以及学生视角的考试列表。
 */
public interface ExamService {

    /** 分页查询考试（教师 / 管理员） */
    PageResult<ExamVO> page(ExamQueryDTO query);

    /** 考试详情 */
    ExamVO detail(Long id);

    /** 创建考试，返回考试 id */
    Long create(ExamSaveDTO dto);

    /** 发布考试（发布后学生才可见） */
    void publish(Long id);

    /** 提前结束考试（把结束时间置为当前时间，学生即刻不能交卷） */
    void finish(Long id);

    /** 删除考试（已有学生交卷时禁止删除） */
    void remove(Long id);

    /** 实时监控：应考 / 已进入 / 已交卷 / 已阅卷与得分概览 */
    ExamMonitorVO monitor(Long id);

    /** 学生视角：本人所在班级的已发布考试列表 */
    List<StudentExamVO> studentExamList();

    /** 新增考试公告（教师 / 管理员，需为该考试的创建人） */
    /** 新增考试公告；push=true 时同时推送站内通知给参考班级的学生 */
    Long addAnnouncement(Long examId, String content, boolean push);

    /** 某场考试的公告列表（按时间倒序） */
    List<ExamAnnouncement> announcements(Long examId);

    /** 删除公告（需为该考试创建人；管理员不受限） */
    void removeAnnouncement(Long announcementId);
}
