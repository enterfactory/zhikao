package com.zhikao.modules.learn;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.zhikao.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v2/learning")
@SaCheckRole("student")
@RequiredArgsConstructor
public class PracticeSessionController {
    private final PracticeSessionService practice;
    @GetMapping("/topics") public R<List<Map<String,Object>>> topics(){return R.ok(practice.topics());}
    @GetMapping("/history") public R<List<Map<String,Object>>> history(){return R.ok(practice.history());}
    @PostMapping("/sessions") public R<Map<String,Object>> create(@RequestBody Map<String,Object> data){return R.ok(practice.create(Objects.toString(data.get("topic"),""),Boolean.TRUE.equals(data.get("wrongOnly"))));}
    @GetMapping("/sessions/{id}") public R<Map<String,Object>> detail(@PathVariable String id){return R.ok(practice.detail(id));}
    @PostMapping("/sessions/{id}/answers") public R<Map<String,Object>> answer(@PathVariable String id,@RequestBody Map<String,String> data){return R.ok(practice.answer(id,data.get("itemId"),data.get("answer")));}
}
