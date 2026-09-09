package com.jacolp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.agent.context.ChatContextManager;
import com.jacolp.mapper.ChatSessionMapper;
import com.jacolp.pojo.dto.ChatMessageDTO;
import com.jacolp.pojo.dto.ChatRequestDTO;
import com.jacolp.pojo.dto.SelectionDTO;
import com.jacolp.pojo.entity.ChatSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    private static final String SESSION_KEY = "550e8400-e29b-41d4-a716-446655440000";

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
        when(this.chatSessionMapper.selectBySessionKey(SESSION_KEY)).thenReturn(session(5L));
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
                new ChatRequestDTO(SESSION_KEY, new ChatMessageDTO("hello", List.of(), List.of())));

        assertEquals("answer", response.content());
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, SESSION_KEY);
    }

    @Test
    void chatPassesContentAndPersistsReferenceMetadata() throws Exception {
        when(this.chatSessionMapper.selectBySessionKey(SESSION_KEY)).thenReturn(session(6L));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        when(this.chatClient.prompt()).thenReturn(this.requestSpec);
        when(this.requestSpec.user("inspect these documents")).thenReturn(this.requestSpec);
        when(this.requestSpec.call()).thenReturn(this.responseSpec);
        when(this.responseSpec.content()).thenReturn("answer");

        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        doAnswer(invocation -> {
            Consumer<ChatClient.AdvisorSpec> consumer = invocation.getArgument(0);
            consumer.accept(advisorSpec);
            return this.requestSpec;
        }).when(this.requestSpec).advisors(any(Consumer.class));

        ObjectMapper objectMapper = new ObjectMapper();
        ChatContextManager chatContextManager = new ChatContextManager(
                this.chatSessionMapper, objectMapper);
        ChatController controller = new ChatController(this.chatClient, chatContextManager);
        ChatMessageDTO message = new ChatMessageDTO(
                "inspect these documents",
                List.of(1L, 2L, 3L),
                List.of(new SelectionDTO(2L, "selected text", "# Guide｜## Install")));

        ChatController.ChatResponse response = controller.chat(new ChatRequestDTO(SESSION_KEY, message));

        assertEquals("answer", response.content());
        assertEquals(1, chatContextManager.flushDirtySessions(false));
        verify(this.requestSpec).user("inspect these documents");
        ArgumentCaptor<ChatSession> snapshotCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(this.chatSessionMapper).updateSnapshot(snapshotCaptor.capture());
        JsonNode references = objectMapper.readTree(snapshotCaptor.getValue().getReferencedFileContents());
        assertEquals(1, references.size());
        JsonNode reference = references.get(0);
        assertEquals(3, reference.get("documentIds").size());
        assertEquals(1L, reference.get("documentIds").get(0).asLong());
        assertEquals(2L, reference.get("documentIds").get(1).asLong());
        assertEquals(3L, reference.get("documentIds").get(2).asLong());
        JsonNode selection = reference.get("selections").get(0);
        assertEquals("selected text", selection.get("originalText").asText());
        assertEquals("# Guide｜## Install", selection.get("sectionText").asText());
        assertFalse(selection.has("content"));
    }

    @Test
    void chatDoesNotPersistAnEmptyReferenceRecord() {
        when(this.chatSessionMapper.selectBySessionKey(SESSION_KEY)).thenReturn(session(7L));
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

        ChatContextManager chatContextManager = new ChatContextManager(
                this.chatSessionMapper, new ObjectMapper());
        ChatController controller = new ChatController(this.chatClient, chatContextManager);

        controller.chat(new ChatRequestDTO(
                SESSION_KEY, new ChatMessageDTO("hello", List.of(), List.of())));

        assertEquals(0, chatContextManager.flushDirtySessions(false));
        verify(this.chatSessionMapper, never()).updateSnapshot(any(ChatSession.class));
    }

    @Test
    void chatRejectsMissingSessionKey() {
        ChatController controller = new ChatController(
                this.chatClient,
                new ChatContextManager(this.chatSessionMapper, new ObjectMapper()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.chat(new ChatRequestDTO(
                        null, new ChatMessageDTO("hello", List.of(), List.of()))));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void chatRejectsInvalidSessionKey() {
        ChatController controller = new ChatController(
                this.chatClient,
                new ChatContextManager(this.chatSessionMapper, new ObjectMapper()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.chat(new ChatRequestDTO(
                        "invalid", new ChatMessageDTO("hello", List.of(), List.of()))));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void chatRejectsMissingMessage() {
        ChatController controller = new ChatController(
                this.chatClient,
                new ChatContextManager(this.chatSessionMapper, new ObjectMapper()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.chat(new ChatRequestDTO(SESSION_KEY, null)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void chatRejectsBlankContent() {
        ChatController controller = new ChatController(
                this.chatClient,
                new ChatContextManager(this.chatSessionMapper, new ObjectMapper()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.chat(new ChatRequestDTO(
                        SESSION_KEY, new ChatMessageDTO("  ", List.of(), List.of()))));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void chatRejectsNonPositiveDocumentId() {
        ChatController controller = new ChatController(
                this.chatClient,
                new ChatContextManager(this.chatSessionMapper, new ObjectMapper()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.chat(new ChatRequestDTO(
                        SESSION_KEY, new ChatMessageDTO("hello", List.of(0L), List.of()))));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void chatRejectsSelectionWithMissingRequiredField() {
        ChatController controller = new ChatController(
                this.chatClient,
                new ChatContextManager(this.chatSessionMapper, new ObjectMapper()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.chat(new ChatRequestDTO(
                        SESSION_KEY,
                        new ChatMessageDTO(
                                "hello", List.of(), List.of(new SelectionDTO(1L, "selected", null))))));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private static ChatSession session(long id) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setSessionKey(SESSION_KEY);
        session.setMessages("[]");
        session.setReferencedFileContents("[]");
        return session;
    }
}
