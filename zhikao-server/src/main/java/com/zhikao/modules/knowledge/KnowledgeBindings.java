package com.zhikao.modules.knowledge;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.entity.*;
import com.zhikao.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** Exact subject-scoped labels are compatibility input; canonical IDs are the stored relation. */
@Service @RequiredArgsConstructor
public class KnowledgeBindings {
 private final KnowledgePointMapper points;
 private final QuestionKnowledgeMapper links;
 private final FileBlobMapper locks;
 public void bind(Question question) {
  links.delete(Wrappers.<QuestionKnowledge>lambdaQuery().eq(QuestionKnowledge::getQuestionId,question.getId()));
  if(question.getKnowledgePoint()==null)return;
  for(String label:new LinkedHashSet<>(Arrays.asList(question.getKnowledgePoint().split("[,，;；|]")))) {
   String name=label.trim();if(name.isEmpty())continue;
   if(name.length()>255)throw new com.zhikao.common.BusinessException("单个知识点名称不能超过255字");
   locks.lockContent("knowledge:"+question.getSubjectId()+":"+name);
   var point=points.selectOne(Wrappers.<KnowledgePoint>lambdaQuery().eq(KnowledgePoint::getSubjectId,question.getSubjectId())
    .eq(KnowledgePoint::getName,name).orderByAsc(KnowledgePoint::getId).last("LIMIT 1"));
   if(point==null){point=KnowledgePoint.builder().subjectId(question.getSubjectId()).name(name).sort(0).build();points.insert(point);}
   links.insert(QuestionKnowledge.builder().questionId(question.getId()).kpId(point.getId()).build());
  }
 }
}
