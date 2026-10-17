package com.zhikao.modules.exam;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.AnswerJudge;
import com.zhikao.common.BusinessException;
import com.zhikao.common.DataScope;
import com.zhikao.common.ExamStudentStatusEnum;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.QuestionTypeEnum;
import com.zhikao.entity.Exam;
import com.zhikao.entity.ExamAnswer;
import com.zhikao.entity.ExamCheatLog;
import com.zhikao.entity.ExamMarkLog;
import com.zhikao.entity.ExamStudent;
import com.zhikao.entity.Paper;
import com.zhikao.entity.PaperQuestion;
import com.zhikao.entity.SysUser;
import com.zhikao.mapper.ExamAnswerMapper;
import com.zhikao.mapper.ExamCheatLogMapper;
import com.zhikao.mapper.ExamMapper;
import com.zhikao.mapper.ExamMarkLogMapper;
import com.zhikao.mapper.ExamStudentMapper;
import com.zhikao.mapper.PaperMapper;
import com.zhikao.mapper.PaperQuestionMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.modules.exam.dto.AnswerSaveDTO;
import com.zhikao.modules.exam.dto.CheatReportDTO;
import com.zhikao.modules.notification.NotificationService;
import com.zhikao.modules.notification.NotificationTypeEnum;
import com.zhikao.modules.exam.vo.CheatLogVO;
import com.zhikao.modules.exam.vo.CheatReportVO;
import com.zhikao.modules.exam.vo.EnterExamVO;
import com.zhikao.modules.exam.vo.MarkItemVO;
import com.zhikao.modules.exam.vo.MarkLogVO;
import com.zhikao.modules.exam.vo.SubmitResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 答题与阅卷服务实现。
 *
 * 并发与一致性要点：
 * 1. 交卷用数据库条件更新（exam_student.status 仅从「考试中」推进为「已交卷」）做幂等保证，
 *    并发重复提交只有一次能成功，从根上避免重复判分；不依赖外部锁组件。
 * 2. 批改用 exam_answer.mark_version 做乐观锁：两位教师同时打分时，后到的那一位
 *    拿到 0 行更新并被明确告知「请刷新」，而不是静默覆盖别人的分数。
 * 3. 判分与状态更新在同一事务内完成，避免出现「答分明细写了、总分没写」的中间态。
 * 4. 剩余时间由服务端按「进入时间 + 时长」与「考试结束时间」取小计算，前端倒计时仅作展示；
 *    学生离开页面导致前端倒计时归零后，由定时任务按同一套口径把卷收上来。
 */
