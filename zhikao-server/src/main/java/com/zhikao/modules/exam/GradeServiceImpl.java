package com.zhikao.modules.exam;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.BusinessException;
import com.zhikao.common.ExcelHelper;
import com.zhikao.common.ExamStudentStatusEnum;
import com.zhikao.common.JsonHelper;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.QuestionTypeEnum;
import com.zhikao.entity.Exam;
import com.zhikao.entity.ExamAnswer;
import com.zhikao.entity.ExamStudent;
import com.zhikao.entity.Paper;
import com.zhikao.entity.PaperQuestion;
import com.zhikao.entity.Subject;
import com.zhikao.entity.SysClass;
import com.zhikao.entity.SysUser;
import com.zhikao.mapper.ExamAnswerMapper;
import com.zhikao.mapper.ExamMapper;
import com.zhikao.mapper.ExamStudentMapper;
import com.zhikao.mapper.PaperMapper;
import com.zhikao.mapper.PaperQuestionMapper;
import com.zhikao.mapper.SubjectMapper;
import com.zhikao.mapper.SysClassMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.modules.exam.vo.ExamGradeVO;
import com.zhikao.modules.exam.vo.GradeItemVO;
import com.zhikao.modules.exam.vo.MyPaperVO;
import com.zhikao.modules.exam.vo.StudentGradeVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成绩服务实现：
 * 分数一律从 exam_student / exam_answer 的落库结果读出并现算统计，
 * 不做「预先算好存一份」的冗余，避免批改后统计与明细不一致。
 */
@Service
public class GradeServiceImpl implements GradeService {

    /** 及格线：试卷总分的 60% */
    private static final BigDecimal PASS_RATIO = new BigDecimal("0.6");

    /** 分数分布区间宽度（分） */
    private static final int BUCKET_SIZE = 10;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final com.zhikao.mapper.ExamRosterMapper roster;
    private final ExamMapper examMapper;
    private final ExamStudentMapper examStudentMapper;
    private final ExamAnswerMapper examAnswerMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final SubjectMapper subjectMapper;
    private final SysUserMapper userMapper;
    private final SysClassMapper classMapper;

    public GradeServiceImpl(com.zhikao.mapper.ExamRosterMapper roster,ExamMapper examMapper, ExamStudentMapper examStudentMapper,
                            ExamAnswerMapper examAnswerMapper, PaperMapper paperMapper,
                            PaperQuestionMapper paperQuestionMapper, SubjectMapper subjectMapper,
                            SysUserMapper userMapper, SysClassMapper classMapper) {
        this.roster=roster;
        this.examMapper = examMapper;
        this.examStudentMapper = examStudentMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.subjectMapper = subjectMapper;
        this.userMapper = userMapper;
        this.classMapper = classMapper;
    }

