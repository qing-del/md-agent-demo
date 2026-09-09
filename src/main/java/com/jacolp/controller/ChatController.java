package com.jacolp.controller;

import java.util.List;
import java.util.UUID;

import com.jacolp.agent.audit.AuditContext;
import com.jacolp.agent.context.ChatContextManager;
import com.jacolp.pojo.dto.ChatMessageDTO;
import com.jacolp.pojo.dto.ChatRequestDTO;
import com.jacolp.pojo.dto.SelectionDTO;
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
    public ChatResponse chat(@RequestBody ChatRequestDTO request) {
        // 会话 UUID 是前端与持久化记录的稳定关联，格式错误时不能安全创建或恢复会话。
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request must not be null");
        }
        ChatMessageDTO message = request.getMessage();
        if (message == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be null");
        }
        // 空消息不会产生有效的对话轮次，因此在调用模型前直接拒绝。
        if (message.getContent() == null || message.getContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message.content must not be blank");
        }
        validateDocumentIds(message.getDocumentIds());
        validateSelections(message.getSelections());

        String conversationId = canonicalSessionKey(request.getSessionKey());
        try (AuditContext.Scope ignored = AuditContext.open(conversationId)) {
            // 整个模型调用期间持有会话锁，避免同一会话的用户消息和 AI 回复交叉写入历史。
            String content = this.chatContextManager.withConversationLock(conversationId,
                    () -> {
                        // 引用元数据与本轮模型调用共享会话锁，避免并发请求交叉追加引用记录。
                        this.chatContextManager.appendReferenceMetadata(conversationId, message);
                        return this.chatClient.prompt()
                                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                                .user(message.getContent())
                                .call()
                                .content();
                    });

            return new ChatResponse(content);
        }
    }

    /**
     * 校验用户明确引用的文档 ID。
     *
     * @param documentIds 文档 ID 列表
     * @throws ResponseStatusException 文档 ID 不是正数时抛出
     */
    private static void validateDocumentIds(List<Long> documentIds) {
        if (documentIds == null) {
            return;
        }
        for (Long documentId : documentIds) {
            if (documentId == null || documentId <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "message.documentIds must contain positive document IDs");
            }
        }
    }

    /**
     * 校验聊天请求中的 Markdown 选区。
     *
     * @param selections 选区列表
     * @throws ResponseStatusException 选区字段缺失或为空白时抛出
     */
    private static void validateSelections(List<SelectionDTO> selections) {
        if (selections == null) {
            return;
        }
        for (SelectionDTO selection : selections) {
            if (selection == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message.selections must not contain null");
            }
            if (selection.getDocumentId() == null || selection.getDocumentId() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "message.selections.documentId must be positive");
            }
            if (selection.getOriginalText() == null || selection.getOriginalText().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "message.selections.originalText must not be blank");
            }
            if (selection.getSectionText() == null || selection.getSectionText().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "message.selections.sectionText must not be blank");
            }
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
     * 聊天接口响应。
     *
     * @param content AI 回复内容
     */
    public record ChatResponse(String content) {
    }
}
