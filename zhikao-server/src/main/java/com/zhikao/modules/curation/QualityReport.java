package com.zhikao.modules.curation;

import java.util.*;

/** Sample counts and uncertainty accompany every aggregate; missing documents are excluded explicitly. */
public final class QualityReport {
 private QualityReport(){}
 public static List<Map<String,Object>> aggregate(List<Map<String,Object>> docs){
  Map<String,List<Map<String,Object>>> buckets=new TreeMap<>();
  buckets.put("all",docs);
  for(var d:docs){
   buckets.computeIfAbsent("format:"+d.get("format"),k->new ArrayList<>()).add(d);
   Object subjects=d.get("subjectIds");
   if(subjects instanceof List<?> ids){if(ids.isEmpty())buckets.computeIfAbsent("subject:unknown",k->new ArrayList<>()).add(d);for(Object id:ids)buckets.computeIfAbsent("subject:"+id,k->new ArrayList<>()).add(d);}
   buckets.computeIfAbsent("family:"+d.get("family"),k->new ArrayList<>()).add(d);
   buckets.computeIfAbsent("quality:"+d.get("qualityBucket"),k->new ArrayList<>()).add(d);
  }
  List<Map<String,Object>> result=new ArrayList<>();
  for(var bucket:buckets.entrySet()){
   Map<String,Object> row=new LinkedHashMap<>();row.put("group",bucket.getKey());row.put("documents",bucket.getValue().size());
   int expected=0,predicted=0,matched=0,usable=0,formulas=0,correctFormulas=0,measured=0,answer=0,content=0,types=0,options=0,pageExpected=0,pages=0,cerCount=0;double cerSum=0;
   List<Long> extraction=new ArrayList<>(),review=new ArrayList<>();
   for(var d:bucket.getValue()){
    if(d.get("extractionMillis") instanceof Number n)extraction.add(n.longValue());
    if(d.get("reviewElapsedMillis") instanceof Number n&&n.longValue()>0)review.add(n.longValue());
    if(!(d.get("metrics") instanceof Map<?,?> m))continue;
    measured++;expected+=number(d,"expectedQuestions");predicted+=number(d,"predictedQuestions");
    matched+=number(m,"matchedQuestions");usable+=number(m,"usableQuestions");
    formulas+=number(m,"formulaExpected");correctFormulas+=number(m,"formulaCorrect");
    answer+=number(m,"answerCorrect");content+=number(m,"contentCorrect");types+=number(m,"typeCorrect");options+=number(m,"optionsCorrect");pageExpected+=number(m,"pageExpected");pages+=number(m,"pageCorrect");
    if(m.get("cer") instanceof Number n){cerCount++;cerSum+=n.doubleValue();}
   }
   row.put("measuredDocuments",measured);row.put("excludedDocuments",bucket.getValue().size()-measured);
   row.put("expectedQuestions",expected);row.put("predictedQuestions",predicted);
   row.put("precision",ratio(matched,predicted));row.put("recall",ratio(matched,expected));row.put("f1",ratio(2*matched,predicted+expected));
   row.put("usableRate",ratio(usable,expected));row.put("usableRateWilson95",wilson(usable,expected));
   row.put("answerAccuracy",ratio(answer,expected));row.put("contentAccuracy",ratio(content,expected));row.put("typeAccuracy",ratio(types,expected));row.put("optionsAccuracy",ratio(options,expected));row.put("pageAccuracy",ratio(pages,pageExpected));
   row.put("macroCer",cerCount==0?null:cerSum/cerCount);row.put("cerMeasuredDocuments",cerCount);
   row.put("formulaAccuracy",ratio(correctFormulas,formulas));row.put("formulaExpected",formulas);row.put("formulaWilson95",wilson(correctFormulas,formulas));
   row.put("extractionP50Millis",percentile(extraction,.5));row.put("extractionP95Millis",percentile(extraction,.95));
   row.put("reviewP50ElapsedMillis",percentile(review,.5));row.put("reviewP95ElapsedMillis",percentile(review,.95));
   row.put("uncertaintyBasis","Wilson二项区间，仅反映当前题目样本；同来源题目可能相关，不能代表总体保证");result.add(row);
  }
  return result;
 }
 private static int number(Map<?,?> m,String key){return m.get(key) instanceof Number n?n.intValue():0;}
 private static Double ratio(int n,int d){return d==0?null:n/(double)d;}
 private static Long percentile(List<Long> values,double p){if(values.isEmpty())return null;values.sort(Long::compareTo);return values.get(Math.max(0,(int)Math.ceil(values.size()*p)-1));}
 private static List<Double> wilson(int successes,int total){
  if(total==0)return null;double z=1.95996398454,p=successes/(double)total,den=1+z*z/total;
  double center=(p+z*z/(2*total))/den,delta=z*Math.sqrt(p*(1-p)/total+z*z/(4d*total*total))/den;
  return List.of(Math.max(0,center-delta),Math.min(1,center+delta));
 }
}
