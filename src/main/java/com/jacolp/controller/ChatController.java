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

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    private final ChatContextManager chatContextManager;

    public ChatController(ChatClient chatClient, ChatContextManager chatContextManager) {
        this.chatClient = chatClient;
        this.chatContextManager = chatContextManager;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        if (request == null || request.chatSessionId() == null || request.chatSessionId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "chatSessionId must be a positive session id");
        }
        if (request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }

        String conversationId = Long.toString(request.chatSessionId());
        String content = this.chatContextManager.withConversationLock(conversationId, () -> this.chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(request.message())
                .call()
                .content());

        return new ChatResponse(content);
    }

    public record ChatRequest(Long chatSessionId, String message) {
    }

    public record ChatResponse(String content) {
    }
}
