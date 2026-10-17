package com.zhikao.modules.ai;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.zhikao.common.LoginUtil;
import com.zhikao.common.R;
import com.zhikao.modules.ai.agent.AgentFactory;
import com.zhikao.modules.ai.agent.AgentType;
import com.zhikao.modules.ai.dto.AiAssistMarkDTO;
import com.zhikao.modules.ai.dto.AiGenQuestionDTO;
import com.zhikao.modules.ai.vo.AiAnalysisVO;
import com.zhikao.modules.ai.vo.AiGenQuestionVO;
import com.zhikao.modules.ai.vo.AiMarkSuggestionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 增强接口。
 *
 * 权限分层：状态查询对所有登录用户开放（学生也要能知道 AI 是否可用），
 * 生成类接口（出题 / 辅助阅卷 / 错题分析）改按 RBAC 权限点控制——
 * ai:gen:question、ai:assist:mark、analysis:view 分别对应三种角色配置。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    private final AiService aiService;
    private final AgentFactory agentFactory;

    public AiController(AiService aiService, AgentFactory agentFactory) {
        this.aiService = aiService;
        this.agentFactory = agentFactory;
    }

    /**
     * AI 能力状态。
     * 前端进页面时先探测一次，决定是否禁用入口并说明原因——
     * 比「让用户发出去再收到报错」体验好，也不浪费一次模型调用。
     *
     * <p>这里返回的 Agent 清单**按当前登录角色过滤**：学生看到的选项里
     * 不该出现「出题助手」「阅卷助手」。虽然真正调用时后端还会再拦一次，
     * 但把不该出现的入口摆到界面上本身就是个错误的产品表达。
     */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        String role = LoginUtil.roleCode();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", agentFactory.available());
        data.put("agents", AgentType.forRole(role).stream()
                .map(type -> Map.of("code", type.getCode(), "label", type.getLabel(),
                        "description", type.getDescription()))
                .toList());
        data.put("message", agentFactory.available()
                ? "模型已启用"
                : "未配置模型 API Key：智能出题 / 辅助阅卷 / 对话不可用；成绩统计与错题分析仍可用");
        return R.ok(data);
    }

    /** 智能出题（返回模型原文 + 解析后的结构化草稿，草稿由教师勾选后入库） */
    @PostMapping("/genQuestion")
    @SaCheckPermission("ai:gen:question")
    public R<AiGenQuestionVO> genQuestion(@Valid @RequestBody AiGenQuestionDTO dto) {
        return R.ok(aiService.genQuestion(dto));
    }

    /** 主观题辅助阅卷建议 */
    @PostMapping("/assistMark")
    @SaCheckPermission("ai:assist:mark")
    public R<AiMarkSuggestionVO> assistMark(@Valid @RequestBody AiAssistMarkDTO dto) {
        return R.ok(aiService.assistMark(dto));
    }

    /** 错题分析：知识点掌握度 + 教学建议 */
    @GetMapping("/analysis/{examId}")
    @SaCheckPermission("analysis:view")
    public R<AiAnalysisVO> analysis(@PathVariable Long examId) {
        return R.ok(aiService.analysis(examId));
    }
}
