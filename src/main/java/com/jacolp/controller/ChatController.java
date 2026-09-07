package com.jacolp.controller;

import com.jacolp.service.ChatContextManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 提供基于已有聊天会话的对话接口。
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
     * 使用指定聊天会话处理一条用户消息。
     *
     * @param request 包含会话 ID 和用户消息的请求
     * @return AI 生成的回复
     * @throws ResponseStatusException 请求参数非法或会话不存在时抛出
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        // 会话 ID 是上下文记忆和数据库记录的唯一关联，缺失或非正数时无法安全处理请求。
        if (request == null || request.chatSessionId() == null || request.chatSessionId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "chatSessionId must be a positive session id");
        }
        // 空消息不会产生有效的对话轮次，因此在调用模型前直接拒绝。
        if (request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }

        String conversationId = Long.toString(request.chatSessionId());
        // 整个模型调用期间持有会话锁，避免同一会话的用户消息和 AI 回复交叉写入历史。
        String content = this.chatContextManager.withConversationLock(conversationId, () -> this.chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(request.message())
                .call()
                .content());

        return new ChatResponse(content);
    }

    /**
     * 聊天请求参数。
     *
     * @param chatSessionId 要继续对话的聊天会话 ID
     * @param message 用户原始消息
     */
    public record ChatRequest(Long chatSessionId, String message) {
    }

    /**
     * 聊天接口响应。
     *
     * @param content AI 回复内容
     */
    public record ChatResponse(String content) {
    }
}
