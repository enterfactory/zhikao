package com.zhikao.modules.ai.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhikao.common.BusinessException;
import com.zhikao.common.ErrorCode;
import com.zhikao.common.LoginUtil;
import com.zhikao.config.SecurityProperties;
import com.zhikao.entity.AiConversation;
import com.zhikao.entity.AiMessage;
import com.zhikao.entity.AiTask;
import com.zhikao.entity.SysUser;
import com.zhikao.mapper.AiConversationMapper;
import com.zhikao.mapper.AiMessageMapper;
import com.zhikao.mapper.AiTaskMapper;
import com.zhikao.mapper.SysUserMapper;
import com.zhikao.modules.ai.agent.dto.ChatRequestDTO;
import com.zhikao.modules.ai.agent.vo.ChatMessageVO;
import com.zhikao.modules.ai.agent.vo.ChatReplyVO;
import com.zhikao.modules.ai.agent.vo.AgentCatalogVO;
import com.zhikao.modules.ai.agent.vo.ChatCatalogVO;
import com.zhikao.modules.ai.agent.vo.ConversationVO;
import com.zhikao.modules.ai.agent.vo.ToolCallVO;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 智能对话服务：会话管理 + 上下文装载 + 流式/同步对话 + 成本核算。
 *
 * <p><b>三条关键设计</b>：
 * <ol>
 *   <li><b>上下文走应用层</b>：每轮从 {@code ai_message} 装载最近若干条历史拼成 {@code List<Msg>} 传入。
 *       不用框架的 stateStore —— 重启不丢、可审计、可导出，也不绑框架内部状态格式。</li>
 *   <li><b>流式与同步共用一条链路</b>：都基于 {@code streamEvents()}。
 *       同步模式只是把事件流收集完再返回——保证两种模式的工具调用记录、token 统计口径完全一致，
 *       不会出现「流式看得见工具调用、同步看不见」这种两套行为。</li>
 *   <li><b>成本可控</b>：每次对话落一条 {@code ai_task}（带 user_id），
 *       配合「每用户每日调用上限」做持久化配额——重启不重置。</li>
 * </ol>
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /** 载入上下文时保留的最大历史消息条数（成对计算，实际约 5 轮） */
    private static final int HISTORY_LIMIT = 10;

    /** 工具参数与结果摘要的落库长度上限 */
    private static final int TOOL_TEXT_LIMIT = 500;

    /** 会话标题长度 */
    private static final int TITLE_LIMIT = 30;

    /** 任务类型：智能对话 */
    private static final String TASK_TYPE_CHAT = "chat";

    private final AgentFactory agentFactory;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiTaskMapper aiTaskMapper;
    private final SysUserMapper userMapper;
    private final SecurityProperties security;
    private final ObjectMapper objectMapper;

    public AiChatService(AgentFactory agentFactory, AiConversationMapper conversationMapper,
                         AiMessageMapper messageMapper, AiTaskMapper aiTaskMapper,
                         SysUserMapper userMapper, SecurityProperties security,
                         ObjectMapper objectMapper) {
        this.agentFactory = agentFactory;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.aiTaskMapper = aiTaskMapper;
        this.userMapper = userMapper;
        this.security = security;
        this.objectMapper = objectMapper;
    }

    // ==================== 会话管理 ====================

    /** 当前用户的会话列表（不含消息体） */
    public List<ConversationVO> conversations() {
        Long userId = LoginUtil.userId();
        List<AiConversation> list = conversationMapper.selectList(Wrappers.<AiConversation>lambdaQuery()
                .eq(AiConversation::getUserId, userId)
                .orderByDesc(AiConversation::getId)
                .last("limit 50"));
        List<ConversationVO> result = new ArrayList<>();
        for (AiConversation conversation : list) {
            ConversationVO vo = new ConversationVO();
            vo.setId(conversation.getId());
            vo.setTitle(conversation.getTitle());
            vo.setAgentType(conversation.getAgentType());
            vo.setAgentLabel(AgentType.of(conversation.getAgentType()).getLabel());
            vo.setMessageCount(messageMapper.selectCount(Wrappers.<AiMessage>lambdaQuery()
                    .eq(AiMessage::getConversationId, conversation.getId())));
            vo.setCreateTime(conversation.getCreateTime());
            vo.setUpdateTime(conversation.getUpdateTime());
            result.add(vo);
        }
        return result;
    }

    /** 某会话的消息列表（按时间正序） */
    public List<ChatMessageVO> messages(Long conversationId) {
        requireOwnedConversation(conversationId, LoginUtil.userId());
        List<AiMessage> list = messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getId));
        List<ChatMessageVO> result = new ArrayList<>();
        for (AiMessage message : list) {
            if ("tool".equals(message.getRole())) {
                continue; // 工具消息只作为轨迹挂在 assistant 消息里，不单独展示
            }
            ChatMessageVO vo = new ChatMessageVO();
            vo.setId(message.getId());
            vo.setRole(message.getRole());
            vo.setContent(message.getContent());
            vo.setTokenUsage(message.getTokenUsage());
            vo.setCreateTime(message.getCreateTime());
            vo.setToolCalls(parseToolCalls(message.getToolCalls()));
            result.add(vo);
        }
        return result;
    }

    /** 删除会话（连同消息） */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Long conversationId) {
        AiConversation conversation = requireOwnedConversation(conversationId, LoginUtil.userId());
        messageMapper.delete(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getConversationId, conversationId));
        conversationMapper.deleteById(conversation.getId());
    }

    // ==================== 同步对话（SSE 不可用时的兜底） ====================

    public ChatReplyVO chat(ChatRequestDTO dto) {
        if (!agentFactory.available()) {
            return degradedReply(dto, "AI 能力未启用：请在后端配置 zhikao.ai.api-key");
        }
        AgentContext context = context();
        checkDailyQuota(context);
        AiConversation conversation = resolveConversation(dto, context, false);
        saveUserMessage(conversation.getId(), dto.getMessage());
        String agentType = requireAllowedType(dto.getAgentType(), context).getCode();

        ReActAgent agent = agentFactory.create(requireAllowedType(dto.getAgentType(), context), context,
                String.valueOf(conversation.getId()));
        List<Msg> history = loadHistory(conversation.getId());

        Collector collector = new Collector();
        StringBuilder text = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        try {
            agent.streamEvents(history, runtimeContext(context, conversation))
                    .doOnNext(collector::accept)
                    .blockLast();
        } catch (Exception e) {
            log.warn("Agent 同步调用失败：{}", e.getMessage());
            // 失败也要留痕：否则「今天失败了多少次、为什么失败」永远是空白
            recordTask(conversation.getId(), dto.getMessage(), null, context, 2, agentType,
                    collector.toolCalls, collector.tokenUsage,
                    System.currentTimeMillis() - startedAt, e.getMessage());
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 调用失败：" + e.getMessage());
        }

        String content = text.length() > 0 ? text.toString() : collector.finalText();
        ChatReplyVO vo = new ChatReplyVO();
        vo.setConversationId(conversation.getId());
        vo.setAgentType(conversation.getAgentType());
        vo.setContent(content);
        vo.setToolCalls(collector.toolCalls);
        vo.setTokenUsage(collector.tokenUsage);
        vo.setMessageId(saveAssistantMessage(conversation.getId(), content, collector));
        recordTask(conversation.getId(), dto.getMessage(), content, context, 1, agentType,
                collector.toolCalls, collector.tokenUsage,
                System.currentTimeMillis() - startedAt, null);
        return vo;
    }

    // ==================== 流式对话（SSE） ====================

    /**
     * 建立 SSE 流。
     *
     * 事件协议（前端按 event 名分发）：
     * <pre>
     *   thinking    思考中（占位，无正文）
     *   tool_call   开始调用工具：{name, args}
     *   tool_result 工具返回：{name, state, resultSummary}
     *   delta       正文增量：{text}
     *   done        结束：{conversationId, messageId, tokenUsage, toolCalls}
     *   error       异常：{message}
     * </pre>
     */
    public SseEmitter stream(ChatRequestDTO dto) {
        // 超时给足：模型 + 多轮工具调用可能耗时较长；客户端断开时靠 onCompletion 取消
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
        AgentContext context = context();

        if (!agentFactory.available()) {
            sendError(emitter, "AI 能力未启用：请在后端配置 zhikao.ai.api-key 后重试");
            return emitter;
        }
        AgentType type;
        try {
            // 越权校验放在最前面：不合法就不该产生会话、也不该写用户消息
            type = requireAllowedType(dto.getAgentType(), context);
            checkDailyQuota(context);
        } catch (BusinessException e) {
            // 越权选择 Agent / 超限：走 SSE 的 error 事件，前端与限流失败的展示路径一致
            sendError(emitter, e.getMessage());
            emitter.complete();
            return emitter;
        }

        AiConversation conversation = resolveConversation(dto, context, true);
        saveUserMessage(conversation.getId(), dto.getMessage());
        send(emitter, "meta", Map.of(
                "conversationId", conversation.getId(),
                "agentType", conversation.getAgentType(),
                "agentLabel", AgentType.of(conversation.getAgentType()).getLabel()));

        ReActAgent agent = agentFactory.create(type, context,
                String.valueOf(conversation.getId()));
        List<Msg> history = loadHistory(conversation.getId());

        Collector collector = new Collector();
        StringBuilder text = new StringBuilder();
        AtomicBoolean finished = new AtomicBoolean(false);

        long startedAt = System.currentTimeMillis();
        Disposable subscription = agent.streamEvents(history, runtimeContext(context, conversation))
                .subscribe(
                        event -> {
                            collector.accept(event);
                            pushEvent(emitter, event, collector);
                        },
                        error -> {
                            if (finished.compareAndSet(false, true)) {
                                log.warn("Agent 流式调用失败：{}", error.getMessage());
                                recordTask(conversation.getId(), dto.getMessage(), null, context, 2,
                                        type.getCode(), collector.toolCalls, collector.tokenUsage,
                                        System.currentTimeMillis() - startedAt, error.getMessage());
                                sendError(emitter, "AI 调用失败：" + error.getMessage());
                                emitter.complete();
                            }
                        },
                        () -> {
                            if (finished.compareAndSet(false, true)) {
                                String content = text.length() > 0 ? text.toString() : collector.finalText();
                                Long messageId = saveAssistantMessage(conversation.getId(), content, collector);
                                recordTask(conversation.getId(), dto.getMessage(), content, context, 1,
                                        type.getCode(), collector.toolCalls, collector.tokenUsage,
                                        System.currentTimeMillis() - startedAt, null);
                                send(emitter, "done", Map.of(
                                        "conversationId", conversation.getId(),
                                        "messageId", messageId == null ? 0L : messageId,
                                        "tokenUsage", collector.tokenUsage,
                                        "toolCalls", collector.toolCalls));
                                emitter.complete();
                            }
                        });

        // 客户端关闭页面 / 点「停止生成」：取消订阅，真正中断模型调用
        emitter.onCompletion(() -> {
            if (finished.compareAndSet(false, true)) {
                subscription.dispose();
                log.debug("SSE 连接结束，已取消 Agent 订阅");
            }
        });
        emitter.onTimeout(() -> {
            if (finished.compareAndSet(false, true)) {
                subscription.dispose();
                log.warn("SSE 超时，已取消 Agent 订阅");
            }
        });
        return emitter;
    }

    // ==================== 内部方法：事件处理 ====================

    /** 把 AgentEvent 翻译成前端能消费的 SSE 事件 */
    private void pushEvent(SseEmitter emitter, AgentEvent event, Collector collector) {
        AgentEventType type = event.getType();
        if (type == AgentEventType.TEXT_BLOCK_DELTA && event instanceof TextBlockDeltaEvent delta) {
            send(emitter, "delta", Map.of("text", nullSafe(delta.getDelta())));
            return;
        }
        if (type == AgentEventType.TOOL_CALL_START && event instanceof ToolCallStartEvent start) {
            send(emitter, "tool_call", Map.of("name", nullSafe(start.getToolCallName())));
            return;
        }
        if (type == AgentEventType.TOOL_RESULT_END && event instanceof ToolResultEndEvent result) {
            ToolCallVO call = collector.find(nullSafe(result.getToolCallName()));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", nullSafe(result.getToolCallName()));
            payload.put("state", result.getState() == null
                    ? "success" : result.getState().name().toLowerCase());
            // 把工具返回摘要一起推给前端：这是「Agent 是怎么得出这个结论的」最直观的证据
            payload.put("resultSummary", call == null ? "" : nullSafe(call.getResultSummary()));
            send(emitter, "tool_result", payload);
        }
    }

    /** 事件收集器：同步与流式共用，保证两种模式的记录口径一致 */
    private final class Collector {

        private final List<ToolCallVO> toolCalls = new ArrayList<>();

        private final Map<String, ToolCallVO> running = new LinkedHashMap<>();

        private int tokenUsage;

        private String finalText = "";

        /** 最终回复文本（来自 AGENT_RESULT 事件） */
        private String finalText() {
            return finalText;
        }

        private void accept(AgentEvent event) {
            AgentEventType type = event.getType();
            if (type == AgentEventType.TOOL_CALL_START && event instanceof ToolCallStartEvent start) {
                ToolCallVO call = new ToolCallVO();
                call.setName(nullSafe(start.getToolCallName()));
                call.setState("started");
                toolCalls.add(call);
                running.put(call.getName(), call);
                return;
            }
            if (type == AgentEventType.TOOL_RESULT_START && event instanceof ToolResultStartEvent result) {
                ToolCallVO call = running.get(nullSafe(result.getToolCallName()));
                if (call != null) {
                    call.setResultSummary("");
                }
                return;
            }            if (type == AgentEventType.TOOL_RESULT_TEXT_DELTA
                    && event instanceof ToolResultTextDeltaEvent delta) {
                // 工具返回内容按增量推送，这里累加成摘要（截断），用于「工具返回了什么」的可解释性
                ToolCallVO call = running.get(nullSafe(delta.getToolCallName()));
                if (call != null) {
                    call.setResultSummary(truncate(nullSafe(call.getResultSummary()) + nullSafe(delta.getDelta())));
                }
                return;
            }
            if (type == AgentEventType.TOOL_RESULT_END && event instanceof ToolResultEndEvent result) {
                ToolCallVO call = running.remove(nullSafe(result.getToolCallName()));
                if (call != null) {
                    call.setState("finished");
                }
                return;
            }
            if (type == AgentEventType.AGENT_RESULT && event instanceof AgentResultEvent agentResult) {
                Msg msg = agentResult.getResult();
                if (msg != null) {
                    finalText = nullSafe(msg.getTextContent());
                    if (msg.getChatUsage() != null) {
                        tokenUsage = msg.getChatUsage().getTotalTokens();
                    }
                }
            }
        }

        /** 按工具名找记录（同级多次调用取最后一次） */
        private ToolCallVO find(String name) {
            ToolCallVO found = running.get(name);
            if (found != null) {
                return found;
            }
            for (int i = toolCalls.size() - 1; i >= 0; i--) {
                if (name.equals(toolCalls.get(i).getName())) {
                    return toolCalls.get(i);
                }
            }
            return null;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= TOOL_TEXT_LIMIT ? value : value.substring(0, TOOL_TEXT_LIMIT) + "...";
    }

    // ==================== 内部方法：会话与消息 ====================

    private AgentContext context() {
        AgentContext context = AgentContext.current();
        if (context.getUserId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        // 补上真实姓名，用于系统提示词中的身份说明
        SysUser user = userMapper.selectById(context.getUserId());
        return user == null ? context : context.withRealName(user.getRealName());
    }

    /** 取得或新建会话（新建时用首条提问前若干字作为标题） */
    private AiConversation resolveConversation(ChatRequestDTO dto, AgentContext context, boolean streaming) {
        AgentType type = requireAllowedType(dto.getAgentType(), context);
        if (dto.getConversationId() != null) {
            AiConversation conversation = requireOwnedConversation(dto.getConversationId(), context.getUserId());
            // 允许在同一会话里切换 Agent（用户在界面上换专家）
            if (!type.getCode().equals(conversation.getAgentType())) {
                conversation.setAgentType(type.getCode());
                conversation.setUpdateTime(LocalDateTime.now());
                conversationMapper.updateById(conversation);
            }
            return conversation;
        }
        AiConversation conversation = AiConversation.builder()
                .userId(context.getUserId())
                .title(truncateTitle(dto.getMessage()))
                .agentType(type.getCode())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        conversationMapper.insert(conversation);
        return conversation;
    }

    /**
     * 校验「这个角色能不能用这个 Agent」。
     *
     * <p>前端只会展示角色可用的入口，所以走到这里说明是手工构造的请求。
     * 返回明确原因而不是静默降级：静默降级会让用户以为「我选了出题专家，它也回了」，
     * 实际上回答来自另一个 Agent —— 这种「结果对不上预期」比直接报错更难排查。
     */
    private AgentType requireAllowedType(String code, AgentContext context) {
        AgentType type = AgentType.of(code);
        if (!type.visibleTo(context.getRole())) {
            String available = AgentType.forRole(context.getRole()).stream()
                    .map(AgentType::getLabel)
                    .collect(Collectors.joining("、"));
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "「" + AiPermissions.labelOf(context.getRole()) + "」无权使用「" + type.getLabel()
                            + "」，可用的是：" + available);
        }
        return type;
    }

    /** 当前角色可用的 Agent 清单（前端据此渲染能力入口，避免前端硬编码角色判断） */
    public List<AgentCatalogVO> catalog(AgentContext context) {
        Set<String> allowedTools = AiPermissions.toolsFor(context.getRole());
        List<AgentCatalogVO> result = new ArrayList<>();
        for (AgentType type : AgentType.forRole(context.getRole())) {
            AgentCatalogVO vo = new AgentCatalogVO();
            vo.setCode(type.getCode());
            vo.setLabel(type.getLabel());
            vo.setDescription(type.getDescription());
            vo.setSpecialist(type.isSpecialist());
            vo.setToolCount((int) type.getToolNames().stream().filter(allowedTools::contains).count());
            result.add(vo);
        }
        return result;
    }

    /** 能力面板：角色 + 边界说明 + 可用 Agent + 今日配额（前端一次请求拿全） */
    public ChatCatalogVO catalogView() {
        AgentContext context = context();
        ChatCatalogVO vo = new ChatCatalogVO();
        vo.setRole(context.getRole());
        vo.setRoleLabel(AiPermissions.labelOf(context.getRole()));
        vo.setScope(scopeOf(context.getRole()));
        vo.setAgents(catalog(context));
        vo.setQuotaLimit(security.aiDailyLimitFor(context.getRole()));
        vo.setQuotaRemaining(remainingQuota(context));
        return vo;
    }

    /** 把权限表翻译成一句人话，前端直接展示在对话框上方 */
    private String scopeOf(String role) {
        if (AiPermissions.ADMIN.equals(role)) {
            return "管理员：可查询全部教学数据与审计日志，可生成题目草稿、导出数据。";
        }
        if (AiPermissions.TEACHER.equals(role)) {
            return "教师：可查询题库/试卷/考试/成绩/学情与待批改数据，可生成题目草稿、导出 Excel；操作日志仅管理员可见。";
        }
        return "学生：可查询本人成绩、考试、错题与学习画像，可做题练习与自测判分；题库管理、阅卷、导出无权限。";
    }

    /** 会话归属校验：别人的会话一律看不到（含直接传 id 的越权尝试） */
    private AiConversation requireOwnedConversation(Long conversationId, Long userId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问他人的对话");
        }
        return conversation;
    }

    private String truncateTitle(String message) {
        String flat = message == null ? "新对话" : message.replaceAll("\\s+", " ").trim();
        return flat.length() <= TITLE_LIMIT ? flat : flat.substring(0, TITLE_LIMIT) + "...";
    }

    /**
     * 装载上下文：取最近 N 条消息转成 {@link Msg}。
     * 倒序取再反转，保证拿到的是「最近」而不是「最早」的一段。
     */
    private List<Msg> loadHistory(Long conversationId) {
        List<AiMessage> recent = messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getConversationId, conversationId)
                .in(AiMessage::getRole, List.of("user", "assistant"))
                .orderByDesc(AiMessage::getId)
                .last("limit " + HISTORY_LIMIT));
        List<Msg> messages = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            AiMessage message = recent.get(i);
            if (!StringUtils.hasText(message.getContent())) {
                continue;
            }
            if ("assistant".equals(message.getRole())) {
                messages.add(new AssistantMessage(message.getContent()));
            } else {
                messages.add(new UserMessage(message.getContent()));
            }
        }
        return messages;
    }

    private void saveUserMessage(Long conversationId, String content) {
        messageMapper.insert(AiMessage.builder()
                .conversationId(conversationId)
                .role("user")
                .content(content)
                .tokenUsage(0)
                .createTime(LocalDateTime.now())
                .build());
        AiConversation conversation = new AiConversation();
        conversation.setId(conversationId);
        conversation.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    private Long saveAssistantMessage(Long conversationId, String content, Collector collector) {
        try {
            AiMessage message = AiMessage.builder()
                    .conversationId(conversationId)
                    .role("assistant")
                    .content(content)
                    .toolCalls(writeToolCalls(collector.toolCalls))
                    .tokenUsage(collector.tokenUsage)
                    .createTime(LocalDateTime.now())
                    .build();
            messageMapper.insert(message);
            return message.getId();
        } catch (Exception e) {
            log.warn("保存 AI 消息失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 记录一次 AI 调用（成功与失败都记）。
     *
     * <p>留痕要能回答的四个问题：谁发的、用的哪个 Agent、走了哪些工具、花了多久/多少 token。
     * 没有这些时，「AI 答错了」只能靠用户口头描述，排查只能靠猜——
     * 而失败调用尤其必须留痕，因为成功时用户至少还看得到结果。
     */
    private void recordTask(Long conversationId, String prompt, String result, AgentContext context,
                            int status, String agentType, List<ToolCallVO> toolCalls,
                            int tokenUsage, long costMs, String errorMsg) {
        try {
            String toolJson = null;
            if (toolCalls != null && !toolCalls.isEmpty()) {
                try {
                    toolJson = objectMapper.writeValueAsString(toolCalls);
                } catch (Exception ignored) {
                    toolJson = null; // 序列化失败不该影响主链路
                }
            }
            aiTaskMapper.insert(AiTask.builder()
                    .type(TASK_TYPE_CHAT)
                    .userId(context.getUserId())
                    .refId(conversationId)
                    .prompt(truncate(prompt))
                    .result(truncate(result))
                    .status(status)
                    .roleCode(context.getRole())
                    .agentType(agentType)
                    .toolCalls(toolJson)
                    .tokenUsage(tokenUsage)
                    .costMs(costMs)
                    .errorMsg(errorMsg == null ? null : truncate(errorMsg))
                    .createTime(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("记录 AI 任务失败：{}", e.getMessage());
        }
    }

    /**
     * 按角色的每日调用上限（控成本）。
     *
     * <p>用 ai_task 表做**持久化**计数——重启不重置，这与「限流用内存」的取舍不同：
     * 限流允许重启后清零（丢了一次配额没人在意），而成本配额丢了会导致真实的费用超支。
     *
     * <p>配额按角色而不是按人头一刀切：学生是高频轻量问答，教师是低频重量调用，
     * 用一个数字约束两者必然顾此失彼（见 SecurityProperties.aiDailyLimitByRole）。
     */
    private void checkDailyQuota(AgentContext context) {
        long used = aiTaskMapper.selectCount(Wrappers.<AiTask>lambdaQuery()
                .eq(AiTask::getUserId, context.getUserId())
                .ge(AiTask::getCreateTime, LocalDate.now().atStartOfDay()));
        int limit = security.aiDailyLimitFor(context.getRole());
        if (used >= limit) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "今日 AI 调用次数已达上限（" + AiPermissions.labelOf(context.getRole()) + " " + limit
                            + " 次），请明天再试");
        }
    }

    /** 剩余配额：给前端显示「今天还能问几次」，比只在超限后报错友好 */
    public int remainingQuota(AgentContext context) {
        long used = aiTaskMapper.selectCount(Wrappers.<AiTask>lambdaQuery()
                .eq(AiTask::getUserId, context.getUserId())
                .ge(AiTask::getCreateTime, LocalDate.now().atStartOfDay()));
        int limit = security.aiDailyLimitFor(context.getRole());
        return Math.max((int) (limit - used), 0);
    }

    private RuntimeContext runtimeContext(AgentContext context, AiConversation conversation) {
        return RuntimeContext.builder()
                .sessionId(String.valueOf(conversation.getId()))
                .userId(context.getUsername())
                .build();
    }

    /** 未配置模型时的降级回复：明确告知「未启用」，绝不伪造内容 */
    private ChatReplyVO degradedReply(ChatRequestDTO dto, String reason) {
        ChatReplyVO vo = new ChatReplyVO();
        vo.setContent(reason
                + "。\n\n在没有大模型的情况下，以下能力仍然可用（不依赖模型、由 SQL 聚合直接生成）：\n"
                + "· 成绩统计与排名　· 知识点正确率统计　· 错题本与薄弱知识点\n"
                + "请到「成绩统计」与「错题本」页面查看，或在配置 api-key 后重新提问。");
        vo.setAgentType(AgentType.of(dto.getAgentType()).getCode());
        vo.setAiGenerated(false);
        return vo;
    }

    // ==================== 内部方法：序列化与 SSE 输出 ====================

    private String writeToolCalls(List<ToolCallVO> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(toolCalls);
        } catch (Exception e) {
            return null;
        }
    }

    private List<ToolCallVO> parseToolCalls(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ToolCallVO>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            // 客户端已断开：向 reactor 抛出让订阅方也停止（onCompletion 里也会兜底取消）
            throw new BusinessException("SSE 连接已断开");
        } catch (IllegalStateException e) {
            log.debug("SSE emitter 已结束，忽略后续事件：{}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(Map.of("message", message), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
