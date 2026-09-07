package com.jacolp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChatContextManagerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChatSessionMapper chatSessionMapper;

    @Test
    void getReturnsTheLastTenMessagesWhileSnapshotKeepsTheFullHistory() throws Exception {
        when(this.chatSessionMapper.selectById(1L)).thenReturn(session(1L, "[]", "[]"));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        ChatContextManager manager = manager();

        for (int index = 1; index <= 12; index++) {
            manager.add("1", new UserMessage("message-" + index));
        }

        List<Message> context = manager.get("1");
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
        when(this.chatSessionMapper.selectById(7L)).thenReturn(session(
                7L,
                "[{\"role\":\"user\",\"content\":\"hello\"},"
                        + "{\"role\":\"assistant\",\"content\":\"hi\"}]",
                "[{\"fileName\":\"guide.md\"}]"));
        ChatContextManager manager = manager();

        List<Message> firstRead = manager.get("7");
        List<Message> secondRead = manager.get("7");

        assertEquals(2, firstRead.size());
        assertEquals(UserMessage.class, firstRead.get(0).getClass());
        assertEquals("hello", firstRead.get(0).getText());
        assertEquals(AssistantMessage.class, firstRead.get(1).getClass());
        assertEquals("hi", secondRead.get(1).getText());
        verify(this.chatSessionMapper, times(1)).selectById(7L);
    }

    @Test
    void clearMarksTheConversationDirtyAndPreservesReferenceMetadata() throws Exception {
        when(this.chatSessionMapper.selectById(3L)).thenReturn(session(
                3L,
                "[{\"role\":\"user\",\"content\":\"hello\"}]",
                "[{\"fileName\":\"guide.md\"}]"));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        ChatContextManager manager = manager();

        manager.clear("3");

        assertEquals(List.of(), manager.get("3"));
        assertEquals(1, manager.flushDirtySessions(false));
        ArgumentCaptor<ChatSession> snapshotCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(this.chatSessionMapper).updateSnapshot(snapshotCaptor.capture());
        assertEquals("[]", snapshotCaptor.getValue().getMessages());
        assertEquals("[{\"fileName\":\"guide.md\"}]",
                snapshotCaptor.getValue().getReferencedFileContents());
    }

    @Test
    void missingConversationReturnsNotFound() {
        when(this.chatSessionMapper.selectById(404L)).thenReturn(null);
        ChatContextManager manager = manager();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> manager.get("404"));

        assertEquals(404, exception.getStatusCode().value());
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
