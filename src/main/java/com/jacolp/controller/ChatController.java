package com.jacolp.controller;

import java.util.UUID;

import com.jacolp.agent.audit.AuditContext;
import com.jacolp.agent.context.ChatContextManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 提供由前端 UUID 标识的聊天对话接口。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    private final ChatContextManager chatContextManager;

    /**
     * 创建聊天控制器。
     *
     * @param chatClient Spring AI 聊天客户端
     * @param chatContextManager 用于串行化同一会话请求的上下文管理器
     */
    public ChatController(ChatClient chatClient, ChatContextManager chatContextManager) {
        this.chatClient = chatClient;
        this.chatContextManager = chatContextManager;
    }

    /**
     * 使用指定 UUID 会话处理一条用户消息。
     *
     * @param request 包含 UUID 会话标识和用户消息的请求
     * @return AI 生成的回复
     * @throws ResponseStatusException 请求参数非法时抛出
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        // 会话 UUID 是前端与持久化记录的稳定关联，格式错误时不能安全创建或恢复会话。
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request must not be null");
        }
        // 空消息不会产生有效的对话轮次，因此在调用模型前直接拒绝。
        if (request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }

        String conversationId = canonicalSessionKey(request.sessionKey());
        try (AuditContext.Scope ignored = AuditContext.open(conversationId)) {
            // 整个模型调用期间持有会话锁，避免同一会话的用户消息和 AI 回复交叉写入历史。
            String content = this.chatContextManager.withConversationLock(conversationId,
                    () -> this.chatClient.prompt()
                            .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                            .user(request.message())
                            .call()
                            .content());

            return new ChatResponse(content);
        }
    }

    /**
     * 校验并规范化前端传入的 UUID 会话标识。
     *
     * @param sessionKey 前端生成的 UUID 会话标识
     * @return 小写、标准格式的 UUID 会话标识
     * @throws ResponseStatusException 会话标识为空或格式非法时抛出
     */
    private static String canonicalSessionKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionKey must be a UUID");
        }
        try {
            UUID uuid = UUID.fromString(sessionKey);
            // UUID.fromString 接受部分非标准缩写，因此再比对标准形式以保证数据库键唯一。
            if (!uuid.toString().equalsIgnoreCase(sessionKey)) {
                throw new IllegalArgumentException("sessionKey must use the standard UUID format");
            }
            return uuid.toString();
        }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionKey must be a UUID", exception);
        }
    }

    /**
     * 聊天请求参数。
     *
     * @param sessionKey 前端生成的 UUID 会话标识
     * @param message 用户原始消息
     */
    public record ChatRequest(String sessionKey, String message) {
    }

    /**
     * 聊天接口响应。
     *
     * @param content AI 回复内容
     */
    public record ChatResponse(String content) {
    }
}
