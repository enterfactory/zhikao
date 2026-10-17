package com.zhikao.modules.workflow;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.*;
import com.zhikao.entity.*;
import com.zhikao.mapper.*;
import com.zhikao.modules.crawler.*;
import com.zhikao.modules.file.*;
import com.zhikao.modules.notification.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"zhikao.notification.delivery-enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("ecs") @Import(EcsTestSupport.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class StageBIntegrationTest {
 static final String SCHEMA=EcsTestSupport.schema("stageb");
 @DynamicPropertySource static void cloud(DynamicPropertyRegistry r){EcsTestSupport.configure(r,SCHEMA);}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired EcsTestSupport support;
 @Autowired SysFileMapper files; @Autowired FileBlobMapper blobs; @Autowired FileBlobStore store;
 @Autowired CrawlRawMapper raws; @Autowired CrawlQuestionMapper candidates;
 @Autowired NotificationService notifications; @Autowired NotificationDelivery delivery;
 @SpyBean NotificationOutboxMapper outbox;
 @Autowired SysNotificationMapper messages; @Autowired PlatformTransactionManager manager;
 @Autowired SysUserMapper users; @Autowired KnowledgePointMapper knowledge;
 @Autowired PaperQuestionMapper snapshots; @Autowired ExamMapper exams; @Autowired ExamRosterMapper roster;
 @Autowired StageAFixtureMapper fixtures; @Autowired DocumentVersions versions;
 @Autowired PaperMapper papers; @Autowired QuestionMapper questions; @Autowired QuestionKnowledgeMapper links;
 @Autowired PaperKnowledgeMapper paperKnowledge; @Autowired PracticeMapper practice;
 @Autowired ExamStudentMapper attempts; @Autowired ExamAnswerMapper answers;
 @Autowired com.zhikao.modules.knowledge.KnowledgeBindings bindings;
 @Autowired org.springframework.data.redis.connection.RedisConnectionFactory redis;
 String teacher,admin,student;long subject,classId;
 @BeforeAll void prepare()throws Exception{
  support.seed();subject=fixtures.firstSubjectId();classId=fixtures.studentClassId();
  // These cases do not revoke credentials; reuse real Redis sessions instead of
  // making every independent Stage B case repeat three expensive WAN logins.
  teacher=login("teacher");admin=login("admin");student=login("student");
 }
 String login(String name)throws Exception{return ok(post("/auth/login"),null,Map.of("username",name,"password",EcsTestSupport.PASSWORD)).path("token").asText();}
 JsonNode request(MockHttpServletRequestBuilder b,String token,Object body)throws Exception{
  if(token!=null)b.header("satoken",token);if(body!=null)b.contentType("application/json").content(json.writeValueAsBytes(body));
  return json.readTree(mvc.perform(b).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
 }
 JsonNode ok(MockHttpServletRequestBuilder b,String token,Object body)throws Exception{var r=request(b,token,body);assertEquals(200,r.path("code").asInt(),r.toString());return r.path("data");}
 JsonNode upload(String token,String name,byte[] bytes)throws Exception{return ok(multipart("/file/upload").file(new MockMultipartFile("file",name,"text/html",bytes)).param("category","other"),token,null);}
 @Test void identicalBytesHaveDifferentOwnersAndDeletingOnePreservesOther()throws Exception{
  byte[] bytes=("Stage B shared reference "+UUID.randomUUID()).getBytes();
  var a=upload(teacher,"shared.txt",bytes);var b=upload(admin,"shared.txt",bytes);
  assertNotEquals(a.path("id").asLong(),b.path("id").asLong());
  var first=files.selectById(a.path("id").asLong());var second=files.selectById(b.path("id").asLong());
  assertEquals(first.getBlobId(),second.getBlobId());
  assertEquals(40301,request(get("/file/view/"+second.getId()),teacher,null).path("code").asInt());
  ok(delete("/file/"+first.getId()),teacher,null);
  var response=mvc.perform(get("/file/download/"+second.getId()).header("satoken",admin)).andReturn().getResponse();
  assertArrayEquals(bytes,response.getContentAsByteArray());assertEquals("nosniff",response.getHeader("X-Content-Type-Options"));
 }
 @Test void sameOwnerRetryIsIdempotentAndClientMimeIsIgnored()throws Exception{
  byte[] bytes=("Stage B idempotent "+UUID.randomUUID()).getBytes();
  var a=upload(teacher,"retry.txt",bytes);var b=upload(teacher,"retry.txt",bytes);assertEquals(a.path("id"),b.path("id"));
  assertEquals("text/plain;charset=UTF-8",files.selectById(a.path("id").asLong()).getContentType());
 }
 @Test void forgedPdfAndActiveTextAreRejected()throws Exception{
  var r=request(multipart("/file/upload").file(new MockMultipartFile("file","fake.pdf","application/pdf","<script>x</script>".getBytes())).param("category","paper"),teacher,null);
  assertNotEquals(200,r.path("code").asInt());
  assertThrows(com.zhikao.common.BusinessException.class,()->new FileTypeVerifier().verify("<html>bad</html>".getBytes(),"txt"));
 }
 @Test void rollbackRemovesNewBlobAndBytes(){
  long before=blobs.selectCount(null);final Path[] saved={null};
  assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).executeWithoutResult(s->{
   var blob=store.attach(UUID.randomUUID().toString().getBytes(),"txt","text/plain;charset=UTF-8");saved[0]=store.path(blob.getAccessPath());throw new IllegalStateException("injected reference write failure");
  }));
  assertEquals(before,blobs.selectCount(null));assertFalse(Files.exists(saved[0]));
 }
 @Test void concurrentBlobCreationUsesOneSharedRow()throws Exception{
  byte[] bytes=UUID.randomUUID().toString().getBytes();var pool=Executors.newFixedThreadPool(2);
  try{Callable<Long> action=()->new TransactionTemplate(manager).execute(tx->store.attach(bytes,"txt","text/plain;charset=UTF-8").getId());
   var results=pool.invokeAll(List.of(action,action));assertEquals(results.get(0).get(),results.get(1).get());
  }finally{pool.shutdownNow();}
 }
 @Test void documentSnapshotSurvivesOriginalChangeAndFingerprintChangesIdentity()throws Exception{
  Path file=Path.of("target",SCHEMA,"ingest","version.txt").toAbsolutePath().normalize();Files.createDirectories(file.getParent());Files.writeString(file,"first original");
  String first=versions.contentHash(file);Path frozen=versions.capture(file);Files.writeString(file,"second original");
  assertNotEquals(first,versions.contentHash(file));assertEquals("first original",Files.readString(frozen));
  assertNotEquals(versions.identity(first,subject),versions.identity(first,subject+1));
 }
 @Test void outboxRollbackAndRecipientFailureAreAtomicAndReplayIsIdempotent(){
  long count=outbox.selectCount(null);var ids=users.selectList(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUserType,3)).stream().map(SysUser::getId).sorted().toList();
  assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).executeWithoutResult(tx->{notifications.send(ids,NotificationTypeEnum.EXAM_PUBLISH,"rollback","body","stageb",991L);throw new IllegalStateException("abort business transaction");}));
  assertEquals(count,outbox.selectCount(null));
  notifications.send(ids,NotificationTypeEnum.EXAM_PUBLISH,"delivery","body","stageb",992L);
  var event=outbox.selectOne(Wrappers.<NotificationOutbox>lambdaQuery().eq(NotificationOutbox::getBizId,992L));
  org.mockito.Mockito.doThrow(new IllegalStateException("second recipient fails")).when(outbox).deliver(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(ids.get(1)));
  delivery.deliverOne(event.getId());
  assertEquals(0,messages.selectCount(Wrappers.<SysNotification>lambdaQuery().eq(SysNotification::getBizId,992L)));
  org.mockito.Mockito.reset(outbox);
  outbox.update(null,Wrappers.<NotificationOutbox>lambdaUpdate().eq(NotificationOutbox::getId,event.getId()).set(NotificationOutbox::getNextAttemptAt,LocalDateTime.of(2000,1,1,0,0)));
  delivery.deliverOne(event.getId());delivery.deliverOne(event.getId());
  assertEquals(ids.size(),messages.selectCount(Wrappers.<SysNotification>lambdaQuery().eq(SysNotification::getBizId,992L)));
 }
 @Test void candidateEditingIsVersionedAndBothPublishEntrypointsRequireConfirmation()throws Exception{
  var q=CrawlQuestion.builder().rawId(991L).stage("TAGGED").revision(1).subjectId(subject).questionType(3)
   .content("这是待复核的标准判断题目").answer("true").difficulty(2).formulaConfirmed(0).build();candidates.insert(q);
  assertNotEquals(200,request(post("/v2/curation/candidates/"+q.getId()+"/publish"),teacher,Map.of("expectedVersion",1)).path("code").asInt());
  var legacy=ok(post("/crawler/question/review"),teacher,Map.of("ids",List.of(q.getId()),"action","PROMOTE"));assertEquals(0,legacy.path("successCount").asInt());assertEquals(1,legacy.path("failCount").asInt());
  var body=Map.of("expectedVersion",1,"subjectId",subject,"questionType",3,"content","这是经过原文复核的判断题目","answer","true","formulaConfirmed",true);
  ok(patch("/v2/curation/candidates/"+q.getId()),teacher,body);
  assertEquals(40901,request(patch("/v2/curation/candidates/"+q.getId()),teacher,body).path("code").asInt());
  long id=ok(post("/v2/curation/candidates/"+q.getId()+"/publish"),teacher,Map.of("expectedVersion",2)).asLong();
  assertEquals(id,ok(post("/v2/curation/candidates/"+q.getId()+"/publish"),teacher,Map.of("expectedVersion",2)).asLong());
  assertEquals(2,ok(get("/v2/curation/candidates/"+q.getId()+"/events"),teacher,null).size());
 }
 @Test void qualityRequiresTwoDistinctReviewersAndKeepsHoldoutFamilySeparate()throws Exception{
  var raw=CrawlRaw.builder().taskId(991L).sourceUrl("quality.txt").sourceName("quality.txt").dedupHash(UUID.randomUUID().toString()).content("独立标注正文").build();raws.insert(raw);
  var body=Map.of("family","stageb-owned","split","development","licenseNote","原创受控测试资料，无个人信息","referenceText","独立标注正文",
   "questions",List.of(Map.of("question_number","1","question_type",3,"content","独立标准判断题目","answer","true")));
  long id=ok(put("/v2/quality/documents/"+raw.getId()+"/annotation"),teacher,body).asLong();
  ok(post("/v2/quality/annotations/"+id+"/sign"),teacher,Map.of("expectedVersion",1));
  assertNotEquals(200,request(post("/v2/quality/annotations/"+id+"/sign"),teacher,Map.of("expectedVersion",2)).path("code").asInt());
  ok(post("/v2/quality/annotations/"+id+"/sign"),admin,Map.of("expectedVersion",2));
  var report=ok(post("/v2/quality/runs"),teacher,Map.of("split","development"));assertEquals("COMPLETED",report.path("status").asText());
  var edit=new HashMap<String,Object>(body);edit.put("revision",3);
  edit.put("split","holdout");assertNotEquals(200,request(put("/v2/quality/documents/"+raw.getId()+"/annotation"),teacher,edit).path("code").asInt());edit.put("split","development");
  ok(put("/v2/quality/documents/"+raw.getId()+"/annotation"),teacher,edit);
  var annotations=ok(get("/v2/quality/annotations"),teacher,null);
  for(var annotation:annotations)if(annotation.path("id").asLong()==id){assertEquals("DRAFT",annotation.path("status").asText());assertTrue(annotation.path("firstReviewer").isNull());assertTrue(annotation.path("secondReviewer").isNull());}
  assertEquals(40301,request(get("/v2/quality/summary"),student,null).path("code").asInt());
 }
 @AfterAll void retainEvidence(){System.out.println("STAGE_B_RETAINED_SCHEMA="+SCHEMA);}

 @Test void businessImageReferencesProtectFileAndGarbageCollectionWaitsForLastReference()throws Exception{
  var a=upload(teacher,"带 空格的文件.txt",UUID.randomUUID().toString().getBytes());long id=a.path("id").asLong();
  var q=Question.builder().subjectId(subject).type(3).content("文件生命周期测试").answer("true")
   .analysis("<img src='/api/file/view/"+id+"'>").difficulty(2).build();questions.insert(q);
  assertNotEquals(200,request(delete("/file/"+id),teacher,null).path("code").asInt());
  questions.deleteById(q.getId());var ref=files.selectById(id);var blob=blobs.selectById(ref.getBlobId());
  ok(delete("/file/"+id),teacher,null);assertNotNull(blobs.selectById(blob.getId()).getGarbageAfter());
  assertTrue(Files.exists(store.path(blob.getAccessPath())));
  blobs.update(null,Wrappers.<FileBlob>lambdaUpdate().eq(FileBlob::getId,blob.getId()).set(FileBlob::getGarbageAfter,LocalDateTime.of(2000,1,1,0,0)));
  store.collectGarbage();assertNull(blobs.selectById(blob.getId()));assertFalse(Files.exists(store.path(blob.getAccessPath())));
  assertThrows(com.zhikao.common.BusinessException.class,()->new TransactionTemplate(manager).executeWithoutResult(tx->store.lockReferences("<img src='/api/file/view/"+id+"'>")));
 }
 @Test void publicationRosterSurvivesTransferAndExcludesLaterJoiner()throws Exception{
  var e=Exam.builder().examName("阶段B冻结名单").paperId(0L).classId(classId).startTime(LocalDateTime.now()).endTime(LocalDateTime.now().plusHours(1)).build();exams.insert(e);
  roster.freeze(e.getId());long count=roster.count(e.getId());
  var learner=users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,"student"));
  try{users.update(null,Wrappers.<SysUser>lambdaUpdate().eq(SysUser::getId,learner.getId()).set(SysUser::getClassId,null));
   assertEquals(count,roster.count(e.getId()));assertEquals(1,roster.contains(e.getId(),learner.getId()));
   var later=SysUser.builder().username("later"+UUID.randomUUID().toString().substring(0,8)).password("unused").userType(3).classId(classId).status(1).build();users.insert(later);
   assertEquals(0,roster.contains(e.getId(),later.getId()));
  }finally{users.update(null,Wrappers.<SysUser>lambdaUpdate().eq(SysUser::getId,learner.getId()).set(SysUser::getClassId,classId));}
 }
 @Test void knowledgeSnapshotsKeepIdsAndSplitTopicsAfterBankChanges(){
  new TransactionTemplate(manager).executeWithoutResult(tx->{
   var q=Question.builder().subjectId(subject).type(3).content("多知识点快照题").answer("true").knowledgePoint("B测试甲，B测试乙;B测试甲").difficulty(2).build();questions.insert(q);bindings.bind(q);
   var kp=links.selectList(Wrappers.<QuestionKnowledge>lambdaQuery().eq(QuestionKnowledge::getQuestionId,q.getId()));assertEquals(2,kp.size());
   var paper=Paper.builder().paperName("知识快照测试").subjectId(subject).genType(1).status(1).build();papers.insert(paper);
   var pq=PaperQuestion.builder().paperId(paper.getId()).questionId(q.getId()).sort(1).type(3).content(q.getContent()).answer("true").knowledgePoint(q.getKnowledgePoint()).score(java.math.BigDecimal.TEN).build();snapshots.insert(pq);assertEquals(2,paperKnowledge.snapshot(pq.getId(),q.getId()));
   var e=Exam.builder().examName("知识快照测试").paperId(paper.getId()).classId(classId).published(1).releaseStatus(2).startTime(LocalDateTime.of(2020,1,1,0,0)).endTime(LocalDateTime.of(2020,1,2,0,0)).build();exams.insert(e);
   var learner=users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,"student"));
   var attempt=ExamStudent.builder().examId(e.getId()).studentId(learner.getId()).status(3).totalScore(java.math.BigDecimal.ZERO).build();attempts.insert(attempt);
   answers.insert(ExamAnswer.builder().examStudentId(attempt.getId()).paperQuestionId(pq.getId()).answer("false").marked(1).correct(0).score(java.math.BigDecimal.ZERO).build());
   for(var link:kp){var point=knowledge.selectById(link.getKpId());point.setName("更名"+point.getId());knowledge.updateById(point);}
   questions.deleteById(q.getId());
   var topics=practice.topics(learner.getId());assertTrue(topics.stream().anyMatch(t->"B测试甲".equals(t.get("topic"))));assertTrue(topics.stream().anyMatch(t->"B测试乙".equals(t.get("topic"))));
   assertEquals(1,practice.available(learner.getId(),"kp:"+kp.get(0).getKpId(),true).size());
   e.setId(null);e.setExamName("同卷第二次考试");exams.insert(e);attempt.setId(null);attempt.setExamId(e.getId());attempts.insert(attempt);
   answers.insert(ExamAnswer.builder().examStudentId(attempt.getId()).paperQuestionId(pq.getId()).answer("false").marked(1).correct(0).score(java.math.BigDecimal.ZERO).build());
   assertEquals(1,practice.available(learner.getId(),"kp:"+kp.get(0).getKpId(),true).size(),"同一快照在多场考试出现仍只生成一道练习");
  });
 }
 @Test void sameFamilyCannotCrossHoldoutBoundary()throws Exception{
  String family="owned-"+UUID.randomUUID();
  var a=CrawlRaw.builder().taskId(0L).sourceUrl("a.txt").dedupHash(UUID.randomUUID().toString()).content("a").build();raws.insert(a);
  var b=CrawlRaw.builder().taskId(0L).sourceUrl("b.txt").dedupHash(UUID.randomUUID().toString()).content("b").build();raws.insert(b);
  var body=new HashMap<String,Object>(Map.of("family",family,"split","development","licenseNote","原创测试","questions",List.of(Map.of("question_number","1","question_type",3,"content","测试题目","answer","true"))));
  ok(put("/v2/quality/documents/"+a.getId()+"/annotation"),teacher,body);body.put("split","holdout");
  assertNotEquals(200,request(put("/v2/quality/documents/"+b.getId()+"/annotation"),teacher,body).path("code").asInt());
 }
}
