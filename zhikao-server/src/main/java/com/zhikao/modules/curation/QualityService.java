package com.zhikao.modules.curation;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.*;
import com.zhikao.common.*;
import com.zhikao.entity.*;
import com.zhikao.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** Human labels remain separate from extraction predictions. Signing requires two different accounts. */
@Service @RequiredArgsConstructor
public class QualityService {
 private final QualityAnnotationMapper labels;
 private final QualityEvaluationRunMapper runs;
 private final CrawlRawMapper documents;
 private final CrawlQuestionMapper candidates;
 private final FileBlobMapper locks;
 private final ObjectMapper json;
 public List<QualityAnnotation> annotations(){return labels.selectList(Wrappers.<QualityAnnotation>lambdaQuery().orderByDesc(QualityAnnotation::getId).last("LIMIT 200"));}
 public List<QualityEvaluationRun> runs(){return runs.selectList(Wrappers.<QualityEvaluationRun>lambdaQuery().orderByDesc(QualityEvaluationRun::getId).last("LIMIT 30"));}
 public Map<String,Object> summary() {
  long verified=labels.selectCount(Wrappers.<QualityAnnotation>lambdaQuery().eq(QualityAnnotation::getStatus,"VERIFIED"));
  return Map.of("documents",documents.selectCount(null),"annotated",labels.selectCount(null),"verified",verified,
    "benchmarkReady",verified>0,"requiredReviewers",2,"qualityBasis","仅双人签核资料进入真实质量指标；自动质量分不等于准确率");
 }
 @Transactional public Long save(Long rawId,Integer revision,JsonNode body) {
  if(documents.selectById(rawId)==null)throw new BusinessException("资料不存在");
  locks.lockContent("annotation:"+rawId);
  String family=body.path("family").asText("").trim(), split=body.path("split").asText("");
  if(family.isEmpty() || family.length()>255 || !Set.of("development","holdout").contains(split))throw new BusinessException("请填写来源家族并选择开发集或保留集");
  if(body.path("licenseNote").asText("").length()>5000 || body.path("referenceText").asText("").length()>200000)throw new BusinessException("许可说明或标准全文超过长度限制");
  if(body.path("licenseNote").asText("").isBlank())throw new BusinessException("请记录资料许可与脱敏说明");
  // A family and identical content can never cross the development/holdout boundary.
  locks.lockContent("quality-dataset-split");
  var doc=documents.selectById(rawId);
  for(var other:labels.selectList(null))if(!other.getRawId().equals(rawId) && !other.getSplit().equals(split)) {
   var otherDoc=documents.selectById(other.getRawId());
   if(other.getFamily().equals(family) || (doc.getContentHash()!=null && otherDoc!=null && doc.getContentHash().equals(otherDoc.getContentHash())))
    throw new BusinessException("同一来源家族或相同内容不能同时分入开发集与保留集");
  }
  JsonNode questions=body.path("questions");
  if(!questions.isArray() || questions.isEmpty() || questions.size()>300)throw new BusinessException("请标注1至300道标准题目");
  if(questions.toString().length()>2000000)throw new BusinessException("整份标注不能超过200万字符");
  Set<String> numbers=new HashSet<>();
  for(JsonNode q:questions){
   if(q.has("formulas")) {
    if(!q.path("formulas").isArray()||q.path("formulas").size()>100)throw new BusinessException("公式必须为数组且每题最多100条");
    for(JsonNode f:q.path("formulas"))if(!f.path("latex").isTextual()||f.path("latex").asText().isBlank()||f.path("latex").asText().length()>5000)throw new BusinessException("请填写有效的LaTeX公式");
   }
   if(q.has("options")&&!q.path("options").isObject())throw new BusinessException("选项必须为键值对象");
   if(q.path("question_number").asText("").isBlank() || !numbers.add(q.path("page").asText()+":"+q.path("question_number").asText()))throw new BusinessException("标准题号不能为空或在同页重复");
   if(q.path("content").asText("").isBlank() || q.path("answer").asText("").isBlank() || q.path("question_type").asInt()<1 || q.path("question_type").asInt()>5)throw new BusinessException("标准题目必须填写题干、答案和题型");
   if(q.path("content").asText().length()>50000 || q.path("answer").asText().length()>20000)throw new BusinessException("标注题目字段过长");
   if(!q.path("page").isMissingNode() && !q.path("page").isNull() && (q.path("page").asInt()<1 || (doc.getPageCount()!=null && q.path("page").asInt()>doc.getPageCount())))throw new BusinessException("标注页码超出原文范围");
  }
  // Signing and editing must serialize on the same row before checking its revision.
  var existing=labels.selectOne(Wrappers.<QualityAnnotation>lambdaQuery().eq(QualityAnnotation::getRawId,rawId).last("FOR UPDATE"));
  if(existing!=null && !Objects.equals(existing.getRevision(),revision))throw new BusinessException(40901,"标注已变化，请刷新");
  if(existing!=null && Integer.valueOf(1).equals(existing.getDatasetFrozen()) && (!existing.getFamily().equals(family)||!existing.getSplit().equals(split)))
   throw new BusinessException("已签核过的资料已冻结来源家族与集合归属，不能在开发集和保留集之间移动");
  var row=existing==null?new QualityAnnotation():existing;
  row.setRawId(rawId);row.setFamily(family);row.setSplit(split);row.setLicenseNote(body.path("licenseNote").asText());
  row.setReferenceText(body.path("referenceText").asText(""));row.setQuestionsJson(questions.toString());row.setStatus("DRAFT");
  row.setRevision(existing==null?1:existing.getRevision()+1);row.setUpdatedAt(LocalDateTime.now());
  if(existing==null)labels.insert(row);
  else {
   row.setFirstReviewer(null);row.setSecondReviewer(null);
   labels.update(row,Wrappers.<QualityAnnotation>lambdaUpdate().eq(QualityAnnotation::getId,row.getId())
    .set(QualityAnnotation::getFirstReviewer,null).set(QualityAnnotation::getSecondReviewer,null));
  }
  return row.getId();
 }
 @Transactional public void sign(Long id,Integer version) {
  var row=labels.selectOne(Wrappers.<QualityAnnotation>lambdaQuery().eq(QualityAnnotation::getId,id).last("FOR UPDATE"));
  if(row==null || !Objects.equals(row.getRevision(),version))throw new BusinessException(40901,"标注不存在或版本已变化");
  String actor=LoginUtil.username();
  if("VERIFIED".equals(row.getStatus()))return;
  row.setDatasetFrozen(1);
  if(row.getFirstReviewer()==null){row.setFirstReviewer(actor);row.setStatus("FIRST_REVIEW");}
  else {if(row.getFirstReviewer().equals(actor))throw new BusinessException("需要另一位复核人登录后签核");row.setSecondReviewer(actor);row.setStatus("VERIFIED");}
  row.setRevision(row.getRevision()+1);row.setUpdatedAt(LocalDateTime.now());labels.updateById(row);
 }
 public JsonNode template(Long rawId) {
  var doc=documents.selectById(rawId);if(doc==null)throw new BusinessException("资料不存在");
  var node=json.createObjectNode();node.put("rawId",rawId);node.put("documentHash",doc.getContentHash());
  node.put("family","");node.put("split","development");node.put("licenseNote","");node.put("referenceText","");
  node.putArray("questions").addObject().put("question_number","1").putNull("page")
   .put("content","").put("question_type",1).put("answer","").putObject("options");
  return node;
 }
 @Transactional public QualityEvaluationRun evaluate(String split) {
  if(!Set.of("development","holdout").contains(split))throw new BusinessException("请选择评测集合");
  var verified=labels.selectList(Wrappers.<QualityAnnotation>lambdaQuery().eq(QualityAnnotation::getStatus,"VERIFIED").eq(QualityAnnotation::getSplit,split));
  Map<String,Object> report=new LinkedHashMap<>();List<Map<String,Object>> groups=new ArrayList<>();
  for(var gold:verified) {
   var raw=documents.selectById(gold.getRawId());
   var predicted=candidates.selectList(Wrappers.<CrawlQuestion>lambdaQuery().eq(CrawlQuestion::getRawId,gold.getRawId()));
   groups.add(QualityScoring.score(raw,gold,predicted,json));
  }
  report.put("split",split);report.put("documents",groups);report.put("verifiedDocumentCount",verified.size());
  report.put("annotationVersions",verified.stream().map(a->Map.of("id",a.getId(),"revision",a.getRevision())).toList());
  report.put("frozenAnnotations",verified);
  report.put("aggregates",QualityReport.aggregate(groups));
  report.put("scoringVersion","online-stem-v1");
  report.put("predictionBasis","人工编辑前的原始识别结果；缺少原始快照的历史题不计准确率");
  report.put("limitations",List.of("关键公式按去空白的LaTeX字符串匹配，不推断符号等价","没有费用账单时成本为null","复核耗时为打开至定论的经过时长，不代表净人工时间"));
  String status=verified.isEmpty()?"AWAITING_ANNOTATION":groups.stream().anyMatch(g->g.get("metrics")==null)?"INCOMPLETE_PREDICTIONS":"COMPLETED";
  report.put("status",status);
  var row=QualityEvaluationRun.builder().name("资料识别质量 · "+split).datasetKind("HUMAN_REVIEWED").status(status)
   .summaryJson(write(report)).createdBy(LoginUtil.username()).createdAt(LocalDateTime.now()).build();
  runs.insert(row);return row;
 }
 private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
