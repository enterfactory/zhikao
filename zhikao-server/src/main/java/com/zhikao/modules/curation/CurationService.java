package com.zhikao.modules.curation;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.*;
import com.zhikao.entity.*;
import com.zhikao.mapper.*;
import com.zhikao.modules.crawler.promote.Promoter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** One state machine for the legacy bulk review endpoint and the versioned review workspace. */
@Service @RequiredArgsConstructor
public class CurationService {
 private final CrawlQuestionMapper candidates;
 private final CrawlRawMapper documents;
 private final CandidateEventMapper events;
 private final CrawlTaskMapper tasks;
 private final Promoter promoter;
 private final ObjectMapper json;
 private final com.zhikao.modules.file.FileBlobStore fileBlobs;

 @Transactional public void start(Long id) {
  candidates.update(null,Wrappers.<CrawlQuestion>lambdaUpdate().eq(CrawlQuestion::getId,id)
   .isNull(CrawlQuestion::getReviewStartedAt).isNull(CrawlQuestion::getPromotedQuestionId)
   .set(CrawlQuestion::getReviewStartedAt,LocalDateTime.now()));
 }
 @Transactional public void update(Long id,CandidateUpdate body) {
  CrawlQuestion q=lock(id,body.getExpectedVersion());
  if(q.getPromotedQuestionId()!=null || "REJECTED".equals(q.getStage()))throw new BusinessException("已定论版本不可改写，请创建新修订");
  q.setSubjectId(body.getSubjectId());q.setQuestionType(body.getQuestionType());q.setContent(body.getContent());
  q.setOptions(JsonHelper.toJson(body.getOptions()==null?Map.of():body.getOptions()));q.setAnswer(body.getAnswer());
  q.setAnalysis(body.getAnalysis()==null?"":body.getAnalysis());q.setDifficulty(body.getDifficulty()==null?3:body.getDifficulty());
  q.setKnowledgeTags(body.getKnowledgeTags());q.setFormulaConfirmed(body.isFormulaConfirmed()?1:0);
  boolean pageChanged=body.getSourcePage()!=null && !Objects.equals(body.getSourcePage(),q.getSourcePage());
  if(pageChanged) {
   CrawlRaw document=documents.selectById(q.getRawId());
   if(document==null || document.getPageCount()==null || body.getSourcePage()<1 || body.getSourcePage()>document.getPageCount())
    throw new BusinessException("来源页码超出原文范围");
   q.setSourcePage(body.getSourcePage());q.setLocatorMethod("MANUAL_PAGE");q.setSourceBbox(null);
  }
  q.setRevision(body.getExpectedVersion()+1);q.setStage("TAGGED");
  fileBlobs.lockReferences(q.getContent(),q.getOptions(),q.getAnswer(),q.getAnalysis());
  candidates.updateById(q);
  if(pageChanged)candidates.update(null,Wrappers.<CrawlQuestion>lambdaUpdate().eq(CrawlQuestion::getId,id).set(CrawlQuestion::getSourceBbox,null));
  event(q,"EDIT");
 }
 @Transactional public Long publish(Long id,Integer version,String note,Long fallbackSubject) {
  CrawlQuestion q=lock(id,version);
  if(q.getPromotedQuestionId()!=null)return q.getPromotedQuestionId();
  if("REJECTED".equals(q.getStage()))throw new BusinessException("已驳回版本不能直接入库");
  if(!Integer.valueOf(1).equals(q.getFormulaConfirmed()))throw new BusinessException("请先核对原文与公式并保存复核结果");
  if(q.getSubjectId()==null)q.setSubjectId(fallbackSubject);
  q.setReviewNote(note==null?"审核通过，已入正式题库":note);
  finishTimer(q);
  Long questionId=promoter.promote(q);event(q,"PUBLISH");return questionId;
 }
 @Transactional public void reject(Long id,Integer version,String note) {
  CrawlQuestion q=lock(id,version);
  if(q.getPromotedQuestionId()!=null)throw new BusinessException("已入库题目不能驳回");
  if("REJECTED".equals(q.getStage()))return;
  q.setStage("REJECTED");q.setReviewNote(note==null?"审核退回":note);q.setRevision(q.getRevision()+1);
  finishTimer(q);candidates.updateById(q);event(q,"REJECT");
 }
 @Transactional public void retry(Long id) {
  int changed=tasks.update(null,Wrappers.<CrawlTask>lambdaUpdate().eq(CrawlTask::getId,id).eq(CrawlTask::getStatus,"FAILED")
    .set(CrawlTask::getStatus,"PENDING").set(CrawlTask::getAttempts,0).set(CrawlTask::getLeaseOwner,null)
    .set(CrawlTask::getLeaseUntil,null).set(CrawlTask::getError,null).set(CrawlTask::getFinishedAt,null));
  if(changed==0)throw new BusinessException("只有失败的任务可以重试");
 }
 private CrawlQuestion lock(Long id,Integer version) {
  CrawlQuestion q=candidates.selectOne(Wrappers.<CrawlQuestion>lambdaQuery().eq(CrawlQuestion::getId,id).last("FOR UPDATE"));
  if(q==null)throw new BusinessException("候选题不存在");
  if(version==null || !version.equals(q.getRevision()))throw new BusinessException(40901,"题目已被修改，请刷新后重试");
  return q;
 }
 private void finishTimer(CrawlQuestion q){if(q.getReviewStartedAt()!=null)q.setReviewMilliseconds(Math.max(0,Duration.between(q.getReviewStartedAt(),LocalDateTime.now()).toMillis()));}
 private void event(CrawlQuestion q,String action) {
  try {events.append(UUID.randomUUID().toString(),q.getId(),q.getRevision(),action,LoginUtil.username(),json.writeValueAsString(q));}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Cannot serialize review revision",e);}
 }
}
