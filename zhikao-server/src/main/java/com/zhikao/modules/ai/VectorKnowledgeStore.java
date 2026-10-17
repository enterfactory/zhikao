package com.zhikao.modules.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhikao.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Only public curriculum outlines are indexed. Exam answers and user records never enter this collection. */
@Service
@RequiredArgsConstructor
public class VectorKnowledgeStore {
    private final VectorProperties properties;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile boolean indexed;
    private volatile boolean batchUnsupported;

    public boolean enabled(){return properties.isEnabled();}

    public List<List<Double>> embed(List<String> input) {
        if(input.isEmpty())return List.of();
        if(batchUnsupported && input.size()>1)return individually(input);
        JsonNode response=call("Embedding", properties.getEmbeddingBaseUrl()+"/embeddings", "POST",
                properties.getEmbeddingApiKey(), true,
                Map.of("model",properties.getEmbeddingModel(),"input",input,"dimensions",properties.getDimensions()), false);
        var data=response.path("data");
        Set<Integer> indices=new HashSet<>();
        boolean valid=data.isArray() && data.size()==input.size();
        for(var row:data){
            int index=row.path("index").asInt(-1);
            valid &= index>=0 && index<input.size() && indices.add(index);
        }
        if(!valid){
            if(input.size()==1)throw new BusinessException("Embedding 返回数量或索引无效");
            batchUnsupported=true;
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Embedding 批量响应索引不符合契约，改用逐条请求保证 {} 条输入的向量归属",input.size());
            return individually(input);
        }
        List<List<Double>> result=new ArrayList<>(Collections.nCopies(input.size(),null));
        for(int i=0;i<data.size();i++){
            JsonNode row=data.get(i);
            List<Double> values=new ArrayList<>();
            for(var n:row.path("embedding")){
                double value=n.asDouble(Double.NaN);
                if(!Double.isFinite(value))throw new BusinessException("Embedding 返回非法数值");
                values.add(value);
            }
            if(values.size()!=properties.getDimensions())throw new BusinessException("Embedding 维度与配置不匹配");
            result.set(row.path("index").asInt(),values);
        }
        return result;
    }

    private List<List<Double>> individually(List<String> input){
        List<List<Double>> result=new ArrayList<>();
        for(String text:input)result.add(embed(List.of(text)).get(0));
        return result;
    }

    public synchronized void indexOutline() {
        if(indexed)return;
        String path=collectionPath();
        JsonNode existing=call("Qdrant",path,"GET",properties.getApiKey(),false,null,true);
        if(existing==null){
            call("Qdrant",path,"PUT",properties.getApiKey(),false,
                    Map.of("vectors",Map.of("size",properties.getDimensions(),"distance","Cosine")),false);
        } else {
            var config=existing.path("result").path("config").path("params").path("vectors");
            if(config.path("size").asInt()!=properties.getDimensions() || !"Cosine".equals(config.path("distance").asText()))
                throw new BusinessException("Qdrant 集合配置不匹配，请配置新的专用集合");
        }
        try(var in=new ClassPathResource("ai-knowledge/knowledge-outline.json").getInputStream()){
            List<Map<String,Object>> points=new ArrayList<>();
            List<JsonNode> entries=new ArrayList<>();
            List<String> texts=new ArrayList<>();
            for(JsonNode entry:json.readTree(in).path("entries")){
                String text=entry.path("subject").asText()+" / "+entry.path("point").asText()+"\n"
                        +entry.path("definition").asText()+"\n典型考法："+entry.path("examTips").asText()
                        +"\n常见错误："+entry.path("commonErrors").asText();
                entries.add(entry);texts.add(text);
            }
            var embeddings=embed(texts);
            for(int i=0;i<entries.size();i++){
                var entry=entries.get(i);
                String id=UUID.nameUUIDFromBytes((entry.path("subject").asText()+":"+entry.path("point").asText()).getBytes(StandardCharsets.UTF_8)).toString();
                points.add(Map.of("id",id,"vector",embeddings.get(i),"payload",
                        Map.of("subject",entry.path("subject").asText(),"point",entry.path("point").asText(),"text",texts.get(i),"source","public-outline-v1")));
            }
            call("Qdrant",path+"/points?wait=true","PUT",properties.getApiKey(),false,Map.of("points",points),false);
            indexed=true;
        }catch(BusinessException e){throw e;}
        catch(Exception e){throw new BusinessException("考纲向量索引失败，请检查服务状态");}
    }

    public String retrieve(String query,String subject) {
        indexOutline();
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("vector",embed(List.of(query)).get(0));body.put("limit",3);body.put("with_payload",true);
        var filters=new ArrayList<Map<String,Object>>();
        filters.add(Map.of("key","source","match",Map.of("value","public-outline-v1")));
        if(subject!=null && !subject.isBlank())filters.add(Map.of("key","subject","match",Map.of("value",subject)));
        body.put("filter",Map.of("must",filters));
        var response=call("Qdrant",collectionPath()+"/points/search","POST",properties.getApiKey(),false,body,false);
        StringBuilder result=new StringBuilder("【考纲语义检索】来源：公开考纲整理；相关性不等于事实核验。\n");
        for(var hit:response.path("result"))result.append("\n").append(hit.path("payload").path("text").asText()).append("\n");
        return result.toString();
    }

    public JsonNode collectionInfo(){return call("Qdrant",collectionPath(),"GET",properties.getApiKey(),false,null,true);}

    /** Caller must supply a test-only collection; never used to delete an application collection. */
    public void removeTestCollection(){
        if(!properties.getCollection().matches("zhikao_test_[a-z]+_[0-9a-f]{12}"))throw new IllegalArgumentException("Not a test collection");
        call("Qdrant",collectionPath(),"DELETE",properties.getApiKey(),false,null,true);
        indexed=false;
    }

    private String collectionPath(){
        if(!properties.getCollection().matches("zhikao_[a-zA-Z0-9_]+"))throw new BusinessException("必须使用 zhikao_ 专用向量集合");
        return properties.getBaseUrl()+"/collections/"+properties.getCollection();
    }

    private JsonNode call(String service,String url,String method,String key,boolean bearer,Object body,boolean allowMissing){
        try{
            var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
            if(key!=null && !key.isBlank())builder.header(bearer?"Authorization":"api-key",bearer?"Bearer "+key:key);
            builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==404 && allowMissing)return null;
            if(response.statusCode()<200 || response.statusCode()>=300)throw new BusinessException(service+" 请求失败（HTTP "+response.statusCode()+"），请核对服务、模型名与凭据");
            return json.readTree(response.body());
        }catch(BusinessException e){throw e;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new BusinessException(service+" 请求已中断");}
        catch(Exception e){
            // Report only the exception type, never URLs, credentials or response bodies.
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("{} {} 请求失败，异常类型 {}",service,method,e.getClass().getSimpleName());
            throw new BusinessException(service+" 连接失败或响应无效（"+e.getClass().getSimpleName()+"）");
        }
    }
}
