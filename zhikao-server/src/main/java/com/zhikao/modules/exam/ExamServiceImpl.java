package com.zhikao.modules.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.common.BusinessException;
import com.zhikao.common.DataScope;
import com.zhikao.common.ExamStatusEnum;
import com.zhikao.common.ExamStudentStatusEnum;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.PageResult;
import com.zhikao.common.UserTypeEnum;
import com.zhikao.entity.Exam;
import com.zhikao.entity.ExamAnnouncement;
import com.zhikao.entity.ExamStudent;
import com.zhikao.entity.Paper;
import com.zhikao.entity.Subject;
import com.zhikao.entity.SysClass;
import com.zhikao.entity.SysSemester;
import com.zhikao.entity.SysUser;
import com.zhikao.mapper.ExamAnnouncementMapper;
import com.zhikao.mapper.ExamMapper;
import com.zhikao.mapper.ExamStudentMapper;
import com.zhikao.mapper.PaperMapper;
import com.zhikao.mapper.SubjectMapper;
import com.zhikao.mapper.SysClassMapper;
import com.zhikao.mapper.SysSemesterMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.modules.notification.NotificationService;
import com.zhikao.modules.notification.NotificationTypeEnum;
import com.zhikao.modules.exam.dto.ExamQueryDTO;
import com.zhikao.modules.exam.dto.ExamSaveDTO;
import com.zhikao.modules.exam.vo.ExamMonitorVO;
import com.zhikao.modules.exam.vo.ExamVO;
import com.zhikao.modules.exam.vo.StudentExamVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考试服务实现。
 */
@Service
public class ExamServiceImpl implements ExamService {

    /** 未发布 */
    private static final int UNPUBLISHED = 0;
    /** 已发布 */
    private static final int PUBLISHED = 1;

    private final com.zhikao.mapper.ExamRosterMapper roster;
    private final ExamMapper examMapper;
    private final ExamStudentMapper examStudentMapper;
    private final PaperMapper paperMapper;
    private final SubjectMapper subjectMapper;
    private final SysClassMapper classMapper;
    private final SysUserMapper userMapper;
    private final ExamTimeSupport timeSupport;
    private final DataScope dataScope;
    private final ExamAnnouncementMapper announcementMapper;
    private final NotificationService notificationService;
    private final SysSemesterMapper semesterMapper;

    public ExamServiceImpl(ExamMapper examMapper, ExamStudentMapper examStudentMapper,
                           PaperMapper paperMapper, SubjectMapper subjectMapper,
                           SysClassMapper classMapper, SysUserMapper userMapper,
                           ExamTimeSupport timeSupport, DataScope dataScope,
                           ExamAnnouncementMapper announcementMapper,
                           NotificationService notificationService,
                           SysSemesterMapper semesterMapper,com.zhikao.mapper.ExamRosterMapper roster) {
        this.roster=roster;
        this.examMapper = examMapper;
        this.examStudentMapper = examStudentMapper;
        this.paperMapper = paperMapper;
        this.subjectMapper = subjectMapper;
        this.classMapper = classMapper;
        this.userMapper = userMapper;
        this.timeSupport = timeSupport;
        this.dataScope = dataScope;
        this.announcementMapper = announcementMapper;
        this.notificationService = notificationService;
        this.semesterMapper = semesterMapper;
    }

