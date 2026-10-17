package com.zhikao.modules.ai.agent;

import com.zhikao.common.LoginUtil;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhikao.common.BusinessException;
import com.zhikao.common.ExamStudentStatusEnum;
import com.zhikao.common.ExcelHelper;
import com.zhikao.common.QuestionTypeEnum;
import com.zhikao.entity.Exam;
import com.zhikao.entity.ExamStudent;
import com.zhikao.entity.Paper;
import com.zhikao.entity.Question;
import com.zhikao.entity.Subject;
import com.zhikao.entity.SysClass;
import com.zhikao.entity.SysFile;
import com.zhikao.entity.SysOperLog;
import com.zhikao.entity.SysUser;
import com.zhikao.mapper.ExamAnswerMapper;
import com.zhikao.mapper.ExamMapper;
import com.zhikao.mapper.ExamStudentMapper;
import com.zhikao.mapper.QuestionMapper;
import com.zhikao.mapper.SysOperLogMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.modules.ai.agent.vo.QuestionDifficultyCountVO;
import com.zhikao.modules.ai.agent.vo.QuestionTypeCountVO;
import com.zhikao.modules.ai.vo.KnowledgeStatVO;
import com.zhikao.modules.exam.ExamService;
import com.zhikao.modules.exam.GradeService;
import com.zhikao.modules.exam.PaperService;
import com.zhikao.modules.exam.QuestionService;
import com.zhikao.modules.exam.dto.PaperQueryDTO;
import com.zhikao.modules.exam.dto.QuestionQueryDTO;
import com.zhikao.modules.exam.vo.ExamGradeVO;
import com.zhikao.modules.exam.vo.ExamVO;
import com.zhikao.modules.exam.vo.GradeItemVO;
import com.zhikao.modules.exam.vo.MarkItemVO;
import com.zhikao.modules.exam.vo.PaperVO;
import com.zhikao.modules.exam.vo.QuestionVO;
import com.zhikao.modules.file.FileStorageService;
import com.zhikao.modules.learn.vo.WrongQuestionVO;
import com.zhikao.modules.system.SysClassService;
import com.zhikao.modules.system.SubjectService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 工具层 —— 把「现有 Service 的只读能力」暴露给大模型。
 *
 * <p><b>一条铁律：这里不写任何业务查询 SQL 的替代实现。</b>
 * 需要数据就调 Service / Mapper，保证「Agent 看到的数据 = 用户在页面上看到的数据」。
 * 唯一需要直接查 Mapper 的场景是「依赖当前登录态的 Service」（如 GradeService.myGrades），
 * 因为 Reactor 线程上取不到 ThreadLocal 登录态——此时用 {@link AgentContext} 里的快照 id 直查，
 * 并强制带上 student_id 过滤，杜绝越权。
 *
 * <p><b>工具实例的生命周期</b>：由 {@code AgentFactory} 每次对话 new 一个，
 * 携带当次请求的身份上下文。因此这里所有方法都不得假设自己跑在请求线程上。
 *
 * <p><b>安全边界</b>：全部工具都是只读；唯一的「写」是 {@code draft_question}，
 * 它只把草稿**返回给前端**，绝不落库——AI 不做写操作决策。
 */
public class AiTools {

    /** 工具返回给模型的列表长度上限（控制 token 消耗） */
    private static final int DEFAULT_LIMIT = 10;

    /** 导出类操作的行数上限：导出是给人看的文件，再多也不该让模型把全库拉一遍 */
    private static final int EXPORT_LIMIT = 1000;

    /** 单条文本在工具结果里的截断长度 */
    private static final int TEXT_LIMIT = 120;

    private final AgentContext context;

    private final SubjectService subjectService;
    private final SysClassService classService;
    private final QuestionService questionService;
    private final PaperService paperService;
    private final ExamService examService;
    private final GradeService gradeService;
    private final KnowledgeBase knowledgeBase;
    private final StudentProfileService profileService;

    private final ExamMapper examMapper;
    private final ExamStudentMapper examStudentMapper;
    private final ExamAnswerMapper examAnswerMapper;
    private final QuestionMapper questionMapper;
    private final SysUserMapper userMapper;
    private final SysOperLogMapper operLogMapper;
    private final FileStorageService fileStorage;
    private final ObjectMapper json;

    public AiTools(AgentContext context,
                   SubjectService subjectService, SysClassService classService,
                   QuestionService questionService, PaperService paperService,
                   ExamService examService, GradeService gradeService,
                   KnowledgeBase knowledgeBase, StudentProfileService profileService,
                   ExamMapper examMapper, ExamStudentMapper examStudentMapper,
                   ExamAnswerMapper examAnswerMapper, QuestionMapper questionMapper,
                   SysUserMapper userMapper, SysOperLogMapper operLogMapper,
                   FileStorageService fileStorage, ObjectMapper json) {
        this.context = context;
        this.subjectService = subjectService;
        this.classService = classService;
        this.questionService = questionService;
        this.paperService = paperService;
        this.examService = examService;
        this.gradeService = gradeService;
        this.knowledgeBase = knowledgeBase;
        this.profileService = profileService;
        this.examMapper = examMapper;
        this.examStudentMapper = examStudentMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.questionMapper = questionMapper;
        this.userMapper = userMapper;
        this.operLogMapper = operLogMapper;
        this.fileStorage = fileStorage;
        this.json = json;
    }

    // ==================== 基础数据 ====================

