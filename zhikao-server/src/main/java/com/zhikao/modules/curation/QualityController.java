package com.zhikao.modules.curation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.JsonNode;
import com.zhikao.common.R;
import com.zhikao.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/v2/quality") @SaCheckPermission("crawler:manage") @RequiredArgsConstructor
public class QualityController {
 private final QualityService quality;
 @GetMapping("/summary") public R<Map<String,Object>> summary(){return R.ok(quality.summary());}
 @GetMapping("/annotations") public R<List<QualityAnnotation>> annotations(){return R.ok(quality.annotations());}
 @GetMapping("/documents/{id}/template") public R<JsonNode> template(@PathVariable Long id){return R.ok(quality.template(id));}
 @PutMapping("/documents/{id}/annotation") public R<Long> save(@PathVariable Long id,@RequestBody JsonNode body){return R.ok(quality.save(id,body.has("revision")?body.path("revision").asInt():null,body));}
 @PostMapping("/annotations/{id}/sign") public R<Void> sign(@PathVariable Long id,@RequestBody Map<String,Integer> body){quality.sign(id,body.get("expectedVersion"));return R.ok();}
 @GetMapping("/runs") public R<List<QualityEvaluationRun>> runs(){return R.ok(quality.runs());}
 @PostMapping("/runs") public R<QualityEvaluationRun> evaluate(@RequestBody Map<String,String> body){return R.ok(quality.evaluate(body.getOrDefault("split","development")));}
}
