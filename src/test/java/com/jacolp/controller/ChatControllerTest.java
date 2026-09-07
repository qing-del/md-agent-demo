package com.jacolp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.mapper.ChatSessionMapper;
import com.jacolp.pojo.entity.ChatSession;
import com.jacolp.service.ChatContextManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Test
    void chatPassesConversationIdToMemoryAdvisor() {
        when(this.chatSessionMapper.selectById(5L)).thenReturn(session(5L));
        when(this.chatClient.prompt()).thenReturn(this.requestSpec);
        when(this.requestSpec.user("hello")).thenReturn(this.requestSpec);
        when(this.requestSpec.call()).thenReturn(this.responseSpec);
        when(this.responseSpec.content()).thenReturn("answer");

        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        doAnswer(invocation -> {
            Consumer<ChatClient.AdvisorSpec> consumer = invocation.getArgument(0);
            consumer.accept(advisorSpec);
            return this.requestSpec;
        }).when(this.requestSpec).advisors(any(Consumer.class));

        ChatController controller = new ChatController(
                this.chatClient,
                new ChatContextManager(this.chatSessionMapper, new ObjectMapper()));

        ChatController.ChatResponse response = controller.chat(
                new ChatController.ChatRequest(5L, "hello"));

        assertEquals("answer", response.content());
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, "5");
    }

    @Test
    void chatRejectsMissingSessionId() {
        ChatController controller = new ChatController(
                this.chatClient,
                new ChatContextManager(this.chatSessionMapper, new ObjectMapper()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.chat(new ChatController.ChatRequest(null, "hello")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void chatRejectsNonPositiveSessionId() {
        ChatController controller = new ChatController(
                this.chatClient,
                new ChatContextManager(this.chatSessionMapper, new ObjectMapper()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.chat(new ChatController.ChatRequest(0L, "hello")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private static ChatSession session(long id) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setMessages("[]");
        session.setReferencedFileContents("[]");
        return session;
    }
}