    @Tool(name = "list_subjects",
            description = "列出系统中的全部科目（含科目 id 与名称）。当你需要把用户口中的科目名对应到系统数据时，先用它。")
    public String listSubjects() {
        List<Subject> subjects = subjectService.list();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Subject subject : subjects) {
            rows.add(Map.of("subject_id", subject.getId(), "subject_name", subject.getSubjectName()));
        }
        return ok(Map.of("total", rows.size(), "subjects", rows));
    }

    @Tool(name = "list_classes",
            description = "列出全部班级（含班级 id、名称、年级、专业）。仅教师与管理员可用。")
    public String listClasses() {
        if (!context.isStaff()) {
            return deny("当前角色无权查看班级列表");
        }
        List<SysClass> classes = classService.list();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysClass clazz : classes) {
            rows.add(Map.of(
                    "class_id", clazz.getId(),
                    "class_name", nullSafe(clazz.getClassName()),
                    "grade", nullSafe(clazz.getGrade()),
                    "major", nullSafe(clazz.getMajor())));
        }
        return ok(Map.of("total", rows.size(), "classes", rows));
    }

    // ==================== 题库 ====================

    @Tool(name = "search_questions",
            description = "按科目、题型、难度、关键字搜索题库题目。出题前用它查重，答题/复习场景用它找同类题。")
    public String searchQuestions(
            @ToolParam(name = "subject_name", description = "科目名称，如「Java 程序设计」；可省略表示不限", required = false) String subjectName,
            @ToolParam(name = "type", description = "题型：单选/多选/判断/填空/简答；可省略", required = false) String type,
            @ToolParam(name = "difficulty", description = "难度 1-5；可省略", required = false) Integer difficulty,
            @ToolParam(name = "keyword", description = "题干或知识点关键字；可省略", required = false) String keyword,
            @ToolParam(name = "limit", description = "返回条数，默认 10，上限 30", required = false) Integer limit) {

        if (!context.isStaff()) {
            return deny("当前角色无权检索题库");
        }
        Long subjectId = null;
        if (StringUtils.hasText(subjectName)) {
            Subject subject = findSubject(subjectName);
            if (subject == null) {
                return notFound("科目「" + subjectName + "」不存在", "subjects", allSubjectNames());
            }
            subjectId = subject.getId();
        }

        QuestionQueryDTO query = new QuestionQueryDTO();
        query.setSubjectId(subjectId);
        query.setType(QuestionTypeEnum.parse(type));
        query.setDifficulty(difficulty);
        query.setKeyword(keyword);
        query.setPageNum(1);
        query.setPageSize(cap(limit));

        List<QuestionVO> records = questionService.page(query).getRecords();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QuestionVO item : records) {
            rows.add(Map.of(
                    "question_id", item.getId(),
                    "type", QuestionTypeEnum.labelOf(item.getType()),
                    "difficulty", item.getDifficulty() == null ? 0 : item.getDifficulty(),
                    "knowledge_point", nullSafe(item.getKnowledgePoint()),
                    "content", truncate(item.getContent()),
                    "answer", truncate(item.getAnswer())));
        }
        return ok(Map.of("total_matched", records.size(), "questions", rows));
    }

    @Tool(name = "get_question_stats",
            description = "统计题库的题型分布与难度分布。出题前用它判断「这个科目还缺哪类题」。")
    public String getQuestionStats(
            @ToolParam(name = "subject_name", description = "科目名称；省略则统计全库", required = false) String subjectName) {

        if (!context.isStaff()) {
            return deny("当前角色无权查看题库统计");
        }
        Long subjectId = null;
        if (StringUtils.hasText(subjectName)) {
            Subject subject = findSubject(subjectName);
            if (subject == null) {
                return notFound("科目「" + subjectName + "」不存在", "subjects", allSubjectNames());
            }
            subjectId = subject.getId();
        }

        Map<String, Object> byType = new LinkedHashMap<>();
        long total = 0;
        for (QuestionTypeCountVO row : questionMapper.countGroupByType(subjectId)) {
            byType.put(QuestionTypeEnum.labelOf(row.getType()), row.getTotal());
            total += row.getTotal() == null ? 0 : row.getTotal();
        }
        Map<String, Object> byDifficulty = new LinkedHashMap<>();
        for (QuestionDifficultyCountVO row : questionMapper.countGroupByDifficulty(subjectId)) {
            byDifficulty.put(String.valueOf(row.getDifficulty()), row.getTotal());
        }
        return ok(Map.of(
                "subject", StringUtils.hasText(subjectName) ? subjectName : "全部科目",
                "total", total,
                "by_type", byType,
                "by_difficulty", byDifficulty));
    }

    // ==================== 试卷与考试 ====================

    @Tool(name = "list_papers",
            description = "列出试卷（名称、科目、总分、题数、是否已发布）。创建考试前可用它确认有哪些可用试卷。")
    public String listPapers(
            @ToolParam(name = "subject_name", description = "按科目过滤；可省略", required = false) String subjectName,
            @ToolParam(name = "limit", description = "返回条数，默认 10", required = false) Integer limit) {

        if (!context.isStaff()) {
            return deny("当前角色无权查看试卷列表");
        }
        PaperQueryDTO query = new PaperQueryDTO();
        if (StringUtils.hasText(subjectName)) {
            Subject subject = findSubject(subjectName);
            if (subject == null) {
                return notFound("科目「" + subjectName + "」不存在", "subjects", allSubjectNames());
            }
            query.setSubjectId(subject.getId());
        }
        query.setPageNum(1);
        query.setPageSize(cap(limit));

        List<PaperVO> records = paperService.page(query).getRecords();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PaperVO paper : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("paper_id", paper.getId());
            row.put("paper_name", paper.getPaperName());
            row.put("subject", nullSafe(paper.getSubjectName()));
            row.put("total_score", paper.getTotalScore());
            row.put("question_count", paper.getQuestionCount());
            row.put("published", paper.getStatus() != null && paper.getStatus() == 1);
            rows.add(row);
        }
        return ok(Map.of("total", rows.size(), "papers", rows));
    }

    @Tool(name = "list_exams",
            description = "列出考试（名称、科目、状态、时间窗、参与情况）。学生只能看到自己班级已发布的考试。")
    public String listExams(
            @ToolParam(name = "subject_name", description = "按科目名称模糊过滤；可省略", required = false) String subjectName,
            @ToolParam(name = "status", description = "状态：未开始/进行中/已结束；可省略", required = false) String status,
            @ToolParam(name = "limit", description = "返回条数，默认 10", required = false) Integer limit) {

        if (context.isStudent()) {
            return studentExams(limit);
        }
        List<ExamVO> exams = examService.page(pageQuery(cap(limit))).getRecords();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExamVO exam : exams) {
            if (StringUtils.hasText(subjectName) && !contains(exam.getSubjectName(), subjectName)) {
                continue;
            }
            if (StringUtils.hasText(status) && !contains(exam.getStatusName(), status)) {
                continue;
            }
            rows.add(examRow(exam));
        }
        return ok(Map.of("total", rows.size(), "exams", rows));
    }

    @Tool(name = "get_exam_monitor",
            description = "查看某场考试的实时监控：应考人数、已进入、已交卷、已阅卷、平均分与最高最低分。教师与管理员可用。")
    public String getExamMonitor(
            @ToolParam(name = "exam_name", description = "考试名称（可只写一部分，如「期中」）") String examName) {

        if (!context.isStaff()) {
            return deny("当前角色无权查看考试监控");
        }
        Exam exam = findExam(examName);
        if (exam == null) {
            return notFound("找不到考试「" + examName + "」", "exams", recentExamNames());
        }
        var monitor = examService.monitor(exam.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exam", exam.getExamName());
        data.put("should_count", monitor.getShouldCount());
        data.put("entered_count", monitor.getEnteredCount());
        data.put("submitted_count", monitor.getSubmittedCount());
        data.put("marked_count", monitor.getMarkedCount());
        data.put("avg_score", monitor.getAvgScore());
        data.put("max_score", monitor.getMaxScore());
        data.put("min_score", monitor.getMinScore());
        return ok(data);
    }

    @Tool(name = "get_exam_stat",
            description = "获取某场考试的成绩统计：参考人数、平均分、最高最低、及格率、分数分布与前若干名明细。教师与管理员可用。")
    public String getExamStat(
            @ToolParam(name = "exam_name", description = "考试名称（可只写一部分）") String examName) {

        if (!context.isStaff()) {
            return deny("当前角色无权查看考试成绩统计");
        }
        Exam exam = findExam(examName);
        if (exam == null) {
            return notFound("找不到考试「" + examName + "」", "exams", recentExamNames());
        }
        return ok(examGradeSummary(gradeService.examGrade(exam.getId())));
    }

    @Tool(name = "get_knowledge_stats",
            description = "获取某场考试终评且已评分作答的知识点正确率（升序，最薄弱在前）。未评分不计入分母，主观题仅满分算完全正确。教师与管理员可用。")
    public String getKnowledgeStats(
            @ToolParam(name = "exam_name", description = "考试名称（可只写一部分）") String examName) {

        if (!context.isStaff()) {
            return deny("当前角色无权查看知识点统计");
        }
        Exam exam = findExam(examName);
        if (exam == null) {
            return notFound("找不到考试「" + examName + "」", "exams", recentExamNames());
        }
        List<KnowledgeStatVO> stats = examAnswerMapper.selectKnowledgeStats(exam.getId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (KnowledgeStatVO stat : stats) {
            int total = stat.getTotal() == null ? 0 : stat.getTotal();
            int correct = stat.getCorrectCount() == null ? 0 : stat.getCorrectCount();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("knowledge_point", nullSafe(stat.getKnowledgePoint()));
            row.put("subject_id", stat.getSubjectId());
            row.put("subject", stat.getSubjectName());
            row.put("total", total);
            row.put("correct", correct);
            row.put("accuracy", percentage(correct, total));
            row.put("weak", percentage(correct, total).compareTo(new BigDecimal("60")) < 0);
            rows.add(row);
        }
        if (rows.isEmpty()) {
            return ok(Map.of("exam", exam.getExamName(), "note", "该考试暂无终评且已评分的作答数据"));
        }
        return ok(Map.of("exam", exam.getExamName(), "knowledge_stats", rows));
    }

    @Tool(name = "list_pending_mark",
            description = "列出某场考试中待批改的主观题（含学生、作答、参考答案、满分）。教师与管理员可用。")
    public String listPendingMark(
            @ToolParam(name = "exam_name", description = "考试名称（可只写一部分）") String examName,
            @ToolParam(name = "limit", description = "返回条数，默认 10", required = false) Integer limit) {

        if (!context.isStaff()) {
            return deny("当前角色无权查看待批改列表");
        }
        Exam exam = findExam(examName);
        if (exam == null) {
            return notFound("找不到考试「" + examName + "」", "exams", recentExamNames());
        }
        List<MarkItemVO> items = examAnswerMapper.selectSubjective(exam.getId());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MarkItemVO item : items) {
            if (item.getMarked() != null && item.getMarked() == 1) {
                continue; // 已批改的不算待办
            }
            if (rows.size() >= cap(limit)) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("answer_id", item.getAnswerId());
            row.put("student", nullSafe(item.getStudentName()));
            row.put("paper_question_id", item.getPaperQuestionId());
            row.put("question", truncate(item.getContent()));
            row.put("standard_answer", truncate(item.getStandardAnswer()));
            row.put("student_answer", truncate(item.getStudentAnswer()));
            row.put("full_score", item.getFullScore());
            rows.add(row);
        }
        return ok(Map.of("exam", exam.getExamName(), "pending_count", rows.size(), "pending", rows));
    }

    @Tool(name = "get_mark_context",
            description = "获取某场考试主观题的批改参考：每道题的满分、已批改数量、均分与最高最低分。"
                    + "批改前用它做一致性检查——若某个分数明显偏离同题分布，应提示教师复核。教师与管理员可用。")
    public String getMarkContext(
            @ToolParam(name = "exam_name", description = "考试名称（可只写一部分）") String examName) {

        if (!context.isStaff()) {
            return deny("当前角色无权查看批改参考");
        }
        Exam exam = findExam(examName);
        if (exam == null) {
            return notFound("找不到考试「" + examName + "」", "exams", recentExamNames());
        }
        List<MarkItemVO> items = examAnswerMapper.selectSubjective(exam.getId());

        // 按试卷题目分组，算出「同题得分分布」——一致性检查的输入
        Map<Long, List<MarkItemVO>> grouped = items.stream()
                .filter(item -> item.getPaperQuestionId() != null)
                .collect(Collectors.groupingBy(MarkItemVO::getPaperQuestionId, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Long, List<MarkItemVO>> entry : grouped.entrySet()) {
            List<MarkItemVO> group = entry.getValue();
            MarkItemVO sample = group.get(0);
            List<BigDecimal> markedScores = group.stream()
                    .filter(item -> item.getMarked() != null && item.getMarked() == 1)
                    .map(MarkItemVO::getScore)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("paper_question_id", entry.getKey());
            row.put("sort", sample.getSort());
            row.put("question", truncate(sample.getContent()));
            row.put("full_score", sample.getFullScore());
            row.put("marked_count", markedScores.size());
            if (!markedScores.isEmpty()) {
                BigDecimal sum = markedScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                row.put("avg_score", sum.divide(BigDecimal.valueOf(markedScores.size()), 2, RoundingMode.HALF_UP));
                row.put("min_score", markedScores.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));
                row.put("max_score", markedScores.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));
            }
            rows.add(row);
        }
        return ok(Map.of("exam", exam.getExamName(), "subjective_questions", rows));
    }

    // ==================== 学生个人数据 ====================

    @Tool(name = "get_student_grade",
            description = "查询已结束且已发布的终评成绩。学生只能查自己；教师与管理员可指定学生账号。返回该学生可见的客观/主观/总分。")
    public String getStudentGrade(
            @ToolParam(name = "username", description = "学生登录账号；学生本人调用时必须省略或填自己", required = false) String username) {

        Long studentId = context.getUserId();
        String target = context.getUsername();
        if (context.isStaff() && StringUtils.hasText(username)) {
            SysUser student = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, username).last("limit 1"));
            if (student == null) {
                return notFound("找不到账号「" + username + "」", "hint", List.of("请确认账号拼写"));
            }
            studentId = student.getId();
            target = student.getUsername();
        } else if (!context.isStaff() && StringUtils.hasText(username)
                && !username.equals(context.getUsername())) {
            // 数据隔离：学生跨账号查询一律拒绝，并在工具层就挡住（不依赖提示词约束）
            return deny("学生只能查询本人的成绩，无法查看「" + username + "」的成绩");
        }
        if (studentId == null) {
            return deny("无法确定当前用户身份");
        }

        var records = gradeService.releasedGrades(studentId);
        return ok(Map.of("student", target, "count", records.size(), "grades", records));
    }

    @Tool(name = "get_wrong_questions",
            description = "查询错题本：学生本人的跨考试错题（含我的作答、标准答案、解析、知识点）。学生专用。")
    public String getWrongQuestions(
            @ToolParam(name = "knowledge_point", description = "按知识点过滤；可省略表示全部", required = false) String knowledgePoint,
            @ToolParam(name = "limit", description = "返回条数，默认 10", required = false) Integer limit) {

        if (!context.isStudent()) {
            return deny("错题本仅学生本人可使用；教师可改用 get_knowledge_stats 分析班级薄弱点");
        }
        if (context.getUserId() == null) {
            return deny("无法确定当前用户身份");
        }
        IPage<WrongQuestionVO> page = examAnswerMapper.selectWrongQuestions(
                new Page<>(1, cap(limit)), context.getUserId(),
                StringUtils.hasText(knowledgePoint) ? knowledgePoint : null);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (WrongQuestionVO item : page.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exam", nullSafe(item.getExamName()));
            row.put("knowledge_point", nullSafe(item.getKnowledgePoint()));
            row.put("type", QuestionTypeEnum.labelOf(item.getType()));
            row.put("question", truncate(item.getContent()));
            row.put("my_answer", truncate(item.getMyAnswer()));
            row.put("standard_answer", truncate(item.getStandardAnswer()));
            row.put("analysis", truncate(item.getAnalysis()));
            row.put("got_score", item.getGotScore());
            row.put("full_score", item.getFullScore());
            rows.add(row);
        }
        return ok(Map.of("total", page.getTotal(), "returned", rows.size(), "wrong_questions", rows));
    }

    @Tool(name = "get_my_profile",
            description = "读取本人的学习画像（历史薄弱知识点与学习偏好，属于长期记忆）。做个性化复习建议前先调用它。")
    public String getMyProfile() {
        if (context.getUserId() == null) {
            return deny("无法确定当前用户身份");
        }
        String summary = profileService.loadSummary(context.getUserId());
        return ok(Map.of("has_profile", !summary.isBlank(),
                "profile", summary.isBlank() ? "暂无画像记录（尚未积累足够的答题数据）" : summary));
    }

    // ==================== 练习与自测（学生主力场景）====================

    @Tool(name = "get_practice_questions",
            description = "取一组练习题用于自测。学生调用时**不会返回答案与解析**（自测才有意义），"
                    + "教师/管理员调用时会附带答案。答完再用 check_answer 判分。")
    public String getPracticeQuestions(
            @ToolParam(name = "subject_name", description = "科目名称，如「Java 程序设计」；可省略表示不限", required = false) String subjectName,
            @ToolParam(name = "knowledge_point", description = "知识点关键字，如「集合框架」；可省略", required = false) String knowledgePoint,
            @ToolParam(name = "type", description = "题型：单选/多选/判断/填空/简答；可省略", required = false) String type,
            @ToolParam(name = "difficulty", description = "难度 1-5；可省略", required = false) Integer difficulty,
            @ToolParam(name = "count", description = "题目数量，默认 5，上限 10", required = false) Integer count) {

        if (context.isStudent()) return fail("请在练习空间创建已授权的练习会话");
        Long subjectId = null;
        if (StringUtils.hasText(subjectName)) {
            Subject subject = findSubject(subjectName);
            if (subject == null) {
                return notFound("科目「" + subjectName + "」不存在", "subjects", allSubjectNames());
            }
            subjectId = subject.getId();
        }

        QuestionQueryDTO query = new QuestionQueryDTO();
        query.setSubjectId(subjectId);
        query.setType(QuestionTypeEnum.parse(type));
        query.setDifficulty(difficulty);
        query.setKeyword(knowledgePoint);
        query.setPageNum(1);
        query.setPageSize(Math.min(count == null || count < 1 ? 5 : count, 10));

        List<QuestionVO> records = questionService.page(query).getRecords();
        if (records.isEmpty()) {
            return ok(Map.of("total", 0, "questions", List.of(),
                    "note", "题库中没有匹配的题目。可以放宽知识点或难度条件再试。"));
        }

        // 关键：学生拿不到答案。这不是「提示词要求」，而是数据根本没返回给模型——
        // 模型就算想泄露也没有素材。
        boolean hideAnswer = context.isStudent();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (QuestionVO item : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("question_id", item.getId());
            row.put("type", QuestionTypeEnum.labelOf(item.getType()));
            row.put("difficulty", item.getDifficulty() == null ? 0 : item.getDifficulty());
            row.put("knowledge_point", nullSafe(item.getKnowledgePoint()));
            row.put("content", item.getContent());
            row.put("options", optionsAsList(item.getOptions()));
            row.put("full_score", item.getScore());
            if (!hideAnswer) {
                row.put("answer", item.getAnswer());
                row.put("analysis", nullSafe(item.getAnalysis()));
            }
            rows.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", rows.size());
        data.put("questions", rows);
        data.put("answer_hidden", hideAnswer);
        data.put("note", hideAnswer
                ? "已对学生隐藏答案与解析。请逐题让学生作答，再用 check_answer 判分。"
                : "含答案与解析（教师视角）。");
        return ok(data);
    }

    @Tool(name = "check_answer",
            description = "判定学生对某道题的作答是否正确，并给出标准答案与解析。"
                    + "客观题自动比对；主观题（简答）无法机器判分，只给出参考答案请学生自查。")
    public String checkAnswer(
            @ToolParam(name = "question_id", description = "题目 id，来自 get_practice_questions") Long questionId,
            @ToolParam(name = "answer", description = "学生的作答（选择题填字母如 A 或 ABD；判断填「对/错」）") String answer) {

        if (context.isStudent()) return fail("请通过练习空间的授权练习会话提交答案");
        if (questionId == null) {
            return fail("请提供题目 id");
        }
        if (!StringUtils.hasText(answer)) {
            return fail("请提供学生的作答内容");
        }
        QuestionVO question = questionService.detail(questionId);
        if (question == null) {
            return notFound("题目 id=" + questionId + " 不存在", "hint", List.of("请先用 get_practice_questions 取题"));
        }

        String standard = nullSafe(question.getAnswer());
        QuestionTypeEnum type = QuestionTypeEnum.of(question.getType());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("question_id", questionId);
        data.put("type", QuestionTypeEnum.labelOf(question.getType()));
        data.put("standard_answer", standard);
        data.put("analysis", nullSafe(question.getAnalysis()));
        data.put("knowledge_point", nullSafe(question.getKnowledgePoint()));

        if (type == QuestionTypeEnum.ESSAY) {
            data.put("auto_judged", false);
            data.put("note", "简答题为主观题，本工具不做机器判分。请把参考答案交给学生自查，"
                    + "正式分数由教师在「主观题阅卷」页面确定。");
            return ok(data);
        }

        String mine = normalizeAnswer(answer);
        String expect = normalizeAnswer(standard);
        boolean correct = switch (type) {
            case JUDGE -> normalizeJudge(mine).equals(normalizeJudge(expect));
            case MULTIPLE -> sortLetters(mine).equals(sortLetters(expect));
            default -> mine.equalsIgnoreCase(expect);
        };
        data.put("auto_judged", true);
        data.put("student_answer", answer.trim());
        data.put("correct", correct);
        data.put("verdict", correct ? "回答正确" : "回答错误");
        return ok(data);
    }

    // ==================== 数据导出与审计 ====================

    @Tool(name = "export_data",
            description = "把查询结果导出为 Excel 文件并返回下载地址。"
                    + "支持的 data_type：grades（某场考试成绩明细）、questions（题库题目）、"
                    + "exams（考试列表）、wrong_questions（错题本）。教师与管理员可用。"
                    + "适用于用户说「导出成绩」「给我一份题目清单」这类请求。")
    public String exportData(
            @ToolParam(name = "data_type", description = "导出类型：grades / questions / exams / wrong_questions") String dataType,
            @ToolParam(name = "exam_name", description = "考试名称（data_type=grades 时必填；可只写一部分）", required = false) String examName,
            @ToolParam(name = "subject_name", description = "科目名称（data_type=questions 时可选）", required = false) String subjectName,
            @ToolParam(name = "student_username", description = "学生账号（教师导出某个学生错题时用；学生本人调用请省略）", required = false) String studentUsername) {

        if (!context.isStaff()) {
            return deny("当前角色无权导出数据");
        }
        String type = dataType == null ? "" : dataType.trim().toLowerCase();
        try {
            return switch (type) {
                case "grades" -> exportGrades(examName);
                case "questions" -> exportQuestions(subjectName);
                case "exams" -> exportExams();
                case "wrong_questions" -> exportWrongQuestions(studentUsername);
                default -> fail("不支持的导出类型「" + dataType + "」，可选：grades / questions / exams / wrong_questions");
            };
        } catch (BusinessException e) {
            return fail(e.getMessage());
        }
    }

    @Tool(name = "list_oper_logs",
            description = "查询系统操作日志（审计）：谁在什么时候做了什么、成功还是失败。"
                    + "适用于「最近有没有异常登录」「谁改过用户」这类审计问题。仅管理员可用。")
    public String listOperLogs(
            @ToolParam(name = "module", description = "业务模块过滤，如「用户管理」「登录」；可省略", required = false) String module,
            @ToolParam(name = "oper_name", description = "操作人账号过滤；可省略", required = false) String operName,
            @ToolParam(name = "only_failed", description = "只看失败记录，默认 false", required = false) Boolean onlyFailed,
            @ToolParam(name = "limit", description = "返回条数，默认 10", required = false) Integer limit) {

        if (!context.isAdmin()) {
            return deny("操作日志属于审计数据，仅管理员可查");
        }
        List<SysOperLog> logs = operLogMapper.selectList(Wrappers.<SysOperLog>lambdaQuery()
                .eq(StringUtils.hasText(module), SysOperLog::getModule, module)
                .eq(StringUtils.hasText(operName), SysOperLog::getOperName, operName)
                .eq(Boolean.TRUE.equals(onlyFailed), SysOperLog::getResultStatus, 0)
                .orderByDesc(SysOperLog::getId)
                .last("limit " + cap(limit)));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysOperLog item : logs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", String.valueOf(item.getOperTime()));
            row.put("module", nullSafe(item.getModule()));
            row.put("action", nullSafe(item.getAction()));
            row.put("operator", nullSafe(item.getOperName()));
            row.put("role", nullSafe(item.getOperRole()));
            row.put("ip", nullSafe(item.getOperIp()));
            row.put("result", item.getResultStatus() != null && item.getResultStatus() == 1 ? "成功" : "失败");
            row.put("cost_ms", item.getCostMs());
            if (item.getErrorMsg() != null) {
                row.put("error", truncate(item.getErrorMsg()));
            }
            rows.add(row);
        }
        return ok(Map.of("total", rows.size(), "logs", rows));
    }

    // ==================== RAG 检索与草稿 ====================

    @Tool(name = "search_knowledge",
            description = "检索本地考纲知识库，获取某知识点的定义、典型考法与常见错误，并附带题库中的同类题。"
                    + "出题、讲解「为什么」、设计复习建议时用它获取可靠上下文。这是关键词+结构化检索，不是向量检索。")
    public String searchKnowledge(
            @ToolParam(name = "query", description = "检索内容，如「Java 并发」「索引失效」") String query,
            @ToolParam(name = "subject", description = "限定科目；可省略", required = false) String subject) {

        String result = knowledgeBase.retrieve(query, subject);
        if (result.isBlank()) {
            return ok(Map.of(
                    "found", false,
                    "message", "知识库中没有匹配「" + query + "」的考点条目。请基于你自己的知识作答，"
                            + "或改用 search_questions 查询题库中已有的相关题目。"));
        }
        return ok(Map.of("found", true, "content", result,
                "outline", knowledgeBase.outline()));
    }

    @Tool(name = "draft_question",
            description = "把生成好的题目登记为**草稿**并返回结构化结果。注意：它不会写入数据库——"
                    + "必须由教师在「题库管理」页面确认后才会真正入库。生成题目后务必调用它。")
    public String draftQuestion(
            @ToolParam(name = "subject_name", description = "所属科目名称") String subjectName,
            @ToolParam(name = "type", description = "题型：单选/多选/判断/填空/简答") String type,
            @ToolParam(name = "content", description = "题干") String content,
            @ToolParam(name = "answer", description = "参考答案") String answer,
            @ToolParam(name = "options", description = "选项，格式 A=选项内容|B=选项内容；非选择题可省略", required = false) String options,
            @ToolParam(name = "analysis", description = "解析；建议填写", required = false) String analysis,
            @ToolParam(name = "knowledge_point", description = "知识点", required = false) String knowledgePoint,
            @ToolParam(name = "difficulty", description = "难度 1-5", required = false) Integer difficulty,
            @ToolParam(name = "score", description = "建议分值", required = false) BigDecimal score) {

        if (!context.isStaff()) {
            return deny("当前角色无权生成题目草稿");
        }
        Integer typeCode = QuestionTypeEnum.parse(type);
        if (typeCode == null) {
            return fail("题型无法识别，只能是：单选 / 多选 / 判断 / 填空 / 简答");
        }
        if (!StringUtils.hasText(content) || !StringUtils.hasText(answer)) {
            return fail("题干与参考答案都不能为空");
        }

        Map<String, String> optionMap = parseOptions(options);
        // 自检：把「不合格的草稿」在工具层拦下，而不是让教师看到脏数据
        List<String> problems = new ArrayList<>();
        if (QuestionTypeEnum.SINGLE.getCode() == typeCode || QuestionTypeEnum.MULTIPLE.getCode() == typeCode) {
            if (optionMap.size() < 2) {
                problems.add("选择题至少需要 2 个选项");
            } else {
                String normalized = answer.replaceAll("\\s+", "").toUpperCase();
                if (QuestionTypeEnum.SINGLE.getCode() == typeCode) {
                    if (!optionMap.containsKey(normalized)) {
                        problems.add("单选题答案必须是某个选项字母，当前为「" + answer + "」");
                    }
                } else {
                    for (char c : normalized.toCharArray()) {
                        if (!optionMap.containsKey(String.valueOf(c))) {
                            problems.add("多选题答案含未定义的选项：" + c);
                        }
                    }
                }
            }
        }
        if (QuestionTypeEnum.JUDGE.getCode() == typeCode
                && !List.of("对", "错", "true", "false", "T", "F").contains(answer.trim())) {
            problems.add("判断题答案只能是「对」或「错」");
        }
        if (difficulty != null && (difficulty < 1 || difficulty > 5)) {
            problems.add("难度必须在 1-5 之间");
        }
        if (!problems.isEmpty()) {
            return fail("草稿自检未通过：" + String.join("；", problems) + "。请修正后重新调用 draft_question。");
        }

        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("subject_name", subjectName);
        draft.put("type", QuestionTypeEnum.labelOf(typeCode));
        draft.put("content", content);
        draft.put("options", optionMap);
        draft.put("answer", answer.trim());
        draft.put("analysis", nullSafe(analysis));
        draft.put("knowledge_point", nullSafe(knowledgePoint));
        draft.put("difficulty", difficulty == null ? 3 : difficulty);
        draft.put("score", score == null ? new BigDecimal("5.00") : score);
        draft.put("persisted", false);
        draft.put("self_check", "passed");

        return ok(Map.of(
                "draft", draft,
                "notice", "以上为草稿，尚未入库。请提示教师到「题库管理」页面新建题目并粘贴内容，确认后才会写入题库。"));
    }

    // ==================== 内部方法：数据装配 ====================

    /** 学生视角的考试列表（不能走 ExamService.studentExamList，它依赖 ThreadLocal 登录态） */
    private String studentExams(Integer limit) {
        SysUser student = userMapper.selectById(context.getUserId());
        if (student == null || student.getClassId() == null) {
            return ok(Map.of("total", 0, "exams", List.of(), "note", "当前账号未分配班级，看不到任何考试"));
        }
        List<Exam> exams = examMapper.selectList(Wrappers.<Exam>lambdaQuery()
                .eq(Exam::getPublished, 1)
                .eq(Exam::getClassId, student.getClassId())
                .orderByDesc(Exam::getStartTime)
                .last("limit " + cap(limit)));

        List<Long> examIds = exams.stream().map(Exam::getId).collect(Collectors.toList());
        Map<Long, ExamStudent> mine = examIds.isEmpty() ? Map.of()
                : examStudentMapper.selectList(Wrappers.<ExamStudent>lambdaQuery()
                        .eq(ExamStudent::getStudentId, context.getUserId())
                        .in(ExamStudent::getExamId, examIds))
                .stream().collect(Collectors.toMap(ExamStudent::getExamId, item -> item, (a, b) -> a));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Exam exam : exams) {
            ExamStudent record = mine.get(exam.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("exam_id", exam.getId());
            row.put("exam_name", exam.getExamName());
            row.put("start_time", String.valueOf(exam.getStartTime()));
            row.put("end_time", String.valueOf(exam.getEndTime()));
            row.put("duration_minutes", exam.getDuration());
            row.put("my_status", record == null ? "未参加" : ExamStudentStatusEnum.labelOf(record.getStatus()));
            row.put("my_total_score", record == null ? null : record.getTotalScore());
            rows.add(row);
        }
        return ok(Map.of("total", rows.size(), "exams", rows));
    }

    private Map<String, Object> examRow(ExamVO exam) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("exam_id", exam.getId());
        row.put("exam_name", exam.getExamName());
        row.put("subject", nullSafe(exam.getSubjectName()));
        row.put("class_name", nullSafe(exam.getClassName()));
        row.put("status", nullSafe(exam.getStatusName()));
        row.put("published", exam.getPublished() != null && exam.getPublished() == 1);
        row.put("start_time", String.valueOf(exam.getStartTime()));
        row.put("end_time", String.valueOf(exam.getEndTime()));
        row.put("paper_name", nullSafe(exam.getPaperName()));
        return row;
    }

    private Map<String, Object> examGradeSummary(ExamGradeVO grade) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exam", grade.getExamName());
        data.put("subject", nullSafe(grade.getSubjectName()));
        data.put("class_name", nullSafe(grade.getClassName()));
        data.put("paper_total_score", grade.getPaperTotalScore());
        data.put("pass_line", grade.getPassLine());
        data.put("should_count", grade.getShouldCount());
        data.put("submitted_count", grade.getSubmittedCount());
        data.put("marked_count", grade.getMarkedCount());
        data.put("participant_count", grade.getParticipantCount());
        data.put("pending_mark_count", grade.getPendingMarkCount());
        data.put("score_basis", grade.getScoreBasis());
        data.put("roster_basis", grade.getRosterBasis());
        data.put("avg_score", grade.getAvgScore());
        data.put("max_score", grade.getMaxScore());
        data.put("min_score", grade.getMinScore());
        data.put("pass_rate", grade.getPassRate());

        List<Map<String, Object>> top = new ArrayList<>();
        List<GradeItemVO> items = grade.getItems() == null ? List.of()
                : grade.getItems().stream().filter(item -> item.getRank() != null).toList();
        for (GradeItemVO item : items.subList(0, Math.min(5, items.size()))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", item.getRank());
            row.put("student", nullSafe(item.getStudentName()));
            row.put("total_score", item.getTotalScore());
            top.add(row);
        }
        data.put("top_students", top);
        if (grade.getDistribution() != null) {
            List<Map<String, Object>> buckets = new ArrayList<>();
            grade.getDistribution().forEach(bucket -> buckets.add(Map.of(
                    "range", bucket.getLabel(), "count", bucket.getCount())));
            data.put("distribution", buckets);
        }
        return data;
    }

    // ==================== 内部方法：练习判分与数据导出 ====================

    /** 选项转成有序列表（A/B/C/D）。顺序对模型很重要：它要靠顺序复述题目 */
    private List<Map<String, String>> optionsAsList(Map<String, String> options) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (options == null) {
            return rows;
        }
        options.forEach((label, text) -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("label", label);
            row.put("text", text);
            rows.add(row);
        });
        return rows;
    }

    /**
     * 作答归一化：去掉空白与常见分隔符后转大写。
     * 目的是让「A,B」「a b」「AB」被判成同一个答案——学生手输格式千奇百怪，
     * 因为多了个逗号就判错会严重损害自测体验。
     */
    private String normalizeAnswer(String answer) {
        if (answer == null) {
            return "";
        }
        return answer.replaceAll("[\\s,，、;；|/]+", "").trim().toUpperCase();
    }

    /** 判断题归一化：把 ∨/√/T/对 统一成「对」，×/F/错 统一成「错」 */
    private String normalizeJudge(String answer) {
        String value = normalizeAnswer(answer);
        if (value.isEmpty()) {
            return "";
        }
        if (value.matches("(√|∨|✓|正确|对|是|TRUE|T|YES|Y|1)")) {
            return "对";
        }
        if (value.matches("(×|✗|Ⅹ|X|错误|错|否|FALSE|F|NO|N|0)")) {
            return "错";
        }
        return value;
    }

    /** 多选答案排序：ABD 与 DBA 等价 */
    private String sortLetters(String letters) {
        char[] chars = letters.replaceAll("[^A-Z]", "").toCharArray();
        java.util.Arrays.sort(chars);
        return new String(chars);
    }

    /**
     * 导出考试成绩明细。
     * 直接复用 {@link GradeService#exportExamGrade(Long)}：与页面上的「导出成绩」按钮
     * 走的是同一个实现，保证「AI 导出的文件」和「页面导出的文件」口径完全一致。
     */
    private String exportGrades(String examName) {
        Exam exam = findExam(examName);
        if (exam == null) {
            return notFound("找不到考试「" + examName + "」（可只写名称的一部分）", "exams", recentExamNames());
        }
        byte[] bytes = gradeService.exportExamGrade(exam.getId());
        return saveExport(bytes, exam.getExamName() + "-成绩明细.xlsx", "ai-export-grades", exam.getId());
    }

    /** 导出题库题目（含答案，仅教师/管理员可达——工具层已按角色摘除） */
    private String exportQuestions(String subjectName) {
        Long subjectId = null;
        if (StringUtils.hasText(subjectName)) {
            Subject subject = findSubject(subjectName);
            if (subject == null) {
                return notFound("科目「" + subjectName + "」不存在", "subjects", allSubjectNames());
            }
            subjectId = subject.getId();
        }
        QuestionQueryDTO query = new QuestionQueryDTO();
        query.setSubjectId(subjectId);
        query.setPageNum(1);
        query.setPageSize(EXPORT_LIMIT);
        List<QuestionVO> records = questionService.page(query).getRecords();
        if (records.isEmpty()) {
            return fail("没有可导出的题目（题库为空或条件无匹配）");
        }

        List<List<String>> rows = new ArrayList<>();
        for (QuestionVO item : records) {
            Map<String, String> options = item.getOptions() == null ? Map.of() : item.getOptions();
            rows.add(List.of(
                    String.valueOf(item.getId()),
                    QuestionTypeEnum.labelOf(item.getType()),
                    nullSafe(item.getKnowledgePoint()),
                    String.valueOf(item.getDifficulty() == null ? 0 : item.getDifficulty()),
                    item.getContent(),
                    options.entrySet().stream()
                            .map(entry -> entry.getKey() + "." + entry.getValue())
                            .collect(Collectors.joining("  ")),
                    nullSafe(item.getAnswer()),
                    nullSafe(item.getAnalysis()),
                    String.valueOf(item.getScore())));
        }
        byte[] bytes = ExcelHelper.write("题库题目",
                List.of("ID", "题型", "知识点", "难度", "题干", "选项", "答案", "解析", "分值"), rows);
        return saveExport(bytes, "题库题目-" + LocalDate.now() + ".xlsx", "ai-export-questions", subjectId);
    }

    /** 导出考试列表 */
    private String exportExams() {
        var page = examService.page(pageQuery(EXPORT_LIMIT));
        List<ExamVO> records = page.getRecords();
        if (records.isEmpty()) {
            return fail("系统中还没有任何考试");
        }
        List<List<String>> rows = new ArrayList<>();
        for (ExamVO item : records) {
            rows.add(List.of(
                    String.valueOf(item.getId()),
                    item.getExamName(),
                    nullSafe(item.getSubjectName()),
                    nullSafe(item.getPaperName()),
                    nullSafe(item.getClassName()),
                    String.valueOf(item.getStartTime()),
                    String.valueOf(item.getEndTime()),
                    String.valueOf(item.getStatusName()),
                    String.valueOf(item.getPublished() != null && item.getPublished() == 1 ? "已发布" : "未发布")));
        }
        byte[] bytes = ExcelHelper.write("考试列表",
                List.of("ID", "考试名称", "科目", "试卷", "班级", "开始时间", "结束时间", "状态", "发布状态"), rows);
        return saveExport(bytes, "考试列表-" + LocalDate.now() + ".xlsx", "ai-export-exams", null);
    }

    /**
     * 导出错题本。
     *
     * <p>复用 {@code selectWrongQuestions} —— 页面、Agent、导出处同一套 SQL，
     * 不会出现「导出的错题数和页面显示的对不上」。学生默认只能导自己的；
     * 教师可指定学生账号（由工具的角色闸门保证学生拿不到这个入口）。
     */
    private String exportWrongQuestions(String studentUsername) {
        Long studentId = context.getUserId();
        String label = "本人";
        if (StringUtils.hasText(studentUsername)) {
            if (!context.isStaff()) {
                return deny("学生只能导出本人的错题本");
            }
            SysUser student = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, studentUsername.trim())
                    .last("limit 1"));
            if (student == null) {
                return notFound("找不到账号「" + studentUsername + "」", "hint",
                        List.of("请确认学生账号是否正确"));
            }
            studentId = student.getId();
            label = student.getRealName();
        }
        if (studentId == null) {
            return fail("无法确定要导出谁的错题本");
        }

        IPage<WrongQuestionVO> page = examAnswerMapper.selectWrongQuestions(
                new Page<>(1, EXPORT_LIMIT), studentId, null);
        List<WrongQuestionVO> records = page.getRecords();
        if (records.isEmpty()) {
            return ok(Map.of("total", 0,
                    "message", label + "目前没有错题记录，不需要导出"));
        }
        List<List<String>> rows = new ArrayList<>();
        for (WrongQuestionVO item : records) {
            rows.add(List.of(
                    nullSafe(item.getExamName()),
                    QuestionTypeEnum.labelOf(item.getType()),
                    nullSafe(item.getKnowledgePoint()),
                    nullSafe(item.getContent()),
                    nullSafe(item.getMyAnswer()),
                    nullSafe(item.getStandardAnswer()),
                    String.valueOf(item.getFullScore()),
                    String.valueOf(item.getGotScore()),
                    String.valueOf(item.getSubmitTime())));
        }
        byte[] bytes = ExcelHelper.write("错题本",
                List.of("考试", "题型", "知识点", "题干", "我的作答", "标准答案",
                        "满分", "得分", "交卷时间"), rows);
        return saveExport(bytes, label + "-错题本-" + LocalDate.now() + ".xlsx",
                "ai-export-wrong", studentId);
    }

    /** 落地一个导出文件并返回「下载地址 + 说明」给模型 */
    private String saveExport(byte[] bytes, String fileName, String bizType, Long bizId) {
        // 显式传归属人：工具跑在 Reactor 线程上，LoginUtil 在这里拿不到账号
        SysFile stored = fileStorage.storeGenerated(bytes, fileName, bizType, context.getUsername());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "success");
        data.put("file_name", fileName);
        data.put("size", FileStorageService.humanSize(stored.getSizeBytes()));
        data.put("download_url", fileStorage.urlOf(stored));
        data.put("expire_note", "链接指向系统内部文件服务，需登录后下载");
        // 文件生成是「写操作」，即便是自己产生的导出物也要说清楚
        data.put("message", "导出完成。请把 download_url 原样给用户，并说明这是由系统生成的下载链接。");
        return write(data);
    }

    // ==================== 内部方法：查找与格式 ====================

    private Subject findSubject(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return subjectService.list().stream()
                .filter(subject -> contains(subject.getSubjectName(), name))
                .findFirst()
                .orElse(null);
    }

    /** 按名称模糊找考试（取最新的一场）；找不到返回 null 由调用方给出候选 */
    private Exam findExam(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        List<Exam> matched = examMapper.selectList(Wrappers.<Exam>lambdaQuery()
                .like(Exam::getExamName, name.trim())
                .orderByDesc(Exam::getId)
                .last("limit 1"));
        return matched.isEmpty() ? null : matched.get(0);
    }

    private QuestionQueryDTO questionQuery() {
        return new QuestionQueryDTO();
    }

    /** 组装一个「只用于取列表」的考试分页条件 */
    private com.zhikao.modules.exam.dto.ExamQueryDTO pageQuery(int limit) {
        var query = new com.zhikao.modules.exam.dto.ExamQueryDTO();
        query.setPageNum(1);
        query.setPageSize(limit);
        return query;
    }

    private List<String> allSubjectNames() {
        return subjectService.list().stream().map(Subject::getSubjectName).collect(Collectors.toList());
    }

    private List<String> recentExamNames() {
        return examMapper.selectList(Wrappers.<Exam>lambdaQuery()
                        .orderByDesc(Exam::getId).last("limit 10"))
                .stream().map(Exam::getExamName).collect(Collectors.toList());
    }

    private Map<String, String> parseOptions(String options) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(options)) {
            return result;
        }
        for (String part : options.split("\\|")) {
            int index = part.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String key = part.substring(0, index).trim().toUpperCase();
            String value = part.substring(index + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private BigDecimal percentage(int correct, int total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(correct * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean contains(String text, String keyword) {
        return text != null && StringUtils.hasText(keyword) && text.contains(keyword.trim());
    }

    private int cap(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 30);
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= TEXT_LIMIT ? flat : flat.substring(0, TEXT_LIMIT) + "...";
    }

    private String nullSafe(String text) {
        return text == null ? "" : text;
    }

    // ==================== 内部方法：统一返回结构 ====================

    /** 成功：包一层 ok 便于模型区分「查到但为空」与「调用失败」 */
    private String ok(Object data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("status", "ok");
        envelope.put("data", data);
        return write(envelope);
    }

    /** 参数/自检问题：把原因说清楚，让模型自我修正后重试 */
    private String fail(String message) {
        return write(Map.of("status", "invalid", "message", message));
    }

    /** 业务上不允许（含越权）：明确告知，不做任何尝试 */
    private String deny(String message) {
        return write(Map.of("status", "denied", "message", message));
    }

    /** 找不到目标：附带候选清单，便于模型改口径重试 */
    private String notFound(String message, String hintKey, List<String> candidates) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "not_found");
        payload.put("message", message);
        payload.put(hintKey, candidates);
        return write(payload);
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"status\":\"error\",\"message\":\"结果序列化失败\"}";
        }
    }
}