    @Override
    public ExamGradeVO examGrade(Long examId) {
        Exam exam = requireExam(examId);
        Paper paper = paperMapper.selectById(exam.getPaperId());
        List<GradeItemVO> items = examStudentMapper.selectGrades(examId);

        // 名次：列表已按总分降序，总分相同则并列名次
        int rank = 0;
        int index = 0;
        BigDecimal lastScore = null;
        for (GradeItemVO item : items) {
            item.setStatusName(ExamStudentStatusEnum.labelOf(item.getStatus()));
            if (!Integer.valueOf(ExamStudentStatusEnum.MARKED.getCode()).equals(item.getStatus())
                    || item.getTotalScore() == null) {
                item.setRank(null);
                continue;
            }
            index++;
            BigDecimal score = item.getTotalScore();
            if (lastScore == null || score == null || score.compareTo(lastScore) != 0) {
                rank = index;
                lastScore = score;
            }
            item.setRank(rank);
        }

        List<BigDecimal> scores = items.stream()
                .filter(item -> Integer.valueOf(ExamStudentStatusEnum.MARKED.getCode()).equals(item.getStatus()))
                .map(GradeItemVO::getTotalScore)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        BigDecimal paperTotal = paper == null ? null : paper.getTotalScore();
        BigDecimal passLine = paperTotal == null ? null
                : paperTotal.multiply(PASS_RATIO).setScale(2, RoundingMode.HALF_UP);

        ExamGradeVO vo = new ExamGradeVO();
        vo.setExamId(exam.getId());
        vo.setExamName(exam.getExamName());
        vo.setPaperName(paper == null ? null : paper.getPaperName());
        vo.setSubjectName(paper == null ? null : subjectNames().get(paper.getSubjectId()));
        vo.setClassName(classNames().get(exam.getClassId()));
        vo.setStartTime(exam.getStartTime());
        vo.setEndTime(exam.getEndTime());
        vo.setPaperTotalScore(paperTotal);
        vo.setPassLine(passLine);
        vo.setShouldCount(roster.count(exam.getId()));
        vo.setSubmittedCount(items.size());
        vo.setMarkedCount(items.stream()
                .filter(item -> ExamStudentStatusEnum.of(item.getStatus()) == ExamStudentStatusEnum.MARKED)
                .count());
        vo.setParticipantCount(scores.size());
        vo.setPendingMarkCount(vo.getSubmittedCount() - vo.getMarkedCount());
        vo.setScoreBasis("final_marked");
        vo.setRosterBasis(Integer.valueOf(1).equals(exam.getRosterFrozen())?"publication_snapshot":"legacy_participants_only");
        vo.setItems(items);

        if (!scores.isEmpty()) {
            BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setAvgScore(sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP));
            vo.setMaxScore(scores.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            vo.setMinScore(scores.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            if (passLine != null) {
                long passed = scores.stream().filter(score -> score.compareTo(passLine) >= 0).count();
                vo.setPassRate(BigDecimal.valueOf(passed * 100.0 / scores.size())
                        .setScale(2, RoundingMode.HALF_UP));
            }
        } else {
            vo.setAvgScore(null);
            vo.setMaxScore(null);
            vo.setMinScore(null);
            vo.setPassRate(null);
        }

        vo.setDistribution(buildDistribution(scores, paperTotal));
        return vo;
    }

    @Override
    public byte[] exportExamGrade(Long examId) {
        ExamGradeVO grade = examGrade(examId);
        List<List<String>> rows = new ArrayList<>();
        for (GradeItemVO item : grade.getItems()) {
            rows.add(List.of(
                    item.getRank() == null ? "" : String.valueOf(item.getRank()),
                    nullToEmpty(item.getUsername()),
                    nullToEmpty(item.getStudentName()),
                    nullToEmpty(item.getClassName()),
                    nullToEmpty(ExamStudentStatusEnum.labelOf(item.getStatus())),
                    number(item.getObjectiveScore()),
                    number(item.getSubjectiveScore()),
                    number(item.getTotalScore()),
                    item.getSubmitTime() == null ? "" : item.getSubmitTime().format(TIME_FORMAT),
                    String.valueOf(item.getCheatCount() == null ? 0 : item.getCheatCount())));
        }
        return ExcelHelper.write(grade.getExamName() + "-成绩",
                List.of("名次", "账号", "姓名", "班级", "状态", "客观题得分", "主观题得分", "总分", "交卷时间", "异常次数"),
                rows);
    }

    @Override
    public List<StudentGradeVO> myGrades() {
        return releasedGrades(LoginUtil.userId());
    }

    @Override
    public List<StudentGradeVO> releasedGrades(Long studentId) {
        if (studentId == null) return List.of();
        List<ExamStudent> records = examStudentMapper.selectList(Wrappers.<ExamStudent>lambdaQuery()
                .eq(ExamStudent::getStudentId, studentId)
                .eq(ExamStudent::getStatus, ExamStudentStatusEnum.MARKED.getCode())
                .orderByDesc(ExamStudent::getSubmitTime));
        if (records.isEmpty()) {
            return List.of();
        }

        Map<Long, Exam> exams = examMapper.selectBatchIds(
                        records.stream().map(ExamStudent::getExamId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Exam::getId, Function.identity()));
        Map<Long, Paper> papers = papersOf(exams.values());
        Map<Long, String> subjectNames = subjectNames();

        List<StudentGradeVO> result = new ArrayList<>();
        for (ExamStudent record : records) {
            Exam exam = exams.get(record.getExamId());
            if (!AnswerVisibilityPolicy.canSeeScore(exam, java.time.LocalDateTime.now())) {
                continue;
            }
            Paper paper = papers.get(exam.getPaperId());
            StudentGradeVO vo = new StudentGradeVO();
            vo.setReleaseStatus(exam.getReleaseStatus());
            vo.setExamId(exam.getId());
            vo.setExamName(exam.getExamName());
            vo.setPaperName(paper == null ? null : paper.getPaperName());
            vo.setSubjectName(paper == null ? null : subjectNames.get(paper.getSubjectId()));
            vo.setSubmitTime(record.getSubmitTime());
            vo.setStatus(record.getStatus());
            vo.setStatusName(ExamStudentStatusEnum.labelOf(record.getStatus()));
            vo.setObjectiveScore(record.getObjectiveScore());
            vo.setSubjectiveScore(record.getSubjectiveScore());
            vo.setTotalScore(record.getTotalScore());
            vo.setPaperTotalScore(paper == null ? null : paper.getTotalScore());
            result.add(vo);
        }
        return result;
    }

    @Override
    public MyPaperVO myPaper(Long examId) {
        Exam exam = requireExam(examId);
        if (!AnswerVisibilityPolicy.canReview(exam, java.time.LocalDateTime.now())) throw new BusinessException("本场考试解析尚未公布");
        ExamStudent record = examStudentMapper.selectOne(Wrappers.<ExamStudent>lambdaQuery()
                .eq(ExamStudent::getExamId, examId)
                .eq(ExamStudent::getStudentId, LoginUtil.userId()));
        if (record == null || record.getSubmitTime() == null) {
            throw new BusinessException("你尚未交卷，暂时无法查看答案与解析");
        }

        Paper paper = paperMapper.selectById(exam.getPaperId());
        List<PaperQuestion> questions = paperQuestionMapper.selectList(Wrappers.<PaperQuestion>lambdaQuery()
                .eq(PaperQuestion::getPaperId, exam.getPaperId())
                .orderByAsc(PaperQuestion::getSort));
        Map<Long, ExamAnswer> answers = examAnswerMapper.selectList(Wrappers.<ExamAnswer>lambdaQuery()
                        .eq(ExamAnswer::getExamStudentId, record.getId()))
                .stream().collect(Collectors.toMap(ExamAnswer::getPaperQuestionId, Function.identity(), (a, b) -> a));

        MyPaperVO vo = new MyPaperVO();
        vo.setExamId(exam.getId());
        vo.setExamName(exam.getExamName());
        vo.setPaperName(paper == null ? null : paper.getPaperName());
        vo.setPaperTotalScore(paper == null ? null : paper.getTotalScore());
        vo.setObjectiveScore(record.getObjectiveScore());
        vo.setSubjectiveScore(record.getSubjectiveScore());
        vo.setTotalScore(record.getTotalScore());

        List<MyPaperVO.MyAnswerVO> items = new ArrayList<>();
        for (PaperQuestion question : questions) {
            ExamAnswer answer = answers.get(question.getId());
            MyPaperVO.MyAnswerVO item = new MyPaperVO.MyAnswerVO();
            item.setPaperQuestionId(question.getId());
            item.setSort(question.getSort());
            item.setType(question.getType());
            item.setTypeName(QuestionTypeEnum.labelOf(question.getType()));
            item.setObjective(QuestionTypeEnum.isObjective(question.getType()));
            item.setContent(question.getContent());
            item.setOptions(JsonHelper.toOptionMap(question.getOptions()));
            item.setFullScore(question.getScore());
            item.setMyAnswer(answer == null ? null : answer.getAnswer());
            item.setStandardAnswer(question.getAnswer());
            item.setAnalysis(question.getAnalysis());
            item.setGotScore(answer == null ? null : answer.getScore());
            item.setCorrect(answer == null ? null : answer.getCorrect());
            item.setMarked(answer == null ? null : answer.getMarked());
            items.add(item);
        }
        vo.setQuestions(items);
        return vo;
    }

    // ==================== 内部方法 ====================

    /** 构建分数分布：以 10 分为一段，最后一段包含满分 */
    private List<ExamGradeVO.ScoreBucket> buildDistribution(List<BigDecimal> scores, BigDecimal paperTotal) {
        int max = paperTotal == null ? 100 : (int) Math.ceil(paperTotal.doubleValue());
        int bucketCount = Math.max(1, (int) Math.ceil((double) max / BUCKET_SIZE));

        List<ExamGradeVO.ScoreBucket> buckets = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            int start = i * BUCKET_SIZE;
            // 最后一段用「以上」收口，避免出现空档
            String label = (i == bucketCount - 1)
                    ? start + "分及以上"
                    : start + "-" + (start + BUCKET_SIZE - 1) + "分";
            buckets.add(new ExamGradeVO.ScoreBucket(label, 0));
        }
        for (BigDecimal score : scores) {
            int index = Math.min(score.intValue() / BUCKET_SIZE, bucketCount - 1);
            ExamGradeVO.ScoreBucket bucket = buckets.get(index);
            bucket.setCount(bucket.getCount() + 1);
        }
        return buckets;
    }

    private Exam requireExam(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException("考试不存在");
        }
        return exam;
    }

    private Map<Long, Paper> papersOf(java.util.Collection<Exam> exams) {
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String number(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