    @Override
    public PageResult<ExamVO> page(ExamQueryDTO query) {
        syncStatus();
        LambdaQueryWrapper<Exam> wrapper = Wrappers.<Exam>lambdaQuery()
                .like(StringUtils.hasText(query.getKeyword()), Exam::getExamName, query.getKeyword())
                .eq(query.getClassId() != null, Exam::getClassId, query.getClassId())
                .eq(query.getStatus() != null, Exam::getStatus, query.getStatus())
                .eq(query.getPublished() != null, Exam::getPublished, query.getPublished())
                .orderByDesc(Exam::getId);

        Page<Exam> page = examMapper.selectPage(
                new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        Map<Long, Paper> papers = paperMap(page.getRecords());
        Map<Long, String> subjectNames = subjectNames();
        Map<Long, String> classNames = classNames();
        Map<Long, String> semesters = semesterNames();
        return PageResult.of(page, exam -> toVO(exam, papers, subjectNames, classNames, semesters));
    }

    @Override
    public ExamVO detail(Long id) {
        Exam exam = requireExam(id);
        return toVO(exam, paperMap(List.of(exam)), subjectNames(), classNames(), semesterNames());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ExamSaveDTO dto) {
        Paper paper = paperMapper.selectById(dto.getPaperId());
        if (paper == null) {
            throw new BusinessException("试卷不存在");
        }
        if (paper.getStatus() == null || paper.getStatus() != 1) {
            throw new BusinessException("试卷尚未发布，请先在「试卷管理」中发布试卷");
        }
        if (classMapper.selectById(dto.getClassId()) == null) {
            throw new BusinessException("参考班级不存在");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException("考试结束时间必须晚于开始时间");
        }

        LocalDateTime now = LocalDateTime.now();
        Exam exam = Exam.builder()
                .examName(dto.getExamName().trim())
                .paperId(dto.getPaperId())
                .classId(dto.getClassId())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .duration(dto.getDuration() == null ? paper.getTotalDuration() : dto.getDuration())
                .status(resolveStatus(dto.getStartTime(), dto.getEndTime(), now))
                .published(UNPUBLISHED)
                .autoMark(dto.getAutoMark() == null ? 1 : dto.getAutoMark())
                .cheatCheck(dto.getCheatCheck() == null ? 0 : dto.getCheatCheck())
                // 学期透传保存：传入非法 id 时保持为空即可——学期是渐进接入的维度，
                // 不能因为一个可选字段把「创建考试」主流程拦死
                .semesterId(dto.getSemesterId())
                .createBy(LoginUtil.username())
                .createTime(now)
                .build();
        examMapper.insert(exam);
        return exam.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Exam exam = examMapper.lockById(id);
        if (exam == null) throw new BusinessException("考试不存在");
        // 水平越权校验：教师只能发布自己创建的考试（管理员不受限）
        dataScope.requireOwner(exam.getCreateBy(), "考试");
        if (Integer.valueOf(PUBLISHED).equals(exam.getPublished())) return;
        if (exam.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("考试结束时间已过，请先修改时间再发布");
        }
        exam.setStatus(resolveStatus(exam.getStartTime(), exam.getEndTime(), LocalDateTime.now()));
        roster.freeze(id);exam.setRosterFrozen(1);
        exam.setPublished(PUBLISHED);
        examMapper.updateById(exam);
        // 「发布」的全部意义就是让学生知道，所以这一步要主动走到学生面前。
        // 只改数据库里的 published 字段，学生除了自己反复刷新页面别无感知方式——
        // 这正是上线时最容易漏的一环：数据是对的，但没人被告知。
        notifyClass(exam, NotificationTypeEnum.EXAM_PUBLISH,
                "考试发布：" + exam.getExamName(),
                "你有一场新的考试，开始时间 " + formatTime(exam.getStartTime()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id) {
        examMapper.lockById(id);
        Exam exam = requireExam(id);
        dataScope.requireOwner(exam.getCreateBy(), "考试");
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(exam.getEndTime())) return;
        // 提前收卷：把结束时间拉到当前，学生即刻不能再交卷
        exam.setEndTime(now);
        exam.setStatus(ExamStatusEnum.FINISHED.getCode());
        examMapper.updateById(exam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        Exam exam = examMapper.lockById(id);
        if (exam == null) throw new BusinessException("考试不存在");
        dataScope.requireOwner(exam.getCreateBy(), "考试");
        // 已有学生交卷的考试不能删：成绩数据不可丢失
        Long submitted = examStudentMapper.selectCount(Wrappers.<ExamStudent>lambdaQuery()
                .eq(ExamStudent::getExamId, id));
        if (submitted > 0) {
            throw new BusinessException("该考试已有 " + submitted + " 名学生已进入，不能删除");
        }
        examStudentMapper.delete(Wrappers.<ExamStudent>lambdaQuery().eq(ExamStudent::getExamId, id));
        examMapper.deleteById(id);
    }

    @Override
    public ExamMonitorVO monitor(Long id) {
        Exam exam = requireExam(id);
        syncStatus();

        ExamMonitorVO vo = new ExamMonitorVO();
        vo.setExamId(exam.getId());
        vo.setExamName(exam.getExamName());
        // 应考人数：参考班级的启用学生数
        vo.setShouldCount(roster.count(id));
        vo.setRosterBasis(Integer.valueOf(1).equals(exam.getRosterFrozen())?"publication_snapshot":"legacy_participants_only");
        vo.setEnteredCount(examStudentMapper.selectCount(Wrappers.<ExamStudent>lambdaQuery()
                .eq(ExamStudent::getExamId, id)
                .ge(ExamStudent::getStatus, ExamStudentStatusEnum.RUNNING.getCode())));
        vo.setSubmittedCount(examStudentMapper.selectCount(Wrappers.<ExamStudent>lambdaQuery()
                .eq(ExamStudent::getExamId, id)
                .ge(ExamStudent::getStatus, ExamStudentStatusEnum.SUBMITTED.getCode())));
        vo.setMarkedCount(examStudentMapper.selectCount(Wrappers.<ExamStudent>lambdaQuery()
                .eq(ExamStudent::getExamId, id)
                .eq(ExamStudent::getStatus, ExamStudentStatusEnum.MARKED.getCode())));

        List<ExamStudent> scored = examStudentMapper.selectList(Wrappers.<ExamStudent>lambdaQuery()
                .eq(ExamStudent::getExamId, id)
                .ge(ExamStudent::getStatus, ExamStudentStatusEnum.MARKED.getCode()));
        List<BigDecimal> scores = scored.stream()
                .map(ExamStudent::getTotalScore)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (!scores.isEmpty()) {
            BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setAvgScore(sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP));
            vo.setMaxScore(scores.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            vo.setMinScore(scores.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
        }
        return vo;
    }

    @Override
    public List<StudentExamVO> studentExamList() {
        syncStatus();
        Long studentId = LoginUtil.userId();
        List<Long> eligible=roster.exams(studentId);
        if(eligible.isEmpty())return List.of();
        List<Exam> exams=examMapper.selectList(Wrappers.<Exam>lambdaQuery().eq(Exam::getPublished,PUBLISHED)
                .in(Exam::getId,eligible).orderByDesc(Exam::getStartTime));
        Map<Long, Paper> papers = paperMap(exams);
        Map<Long, String> subjectNames = subjectNames();

        // 一次取出本人在这些考试中的参与记录，避免逐场查询
        List<Long> examIds = exams.stream().map(Exam::getId).collect(Collectors.toList());
        Map<Long, ExamStudent> mine = examIds.isEmpty()
                ? Map.of()
                : examStudentMapper.selectList(Wrappers.<ExamStudent>lambdaQuery()
                        .eq(ExamStudent::getStudentId, studentId)
                        .in(ExamStudent::getExamId, examIds))
                .stream().collect(Collectors.toMap(ExamStudent::getExamId, Function.identity(), (a, b) -> a));

        // 一次取出这些考试的最新公告（按 id 倒序，首次出现即最新），避免逐场查库
        Map<Long, String> announcements = examIds.isEmpty()
                ? Map.of()
                : announcementMapper.selectList(Wrappers.<ExamAnnouncement>lambdaQuery()
                        .in(ExamAnnouncement::getExamId, examIds)
                        .orderByDesc(ExamAnnouncement::getId))
                .stream()
                .collect(Collectors.toMap(ExamAnnouncement::getExamId,
                        ExamAnnouncement::getContent, (first, second) -> first));

        LocalDateTime now = LocalDateTime.now();
        List<StudentExamVO> result = new ArrayList<>();
        for (Exam exam : exams) {
            Paper paper = papers.get(exam.getPaperId());
            ExamStudent record = mine.get(exam.getId());

            StudentExamVO vo = new StudentExamVO();
            vo.setId(exam.getId());
            vo.setExamName(exam.getExamName());
            vo.setAnnouncement(announcements.get(exam.getId()));
            vo.setPaperName(paper == null ? null : paper.getPaperName());
            vo.setSubjectName(paper == null ? null : subjectNames.get(paper.getSubjectId()));
            vo.setStartTime(exam.getStartTime());
            vo.setEndTime(exam.getEndTime());
            vo.setDuration(exam.getDuration());
            vo.setStatus(exam.getStatus());
            vo.setStatusName(ExamStatusEnum.labelOf(exam.getStatus()));
            vo.setReleaseStatus(exam.getReleaseStatus() == null ? 0 : exam.getReleaseStatus());
            vo.setQuestionCount(paper == null ? null : paper.getQuestionCount());
            vo.setTotalScore(paper == null ? null : paper.getTotalScore());
            if (record != null) {
                vo.setMyStatus(record.getStatus());
                vo.setMyStatusName(ExamStudentStatusEnum.labelOf(record.getStatus()));
                vo.setObjectiveScore(AnswerVisibilityPolicy.canSeeScore(exam, LocalDateTime.now()) && record.getStatus() == 3 ? record.getObjectiveScore() : null);
                vo.setSubjectiveScore(AnswerVisibilityPolicy.canSeeScore(exam, LocalDateTime.now()) && record.getStatus() == 3 ? record.getSubjectiveScore() : null);
                vo.setMyTotalScore(AnswerVisibilityPolicy.canSeeScore(exam, LocalDateTime.now()) && record.getStatus() == 3 ? record.getTotalScore() : null);
                if (ExamStudentStatusEnum.of(record.getStatus()) == ExamStudentStatusEnum.RUNNING) {
                    vo.setRemainingSeconds(timeSupport.remainingSeconds(exam, record, now));
                }
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public Long addAnnouncement(Long examId, String content, boolean push) {
        Exam exam = requireExam(examId);
        dataScope.requireOwner(exam.getCreateBy(), "考试");
        ExamAnnouncement announcement = ExamAnnouncement.builder()
                .examId(examId)
                .content(content.trim())
                .createBy(LoginUtil.username())
                .createTime(LocalDateTime.now())
                .build();
        announcementMapper.insert(announcement);
        if (push) {
            // 公告默认要推送：写了公告却没人看到，比不写更让人困惑。
            // 教师如果只是想留个备忘，可以显式关掉推送。
            notifyClass(exam, NotificationTypeEnum.EXAM_ANNOUNCEMENT,
                    "考试公告：" + exam.getExamName(), announcement.getContent());
        }
        return announcement.getId();
    }

    /** 给某场考试参考班级的全体启用学生发一条通知 */
    private void notifyClass(Exam exam, NotificationTypeEnum type, String title, String content) {
        List<Long> studentIds=roster.students(exam.getId());
        notificationService.send(studentIds, type, title, content, "exam", exam.getId());
    }

    /** 时间显示统一到这里，避免各处格式不一致 */
    private String formatTime(LocalDateTime time) {
        return time.toString().replace('T', ' ').substring(0, 16);
    }

    @Override
    public List<ExamAnnouncement> announcements(Long examId) {
        requireExam(examId);
        return announcementMapper.selectList(Wrappers.<ExamAnnouncement>lambdaQuery()
                .eq(ExamAnnouncement::getExamId, examId)
                .orderByDesc(ExamAnnouncement::getId));
    }

    @Override
    public void removeAnnouncement(Long announcementId) {
        ExamAnnouncement announcement = announcementMapper.selectById(announcementId);
        if (announcement == null) {
            throw new BusinessException("公告不存在");
        }
        Exam exam = requireExam(announcement.getExamId());
        dataScope.requireOwner(exam.getCreateBy(), "考试");
        announcementMapper.deleteById(announcementId);
    }

    // ==================== 内部方法 ====================

    /** 读取考试，不存在则报错 */
    private Exam requireExam(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        return exam;
    }

    /** 刷新全部已发布考试的状态（时间驱动） */
    void syncStatus() {
        LocalDateTime now = LocalDateTime.now();
        examMapper.syncFinished(now);
        examMapper.syncRunning(now);
        examMapper.syncNotStarted(now);
    }

    /** 按时间窗推导考试状态 */
    private int resolveStatus(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (now.isBefore(start)) {
            return ExamStatusEnum.NOT_STARTED.getCode();
        }
        if (now.isAfter(end)) {
            return ExamStatusEnum.FINISHED.getCode();
        }
        return ExamStatusEnum.RUNNING.getCode();
    }

    private Map<Long, Paper> paperMap(List<Exam> exams) {
        List<Long> paperIds = exams.stream().map(Exam::getPaperId).distinct().collect(Collectors.toList());
        if (paperIds.isEmpty()) {
            return Map.of();
        }
        return paperMapper.selectBatchIds(paperIds).stream()
                .collect(Collectors.toMap(Paper::getId, Function.identity()));
    }

    private Map<Long, String> subjectNames() {
        return subjectMapper.selectList(null).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getSubjectName));
    }

    private Map<Long, String> classNames() {
        return classMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysClass::getId, SysClass::getClassName));
    }

    /** 学期 id -> 名称：量级是个位数，全量取回内存拼，不给列表页加逐行查询 */
    private Map<Long, String> semesterNames() {
        return semesterMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysSemester::getId, SysSemester::getName));
    }

    private ExamVO toVO(Exam exam, Map<Long, Paper> papers, Map<Long, String> subjectNames,
                        Map<Long, String> classNames, Map<Long, String> semesterNames) {
        Paper paper = papers.get(exam.getPaperId());
        ExamVO vo = new ExamVO();
        vo.setId(exam.getId());
        vo.setExamName(exam.getExamName());
        vo.setPaperId(exam.getPaperId());
        vo.setPaperName(paper == null ? null : paper.getPaperName());
        vo.setSubjectId(paper == null ? null : paper.getSubjectId());
        vo.setSubjectName(paper == null ? null : subjectNames.get(paper.getSubjectId()));
        vo.setClassId(exam.getClassId());
        vo.setClassName(classNames.get(exam.getClassId()));
        vo.setStartTime(exam.getStartTime());
        vo.setEndTime(exam.getEndTime());
        vo.setDuration(exam.getDuration());
        vo.setStatus(exam.getStatus());
        vo.setStatusName(ExamStatusEnum.labelOf(exam.getStatus()));
        vo.setPublished(exam.getPublished());
        vo.setAutoMark(exam.getAutoMark());
        vo.setCheatCheck(exam.getCheatCheck());
        vo.setReleaseStatus(exam.getReleaseStatus() == null ? 0 : exam.getReleaseStatus());
            vo.setQuestionCount(paper == null ? null : paper.getQuestionCount());
        vo.setTotalScore(paper == null ? null : paper.getTotalScore());
        vo.setSemesterId(exam.getSemesterId());
        vo.setSemesterName(semesterNames.get(exam.getSemesterId()));
        vo.setCreateBy(exam.getCreateBy());
        vo.setCreateTime(exam.getCreateTime());
        return vo;
    }
}
