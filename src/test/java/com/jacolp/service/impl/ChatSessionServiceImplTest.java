package com.jacolp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.jacolp.agent.context.ChatContextManager;
import com.jacolp.mapper.ChatSessionMapper;
import com.jacolp.pojo.entity.ChatSession;
import com.jacolp.pojo.vo.ChatMessageVO;
import com.jacolp.pojo.vo.ChatSessionDetailVO;
import com.jacolp.pojo.vo.ChatSessionSummaryVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTest {

    @Mock
    private ChatSessionMapper mapper;

    @Mock
    private ChatContextManager chatContextManager;

    @Test
    void listMapsOnlySummaryFieldsAndNormalizesTitle() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 7, 12, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 7, 12, 5);
        ChatSession session = new ChatSession();
        session.setSessionKey("550e8400-e29b-41d4-a716-446655440000");
        session.setTitle("Spring AI");
        session.setMessages("[{\"role\":\"user\",\"content\":\"hidden\"}]");
        session.setReferencedFileContents("[{\"fileName\":\"guide.md\"}]");
        session.setCreatedAt(createdAt);
        session.setUpdatedAt(updatedAt);
        when(mapper.selectSummaryList("Spring")).thenReturn(List.of(session));

        List<ChatSessionSummaryVO> actual = new ChatSessionServiceImpl(mapper).list("  Spring  ");

        assertEquals(1, actual.size());
        assertEquals(session.getSessionKey(), actual.get(0).getSessionKey());
        assertEquals(session.getTitle(), actual.get(0).getTitle());
        assertEquals(createdAt, actual.get(0).getCreatedAt());
        assertEquals(updatedAt, actual.get(0).getUpdatedAt());
        verify(mapper).selectSummaryList("Spring");
    }

    @Test
    void blankTitleMeansNoFilter() {
        when(mapper.selectSummaryList(null)).thenReturn(List.of());

        assertEquals(List.of(), new ChatSessionServiceImpl(mapper).list("  "));

        verify(mapper).selectSummaryList(null);
    }

    @Test
    void escapesLikeWildcardsBeforeQuerying() {
        when(mapper.selectSummaryList("100!%"))
                .thenReturn(List.of());

        new ChatSessionServiceImpl(mapper).list("100%");

        verify(mapper).selectSummaryList("100!%");
    }

    @Test
    void getBySessionKeyMapsTheCurrentHistorySnapshot() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 9, 12, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 9, 12, 5);
        ChatContextManager.ChatSessionSnapshot snapshot = new ChatContextManager.ChatSessionSnapshot(
                "550e8400-e29b-41d4-a716-446655440000",
                "Spring AI",
                List.of(new UserMessage("hello"), new AssistantMessage("hi")),
                createdAt,
                updatedAt);
        when(this.chatContextManager.getExistingSnapshot(snapshot.sessionKey()))
                .thenReturn(Optional.of(snapshot));

        ChatSessionDetailVO actual = new ChatSessionServiceImpl(this.mapper, this.chatContextManager)
                .getBySessionKey(snapshot.sessionKey());

        assertEquals(snapshot.sessionKey(), actual.getSessionKey());
        assertEquals(snapshot.title(), actual.getTitle());
        assertEquals(createdAt, actual.getCreatedAt());
        assertEquals(updatedAt, actual.getUpdatedAt());
        assertEquals(List.of("user", "assistant"), actual.getMessages().stream()
                .map(ChatMessageVO::getRole)
                .toList());
        assertEquals(List.of("hello", "hi"), actual.getMessages().stream()
                .map(ChatMessageVO::getContent)
                .toList());
        verify(this.chatContextManager).getExistingSnapshot(snapshot.sessionKey());
        verifyNoInteractions(this.mapper);
    }

    @Test
    void getBySessionKeyReturnsNotFoundForAnUnknownSession() {
        when(this.chatContextManager.getExistingSnapshot("550e8400-e29b-41d4-a716-446655440000"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new ChatSessionServiceImpl(this.mapper, this.chatContextManager)
                        .getBySessionKey("550e8400-e29b-41d4-a716-446655440000"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getBySessionKeyRejectsAnInvalidUuidBeforeReadingTheContext() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new ChatSessionServiceImpl(this.mapper, this.chatContextManager)
                        .getBySessionKey("invalid"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(this.chatContextManager, this.mapper);
    }
}