@Service
public class AnswerServiceImpl implements AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerServiceImpl.class);

    private static final int FLAG_ON = 1;

    /** 交卷方式：1手动 2超时自动收卷 3作弊强制收卷 */
    private static final int SUBMIT_MANUAL = 1;
    private static final int SUBMIT_TIMEOUT = 2;
    private static final int SUBMIT_FORCED = 3;

    /** 批改留痕动作：1首评 2改分 */
    private static final int MARK_ACTION_FIRST = 1;
    private static final int MARK_ACTION_CHANGE = 2;

    private final com.zhikao.mapper.ExamRosterMapper roster;
    private final ExamMapper examMapper;
    private final ExamStudentMapper examStudentMapper;
    private final ExamAnswerMapper examAnswerMapper;
    private final ExamMarkLogMapper markLogMapper;
    private final ExamCheatLogMapper cheatLogMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final PaperMapper paperMapper;
    private final SysUserMapper userMapper;
    private final PaperService paperService;
    private final ExamTimeSupport timeSupport;
    private final DataScope dataScope;
    private final NotificationService notificationService;
    private final org.springframework.transaction.support.TransactionTemplate submissionTransaction;
    private long timeoutCursor;

    /** 达到该次数开始警告学生（配置项，可在 application.yml 调整） */
    private final int cheatWarnThreshold;

    /** 达到该次数视为严重异常 */
    private final int cheatSeriousThreshold;

    /**
     * 达到严重阈值时是否自动强制收卷。
     *
     * 默认关闭：自动收卷会直接剥夺学生的答题机会，属于「一旦误判就无法挽回」的动作。
     * 默认行为是把证据完整记录下来并标为严重，由教师决定处置——
     * 系统负责把事实摆清楚，做决定的仍然是人。
     */
    private final boolean cheatForceSubmit;

    public AnswerServiceImpl(com.zhikao.mapper.ExamRosterMapper roster,ExamMapper examMapper, ExamStudentMapper examStudentMapper,
                             ExamAnswerMapper examAnswerMapper, ExamMarkLogMapper markLogMapper,
                             ExamCheatLogMapper cheatLogMapper, PaperQuestionMapper paperQuestionMapper,
                             PaperMapper paperMapper, SysUserMapper userMapper,
                             PaperService paperService, ExamTimeSupport timeSupport,
                             DataScope dataScope,
                             NotificationService notificationService,
                             org.springframework.transaction.PlatformTransactionManager transactionManager,
                             @Value("${zhikao.exam.cheat-warn-threshold:3}") int cheatWarnThreshold,
                             @Value("${zhikao.exam.cheat-serious-threshold:10}") int cheatSeriousThreshold,
                             @Value("${zhikao.exam.cheat-force-submit:false}") boolean cheatForceSubmit) {
        this.roster=roster;
        this.examMapper = examMapper;
        this.examStudentMapper = examStudentMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.markLogMapper = markLogMapper;
        this.cheatLogMapper = cheatLogMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.paperMapper = paperMapper;
        this.userMapper = userMapper;
        this.paperService = paperService;
        this.timeSupport = timeSupport;
        this.dataScope = dataScope;
        this.notificationService = notificationService;
        this.submissionTransaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.cheatWarnThreshold = cheatWarnThreshold;
        this.cheatSeriousThreshold = cheatSeriousThreshold;
        this.cheatForceSubmit = cheatForceSubmit;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnterExamVO enter(Long examId) {
        examMapper.lockById(examId); // Serialize first entry with publication; avoid duplicate attempts.
        Exam exam = requireExam(examId);
        SysUser student = requireStudentInClass(exam);
        LocalDateTime now = LocalDateTime.now();

        ExamStudent record = findRecord(examId, student.getId());
        if (record != null
                && ExamStudentStatusEnum.of(record.getStatus()) != ExamStudentStatusEnum.RUNNING) {
            throw new BusinessException("你已交卷，不能重复进入考试");
        }
        if (record == null) {
            // 惰性创建参与记录：不需要在建考试时给全班预插数据
            record = ExamStudent.builder()
                    .examId(examId)
                    .studentId(student.getId())
                    .status(ExamStudentStatusEnum.RUNNING.getCode())
                    .enterTime(now)
                    .objectiveScore(BigDecimal.ZERO)
                    .subjectiveScore(BigDecimal.ZERO)
                    .totalScore(BigDecimal.ZERO)
                    .cheatCount(0)
                    .build();
            examStudentMapper.insert(record);
        }

        Paper paper = paperMapper.selectById(exam.getPaperId());
        EnterExamVO vo = new EnterExamVO();
        vo.setExamId(exam.getId());
        vo.setExamName(exam.getExamName());
        vo.setPaperName(paper == null ? null : paper.getPaperName());
        vo.setTotalScore(paper == null ? null : paper.getTotalScore());
        vo.setDuration(exam.getDuration());
        vo.setRemainingSeconds(timeSupport.remainingSeconds(exam, record, now));
        vo.setAutoMark(exam.getAutoMark());
        vo.setCheatCheck(exam.getCheatCheck());
        vo.setMyStatus(record.getStatus());
        vo.setMyStatusName(ExamStudentStatusEnum.labelOf(record.getStatus()));
        // 学生取卷：withAnswer=false，标准答案绝不下发给考生
        vo.setQuestions(paperService.listQuestions(exam.getPaperId(), false));

        // 已保存的作答（支持中途退出后继续作答）
        Map<Long, String> saved = examAnswerMapper.selectList(Wrappers.<ExamAnswer>lambdaQuery()
                        .eq(ExamAnswer::getExamStudentId, record.getId()))
                .stream()
                .filter(answer -> StringUtils.hasText(answer.getAnswer()))
                .collect(Collectors.toMap(ExamAnswer::getPaperQuestionId, ExamAnswer::getAnswer, (a, b) -> a));
        vo.setSavedAnswers(saved);
        vo.setAnswerVersions(examAnswerMapper.selectList(Wrappers.<ExamAnswer>lambdaQuery().eq(ExamAnswer::getExamStudentId, record.getId())).stream().collect(Collectors.toMap(ExamAnswer::getPaperQuestionId, a -> a.getAnswerVersion() == null ? 0 : a.getAnswerVersion())));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAnswer(Long examId, AnswerSaveDTO dto) {
        Exam exam = requireExam(examId);
        SysUser student = requireStudentInClass(exam);
        ExamStudent record = requireRunningRecord(examId, student.getId());
        LocalDateTime now = LocalDateTime.now();
        if (timeSupport.isExpired(exam, record, now)) {
            throw new BusinessException("答题时间已到，请立即交卷");
        }

        PaperQuestion question = paperQuestionMapper.selectById(dto.getPaperQuestionId());
        if (question == null || !question.getPaperId().equals(exam.getPaperId())) {
            throw new BusinessException("题目不属于本次考试");
        }

        // 逐题 upsert：只存作答内容，判分统一放到交卷时做（避免边答边判产生中间态）
        ExamAnswer exist = examAnswerMapper.selectOne(Wrappers.<ExamAnswer>lambdaQuery()
                .eq(ExamAnswer::getExamStudentId, record.getId())
                .eq(ExamAnswer::getPaperQuestionId, dto.getPaperQuestionId()));
        if (dto.getExpectedVersion() != null && dto.getExpectedVersion() != (exist == null || exist.getAnswerVersion() == null ? 0 : exist.getAnswerVersion())) {
            throw new BusinessException(40901, "作答版本冲突，请重新载入保存状态");
        }
        if (exist == null) {
            examAnswerMapper.insert(ExamAnswer.builder()
                    .examStudentId(record.getId())
                    .paperQuestionId(dto.getPaperQuestionId())
                    .answer(dto.getAnswer() == null ? "" : dto.getAnswer())
                    .answerVersion(1)
                    .isObjective(QuestionTypeEnum.isObjective(question.getType()) ? 1 : 0)
                    .marked(0)
                    .build());
        } else {
            if (exist.getMarked() != null && exist.getMarked() == 1) {
                throw new BusinessException("该题已判分，不能再修改");
            }
            exist.setAnswer(dto.getAnswer() == null ? "" : dto.getAnswer());
            exist.setAnswerVersion((exist.getAnswerVersion() == null ? 0 : exist.getAnswerVersion()) + 1);
            examAnswerMapper.updateById(exist);
        }
        return timeSupport.remainingSeconds(exam, record, now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheatReportVO reportCheat(Long examId, CheatReportDTO dto) {
        CheatReportVO vo = new CheatReportVO();
        vo.setWarnThreshold(cheatWarnThreshold);
        vo.setSeriousThreshold(cheatSeriousThreshold);

        Exam exam = requireExam(examId);
        if (exam.getCheatCheck() == null || exam.getCheatCheck() != FLAG_ON) {
            // 未开启防作弊：明确告诉调用方「没记」，而不是让它以为记上了
            vo.setCount(0);
            vo.setAction("ignored");
            vo.setMessage("本场考试未开启防作弊，本次上报未记录");
            return vo;
        }
        SysUser student = requireStudentInClass(exam);
        ExamStudent record = findRecord(examId, student.getId());
        if (record == null) {
            vo.setCount(0);
            vo.setAction("ignored");
            vo.setMessage("尚未进入考试，本次上报未记录");
            return vo;
        }

        int count = (record.getCheatCount() == null ? 0 : record.getCheatCount()) + 1;
        record.setCheatCount(count);
        examStudentMapper.updateById(record);

        // 证据链：只存计数时回答不了「什么时候、第几题」，而教师做处置决定需要的正是这些
        String type = dto == null || !StringUtils.hasText(dto.getType()) ? "blur" : dto.getType().trim();
        cheatLogMapper.insert(ExamCheatLog.builder()
                .examStudentId(record.getId())
                .examId(examId)
                .cheatType(type)
                .happenTime(LocalDateTime.now())
                .paperQuestionId(dto == null ? null : dto.getPaperQuestionId())
                .remark(dto == null ? null : dto.getRemark())
                .seq(count)
                .build());

        vo.setCount(count);
        String message = "已记录第 " + count + " 次异常行为";
        if (count >= cheatSeriousThreshold) {
            // 严重阈值：默认只标记，是否自动收卷由配置决定（默认关闭，见字段注释）
            if (cheatForceSubmit && ExamStudentStatusEnum.of(record.getStatus()) == ExamStudentStatusEnum.RUNNING) {
                doSubmit(exam, record, SUBMIT_FORCED);
                vo.setAction("force_submitted");
                vo.setMessage("异常次数已达 " + count + " 次，系统已强制收卷，本场成绩以当前作答为准");
            } else {
                vo.setAction("serious");
                vo.setMessage(message + "，已达严重阈值，请监考教师核实后处置");
            }
        } else if (count >= cheatWarnThreshold) {
            vo.setAction("warned");
            vo.setMessage(message + "，异常次数偏多，继续切屏可能被强制收卷");
        } else {
            vo.setAction("recorded");
            vo.setMessage(message);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitResultVO submit(Long examId) {
        // 这里刻意**不**用 requireExam：那份校验在「考试已结束」时直接抛异常，
        // 而恰恰是这批学生（浏览器关了、倒计时归零了）最需要把卷收上来。
        // 是否超时由 doSubmit 按同一套时间口径判断并决定处置方式。
        Exam exam = requireExamAllowExpired(examId);
        SysUser student = requireStudentInClass(exam);
        ExamStudent record = findRecord(examId, student.getId());
        if (record == null) {
            throw new BusinessException("请先进入考试再交卷");
        }
        LocalDateTime now = LocalDateTime.now();
        int submitType = timeSupport.isExpired(exam, record, now) ? SUBMIT_TIMEOUT : SUBMIT_MANUAL;
        return doSubmit(exam, record, submitType);
    }

    /**
     * 收卷与判分的唯一实现：手动交卷、超时自动收卷、作弊强制收卷都走这里。
     *
     * <p>抽成一处而不是三份拷贝，是因为这三件事的本质是同一个动作
     * （推进状态 + 判客观题 + 落明细），差别只在「是谁发起的」。
     * 若各写一份，很快就出现「手动交卷判分、自动收卷不判分」这类不一致。
     */
    private SubmitResultVO doSubmit(Exam exam, ExamStudent record, int submitType) {
        record = examStudentMapper.lockById(record.getId());
        if (record.getStatus() >= ExamStudentStatusEnum.SUBMITTED.getCode()) return receipt(exam, record);
        // 幂等闸门：只有把状态从「考试中」推进为「已交卷」成功的那个请求才继续判分。
        // 前端连点、超时自动交卷与手动交卷同时到达等并发场景下，其余请求拿到 0 行更新，直接返回。
        int updated = examStudentMapper.updateStatusIfMatch(record.getId(),
                ExamStudentStatusEnum.RUNNING.getCode(), ExamStudentStatusEnum.SUBMITTED.getCode());
        if (updated == 0) {
            throw new BusinessException("本次交卷已处理，请勿重复提交");
        }

        List<PaperQuestion> questions = paperQuestionMapper.selectList(Wrappers.<PaperQuestion>lambdaQuery()
                .eq(PaperQuestion::getPaperId, exam.getPaperId())
                .orderByAsc(PaperQuestion::getSort));
        Map<Long, ExamAnswer> answerMap = examAnswerMapper.selectList(Wrappers.<ExamAnswer>lambdaQuery()
                        .eq(ExamAnswer::getExamStudentId, record.getId()))
                .stream().collect(Collectors.toMap(ExamAnswer::getPaperQuestionId, answer -> answer, (a, b) -> a));

        boolean autoMark = exam.getAutoMark() != null && exam.getAutoMark() == FLAG_ON;
        BigDecimal objectiveScore = BigDecimal.ZERO;
        int unmarked = 0;

        for (PaperQuestion question : questions) {
            ExamAnswer answer = answerMap.get(question.getId());
            boolean objective = QuestionTypeEnum.isObjective(question.getType());
            if (answer == null) {
                // 未作答也落一条明细：便于统计漏答、也便于教师查看空白卷
                answer = ExamAnswer.builder()
                        .examStudentId(record.getId())
                        .paperQuestionId(question.getId())
                        .isObjective(objective ? 1 : 0)
                        .marked(0)
                        .build();
            }

            if (objective && autoMark) {
                boolean correct = AnswerJudge.correct(question.getType(), question.getAnswer(), answer.getAnswer());
                answer.setCorrect(correct ? 1 : 0);
                answer.setScore(correct ? question.getScore() : BigDecimal.ZERO);
                answer.setMarked(1);
                objectiveScore = objectiveScore.add(answer.getScore());
            } else {
                // 主观题，或教师关闭了自动阅卷：留给人工批改
                unmarked++;
                answer.setMarked(0);
            }

            if (answer.getId() == null) {
                examAnswerMapper.insert(answer);
            } else {
                examAnswerMapper.updateById(answer);
            }
        }

        ExamStudentStatusEnum finalStatus = unmarked == 0
                ? ExamStudentStatusEnum.MARKED
                : ExamStudentStatusEnum.SUBMITTED;
        LocalDateTime now = LocalDateTime.now();
        record.setStatus(finalStatus.getCode());
        record.setSubmitTime(now);
        record.setSubmitType(submitType);
        record.setObjectiveScore(objectiveScore);
        record.setSubjectiveScore(BigDecimal.ZERO);
        record.setTotalScore(objectiveScore);
        examStudentMapper.updateById(record);

        Paper paper = paperMapper.selectById(exam.getPaperId());
        SubmitResultVO result = new SubmitResultVO();
        result.setObjectiveScore(objectiveScore);
        result.setSubjectiveScore(BigDecimal.ZERO);
        result.setTotalScore(objectiveScore);
        result.setPaperTotalScore(paper == null ? null : paper.getTotalScore());
        result.setUnmarkedCount(unmarked);
        result.setStatus(finalStatus.getCode());
        result.setStatusName(finalStatus.getLabel());
        result.setSubmitType(submitType);
        result.setSubmitTypeName(submitTypeName(submitType));
        result.setMessage(messageOf(submitType, unmarked));
        if (!AnswerVisibilityPolicy.canSeeScore(exam, LocalDateTime.now())) {
            result.setObjectiveScore(null); result.setSubjectiveScore(null); result.setTotalScore(null);
        }
        return result;
    }

    private SubmitResultVO receipt(Exam exam, ExamStudent record) {
        SubmitResultVO vo = new SubmitResultVO();
        vo.setStatus(record.getStatus()); vo.setStatusName(ExamStudentStatusEnum.labelOf(record.getStatus()));
        vo.setSubmitType(record.getSubmitType()); vo.setMessage("答卷已提交，作答已锁定。成绩与解析按教师发布状态开放。");
        if (AnswerVisibilityPolicy.canSeeScore(exam, LocalDateTime.now()) && Integer.valueOf(3).equals(record.getStatus())) {
            vo.setTotalScore(record.getTotalScore()); vo.setObjectiveScore(record.getObjectiveScore()); vo.setSubjectiveScore(record.getSubjectiveScore());
        }
        return vo;
    }

    private String submitTypeName(int submitType) {
        return switch (submitType) {
            case SUBMIT_TIMEOUT -> "超时自动收卷";
            case SUBMIT_FORCED -> "作弊强制收卷";
            default -> "手动交卷";
        };
    }

    private String messageOf(int submitType, int unmarked) {
        String tail = unmarked == 0 ? "客观题已自动判分" : "还有 " + unmarked + " 道主观题等待教师批改";
        return switch (submitType) {
            case SUBMIT_TIMEOUT -> "答题时间已到，系统已自动收卷，" + tail;
            case SUBMIT_FORCED -> "异常次数过多，系统已强制收卷，" + tail;
            default -> "交卷成功，" + tail;
        };
    }

    @Override
    public List<MarkItemVO> subjectiveList(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) throw new BusinessException("考试不存在");
        dataScope.requireOwner(exam.getCreateBy(), "考试");
        return examAnswerMapper.selectSubjective(examId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mark(Long answerId, BigDecimal score, String reason) {
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("分数不能为空且不能为负");
        }
        ExamAnswer answer = examAnswerMapper.selectById(answerId);
        if (answer == null) {
            throw new BusinessException("答题记录不存在");
        }
        // 水平越权校验：教师只能批改自己创建的考试下的答卷（管理员不受限）
        Exam exam = requireExamOwnership(answer.getExamStudentId());
        if (exam.getReleaseStatus() != null && exam.getReleaseStatus() > 0) throw new BusinessException("成绩已公布，不能直接改写已发布成绩");
        ExamStudent locked = examStudentMapper.lockById(answer.getExamStudentId());
        if (locked.getStatus() < 2) throw new BusinessException("学生尚未交卷");
        answer = examAnswerMapper.selectById(answerId);
        if (answer.getIsObjective() != null && answer.getIsObjective() == FLAG_ON && Integer.valueOf(1).equals(exam.getAutoMark())) throw new BusinessException("客观题已启用自动判分");
        PaperQuestion question = paperQuestionMapper.selectById(answer.getPaperQuestionId());
        if (question == null) {
            throw new BusinessException("题目快照不存在");
        }
        if (score.compareTo(question.getScore()) > 0) {
            throw new BusinessException("分数不能超过本题满分 " + question.getScore());
        }

        boolean alreadyMarked = answer.getMarked() != null && answer.getMarked() == FLAG_ON;
        // 改分必须说明理由：这是留痕能被信任的前提。
        // 只要求「改分」填，首评不强制，避免把正常的批量阅卷变成填写负担。
        if (alreadyMarked && !StringUtils.hasText(reason)) {
            throw new BusinessException("该题已批改过，改分必须填写理由");
        }

        int expectedVersion = answer.getMarkVersion() == null ? 0 : answer.getMarkVersion();
        BigDecimal oldScore = answer.getScore();
        int updated = examAnswerMapper.markWithVersion(answerId, expectedVersion,
                score, score.compareTo(question.getScore()) == 0 ? 1 : 0);
        if (updated == 0) {
            // 期间被别人改过：明确报冲突，而不是让后写的人静默覆盖前一个人的分数
            throw new BusinessException("该题刚刚被其他教师批改过，请刷新后重新打分");
        }

        markLogMapper.insert(ExamMarkLog.builder()
                .examStudentId(answer.getExamStudentId())
                .examAnswerId(answerId)
                .examId(exam.getId())
                .action(alreadyMarked ? MARK_ACTION_CHANGE : MARK_ACTION_FIRST)
                .oldScore(oldScore)
                .newScore(score)
                .reason(alreadyMarked ? reason.trim() : (StringUtils.hasText(reason) ? reason.trim() : null))
                .operator(LoginUtil.username())
                .createTime(LocalDateTime.now())
                .build());

        // 每次批改后立即重算总分，保证成绩页看到的始终是最新值
        settle(answer.getExamStudentId());
    }

    @Override
    public List<MarkLogVO> markLogs(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        dataScope.requireOwner(exam.getCreateBy(), "考试");
        List<MarkLogVO> logs = markLogMapper.selectByExam(examId);
        for (MarkLogVO item : logs) {
            item.setActionName(item.getAction() != null && item.getAction() == MARK_ACTION_CHANGE
                    ? "改分" : "首评");
            item.setQuestionContent(brief(item.getQuestionContent()));
        }
        return logs;
    }

    @Override
    public List<CheatLogVO> cheatLogs(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        dataScope.requireOwner(exam.getCreateBy(), "考试");
        return cheatLogMapper.selectByExam(examId);
    }

    @Override
    public synchronized int autoSubmitExpired(int limit) {
        LocalDateTime now = LocalDateTime.now();
        List<ExamStudent> running = examStudentMapper.selectList(Wrappers.<ExamStudent>lambdaQuery().eq(ExamStudent::getStatus, 1).gt(ExamStudent::getId, timeoutCursor).orderByAsc(ExamStudent::getId).last("limit " + Math.max(1, Math.min(limit, 1000))));
        if (running.isEmpty()) { timeoutCursor = 0; return 0; }
        timeoutCursor = running.get(running.size()-1).getId();
        int submitted = 0;
        for (ExamStudent record : running) {
            Exam exam = examMapper.selectById(record.getExamId());
            if (exam == null) {
                continue;
            }
            // 与前端倒计时同源：min(进入时间 + 时长, 考试结束时间)
            if (!timeSupport.isExpired(exam, record, now)) {
                continue;
            }
            try {
                submissionTransaction.execute(status -> doSubmit(examMapper.selectById(record.getExamId()), record, SUBMIT_TIMEOUT));
                submitted++;
            } catch (Exception e) {
                // 单条失败不影响整批：记录后继续，下一轮扫描会再试
                log.warn("自动收卷失败 examStudentId={}：{}", record.getId(), e.getMessage());
            }
        }
        return submitted;
    }

    /** 留痕列表里的题干摘要：CLOB 全量回传既慢又占版面 */
    private String brief(String content) {
        if (content == null) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= 40 ? flat : flat.substring(0, 40) + "…";
    }

    // ==================== 内部方法 ====================

    /**
     * 由答卷反查考试并校验归属（批改动作的水平越权防护）。
     *
     * @return 反查到的考试：批改留痕需要把 exam_id 一起存下来，
     *         否则「这场考试改过哪些分」要再 JOIN 一次才能查。
     */
    private Exam requireExamOwnership(Long examStudentId) {
        ExamStudent record = examStudentMapper.selectById(examStudentId);
        if (record == null) {
            throw new BusinessException("答题记录不存在");
        }
        Exam exam = examMapper.lockById(record.getExamId());
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        dataScope.requireOwner(exam.getCreateBy(), "考试");
        return exam;
    }

    /** 重算某学生的客观/主观/总分与状态（只在学生已交卷后推进状态） */
    private void settle(Long examStudentId) {
        ExamStudent record = examStudentMapper.selectById(examStudentId);
        if (record == null) {
            return;
        }
        Integer beforeStatus = record.getStatus();
        List<ExamAnswer> answers = examAnswerMapper.selectList(Wrappers.<ExamAnswer>lambdaQuery()
                .eq(ExamAnswer::getExamStudentId, examStudentId));

        BigDecimal objective = BigDecimal.ZERO;
        BigDecimal subjective = BigDecimal.ZERO;
        int unmarked = 0;
        for (ExamAnswer answer : answers) {
            BigDecimal got = answer.getScore() == null ? BigDecimal.ZERO : answer.getScore();
            if (Integer.valueOf(1).equals(answer.getIsObjective())) {
                objective = objective.add(got);
            } else {
                subjective = subjective.add(got);
            }
            if (answer.getMarked() == null || answer.getMarked() == 0) {
                unmarked++;
            }
        }

        record.setObjectiveScore(objective);
        record.setSubjectiveScore(subjective);
        record.setTotalScore(objective.add(subjective));
        // 只有已交卷的学生才推进状态；仍在答题的学生不因教师批改而改变状态
        if (record.getSubmitTime() != null) {
            boolean justMarked = unmarked == 0
                    && !Integer.valueOf(ExamStudentStatusEnum.MARKED.getCode()).equals(beforeStatus);
            record.setStatus((unmarked == 0
                    ? ExamStudentStatusEnum.MARKED
                    : ExamStudentStatusEnum.SUBMITTED).getCode());
            if (justMarked) {
                // 只有状态「首次变成已批改」才通知：在此之前发通知，学生点进去只会看到
                // 「暂无成绩」，而这种「点开是空的」比晚一点收到通知更伤体验。
                notifyGradeReady(record);
            }
        }
        examStudentMapper.updateById(record);
    }

    /** 成绩真正可查时通知学生本人 */
    private void notifyGradeReady(ExamStudent record) {
        Exam exam = examMapper.selectById(record.getExamId());
        if (!AnswerVisibilityPolicy.canSeeScore(exam, LocalDateTime.now())) {
            return;
        }
        notificationService.sendOne(record.getStudentId(), NotificationTypeEnum.GRADE_PUBLISHED,
                "成绩已公布：" + exam.getExamName(),
                "你的成绩为 " + record.getTotalScore() + " 分，点击查看明细",
                "exam", exam.getId());
    }

    private Exam requireExam(Long examId) {
        Exam exam = requireExamAllowExpired(examId);
        if (LocalDateTime.now().isAfter(exam.getEndTime())) {
            throw new BusinessException("考试已结束");
        }
        return exam;
    }

    /**
     * 加载考试但**不校验是否已结束**。
     *
     * <p>只给交卷与自动收卷用：对「考试已结束」的处理在这里是「把卷收上来」，
     * 而不是「拒绝」。若沿用 requireExam，超时未交卷的学生将永远无法交卷，
     * 监考看到的已交卷人数也永远少一个。
     */
    private Exam requireExamAllowExpired(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        if (exam.getPublished() == null || exam.getPublished() != FLAG_ON) {
            throw new BusinessException("考试尚未发布");
        }
        if (LocalDateTime.now().isBefore(exam.getStartTime())) {
            throw new BusinessException("考试尚未开始");
        }
        return exam;
    }

    /** 校验当前登录用户是该考试的参考班级学生 */
    private SysUser requireStudentInClass(Exam exam) {
        SysUser student = userMapper.selectById(LoginUtil.userId());
        if (student == null) {
            throw new BusinessException("用户不存在");
        }
        if (roster.contains(exam.getId(),student.getId())==0) {
            throw new BusinessException("你不在该考试的参考班级中，无法参加");
        }
        return student;
    }

    private ExamStudent findRecord(Long examId, Long studentId) {
        ExamStudent record = examStudentMapper.selectOne(Wrappers.<ExamStudent>lambdaQuery()
                .eq(ExamStudent::getExamId, examId)
                .eq(ExamStudent::getStudentId, studentId));
        return record == null ? null : examStudentMapper.lockById(record.getId());
    }

    private ExamStudent requireRunningRecord(Long examId, Long studentId) {
        ExamStudent record = findRecord(examId, studentId);
        if (record == null) {
            throw new BusinessException("请先进入考试");
        }
        if (ExamStudentStatusEnum.of(record.getStatus()) != ExamStudentStatusEnum.RUNNING) {
            throw new BusinessException("你已交卷，不能再修改作答");
        }
        return record;
    }
}
