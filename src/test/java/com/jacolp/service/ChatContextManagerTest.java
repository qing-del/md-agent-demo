package com.jacolp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.agent.context.ChatContextManager;
import com.jacolp.mapper.ChatSessionMapper;
import com.jacolp.pojo.dto.ChatMessageDTO;
import com.jacolp.pojo.dto.SelectionDTO;
import com.jacolp.pojo.entity.ChatSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

@ExtendWith(MockitoExtension.class)
class ChatContextManagerTest {

    private static final String SESSION_KEY = "550e8400-e29b-41d4-a716-446655440000";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Test
    void getReturnsTheLastTenSuccessfulRoundsAndSnapshotUsesTheSameWindow() throws Exception {
        when(this.chatSessionMapper.selectBySessionKey(SESSION_KEY)).thenReturn(session(1L, "[]", "[]"));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        ChatContextManager manager = manager();

        for (int index = 1; index <= 12; index++) {
            manager.add(SESSION_KEY, new UserMessage("message-" + index));
            manager.add(SESSION_KEY, new AssistantMessage("answer-" + index));
        }

        List<Message> context = manager.get(SESSION_KEY);
        assertEquals(20, context.size());
        assertEquals("message-3", context.get(0).getText());
        assertEquals("answer-12", context.get(19).getText());

        assertEquals(1, manager.flushDirtySessions(false));
        ArgumentCaptor<ChatSession> snapshotCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(this.chatSessionMapper).updateSnapshot(snapshotCaptor.capture());
        JsonNode messages = this.objectMapper.readTree(snapshotCaptor.getValue().getMessages());
        assertEquals(20, messages.size());
        assertEquals("message-3", messages.get(0).get("content").asText());
        assertEquals("user", messages.get(0).get("role").asText());
    }

    @Test
    void cacheMissLoadsMessagesAndReferencesOnlyOnce() {
        when(this.chatSessionMapper.selectBySessionKey(SESSION_KEY)).thenReturn(session(
                7L,
                "[{\"role\":\"user\",\"content\":\"hello\"},"
                        + "{\"role\":\"assistant\",\"content\":\"hi\"}]",
                "[{\"fileName\":\"guide.md\"}]"));
        ChatContextManager manager = manager();

        List<Message> firstRead = manager.get(SESSION_KEY);
        List<Message> secondRead = manager.get(SESSION_KEY);

        assertEquals(2, firstRead.size());
        assertEquals(UserMessage.class, firstRead.get(0).getClass());
        assertEquals("hello", firstRead.get(0).getText());
        assertEquals(AssistantMessage.class, firstRead.get(1).getClass());
        assertEquals("hi", secondRead.get(1).getText());
        verify(this.chatSessionMapper, times(1)).selectBySessionKey(SESSION_KEY);
    }

    @Test
    void clearMarksTheConversationDirtyAndPreservesReferenceMetadata() throws Exception {
        when(this.chatSessionMapper.selectBySessionKey(SESSION_KEY)).thenReturn(session(
                3L,
                "[{\"role\":\"user\",\"content\":\"hello\"}]",
                "[{\"fileName\":\"guide.md\"}]"));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        ChatContextManager manager = manager();

        manager.clear(SESSION_KEY);

        assertEquals(List.of(), manager.get(SESSION_KEY));
        assertEquals(1, manager.flushDirtySessions(false));
        ArgumentCaptor<ChatSession> snapshotCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(this.chatSessionMapper).updateSnapshot(snapshotCaptor.capture());
        assertEquals("[]", snapshotCaptor.getValue().getMessages());
        assertEquals("[{\"fileName\":\"guide.md\"}]",
                snapshotCaptor.getValue().getReferencedFileContents());
    }

