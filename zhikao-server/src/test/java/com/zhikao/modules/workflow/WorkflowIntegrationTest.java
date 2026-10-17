package com.zhikao.modules.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhikao.common.AnswerJudge;
import com.zhikao.modules.crawler.CrawlTaskLease;
import com.zhikao.modules.exam.AnswerService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.zhikao.mapper.StageAFixtureMapper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"zhikao.crawler.ingest-dir=./target/test-ingest","zhikao.crawler.recovery-enabled=false","zhikao.export.recovery-enabled=false"})
@org.springframework.context.annotation.Import(EcsTestSupport.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@ActiveProfiles("ecs")
class WorkflowIntegrationTest {
    static final String SCHEMA = EcsTestSupport.schema("workflow");
    @org.springframework.test.context.DynamicPropertySource
    static void cloud(org.springframework.test.context.DynamicPropertyRegistry r) { EcsTestSupport.configure(r, SCHEMA); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StageAFixtureMapper fixtures;
    @org.junit.jupiter.api.AfterAll static void cleanup(@Autowired StageAFixtureMapper fixtures,@Autowired org.springframework.data.redis.connection.RedisConnectionFactory redis) {
        EcsTestSupport.cleanupRedis(redis,SCHEMA);fixtures.dropTestSchema(SCHEMA);
    }
    @Autowired AnswerService answers;
    @Autowired CrawlTaskLease leases;
    String teacher,student,other,admin;
    long subject,classId;
    static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired EcsTestSupport support;
    @BeforeEach void login() throws Exception {
        support.seed();
        teacher=login("teacher");student=login("student");other=login("student02");admin=login("admin");
        subject=fixtures.firstSubjectId();
        classId=fixtures.studentClassId();
    }
    String login(String name)throws Exception{return ok(post("/auth/login"),null,Map.of("username",name,"password",EcsTestSupport.PASSWORD)).path("token").asText();}
    JsonNode request(MockHttpServletRequestBuilder b,String token,Object body)throws Exception {
        if(token!=null)b.header("satoken",token);
        if(body!=null)b.contentType("application/json").content(json.writeValueAsBytes(body));
        String text=mvc.perform(b).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(text);
    }
    JsonNode ok(MockHttpServletRequestBuilder b,String token,Object body)throws Exception {
        JsonNode r=request(b,token,body);assertEquals(200,r.path("code").asInt(),r.toString());return r.path("data");
    }
    void denied(MockHttpServletRequestBuilder b,String token,Object body)throws Exception {assertNotEquals(200,request(b,token,body).path("code").asInt());}
    long question(int type,String title,String answer)throws Exception {
        Map<String,Object> q=new HashMap<>(Map.of("subjectId",subject,"type",type,"content",title,"answer",answer,"analysis","参考解题过程","difficulty",2,"score",5,"knowledgePoint","导数与微分"));
        if(type<=2)q.put("options",Map.of("A","x","B","2x","C","3x","D","4x"));
        ok(post("/question"),teacher,q);
        return fixtures.questionIdByContent(title);
    }
    long exam(List<Long> ids,int auto)throws Exception {
        var items=ids.stream().map(id->Map.of("questionId",id,"score",5)).toList();
        long p=ok(post("/paper/genManual"),teacher,Map.of("paperName","工作流回归试卷","subjectId",subject,"totalDuration",30,"questions",items)).asLong();
        ok(put("/paper/"+p+"/status").param("status","1"),teacher,null);
        long id=ok(post("/exam"),teacher,Map.of("examName","工作流回归考试","paperId",p,"classId",classId,"startTime",LocalDateTime.now().minusMinutes(1).format(TIME),"endTime",LocalDateTime.now().plusHours(1).format(TIME),"duration",30,"autoMark",auto)).asLong();
        ok(post("/exam/"+id+"/publish"),teacher,null);return id;
    }

    @Test void browserCanPreflightAndPatchCandidates() throws Exception {
        var response = mvc.perform(options("/v2/curation/candidates/1")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "content-type,satoken"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("PATCH"));
        var patchResponse = mvc.perform(patch("/v2/curation/candidates/1")
                .header("Origin", "http://localhost:5173").header("satoken", teacher)
                .contentType("application/json").content("{\"expectedVersion\":1}"))
                .andReturn().getResponse();
        assertEquals("http://localhost:5173", patchResponse.getHeader("Access-Control-Allow-Origin"));
        assertNotEquals(403, patchResponse.getStatus());
    }

    @Test void examReleasePracticeAndOwnership()throws Exception {
        long id=exam(List.of(question(1,"求函数 f(x)=x² 的导数。","B"),question(3,"常数函数的导数始终不为零。","错"),question(5,"请写出求导过程。","使用幂函数求导法则。")),1);
        JsonNode paper=ok(post("/exam/"+id+"/enter"),student,null);
        for(JsonNode q:paper.path("questions")){assertTrue(q.path("answer").isNull()||q.path("answer").isMissingNode());assertTrue(q.path("questionId").isNull()||q.path("questionId").isMissingNode());}
        long first=paper.path("questions").get(0).path("id").asLong();
        ok(put("/exam/"+id+"/answer"),student,Map.of("paperQuestionId",first,"answer","A","expectedVersion",0));
        assertEquals(40901,request(put("/exam/"+id+"/answer"),student,Map.of("paperQuestionId",first,"answer","B","expectedVersion",0)).path("code").asInt());
        JsonNode result=ok(post("/exam/"+id+"/submit"),student,null);
        assertTrue(result.path("totalScore").isNull()||result.path("totalScore").isMissingNode());
        ok(post("/exam/"+id+"/submit"),student,null);
        denied(put("/exam/"+id+"/answer"),student,Map.of("paperQuestionId",first,"answer","B","expectedVersion",1));
        denied(get("/grade/mine/"+id),student,null);
        denied(post("/v2/exams/"+id+"/releases"),teacher,Map.of("level",2));
        ok(post("/exam/"+id+"/finish"),teacher,null);
        denied(post("/v2/exams/"+id+"/releases"),teacher,Map.of("level",1)); // essay still unmarked
        long essay=fixtures.essayAnswerId(id);
        ok(put("/exam/answer/"+essay+"/mark").param("score","3"),teacher,null);
        ok(post("/v2/exams/"+id+"/releases"),teacher,Map.of("level",1));
        denied(get("/grade/mine/"+id),student,null);
        ok(post("/v2/exams/"+id+"/releases"),teacher,Map.of("level",2));
        ok(post("/v2/exams/"+id+"/releases"),teacher,Map.of("level",2));
        assertEquals(3,ok(get("/grade/mine/"+id),student,null).path("totalScore").asInt());
        assertFalse(ok(get("/v2/learning/topics"),student,null).isEmpty());
        var session=ok(post("/v2/learning/sessions"),student,Map.of("topic","导数与微分","wrongOnly",true));
        String sid=session.path("id").asText(),item=session.path("items").get(0).path("id").asText();
        assertFalse(session.toString().contains("standard_answer"));
        denied(get("/v2/learning/sessions/"+sid),other,null);
        denied(post("/v2/learning/sessions/"+sid+"/answers"),other,Map.of("itemId",item,"answer","B"));
        denied(post("/v2/learning/sessions/"+sid+"/answers"),student,Map.of("itemId","unrelated","answer","B"));
        assertTrue(ok(post("/v2/learning/sessions/"+sid+"/answers"),student,Map.of("itemId",item,"answer","B")).has("answer"));
        assertFalse(ok(get("/v2/learning/history"),student,null).isEmpty());
        denied(put("/exam/answer/"+essay+"/mark").param("score","4"),teacher,null);
    }

    @Test void concurrentEntrySaveAndSubmitAreSerialized()throws Exception {
        long id=exam(List.of(question(1,"并发测试选择导数。","B")),1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var enter1=pool.submit(()->ok(post("/exam/"+id+"/enter"),student,null));
            var enter2=pool.submit(()->ok(post("/exam/"+id+"/enter"),student,null));
            long pq=enter1.get().path("questions").get(0).path("id").asLong();enter2.get();
            assertEquals(1,fixtures.attemptCount(id));
            var start=new CountDownLatch(1);
            var save=pool.submit(()->{start.await();return request(put("/exam/"+id+"/answer"),student,Map.of("paperQuestionId",pq,"answer","B","expectedVersion",0));});
            var submit=pool.submit(()->{start.await();return ok(post("/exam/"+id+"/submit"),student,null);});
            start.countDown();JsonNode saved=save.get();submit.get();
            var state=fixtures.answerState(id);
            assertEquals(3,((Number)state.get("status")).intValue());
            assertEquals(saved.path("code").asInt()==200?5:0,((Number)state.get("total_score")).intValue());
        }finally{pool.shutdownNow();}
    }

    @Test void automaticCollectionMarksObjectiveQuestionsWhenTeacherDisabledAutoMark()throws Exception {
        long id=exam(List.of(question(1,"人工客观题评分回归。","B")),0);
        ok(post("/exam/"+id+"/enter"),student,null);
        fixtures.setExamEnd(LocalDateTime.now().minusSeconds(1),id);
        answers.autoSubmitExpired(1000);answers.autoSubmitExpired(1000);
        long aid=fixtures.singleAnswerId(id);
        assertFalse(ok(get("/exam/"+id+"/subjective"),teacher,null).isEmpty());
        ok(put("/exam/answer/"+aid+"/mark").param("score","4"),teacher,null);
        var state=fixtures.attemptScores(id);
        assertEquals(3,((Number)state.get("status")).intValue());
        assertEquals(4,((Number)state.get("objective_score")).intValue());
        assertEquals(0,((Number)state.get("subjective_score")).intValue());
    }

    @Test void candidateVersionChecksAndPublicationAreAtomic()throws Exception {
        fixtures.insertCandidate();
        long id=fixtures.lastCandidateId();
        denied(post("/v2/curation/candidates/"+id+"/publish"),teacher,Map.of("expectedVersion",1));
        var body=new HashMap<String,Object>(Map.of("expectedVersion",1,"subjectId",subject,"questionType",1,"content","求导复核：函数 x² 的导数。","options",Map.of("A","x","B","2x","C","3x","D","4x"),"answer","B","analysis","幂函数求导","formulaConfirmed",true));
        var updated=ok(patch("/v2/curation/candidates/"+id),teacher,body);assertEquals(2,updated.path("revision").asInt());
        assertEquals(40901,request(patch("/v2/curation/candidates/"+id),teacher,body).path("code").asInt());
        long published=ok(post("/v2/curation/candidates/"+id+"/publish"),teacher,Map.of("expectedVersion",2)).asLong();
        assertEquals(published,ok(post("/v2/curation/candidates/"+id+"/publish"),teacher,Map.of("expectedVersion",2)).asLong());
        denied(patch("/v2/curation/candidates/"+id),teacher,new HashMap<>(body){{put("expectedVersion",2);}});
        denied(get("/v2/curation/candidates/"+id),student,null);
        assertEquals(2,fixtures.candidateEventCount(id));
    }

    @Test void taskLeaseRejectsSecondWorkerAndExpiredWorker() {
        fixtures.insertPendingCrawl();
        long id=fixtures.lastCrawlId();
        String first=leases.claim(id);assertNotNull(first);assertNull(leases.claim(id));
        // Lease eligibility is evaluated by PostgreSQL; inject expiry on that same clock.
        fixtures.expireCrawlLease(id);
        String second=leases.claim(id);assertNotNull(second);assertNotEquals(first,second);
        assertThrows(IllegalStateException.class,()->leases.fence(id,first));
        leases.fence(id,second);leases.release(id,second);
        fixtures.failCrawl(id);
    }

    @Test void invalidJudgmentAndEmptyTrailingBlankCannotEarnMarks() {
        assertFalse(AnswerJudge.correct(3,"错","随便写"));
        assertFalse(AnswerJudge.correct(3,"错",""));
        assertFalse(AnswerJudge.correct(4,"北京;上海","北京;上海;"));
        assertFalse(AnswerJudge.correct(4,"北京;","北京"));
    }

    @Test void failedAutomaticCollectionRollsBackStatusAndAllAnswerWrites() throws Exception {
        long id=exam(List.of(question(1,"事务回滚第一题。","B"),question(1,"事务回滚第二题。","B")),1);
        var paper=ok(post("/exam/"+id+"/enter"),student,null);
        long last=paper.path("questions").get(1).path("id").asLong();
        fixtures.setExamEnd(LocalDateTime.now().minusSeconds(1),id);
        fixtures.addFailure(last);
        try {
            answers.autoSubmitExpired(1000);answers.autoSubmitExpired(1000);
            assertEquals(1,fixtures.attemptStatus(id));
            assertEquals(0,fixtures.answerCount(id));
        } finally { fixtures.dropFailure(); }
        answers.autoSubmitExpired(1000);answers.autoSubmitExpired(1000);
        assertEquals(3,fixtures.attemptStatus(id));
        assertEquals(2,fixtures.answerCount(id));
    }

    @Autowired org.springframework.context.ApplicationContext beans;
    @Autowired com.zhikao.mapper.SysUserMapper users;
    @Autowired com.zhikao.mapper.ExamMapper exams;
    @Autowired com.zhikao.mapper.ExamStudentMapper attempts;
    @Autowired com.zhikao.mapper.PaperMapper papers;
    @Autowired com.zhikao.mapper.PaperQuestionMapper snapshots;
    @Autowired com.zhikao.mapper.QuestionMapper questions;
    @Autowired com.zhikao.mapper.SysExportTaskMapper exportTasks;
    @Autowired com.zhikao.mapper.ExportLeaseMapper exportLeases;
    @Autowired com.zhikao.modules.exam.ExportWorker exportWorker;
    @Autowired com.zhikao.modules.ai.VectorKnowledgeStore vectors;
    @Autowired com.zhikao.modules.ai.AiClient ai;
    @Autowired com.zhikao.cache.CacheService cache;
    com.zhikao.entity.SysUser user(String name){return users.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.zhikao.entity.SysUser>lambdaQuery().eq(com.zhikao.entity.SysUser::getUsername,name));}

    @Test void cloudLlmActuallyResponds(){
        assertTrue(ai.available());
        String answer=ai.chat("仅作连通性验证，直接回答。","请只输出 OK");
        assertTrue(answer.contains("OK"));
    }
    @Test void cloudEmbeddingAndQdrantRoundTrip(){
        assertTrue(vectors.enabled());
        var embedding=vectors.embed(List.of("导数描述函数的变化率。"));
        assertEquals(1024,embedding.get(0).size());
        try {
            String result=vectors.retrieve("HashMap 集合的实现和扩容","Java 程序设计");
            assertTrue(result.contains("HashMap"));
            assertTrue(vectors.collectionInfo().path("result").path("points_count").asInt()>0);
        } finally {vectors.removeTestCollection();}
    }
    @Test void actualRedisCounterExpiryAndIndependentClient() throws Exception {
        assertEquals("redis",cache.mode());
        String key="stage-a-"+UUID.randomUUID();
        assertEquals(1,cache.increment(key,java.time.Duration.ofSeconds(10)));
        assertEquals(2,cache.increment(key,java.time.Duration.ofSeconds(10)));
        Thread.sleep(10100);
        assertEquals(1,cache.increment(key,java.time.Duration.ofSeconds(2)));
        cache.delete(key);
        assertTrue(fixtures.databaseVersion().startsWith("PostgreSQL"));
        assertEquals(SCHEMA,fixtures.schema());
    }
    @Test void randomPaperSupportsPostgresOverlapShortageAndRollback() throws Exception {
        var one=question(1,"随机组卷 A "+UUID.randomUUID(),"B");
        var two=question(1,"随机组卷 B "+UUID.randomUUID(),"B");
        var three=question(1,"随机组卷 C "+UUID.randomUUID(),"B");
        long count=papers.selectCount(null);
        var rules=List.of(Map.of("type",1,"count",2,"score",5),Map.of("type",1,"count",1,"score",5));
        long id=ok(post("/paper/genRandom"),teacher,Map.of("paperName","随机验证","subjectId",subject,"rules",rules)).asLong();
        var qs=snapshots.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.zhikao.entity.PaperQuestion>lambdaQuery().eq(com.zhikao.entity.PaperQuestion::getPaperId,id));
        assertEquals(3,qs.size());assertEquals(3,qs.stream().map(com.zhikao.entity.PaperQuestion::getQuestionId).distinct().count());
        denied(post("/paper/genRandom"),teacher,Map.of("paperName","失败不留半卷","subjectId",subject,"rules",List.of(Map.of("type",1,"count",1),Map.of("type",5,"count",10000))));
        assertEquals(count+1,papers.selectCount(null));
        // The mapper excludes a non-contiguous existing ID list without string concatenation.
        assertFalse(questions.selectRandomIds(subject,1,null,List.of(one,two,three),100).stream().anyMatch(List.of(one,two,three)::contains));
    }
    @Test void aiToolAndHttpSharePublicationMatrix() throws Exception {
        var learner=user("student");
        var exam=com.zhikao.entity.Exam.builder().examName("发布矩阵专用").paperId(0L).classId(classId)
            .startTime(LocalDateTime.now().minusDays(2)).endTime(LocalDateTime.now().minusDays(1)).duration(30).published(1).releaseStatus(0).status(3).createBy("teacher").build();
        exams.insert(exam);
        var attempt=com.zhikao.entity.ExamStudent.builder().examId(exam.getId()).studentId(learner.getId()).status(3)
            .objectiveScore(new java.math.BigDecimal("73.25")).subjectiveScore(java.math.BigDecimal.ZERO).totalScore(new java.math.BigDecimal("73.25")).build();
        attempts.insert(attempt);
        var tool=new com.zhikao.modules.ai.agent.AiTools(com.zhikao.modules.ai.agent.AgentContext.of(learner.getId(),"student","验证学生","student"),
            beans.getBean(com.zhikao.modules.system.SubjectService.class),beans.getBean(com.zhikao.modules.system.SysClassService.class),beans.getBean(com.zhikao.modules.exam.QuestionService.class),beans.getBean(com.zhikao.modules.exam.PaperService.class),beans.getBean(com.zhikao.modules.exam.ExamService.class),beans.getBean(com.zhikao.modules.exam.GradeService.class),beans.getBean(com.zhikao.modules.ai.agent.KnowledgeBase.class),beans.getBean(com.zhikao.modules.ai.agent.StudentProfileService.class),exams,attempts,beans.getBean(com.zhikao.mapper.ExamAnswerMapper.class),questions,users,beans.getBean(com.zhikao.mapper.SysOperLogMapper.class),beans.getBean(com.zhikao.modules.file.FileStorageService.class),json);
        for(int release=0;release<=2;release++)for(int phase=-1;phase<=1;phase++){
            exam.setReleaseStatus(release);
            exam.setStartTime(LocalDateTime.now().plusHours(phase-1));
            if(phase==1)exam.setStartTime(LocalDateTime.now().plusMinutes(30));
            exam.setEndTime(LocalDateTime.now().plusHours(phase));
            if(phase==0)exam.setEndTime(LocalDateTime.now().plusMinutes(30));
            exams.updateById(exam);
            boolean visible=release>0 && phase<0;
            var toolOutput=json.readTree(tool.getStudentGrade(null));
            var grades=toolOutput.path("data").path("grades");
            boolean contains=false;for(var g:grades)if(g.path("examId").asLong()==exam.getId())contains=true;
            assertEquals(visible,contains,"AI release="+release+",phase="+phase);
            var http=ok(get("/grade/mine"),student,null);
            contains=false;for(var g:http)if(g.path("examId").asLong()==exam.getId())contains=true;
            assertEquals(visible,contains,"HTTP release="+release+",phase="+phase);
            assertEquals("denied",json.readTree(tool.getStudentGrade("student02")).path("status").asText());
        }
        exam.setReleaseStatus(2);exam.setEndTime(LocalDateTime.now().minusHours(1));exams.updateById(exam);
        attempt.setStatus(2);attempts.updateById(attempt);
        assertFalse(tool.getStudentGrade(null).contains("发布矩阵专用"));
        denied(get("/grade/mine/"+exam.getId()),other,null);
        denied(get("/grade/exam/"+exam.getId()),student,null);
    }
    @Test void temporaryPasswordIsRestrictedExpiresAndCannotBeReused() throws Exception {
        String name="temp_"+UUID.randomUUID().toString().substring(0,8);
        denied(post("/user"),admin,Map.of("username",name,"realName","口令验证","userType",2));
        ok(post("/user"),admin,Map.of("username",name,"realName","口令验证","userType",2,"password",EcsTestSupport.PASSWORD));
        String token=login(name);
        assertTrue(ok(get("/auth/info"),token,null).path("userInfo").path("passwordChangeRequired").asBoolean());
        denied(get("/question/page"),token,null);
        ok(post("/auth/password/change"),token,Map.of("oldPassword",EcsTestSupport.PASSWORD,"newPassword","ChangedStageA2026!"));
        denied(get("/auth/info"),token,null);
        denied(post("/auth/login"),null,Map.of("username",name,"password",EcsTestSupport.PASSWORD));
        String next=ok(post("/auth/login"),null,Map.of("username",name,"password","ChangedStageA2026!")).path("token").asText();
        ok(get("/question/page"),next,null);
        long uid=user(name).getId();
        String temporary=ok(put("/user/"+uid+"/password/reset"),admin,null).asText();
        assertTrue(temporary.length()>24);assertNotEquals(EcsTestSupport.PASSWORD,temporary);
        denied(get("/auth/info"),next,null);
        var update=new com.zhikao.entity.SysUser();update.setId(uid);update.setPasswordExpiresAt(LocalDateTime.now().minusMinutes(1));users.updateById(update);
        denied(post("/auth/login"),null,Map.of("username",name,"password",temporary));
        ok(delete("/user/"+uid),admin,null);
    }
    @Test void disableAndRoleChangeRevokeBothTokensAfterCommit() throws Exception {
        String name="revoke_"+UUID.randomUUID().toString().substring(0,8);
        // Fixtures provide fully activated accounts; temporary-password behaviour is tested separately.
        var u=com.zhikao.entity.SysUser.builder().username(name).realName("撤权验证").userType(2).status(1).passwordChangeRequired(0)
           .password(org.mindrot.jbcrypt.BCrypt.hashpw(EcsTestSupport.PASSWORD,org.mindrot.jbcrypt.BCrypt.gensalt())).build();users.insert(u);
        String a=login(name),b=login(name);assertNotEquals(a,b);
        ok(put("/user/"+u.getId()+"/status").param("status","0"),admin,null);
        denied(get("/auth/info"),a,null);denied(get("/auth/info"),b,null);
        var independent=new cn.dev33.satoken.stp.StpLogic("login");
        assertNotEquals(u.getId().toString(),String.valueOf(independent.getLoginIdByToken(a)));
        ok(put("/user/"+u.getId()+"/status").param("status","1"),admin,null);
        a=login(name);b=login(name);
        ok(post("/user"),admin,Map.of("id",u.getId(),"username",name,"realName","撤权验证","userType",3,"roleCode","student","classId",classId,"status",1));
        denied(get("/auth/info"),a,null);denied(get("/auth/info"),b,null);
        a=login(name);denied(get("/question/page"),a,null);
        ok(delete("/user/"+u.getId()),admin,null);denied(get("/auth/info"),a,null);
    }
    @Test void exportLeaseRecoversExpiredWorkAndRejectsStaleCompletion() throws Exception {
        var task=com.zhikao.entity.SysExportTask.builder().bizType("exam-grade").bizId(-1L).fileName("验证.xlsx").status(0).createBy("teacher").createTime(LocalDateTime.now()).build();exportTasks.insert(task);
        assertEquals(1,exportLeases.claim(task.getId(),"first"));assertEquals(0,exportLeases.claim(task.getId(),"second"));
        fixtures.expireExport(task.getId());
        assertEquals(1,exportLeases.claim(task.getId(),"second"));
        assertEquals(0,exportLeases.complete(task.getId(),"first",1,1L,null));
        fixtures.expireExport(task.getId());exportWorker.generate(task.getId());
        for(int i=0;i<60 && exportTasks.selectById(task.getId()).getStatus()==0;i++)Thread.sleep(100);
        assertEquals(2,exportTasks.selectById(task.getId()).getStatus());
        var exhausted=com.zhikao.entity.SysExportTask.builder().bizType("exam-grade").bizId(-1L).fileName("中断.xlsx").status(0).createBy("teacher").createTime(LocalDateTime.now()).build();exportTasks.insert(exhausted);
        for(int i=0;i<3;i++){assertEquals(1,exportLeases.claim(exhausted.getId(),"attempt"+i));fixtures.expireExport(exhausted.getId());}
        exportLeases.failExhausted();assertEquals(2,exportTasks.selectById(exhausted.getId()).getStatus());
    }

    @Test void sessionsAndFilesSurviveRestartAndRevocationReachesOtherJvm() throws Exception {
        int port;
        try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        java.nio.file.Path log=java.nio.file.Path.of("target/stage-a-secondary.log");
        var process=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),
            "-Dfile.encoding=UTF-8","-cp",System.getProperty("surefire.test.class.path"),"com.zhikao.ZkApplication",
            "--server.port="+port,"--spring.main.banner-mode=off","--zhikao.crawler.recovery-enabled=false","--zhikao.export.recovery-enabled=false",
            "--zhikao.exam.timeout-scan-initial-delay-ms=3600000","--zhikao.upload.dir=./target/"+SCHEMA+"/uploads");
        process.environment().put("ZHIKAO_DB_SCHEMA",SCHEMA);
        process.environment().put("ZHIKAO_REDIS_NAMESPACE",SCHEMA+":");
        process.redirectErrorStream(true).redirectOutput(log.toFile());
        var http=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
        Process app=process.start();
        try {
            waitHealthy(http,port,app);
            assertEquals(200,remote(http,port,"GET","/auth/info",teacher,null).path("code").asInt());
            var file=beans.getBean(com.zhikao.modules.file.FileStorageService.class).storeGenerated("Stage A persistent artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8),"stage-a.txt","exam-grade","teacher");
            app.destroy();if(!app.waitFor(20,TimeUnit.SECONDS))app.destroyForcibly().waitFor();
            app=process.start();waitHealthy(http,port,app);
            assertEquals(200,remote(http,port,"GET","/auth/info",teacher,null).path("code").asInt());
            var download=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/file/view/"+file.getId())).header("satoken",teacher).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,download.statusCode());assertEquals("Stage A persistent artifact",download.body());
            for(String change:List.of("disable","role","reset","delete","batch-disable","batch-delete")){
                String name="jvm_"+UUID.randomUUID().toString().substring(0,8);
                var u=com.zhikao.entity.SysUser.builder().username(name).realName("跨实例验证").userType(2).status(1).passwordChangeRequired(0)
                    .password(org.mindrot.jbcrypt.BCrypt.hashpw(EcsTestSupport.PASSWORD,org.mindrot.jbcrypt.BCrypt.gensalt())).build();users.insert(u);
                String local=login(name);
                String remote=remote(http,port,"POST","/auth/login",null,Map.of("username",name,"password",EcsTestSupport.PASSWORD)).path("data").path("token").asText();
                assertFalse(remote.isBlank());assertNotEquals(local,remote);
                switch(change){
                    case "disable" -> ok(put("/user/"+u.getId()+"/status").param("status","0"),admin,null);
                    case "role" -> ok(post("/user"),admin,Map.of("id",u.getId(),"username",name,"realName","跨实例验证","userType",3,"roleCode","student","classId",classId,"status",1));
                    case "reset" -> ok(put("/user/"+u.getId()+"/password/reset"),admin,null);
                    case "delete" -> ok(delete("/user/"+u.getId()),admin,null);
                    case "batch-disable" -> ok(put("/user/batch/status").param("status","0"),admin,Map.of("ids",List.of(u.getId())));
                    case "batch-delete" -> ok(delete("/user/batch"),admin,Map.of("ids",List.of(u.getId())));
                }
                denied(get("/auth/info"),local,null);
                assertNotEquals(200,remote(http,port,"GET","/auth/info",remote,null).path("code").asInt(),change);
            }
        } finally {app.destroy();if(!app.waitFor(20,TimeUnit.SECONDS))app.destroyForcibly().waitFor();}
    }
    private void waitHealthy(java.net.http.HttpClient http,int port,Process process)throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(150);
        while(System.nanoTime()<deadline){
            assertTrue(process.isAlive(),"Secondary JVM stopped; inspect target/stage-a-secondary.log");
            try {
                var response=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api/actuator/health")).timeout(java.time.Duration.ofSeconds(3)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()==200){
                    // The ready event follows the bootstrap runners; health may precede them.
                    if(java.nio.file.Files.readString(java.nio.file.Path.of("target/stage-a-secondary.log")).contains("Sa-Token"))return;
                }
            }catch(java.io.IOException ignored){}
            Thread.sleep(300);
        }
        fail("Secondary JVM readiness timeout; inspect target/stage-a-secondary.log");
    }
    private JsonNode remote(java.net.http.HttpClient http,int port,String method,String path,String token,Object body)throws Exception {
        var b=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+"/api"+path)).timeout(java.time.Duration.ofSeconds(45)).header("Content-Type","application/json");
        if(token!=null)b.header("satoken",token);
        b.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return json.readTree(http.send(b.build(),java.net.http.HttpResponse.BodyHandlers.ofString()).body());
    }

    @org.springframework.boot.test.mock.mockito.SpyBean
    com.zhikao.modules.exam.GradeServiceImpl gradeSpy;

    @Test void exportSubmissionReturnsBeforeWorkerFinishes() throws Exception {
        long id=exam(List.of(question(1,"异步导出验证 "+UUID.randomUUID(),"B")),1);
        var started=new CountDownLatch(1);var proceed=new CountDownLatch(1);
        var requestThread=Thread.currentThread();var workerThread=new java.util.concurrent.atomic.AtomicReference<Thread>();
        org.mockito.Mockito.doAnswer(invocation->{
            workerThread.set(Thread.currentThread());started.countDown();
            if(!proceed.await(20,TimeUnit.SECONDS))throw new IllegalStateException("Test gate timeout");
            return invocation.callRealMethod();
        }).when(gradeSpy).exportExamGrade(id);
        Long taskId=null;
        try {
            taskId=ok(post("/grade/exam/"+id+"/export/task"),teacher,null).path("id").asLong();
            assertTrue(started.await(10,TimeUnit.SECONDS));assertNotSame(requestThread,workerThread.get());
            assertTrue(workerThread.get().getName().startsWith("export-"));
            assertEquals(0,exportTasks.selectById(taskId).getStatus());
        } finally {proceed.countDown();}
        for(int i=0;i<100 && exportTasks.selectById(taskId).getStatus()==0;i++)Thread.sleep(100);
        assertEquals(1,exportTasks.selectById(taskId).getStatus());
        assertNotNull(exportTasks.selectById(taskId).getFileId());
        org.mockito.Mockito.reset(gradeSpy);
    }
    @Test void authorizationRollbackPreservesTokensButCommittedVersionRejectsStaleRedis() throws Exception {
        String token=login("student02");var u=user("student02");
        var tx=new org.springframework.transaction.support.TransactionTemplate(beans.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{
            beans.getBean(com.zhikao.modules.auth.SessionRevocation.class).afterCommit(u.getId());
            throw new IllegalStateException("rollback");
        }));
        ok(get("/auth/info"),token,null);
        users.advanceAuthVersion(u.getId()); // Simulates a crash after DB commit but before Redis cleanup.
        String fresh=login("student02");
        ok(get("/auth/info"),fresh,null);
        denied(get("/auth/info"),token,null);
    }
    @Test void uploadWordAndPdfReviewThenExamAndPracticeOnRealPostgres() throws Exception {
        byte[] wordBytes,pdfBytes;
        try(var word=new org.apache.poi.xwpf.usermodel.XWPFDocument();var bytes=new java.io.ByteArrayOutputStream()){
            for(String line:List.of("题干：函数 f(x)=x^2 的导数是什么？Stage A 文档全链路验证。","A. x","B. 2x","C. 3x","D. 4x","答案：B","解析：使用幂函数求导法则得到 2x。"))word.createParagraph().createRun().setText(line);
            word.write(bytes);wordBytes=bytes.toByteArray();
        }
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var bytes=new java.io.ByteArrayOutputStream()){
            var page=new org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page);
            try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)){
                stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);stream.setLeading(18);stream.newLineAtOffset(60,700);
                for(String line:List.of("1. What is the derivative of a constant function? Stage A PDF.","A. 0","B. 1","C. x","D. 2x","Answer: A")){stream.showText(line);stream.newLine();}stream.endText();
            }
            pdf.save(bytes);pdfBytes=bytes.toByteArray();
        }
        var upload=ok(multipart("/crawler/upload")
            .file(new org.springframework.mock.web.MockMultipartFile("files","stage-a-"+UUID.randomUUID()+".docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",wordBytes))
            .file(new org.springframework.mock.web.MockMultipartFile("files","stage-a-"+UUID.randomUUID()+".pdf","application/pdf",pdfBytes)),teacher,null);
        assertEquals(2,upload.path("stored").asInt());long taskId=upload.path("taskId").asLong();
        var taskMapper=beans.getBean(com.zhikao.mapper.CrawlTaskMapper.class);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(150);
        while(System.nanoTime()<deadline && Set.of("PENDING","RUNNING").contains(taskMapper.selectById(taskId).getStatus()))Thread.sleep(300);
        var task=taskMapper.selectById(taskId);assertEquals("SUCCESS",task.getStatus(),task.getError());assertEquals(2,task.getFetched());assertTrue(task.getStaged()>=2);
        var rawMapper=beans.getBean(com.zhikao.mapper.CrawlRawMapper.class);
        var raws=rawMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.zhikao.entity.CrawlRaw>lambdaQuery().eq(com.zhikao.entity.CrawlRaw::getTaskId,taskId));
        assertEquals(2,raws.size());
        for(var raw:raws){
            var response=mvc.perform(get("/v2/curation/documents/"+raw.getId()+"/source").header("satoken",teacher)).andReturn().getResponse();
            assertEquals(200,response.getStatus());
            assertArrayEquals(raw.getSourceUrl().endsWith(".pdf")?pdfBytes:wordBytes,response.getContentAsByteArray());
        }
        var candidateMapper=beans.getBean(com.zhikao.mapper.CrawlQuestionMapper.class);
        var candidates=candidateMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.zhikao.entity.CrawlQuestion>lambdaQuery().in(com.zhikao.entity.CrawlQuestion::getRawId,raws.stream().map(com.zhikao.entity.CrawlRaw::getId).toList()));
        var q=candidates.stream().filter(candidate->candidate.getContent().contains("Stage A 文档")).findFirst().orElseThrow();
        ok(patch("/v2/curation/candidates/"+q.getId()),teacher,Map.of("expectedVersion",q.getRevision(),"subjectId",subject,"questionType",1,"content",q.getContent(),"options",Map.of("A","x","B","2x","C","3x","D","4x"),"answer","B","analysis","求导后为 2x","difficulty",2,"knowledgeTags","Stage A 全链路","formulaConfirmed",true));
        long questionId=ok(post("/v2/curation/candidates/"+q.getId()+"/publish"),teacher,Map.of("expectedVersion",q.getRevision()+1)).asLong();
        long examId=exam(List.of(questionId),1);
        long snapshot=ok(post("/exam/"+examId+"/enter"),student,null).path("questions").get(0).path("id").asLong();
        ok(put("/exam/"+examId+"/answer"),student,Map.of("paperQuestionId",snapshot,"answer","B","expectedVersion",0));
        ok(post("/exam/"+examId+"/submit"),student,null);
        ok(post("/exam/"+examId+"/finish"),teacher,null);ok(post("/v2/exams/"+examId+"/releases"),teacher,Map.of("level",2));
        assertEquals(5,ok(get("/grade/mine/"+examId),student,null).path("totalScore").asInt());
        assertFalse(ok(post("/v2/learning/sessions"),student,Map.of("topic","Stage A 全链路","wrongOnly",false)).path("items").isEmpty());
    }

    @Test void lifecycleRacesPreserveRelationshipsOnPostgres() throws Exception {
        long template=exam(List.of(question(1,"生命周期并发验证 "+UUID.randomUUID(),"B")),1);
        var base=exams.selectById(template);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        int rounds=Integer.getInteger("zhikao.race-rounds",10);
        assertTrue(rounds>=1 && rounds<=1000);
        try {
            for(String operation:List.of("delete","finish","submit-finish","publish-finish"))for(int round=0;round<rounds;round++){
                var e=com.zhikao.entity.Exam.builder().examName("竞争 "+operation+" "+round).paperId(base.getPaperId()).classId(classId)
                    .duration(30).startTime(LocalDateTime.now().minusMinutes(1)).endTime(LocalDateTime.now().plusHours(1)).status(1)
                    .published(operation.equals("publish-finish")?0:1).releaseStatus(0).autoMark(1).createBy("teacher").build();exams.insert(e);
                long id=e.getId();
                if(operation.equals("submit-finish"))ok(post("/exam/"+id+"/enter"),student,null);
                CyclicBarrier barrier=new CyclicBarrier(2);
                Future<JsonNode> first=pool.submit(()->{barrier.await();return switch(operation){
                    case "publish-finish" -> request(post("/exam/"+id+"/publish"),teacher,null);
                    case "submit-finish" -> request(post("/exam/"+id+"/submit"),student,null);
                    default -> request(post("/exam/"+id+"/enter"),student,null);
                };});
                Future<JsonNode> second=pool.submit(()->{barrier.await();return operation.equals("delete")?request(delete("/exam/"+id),teacher,null):request(post("/exam/"+id+"/finish"),teacher,null);});
                var a=first.get(90,TimeUnit.SECONDS);var b=second.get(90,TimeUnit.SECONDS);
                assertNotEquals(50000,a.path("code").asInt(),a.toString());assertNotEquals(50000,b.path("code").asInt(),b.toString());
                var remaining=exams.selectById(id);int count=fixtures.attemptCount(id);
                if(operation.equals("delete")){
                    assertTrue((remaining==null && count==0) || (remaining!=null && count==1));
                    assertTrue(a.path("code").asInt()==200 || b.path("code").asInt()==200);
                }else {
                    assertNotNull(remaining);assertFalse(remaining.getEndTime().isAfter(LocalDateTime.now()));
                    assertEquals(200,b.path("code").asInt());
                    if(operation.equals("submit-finish")){assertEquals(200,a.path("code").asInt());assertEquals(3,fixtures.attemptStatus(id));}
                    else if(operation.equals("finish"))assertEquals(a.path("code").asInt()==200?1:0,count);
                    else assertEquals(a.path("code").asInt()==200?1:0,remaining.getPublished());
                }
            }
        } finally {pool.shutdownNow();}
    }

}
