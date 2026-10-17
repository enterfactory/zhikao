package com.zhikao.modules.learn;

import com.zhikao.common.*;
import lombok.RequiredArgsConstructor;
import com.zhikao.mapper.PracticeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Practice contains immutable, released exam snapshots, never arbitrary bank ids. */
@Service
@RequiredArgsConstructor
public class PracticeSessionService {
    private final PracticeMapper mapper;

    public List<Map<String,Object>> topics() {
        return mapper.topics(LoginUtil.userId());
    }

    @Transactional
    public Map<String,Object> create(String topic, boolean wrongOnly) {
        String id=UUID.randomUUID().toString();
        var rows=mapper.available(LoginUtil.userId(),topic,wrongOnly);
        if(rows.isEmpty()) throw new BusinessException("当前没有已开放的练习题目");
        mapper.create(id,LoginUtil.userId(),wrongOnly?"历史错题":topic);
        for(var q:rows) mapper.addItem(UUID.randomUUID().toString(),id,q);
        return detail(id);
    }

    public Map<String,Object> detail(String id) {
        require(id,false);
        var items=mapper.items(id);
        for(var q:items) {
            q.put("options",JsonHelper.toOptionMap((String)q.get("options")));
            Object standard=q.remove("standard_answer"),analysis=q.remove("analysis");
            if(q.get("answered_at")!=null) {
                var feedback=new LinkedHashMap<String,Object>();
                feedback.put("answer",standard);feedback.put("analysis",analysis);feedback.put("source",q.get("source_name"));
                feedback.put("correct",q.get("correct")==null?null:((Number)q.get("correct")).intValue()==1);
                feedback.put("subjective",!QuestionTypeEnum.isObjective(((Number)q.get("type")).intValue()));
                q.put("feedback",feedback);
            }
        }
        return Map.of("id",id,"items",items);
    }

    @Transactional
    public Map<String,Object> answer(String sessionId, String itemId, String answer) {
        require(sessionId,true);
        if(answer==null||answer.isBlank()||answer.length()>20000) throw new BusinessException("请填写有效答案");
        var rows=mapper.item(itemId,sessionId);
        if(rows.isEmpty()) throw new BusinessException("题目不属于本次练习");
        var q=rows.get(0);int type=((Number)q.get("type")).intValue();
        boolean objective=QuestionTypeEnum.isObjective(type);
        boolean correct=AnswerJudge.correct(type,(String)q.get("standard_answer"),answer);
        mapper.answer(itemId,answer,objective?(correct?1:0):null);
        Map<String,Object> result=new LinkedHashMap<>();result.put("correct",objective?correct:null);result.put("answer",q.get("standard_answer"));result.put("analysis",q.get("analysis"));result.put("source",q.get("source_name"));result.put("subjective",!objective);return result;
    }

    public List<Map<String,Object>> history() {
        return mapper.history(LoginUtil.userId());
    }
    private void require(String id,boolean lock) {
        if(mapper.require(id,LoginUtil.userId(),lock)==null)throw new BusinessException("练习不存在或无权访问");
    }
}
