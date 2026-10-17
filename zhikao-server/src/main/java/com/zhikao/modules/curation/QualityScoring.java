package com.zhikao.modules.curation;
import com.fasterxml.jackson.databind.*;
import com.zhikao.entity.*;
import java.util.*;
import java.util.regex.*;

/** Bounded, reproducible measurements. Missing evidence is null, never a perfect score. */
public final class QualityScoring {
 private QualityScoring(){}
 public static Map<String,Object> score(CrawlRaw raw,QualityAnnotation gold,List<CrawlQuestion> candidates,ObjectMapper json) {
  Map<String,Object> result=new LinkedHashMap<>();result.put("rawId",raw.getId());result.put("family",gold.getFamily());
  result.put("format",raw.getSourceName()==null?"legacy":raw.getSourceName().substring(raw.getSourceName().lastIndexOf('.')+1));
  result.put("contentHash",raw.getContentHash());result.put("parserVersion",raw.getParserVersion());
  result.put("subjectIds",candidates.stream().map(CrawlQuestion::getSubjectId).filter(Objects::nonNull).distinct().sorted().toList());
  result.put("qualityBucket",raw.getQualityScore()==null?"unknown":raw.getQualityScore()>=80?"automatic-score-80-100":raw.getQualityScore()>=60?"automatic-score-60-79":"automatic-score-0-59");
  result.put("extractionMillis",raw.getExtractionMillis());result.put("cloudCalls",raw.getFormulaCloudCalls());result.put("actualCost",null);
  result.put("reviewElapsedMillis",candidates.stream().map(CrawlQuestion::getReviewMilliseconds).filter(Objects::nonNull).mapToLong(Long::longValue).sum());
  try {
   JsonNode expected=json.readTree(gold.getQuestionsJson());
   result.put("expectedQuestions",expected.size());result.put("predictedQuestions",candidates.size());
   List<JsonNode> predictions=new ArrayList<>();
   for(var q:candidates)if(q.getPredictionJson()!=null)predictions.add(json.readTree(q.getPredictionJson()));
   if(predictions.size()!=candidates.size()) {
    result.put("metrics",null);result.put("reason","缺少不可变的识别快照，不能用人工修订结果代替机器预测");return result;
   }
   List<Map<String,Object>> errors=new ArrayList<>();
   Set<Integer> used=new HashSet<>();int matched=0,content=0,answers=0,types=0,options=0,pages=0,pageExpected=0,usable=0,formulaExpected=0,formulaCorrect=0;
   for(JsonNode q:expected) {
    String stem=normalize(q.path("content").asText());double best=0.65;int chosen=-1;
    for(int i=0;i<predictions.size();i++)if(!used.contains(i)) {
     String other=normalize(predictions.get(i).path("content").asText());
     double similarity=similarity(stem,other);
     if(similarity>=best){best=similarity;chosen=i;}
    }
    int expectedFormulas=q.path("formulas").isArray()?q.path("formulas").size():0;formulaExpected+=expectedFormulas;
    boolean hasPage=q.path("page").isInt()&&q.path("page").asInt()>0;
    if(hasPage)pageExpected++;
    if(chosen<0){errors.add(Map.of("questionNumber",q.path("question_number").asText(),"fields",List.of("missingQuestion")));continue;}
    used.add(chosen);matched++;JsonNode p=predictions.get(chosen);
    boolean c=stem.equals(normalize(p.path("content").asText()));
    boolean a=normalize(q.path("answer").asText()).equals(normalize(p.path("answer").asText()));
    boolean t=q.path("question_type").asInt()==p.path("question_type").asInt();
    boolean o=canonicalOptions(q.path("options")).equals(canonicalOptions(p.path("options")));
    boolean page=!hasPage||q.path("page").asInt()==p.path("page").asInt();
    if(c)content++;if(a)answers++;if(t)types++;if(o)options++;if(hasPage&&page)pages++;
    StringBuilder optionText=new StringBuilder();p.path("options").elements().forEachRemaining(v->optionText.append(v.asText()).append('\n'));
    int found=0;String text=p.path("content").asText()+"\n"+p.path("answer").asText()+"\n"+optionText+p.path("analysis").asText();
    List<String> predictedFormulas=new ArrayList<>();
    Matcher formula=Pattern.compile("\\$\\$?(.*?)\\$\\$?|\\\\\\((.*?)\\\\\\)|\\\\\\[(.*?)\\\\\\]",Pattern.DOTALL).matcher(text);
    while(formula.find()){for(int g=1;g<=3;g++)if(formula.group(g)!=null)predictedFormulas.add(formula.group(g).replaceAll("\\s+",""));}
    for(JsonNode f:q.path("formulas"))if(predictedFormulas.remove(f.path("latex").asText().replaceAll("\\s+","")))found++;
    formulaCorrect+=found;if(c&&a&&t&&o&&page&&found==expectedFormulas)usable++;
    List<String> fields=new ArrayList<>();if(!c)fields.add("content");if(!a)fields.add("answer");if(!t)fields.add("type");if(!o)fields.add("options");if(!page)fields.add("page");if(found!=expectedFormulas)fields.add("formula");
    if(!fields.isEmpty())errors.add(Map.of("questionNumber",q.path("question_number").asText(),"predictionIndex",chosen,"fields",fields));
   }
   Map<String,Object> metrics=new LinkedHashMap<>();metrics.put("matchedQuestions",matched);
   metrics.put("precision",ratio(matched,predictions.size()));metrics.put("recall",ratio(matched,expected.size()));
   metrics.put("f1",ratio(2*matched,predictions.size()+expected.size()));
   metrics.put("contentAccuracy",ratio(content,expected.size()));metrics.put("answerAccuracy",ratio(answers,expected.size()));
   metrics.put("typeAccuracy",ratio(types,expected.size()));metrics.put("optionsAccuracy",ratio(options,expected.size()));
   metrics.put("pageExpected",pageExpected);metrics.put("pageCorrect",pages);
   metrics.put("pageAccuracy",ratio(pages,pageExpected));metrics.put("usableRate",ratio(usable,expected.size()));
   metrics.put("contentCorrect",content);metrics.put("answerCorrect",answers);metrics.put("typeCorrect",types);metrics.put("optionsCorrect",options);metrics.put("usableQuestions",usable);
   metrics.put("usableBasis","题干、答案、题型、选项、已标注页码和关键公式均匹配；不包含配图视觉等价及解析质量");
   metrics.put("formulaExpected",formulaExpected);metrics.put("formulaCorrect",formulaCorrect);metrics.put("formulaAccuracy",ratio(formulaCorrect,formulaExpected));
   String reference=normalize(gold.getReferenceText()),actual=normalize(raw.getContent());
   Integer edits=reference.isEmpty()?null:distance(reference,actual);
   metrics.put("cer",edits==null?null:ratio(edits,reference.length()));
   metrics.put("cerReason",reference.isEmpty()?"未标注标准全文":edits==null?"超过在线评分计算上限，请使用离线评测工具":"measured");
   metrics.put("matchingRule","按题干归一化编辑相似度≥0.65进行一对一贪心匹配；按人工真值题数计算字段准确率，漏题计错");
   result.put("errors",errors);result.put("metrics",metrics);
  }catch(Exception e){throw new IllegalStateException("Invalid persisted evaluation data",e);}
  return result;
 }
 private static String normalize(String s){return org.springframework.web.util.HtmlUtils.htmlUnescape(s==null?"":s).replaceAll("<[^>]*>","").replaceAll("\\s+","").trim();}
 private static Map<String,String> canonicalOptions(JsonNode node){Map<String,String> result=new TreeMap<>();node.fields().forEachRemaining(e->result.put(e.getKey(),normalize(e.getValue().asText())));return result;}
 private static Double ratio(int n,int d){return d==0?null:Math.round(n*1000000d/d)/1000000d;}
 private static double similarity(String a,String b){if(a.equals(b))return 1;Integer distance=distance(a,b);return distance==null?0:1-distance/(double)Math.max(a.length(),b.length());}
 private static Integer distance(String a,String b){
  if((long)a.length()*b.length()>10000000L)return null;
  int[] last=new int[b.length()+1];for(int j=0;j<last.length;j++)last[j]=j;
  for(int i=1;i<=a.length();i++){int[] next=new int[b.length()+1];next[0]=i;
   for(int j=1;j<=b.length();j++)next[j]=Math.min(Math.min(next[j-1]+1,last[j]+1),last[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));last=next;}
  return last[b.length()];
 }
}
