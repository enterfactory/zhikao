package com.zhikao.modules.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhikao.entity.*;
import com.zhikao.mapper.*;
import com.zhikao.modules.exam.GradeService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** A hand-calculated ledger on the same real ECS services as Stage A. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("ecs")
@Import(EcsTestSupport.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatisticsLedgerIntegrationTest {
    static final String SCHEMA = EcsTestSupport.schema("ledger");
    @DynamicPropertySource static void cloud(DynamicPropertyRegistry registry) { EcsTestSupport.configure(registry, SCHEMA); }

    @Autowired EcsTestSupport support;
    @Autowired StageAFixtureMapper fixtures;
    @Autowired org.springframework.data.redis.connection.RedisConnectionFactory redis;
    @Autowired SysUserMapper users;
    @Autowired SysUserRoleMapper links;
    @Autowired SysRoleMapper roles;
    @Autowired SysClassMapper classes;
    @Autowired SubjectMapper subjects;
    @Autowired PaperMapper papers;
    @Autowired PaperQuestionMapper snapshots;
    @Autowired ExamMapper exams;
    @Autowired ExamStudentMapper attempts;
    @Autowired ExamAnswerMapper answers;
    @Autowired GradeService grades;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    final List<SysUser> learners = new ArrayList<>();
    final Map<Long, List<PaperQuestion>> questions = new HashMap<>();
    Long examA, examB, examC;

    @BeforeAll void ledger() {
        support.seed();
        learners.add(user("student")); learners.add(user("student02"));
        Long classId = learners.get(0).getClassId();
        Long roleId = roles.selectList(null).stream().filter(r -> "student".equals(r.getRoleCode())).findFirst().orElseThrow().getId();
        String hash = learners.get(0).getPassword();
        for (int i = 3; i <= 12; i++) {
            var student = SysUser.builder().username("ledger" + i).realName("账本学生" + i).userType(3).status(1)
                    .classId(classId).password(hash).passwordChangeRequired(0).build();
            users.insert(student); links.insert(SysUserRole.builder().userId(student.getId()).roleId(roleId).build());
            learners.add(student);
        }
        var secondClass = new SysClass(null, "账本二班", "2026", "测试", LocalDateTime.now());
        classes.insert(secondClass);
        var secondSubject = new Subject(null, "账本科目二", "LEDGER2", "test", LocalDateTime.now());
        subjects.insert(secondSubject);
        Long firstSubject = fixtures.firstSubjectId();
        examA = exam("A：100 分终评", firstSubject, classId, 100);
        examB = exam("B：50 分含待阅", secondSubject.getId(), classId, 50);
        examC = exam("C：200 分历史记录", firstSubject, classId, 200);
        int[] scores = {100, 95, 90, 85, 80, 75, 70, 65, 60, 60, 50, 45};
        for (int i = 0; i < learners.size(); i++) attempt(examA, learners.get(i), 3, scores[i]);
        attempt(examB, learners.get(0), 3, 25);
        attempt(examB, learners.get(1), 2, 50);
        attempt(examB, learners.get(2), 1, 40);
        attempt(examC, learners.get(0), 3, 100);
        attempt(examC, learners.get(11), 3, 0);
        // A later transfer must not remove the student's historical released results.
        var transfer = new SysUser(); transfer.setId(learners.get(11).getId()); transfer.setClassId(secondClass.getId());
        users.updateById(transfer);
    }

    Long exam(String title, Long subjectId, Long classId, int total) {
        var paper = Paper.builder().paperName(title).subjectId(subjectId).totalScore(BigDecimal.valueOf(total))
                .questionCount(6).totalDuration(30).genType(1).status(1).createBy("teacher").build();
        papers.insert(paper);
        var snapshotsForPaper = new ArrayList<PaperQuestion>();
        int[] weights = {20, 20, 20, 20, 10, 10};
        for (int i = 0; i < 6; i++) {
            var q = PaperQuestion.builder().paperId(paper.getId()).questionId(900000L + i).sort(i + 1)
                    .type(5).content("账本知识点 " + (i + 1)).answer("标准答案").knowledgePoint("同名知识点" + (i + 1))
                    .score(BigDecimal.valueOf(weights[i] * total).divide(BigDecimal.valueOf(100))).build();
            snapshots.insert(q); snapshotsForPaper.add(q);
        }
        var exam = Exam.builder().examName(title).paperId(paper.getId()).classId(classId).published(1).status(2)
                .releaseStatus(2).duration(30).startTime(LocalDateTime.now().minusHours(3))
                .endTime(LocalDateTime.now().minusHours(2)).createBy("teacher").build();
        exams.insert(exam); questions.put(exam.getId(), snapshotsForPaper); return exam.getId();
    }

    void attempt(Long examId, SysUser user, int status, int total) {
        var record = ExamStudent.builder().examId(examId).studentId(user.getId()).status(status)
                .enterTime(LocalDateTime.now().minusHours(3)).submitTime(status >= 2 ? LocalDateTime.now().minusMinutes(1) : null)
                .totalScore(BigDecimal.valueOf(total)).objectiveScore(BigDecimal.ZERO).subjectiveScore(BigDecimal.valueOf(total)).build();
        attempts.insert(record);
        BigDecimal left = BigDecimal.valueOf(total);
        for (var q : questions.get(examId)) {
            BigDecimal got = left.min(q.getScore()).max(BigDecimal.ZERO); left = left.subtract(got);
            answers.insert(ExamAnswer.builder().examStudentId(record.getId()).paperQuestionId(q.getId()).answer("已作答")
                    .isObjective(0).score(got).marked(status == 3 ? 1 : 0).correct(got.compareTo(q.getScore()) == 0 ? 1 : 0).build());
        }
        assertEquals(0, left.signum(), "Ledger detail scores must sum to the total");
    }

    SysUser user(String name) {
        return users.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, name));
    }
    JsonNode as(SysUser user, String path) throws Exception {
        var login = mvc.perform(post("/auth/login").contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("username", user.getUsername(), "password", EcsTestSupport.PASSWORD))))
                .andReturn().getResponse();
        JsonNode session = json.readTree(login.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(200, session.path("code").asInt());
        var response = mvc.perform(get(path).header("satoken", session.path("data").path("token").asText())).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(200, body.path("code").asInt()); return body.path("data");
    }

    @Test void finalStatisticsExcludeProvisionalScoresAndRanks() {
        var b = grades.examGrade(examB);
        assertEquals(2, b.getSubmittedCount()); assertEquals(1, b.getMarkedCount());
        assertEquals(1, b.getParticipantCount());
        assertEquals(new BigDecimal("25.00"), b.getAvgScore());
        assertEquals(new BigDecimal("0.00"), b.getPassRate());
        assertEquals(1, b.getDistribution().stream().mapToInt(x -> x.getCount()).sum());
        var pending = b.getItems().stream().filter(x -> x.getStatus() == 2).findFirst().orElseThrow();
        assertNull(pending.getRank());
        assertEquals(1, b.getItems().stream().filter(x -> x.getStatus() == 3).findFirst().orElseThrow().getRank());
        var a = grades.examGrade(examA);
        // Sum=875; 875/12=72.9166..., independently fixed in the published ledger.
        assertEquals(new BigDecimal("72.92"), a.getAvgScore());
        assertEquals(new BigDecimal("83.33"), a.getPassRate());
        assertEquals(List.of(9, 9), a.getItems().stream().filter(x -> x.getTotalScore().intValue() == 60).map(x -> x.getRank()).toList());
    }

    @Test void knowledgeDenominatorContainsOnlyFinalMarkedAnswers() {
        var stats = answers.selectKnowledgeStats(examB);
        assertEquals(6, stats.size());
        for (var stat : stats) assertEquals(1, stat.getTotal());
    }

    @Test void leaderboardIncludesSelfBeyondTopTenWithActualScore() throws Exception {
        for (int position : new int[]{0, 9, 10, 11}) {
            var student = learners.get(position);
            var rows = as(student, "/portal/home").path("leaderboard");
            var mine = new ArrayList<JsonNode>(); rows.forEach(r -> { if (r.path("userId").asLong() == student.getId()) mine.add(r); });
            assertEquals(1, mine.size(), "Self must appear exactly once at position " + (position + 1));
            assertEquals(position + 1, mine.get(0).path("rank").asInt());
            if (position == 10) assertEquals(50, mine.get(0).path("score").asInt());
        }
    }

    @Test void dashboardSeparatesWeakTotalFromDisplayAndMissingFromZero() throws Exception {
        var view = as(learners.get(11), "/dashboard/overview");
        assertEquals(5, view.path("weakPoints").size());
        JsonNode weak = null;
        for (var metric : view.path("metrics")) if (metric.path("key").asText().equals("weak")) weak = metric;
        assertNotNull(weak); assertEquals("6", weak.path("value").asText());
    }

    @Test void missingResultDatesAreNullInsteadOfZero() throws Exception {
        var view = as(learners.get(11), "/dashboard/overview");
        var trend = view.path("trend"); assertEquals(7, trend.size());
        assertTrue(trend.get(0).path("avgValue").isNull(), "No result is different from a real zero score");
    }

    @Test void mixedFullScoresUseAveragePercentageAcrossHistoricalReleasedExams() throws Exception {
        var view = as(learners.get(0), "/dashboard/overview");
        JsonNode average = null;
        for (var metric : view.path("metrics")) if (metric.path("key").asText().equals("avg")) average = metric;
        assertNotNull(average); assertEquals("66.67", average.path("value").asText());
        assertEquals("%", average.path("unit").asText());
        var points = attempts.avgScoreByDayForStudent(learners.get(0).getId(), LocalDateTime.now().minusDays(7));
        assertEquals(new BigDecimal("66.67"), points.get(0).getAvgValue().setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test void sameKnowledgeNameInDifferentSubjectsRemainsSeparate() {
        var stats = answers.selectStudentKnowledgeStats(learners.get(0).getId());
        assertEquals(12, stats.size());
        assertEquals(2, stats.stream().map(x -> x.getSubjectId()).distinct().count());
        assertEquals(2, stats.stream().filter(x -> "同名知识点1".equals(x.getKnowledgePoint())).count());
    }

    @Test void noFinalResultsHaveNoAverageOrDistributionPopulation() {
        Long empty = exam("空终评账本", fixtures.firstSubjectId(), learners.get(0).getClassId(), 100);
        var grade = grades.examGrade(empty);
        assertNull(grade.getAvgScore()); assertNull(grade.getMaxScore()); assertNull(grade.getPassRate());
        assertEquals(0, grade.getParticipantCount());
        assertEquals(0, grade.getDistribution().stream().mapToInt(x -> x.getCount()).sum());
    }

    @AfterAll void cleanup() { EcsTestSupport.cleanupRedis(redis, SCHEMA); fixtures.dropTestSchema(SCHEMA); }
}
