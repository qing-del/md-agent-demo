package com.jacolp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.mapper.ChatSessionMapper;
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
    void getReturnsTheLastTenMessagesWhileSnapshotKeepsTheFullHistory() throws Exception {
        when(this.chatSessionMapper.selectBySessionKey(SESSION_KEY)).thenReturn(session(1L, "[]", "[]"));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        ChatContextManager manager = manager();

        for (int index = 1; index <= 12; index++) {
            manager.add(SESSION_KEY, new UserMessage("message-" + index));
        }

        List<Message> context = manager.get(SESSION_KEY);
        assertEquals(10, context.size());
        assertEquals("message-3", context.get(0).getText());
        assertEquals("message-12", context.get(9).getText());

        assertEquals(1, manager.flushDirtySessions(false));
        ArgumentCaptor<ChatSession> snapshotCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(this.chatSessionMapper).updateSnapshot(snapshotCaptor.capture());
        JsonNode messages = this.objectMapper.readTree(snapshotCaptor.getValue().getMessages());
        assertEquals(12, messages.size());
        assertEquals("message-1", messages.get(0).get("content").asText());
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
