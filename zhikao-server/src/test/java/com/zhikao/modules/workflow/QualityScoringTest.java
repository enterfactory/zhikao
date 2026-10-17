package com.zhikao.modules.workflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhikao.entity.*;
import com.zhikao.modules.curation.*;
import com.zhikao.modules.crawler.image.ImageTargetPolicy;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QualityScoringTest {
 final ObjectMapper json=new ObjectMapper();
 Map<String,Object> score(String expected,List<CrawlQuestion> predicted){
  return QualityScoring.score(CrawlRaw.builder().id(1L).sourceName("owned.docx").content("正文").build(),
   QualityAnnotation.builder().family("owned").referenceText("").questionsJson(expected).build(),predicted,json);
 }
 @Test void optionLatexIsDecodedAndUnlabelledPagesHaveNoAccuracy()throws Exception{
  var q=Map.of("content","计算函数的导数并选择正确答案","question_type",1,"answer","A","options",Map.of("A","$\\frac{1}{x}$"));
  var gold=new HashMap<String,Object>(q);gold.put("question_number","1");gold.put("formulas",List.of(Map.of("latex","\\frac{1}{x}")));
  var result=score(json.writeValueAsString(List.of(gold)),List.of(CrawlQuestion.builder().predictionJson(json.writeValueAsString(q)).build()));
  var metrics=(Map<?,?>)result.get("metrics");assertEquals(1d,metrics.get("formulaAccuracy"));assertNull(metrics.get("pageAccuracy"));
 }
 @Test void missingQuestionsAndWrongAnswersEnterDenominator()throws Exception{
  var q=Map.of("question_number","1","content","计算函数的导数并选择正确答案","question_type",1,"answer","A","page",2);
  var other=Map.of("question_number","2","content","证明任意三角形的内角和等于一百八十度","question_type",5,"answer","证明过程","page",3);
  var prediction=new HashMap<String,Object>(q);prediction.put("answer","B");
  var result=score(json.writeValueAsString(List.of(q,other)),List.of(CrawlQuestion.builder().predictionJson(json.writeValueAsString(prediction)).build()));
  var metrics=(Map<?,?>)result.get("metrics");assertEquals(.5d,metrics.get("recall"));assertEquals(0d,metrics.get("answerAccuracy"));assertEquals(.5d,metrics.get("pageAccuracy"));assertEquals(2,((List<?>)result.get("errors")).size());
 }
 @Test void editedCandidateCannotSubstituteMissingPrediction(){
  assertNull(score("[]",List.of(CrawlQuestion.builder().content("人工修订结果").build())).get("metrics"));
 }
 @Test void noSamplesGiveNullRatesAndIntervals(){
  var row=QualityReport.aggregate(List.of()).get(0);assertNull(row.get("usableRate"));assertNull(row.get("usableRateWilson95"));assertNull(row.get("extractionP95Millis"));
 }
 @Test void imageAddressPolicyRejectsPrivateAndMetadataWithoutNetwork()throws Exception{
  for(byte[] address:List.of(new byte[]{127,0,0,1},new byte[]{10,1,1,1},new byte[]{(byte)169,(byte)254,(byte)169,(byte)254},new byte[]{100,64,1,1}))
   assertFalse(ImageTargetPolicy.publicAddress(java.net.InetAddress.getByAddress(address)));
  assertTrue(ImageTargetPolicy.publicAddress(java.net.InetAddress.getByAddress(new byte[]{8,8,8,8})));
  var policy=new ImageTargetPolicy(new com.zhikao.config.CrawlerProperties());
  assertThrows(com.zhikao.common.BusinessException.class,()->policy.requireAllowed(java.net.URI.create("http://169.254.169.254/latest/meta-data")));
 }
}