    @Test
    void appendsMultipleReferenceRecordsAndPreservesExistingReferences() throws Exception {
        when(this.chatSessionMapper.selectBySessionKey(SESSION_KEY)).thenReturn(session(
                8L,
                "[]",
                "[{\"fileName\":\"guide.md\"}]"));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        ChatContextManager manager = manager();

        manager.appendReferenceMetadata(SESSION_KEY, new ChatMessageDTO(
                "first",
                List.of(1L, 2L),
                List.of(new SelectionDTO(2L, "first selection", "# Guide"))));
        manager.appendReferenceMetadata(SESSION_KEY, new ChatMessageDTO(
                "second",
                List.of(1L, 2L),
                List.of(new SelectionDTO(2L, "first selection", "# Guide"))));

        assertEquals(1, manager.flushDirtySessions(false));
        ArgumentCaptor<ChatSession> snapshotCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(this.chatSessionMapper).updateSnapshot(snapshotCaptor.capture());
        JsonNode references = this.objectMapper.readTree(snapshotCaptor.getValue().getReferencedFileContents());
        assertEquals(3, references.size());
        assertEquals("guide.md", references.get(0).get("fileName").asText());
        assertEquals(List.of(1, 2), this.objectMapper.convertValue(
                references.get(1).get("documentIds"), List.class));
        assertEquals("first selection", references.get(1).get("selections").get(0)
                .get("originalText").asText());
        assertEquals(List.of(1, 2), this.objectMapper.convertValue(
                references.get(2).get("documentIds"), List.class));
    }

    @Test
    void emptyReferenceMetadataDoesNotMarkConversationDirty() {
        ChatContextManager manager = manager();

        manager.appendReferenceMetadata(SESSION_KEY, new ChatMessageDTO("hello", List.of(), List.of()));

        assertEquals(0, manager.flushDirtySessions(false));
        verify(this.chatSessionMapper, never()).updateSnapshot(any(ChatSession.class));
    }

    @Test
    void successfulTurnStoresOriginalQuestionAndReferenceSnapshot() throws Exception {
        when(this.chatSessionMapper.selectBySessionKey(SESSION_KEY)).thenReturn(session(10L, "[]", "[]"));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        ChatContextManager manager = manager();
        ChatMessageDTO message = new ChatMessageDTO(
                "original question",
                List.of(3L),
                List.of(new SelectionDTO(3L, "selected", "# Guide")));

        String answer = manager.executeSuccessfulTurn(SESSION_KEY, message, () -> {
            manager.add(SESSION_KEY, new UserMessage("original question\n\n<selections>temporary</selections>"));
            manager.add(SESSION_KEY, new AssistantMessage("final answer"));
            return "final answer";
        });

        assertEquals("final answer", answer);
        assertEquals(1, manager.flushDirtySessions(false));
        ArgumentCaptor<ChatSession> snapshotCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(this.chatSessionMapper).updateSnapshot(snapshotCaptor.capture());
        JsonNode snapshot = snapshotCaptor.getValue() == null
                ? null
                : this.objectMapper.readTree(snapshotCaptor.getValue().getMessages());
        assertEquals("original question", snapshot.get(0).get("content").asText());
        assertEquals(1, this.objectMapper.readTree(snapshotCaptor.getValue().getReferencedFileContents()).size());
    }

    @Test
    void failedTurnRollsBackAdvisorMessagesAndReferences() {
        when(this.chatSessionMapper.selectBySessionKey(SESSION_KEY)).thenReturn(session(11L, "[]", "[]"));
        ChatContextManager manager = manager();

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> manager.executeSuccessfulTurn(SESSION_KEY,
                        new ChatMessageDTO("question", List.of(1L), List.of()),
                        () -> {
                            manager.add(SESSION_KEY, new UserMessage("question"));
                            throw new IllegalStateException("model failed");
                        }));

        assertEquals(List.of(), manager.get(SESSION_KEY));
        assertEquals(0, manager.flushDirtySessions(false));
        verify(this.chatSessionMapper, never()).updateSnapshot(any(ChatSession.class));
    }

    @Test
    void missingConversationCreatesANewSession() {
        when(this.chatSessionMapper.selectBySessionKey(SESSION_KEY)).thenReturn(null);
        doAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(404L);
            return 1;
        }).when(this.chatSessionMapper).insert(any(ChatSession.class));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        ChatContextManager manager = manager();

        manager.add(SESSION_KEY, new UserMessage("hello"));

        assertEquals(1, manager.flushDirtySessions(false));
        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(this.chatSessionMapper).insert(sessionCaptor.capture());
        assertEquals(SESSION_KEY, sessionCaptor.getValue().getSessionKey());
    }

    private ChatContextManager manager() {
        return new ChatContextManager(this.chatSessionMapper, this.objectMapper);
    }

    private static ChatSession session(long id, String messages, String references) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setMessages(messages);
        session.setReferencedFileContents(references);
        return session;
    }
}
