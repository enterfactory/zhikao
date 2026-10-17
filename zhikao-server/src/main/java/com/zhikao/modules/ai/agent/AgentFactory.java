package com.zhikao.modules.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhikao.common.BusinessException;
import com.zhikao.common.ErrorCode;
import com.zhikao.config.AiProperties;
import com.zhikao.config.SecurityProperties;
import com.zhikao.mapper.ExamAnswerMapper;
import com.zhikao.mapper.ExamMapper;
import com.zhikao.mapper.ExamStudentMapper;
import com.zhikao.mapper.QuestionMapper;
import com.zhikao.mapper.SysOperLogMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.modules.exam.ExamService;
import com.zhikao.modules.exam.GradeService;
import com.zhikao.modules.exam.PaperService;
import com.zhikao.modules.exam.QuestionService;
import com.zhikao.modules.file.FileStorageService;
import com.zhikao.modules.system.SysClassService;
import com.zhikao.modules.system.SubjectService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.core.tool.subagent.SubAgentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Agent 装配工厂：按 {@link AgentType} + 请求身份，构建一次性的 {@link ReActAgent}。
 *
 * <p><b>关键设计一：Agent 不复用。</b>
 * AgentScope 官方注释明确写着「ReActAgent is not thread-safe」，
 * 并且 {@code SubAgentProvider} 的存在意义就是「每次调用新建实例」。
 * 所以这里**每次请求都新建 Agent**，只把昂贵的 {@link Model}（HTTP 客户端）缓存复用。
 * 一期把单个 Agent 缓存在 volatile 字段里复用，在并发下是隐患——这里一并修正。
 *
 * <p><b>关键设计二：工具按「Agent 类型 + 角色」双重过滤。</b>
 * 先注册全部工具，再按 {@link AgentType#getToolNames()} 白名单逐个摘掉不需要的。
 * 白名单只有一处（枚举），不会出现「提示词说不许用、但工具其实还注册着」的错配。
 *
 * <p><b>关键设计三：多轮上下文走应用层。</b>
 * 没有使用 {@code stateStore}：对话历史直接落 {@code ai_message} 表，
 * 每轮把最近若干条历史作为输入传入。好处是重启不丢、可审计、可导出，
 * 也不依赖框架内部状态格式（该部分在 2.0.x 正处于迁移期）。
 */
@Component
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    /** ReAct 最大迭代轮数：防止模型在「查了又查」上打转 */
    private static final int MAX_ITERS = 8;

    private final AiProperties aiProperties;
    private final SecurityProperties security;

    private final SubjectService subjectService;
    private final SysClassService classService;
    private final QuestionService questionService;
    private final PaperService paperService;
    private final ExamService examService;
    private final GradeService gradeService;
    private final KnowledgeBase knowledgeBase;
    private final StudentProfileService profileService;

    private final ExamMapper examMapper;
    private final ExamStudentMapper examStudentMapper;
    private final ExamAnswerMapper examAnswerMapper;
    private final QuestionMapper questionMapper;
    private final SysUserMapper userMapper;
    private final SysOperLogMapper operLogMapper;
    private final FileStorageService fileStorage;
    private final ObjectMapper objectMapper;

    /** 缓存的模型客户端（可复用；Agent 不可复用） */
    private volatile Model model;

    public AgentFactory(AiProperties aiProperties, SecurityProperties security,
                        SubjectService subjectService, SysClassService classService,
                        QuestionService questionService, PaperService paperService,
                        ExamService examService, GradeService gradeService,
                        KnowledgeBase knowledgeBase, StudentProfileService profileService,
                        ExamMapper examMapper, ExamStudentMapper examStudentMapper,
                        ExamAnswerMapper examAnswerMapper, QuestionMapper questionMapper,
                        SysUserMapper userMapper, SysOperLogMapper operLogMapper,
                        FileStorageService fileStorage, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.security = security;
        this.subjectService = subjectService;
        this.classService = classService;
        this.questionService = questionService;
        this.paperService = paperService;
        this.examService = examService;
        this.gradeService = gradeService;
        this.knowledgeBase = knowledgeBase;
        this.profileService = profileService;
        this.examMapper = examMapper;
        this.examStudentMapper = examStudentMapper;
        this.examAnswerMapper = examAnswerMapper;
        this.questionMapper = questionMapper;
        this.userMapper = userMapper;
        this.operLogMapper = operLogMapper;
        this.fileStorage = fileStorage;
        this.objectMapper = objectMapper;
    }

    /** AI 是否可用（未配置 Key 时上层走降级分支） */
    public boolean available() {
        return aiProperties.ready();
    }

    /**
     * 构建一个 Agent 实例（每次调用都返回新对象）。
     *
     * @param type     Agent 类型
     * @param context  当次请求的身份上下文
     * @param sessionId 会话 id（用于框架侧标识；上下文实际由应用层传入）
     */
    public ReActAgent create(AgentType type, AgentContext context, String sessionId) {
        if (!available()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "AI 能力未启用：请配置 zhikao.ai.api-key 后重试");
        }
        Toolkit toolkit = buildToolkit(type, context, sessionId);
        return ReActAgent.builder()
                .name("zhikao_" + type.getCode())
                .description(type.getDescription())
                .sysPrompt(buildSysPrompt(type, context))
                .model(model())
                .toolkit(toolkit)
                .maxIters(MAX_ITERS)
                .build();
    }

    /** 构建该 Agent 的工具箱：先全量注册，再按「类型白名单 ∩ 角色白名单」双重摘除 */
    private Toolkit buildToolkit(AgentType type, AgentContext context, String sessionId) {
        Toolkit toolkit = newToolkit(type, context);

        // 主控额外挂载四个专家：把「意图识别 → 委派」交给模型的一次工具调用完成
        if (type == AgentType.COORDINATOR) {
            for (AgentType specialist : AgentType.specialists()) {
                // 学生不该被委派到「出题专家」这类管理侧角色：专家自身没有可用工具时直接跳过，
                // 否则会出现一个「什么都不能做」的专家工具，徒增模型困惑与 token。
                if (AiPermissions.toolsFor(context.getRole())
                        .stream().noneMatch(name -> specialist.getToolNames().contains(name))) {
                    continue;
                }
                SubAgentProvider<ReActAgent> provider =
                        () -> createSpecialist(specialist, context, sessionId);
                toolkit.registration()
                        .subAgent(provider, SubAgentConfig.builder()
                                .toolName("ask_" + specialist.getCode())
                                .description("委派给「" + specialist.getLabel() + "」：" + specialist.getDescription())
                                .forwardEvents(true)
                                .build())
                        .apply();
            }
        }
        log.debug("Agent[{}] 角色[{}] 装载工具 {} 个：{}", type.getCode(), context.getRole(),
                toolkit.getToolNames().size(), toolkit.getToolNames());
        return toolkit;
    }

    /**
     * 注册全部工具，然后按「Agent 类型 + 用户角色」两个白名单求交集后摘除。
     *
     * <p>两道过滤缺一不可，它们回答的是两个不同的问题：
     * 「这个 Agent 该不该有这个能力」（类型）与「这个人该不该有这个能力」（角色）。
     * 例如 {@code export_data} 属于教师专属 Agent 的能力范围，
     * 但学生即使访问同名 Agent 也不该拿到它 —— 单靠类型白名单拦不住这种情况。
     */
    private Toolkit newToolkit(AgentType type, AgentContext context) {
        Toolkit toolkit = new Toolkit();
        AiTools tools = new AiTools(context, subjectService, classService, questionService,
                paperService, examService, gradeService, knowledgeBase, profileService,
                examMapper, examStudentMapper, examAnswerMapper, questionMapper, userMapper,
                operLogMapper, fileStorage, objectMapper);
        toolkit.registerTool(tools);

        Set<String> roleAllowed = AiPermissions.toolsFor(context.getRole());
        Set<String> allowed = type.getToolNames();
        for (String name : new HashSet<>(toolkit.getToolNames())) {
            if (!allowed.contains(name) || !roleAllowed.contains(name)) {
                toolkit.removeTool(name);
            }
        }
        return toolkit;
    }

    /**
     * 创建专家子 Agent。
     * 注意：这里**不能**再挂子 Agent，否则会形成无限递归的委派链。
     */
    private ReActAgent createSpecialist(AgentType type, AgentContext context, String sessionId) {
        Toolkit toolkit = newToolkit(type, context);
        return ReActAgent.builder()
                .name("zhikao_" + type.getCode())
                .description(type.getDescription())
                .sysPrompt(buildSysPrompt(type, context))
                .model(model())
                .toolkit(toolkit)
                .maxIters(5)
                .build();
    }

    /**
     * 拼装系统提示词：角色定位 + 当前用户身份 + （学生）长期记忆画像。
     *
     * 长期记忆在这里注入而不是做成「记忆工具」：画像是一段稳定的归纳结论，
     * 每轮直接放进提示词，比让模型多调一次工具更省 token 也更可靠。
     */
    private String buildSysPrompt(AgentType type, AgentContext context) {
        StringBuilder builder = new StringBuilder(type.getSysPrompt());
        builder.append("\n\n").append(context.describe());

        // 把「你不能做什么」显式写进提示词。
        // 工具层已经物理摘除了越权工具，但模型不知道「摘掉」这件事，
        // 它会反复尝试调用不存在的工具、或者干脆编一个答案。
        // 提前告知边界，模型才能给出「我做不到，你可以去哪里做」这种有用地回答。
        if (context.isStudent()) {
            builder.append("\n你的角色权限边界：只能查询**本人**的成绩、考试、错题与学习画像，可以出题练习和自测判对错。")
                    .append("你没有题库管理、组卷、阅卷、数据导出、操作日志的权限。")
                    .append("如果用户问到这些，明确说明「学生角色无此权限」，并告诉他去哪个页面可以自助完成，不要假装查到数据。");
        } else if (context.isTeacher()) {
            builder.append("\n你的角色权限边界：可以查询题库、试卷、考试、成绩、学情、待批改等教学管理数据，并生成题目草稿。")
                    .append("操作日志属于审计数据，仅管理员可见。");
        } else {
            builder.append("\n你的角色权限边界：管理员，可使用全部只读工具（含操作日志审计）。");
        }
        builder.append("\n今天的日期是 ").append(java.time.LocalDate.now()).append("。");

        if (context.isStudent() && context.getUserId() != null) {
            String profile = profileService.loadSummary(context.getUserId());
            if (!profile.isBlank()) {
                builder.append("\n该学生的历史学习画像（长期记忆，仅供参考，实时成绩仍以工具查询为准）：")
                        .append(profile);
            }
        }
        builder.append("\n单次回答请控制在 400 字以内，除非用户明确要求详细展开。");
        return builder.toString();
    }

    /** 懒加载模型：api-key 未配置时不在启动期报错，避免把可选能力变成启动依赖 */
    private Model model() {
        Model current = model;
        if (current == null) {
            synchronized (this) {
                if (model == null) {
                    ModelCreationContext creationContext = ModelCreationContext.builder()
                            .apiKey(aiProperties.getApiKey())
                            .baseUrl(aiProperties.getBaseUrl())
                            .build();
                    String modelId = aiProperties.getModel();
                    if (!modelId.contains(":")) modelId = "deepseek:" + modelId;
                    model = ModelRegistry.resolve(modelId, creationContext);
                }
                current = model;
            }
        }
        return current;
    }

    /** 供上层读取配置（如每用户每日调用上限） */
    public SecurityProperties getSecurity() {
        return security;
    }
}
