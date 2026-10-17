package com.zhikao.modules.ai.agent;

import com.zhikao.common.R;
import com.zhikao.common.RateLimit;
import com.zhikao.modules.ai.agent.dto.ChatRequestDTO;
import com.zhikao.modules.ai.agent.vo.ChatCatalogVO;
import com.zhikao.modules.ai.agent.vo.ChatMessageVO;
import com.zhikao.modules.ai.agent.vo.ChatReplyVO;
import com.zhikao.modules.ai.agent.vo.ConversationVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 智能对话接口（三种角色都可用，数据范围由工具层按角色隔离）。
 *
 * <p>为什么不限制为教师：学生的「错题分析 / 该复习什么」是这个系统最有价值的场景之一。
 * 学生的数据边界不靠「禁用接口」来保证，而靠 {@code AiTools} 里强制注入 student_id 过滤——
 * 这比在接口层一刀切更精确，也更能体现「按数据归属做隔离」的正确姿势。
 */
@RestController
@RequestMapping("/ai/chat")
public class AiChatController {

    private final AiChatService chatService;

    public AiChatController(AiChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 流式对话（SSE）：逐字返回，工具调用过程实时可见，客户端断开即中断。
     * 命中限流或未配置模型时，也通过 SSE 的 error 事件返回，前端统一处理。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(key = "ai_chat_stream", capacity = 20, refillPerSecond = 0.5,
            message = "AI 对话过于频繁，请稍后再试")
    public SseEmitter stream(@Valid @RequestBody ChatRequestDTO dto) {
        return chatService.stream(dto);
    }

    /** 同步对话：SSE 不可用时的兜底通道 */
    @PostMapping
    @RateLimit(key = "ai_chat", capacity = 20, refillPerSecond = 0.5,
            message = "AI 对话过于频繁，请稍后再试")
    public R<ChatReplyVO> chat(@Valid @RequestBody ChatRequestDTO dto) {
        return R.ok(chatService.chat(dto));
    }

    /** 会话列表 */
    @GetMapping("/conversations")
    public R<List<ConversationVO>> conversations() {
        return R.ok(chatService.conversations());
    }

    /**
     * 当前角色可用的 Agent 清单 + 能力边界说明。
     *
     * <p>前端用它渲染「我现在能用什么」：学生看到练习与错题分析，教师看到出题与阅卷。
     * 角色判断只发生在后端一处，前端不需要维护「学生不能显示出题」这类规则。
     */
    @GetMapping("/catalog")
    public R<ChatCatalogVO> catalog() {
        return R.ok(chatService.catalogView());
    }

    /** 某会话的历史消息 */
    @GetMapping("/conversations/{id}/messages")
    public R<List<ChatMessageVO>> messages(@PathVariable Long id) {
        return R.ok(chatService.messages(id));
    }

    /** 删除会话 */
    @DeleteMapping("/conversations/{id}")
    public R<Void> delete(@PathVariable Long id) {
        chatService.deleteConversation(id);
        return R.ok();
    }
}
