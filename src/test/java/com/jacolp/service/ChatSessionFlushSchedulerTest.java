package com.jacolp.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.mapper.ChatSessionMapper;
import com.jacolp.pojo.entity.ChatSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.UserMessage;

@ExtendWith(MockitoExtension.class)
class ChatSessionFlushSchedulerTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    private ExecutorService executor;

    @AfterEach
    void shutdownExecutor() {
        if (this.executor != null) {
            this.executor.shutdownNow();
        }
    }

    @Test
    void scheduledFlushWritesMessagesAndReferencesAndDoesNotRepeatAfterSuccess() {
        when(this.chatSessionMapper.selectById(1L)).thenReturn(session(
                1L, "[]", "[{\"fileName\":\"guide.md\"}]"));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        ChatContextManager manager = manager();
        ChatSessionFlushScheduler scheduler = new ChatSessionFlushScheduler(manager);

        manager.add("1", new UserMessage("hello"));
        scheduler.flushDirtySessions();
        scheduler.flushDirtySessions();

        ArgumentCaptor<ChatSession> snapshotCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(this.chatSessionMapper, times(1)).updateSnapshot(snapshotCaptor.capture());
        assertTrue(snapshotCaptor.getValue().getMessages().contains("hello"));
        assertTrue(snapshotCaptor.getValue().getReferencedFileContents().contains("guide.md"));
    }

    @Test
    void failedFlushKeepsTheConversationDirtyForRetry() {
        when(this.chatSessionMapper.selectById(2L)).thenReturn(session(2L, "[]", "[]"));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class)))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(1);
        ChatContextManager manager = manager();
        ChatSessionFlushScheduler scheduler = new ChatSessionFlushScheduler(manager);

        manager.add("2", new UserMessage("retry me"));
        scheduler.flushDirtySessions();
        scheduler.flushDirtySessions();

        verify(this.chatSessionMapper, times(2)).updateSnapshot(any(ChatSession.class));
    }

    @Test
    void scheduledFlushSkipsAConversationWhileARequestIsInFlight() throws Exception {
        when(this.chatSessionMapper.selectById(3L)).thenReturn(session(3L, "[]", "[]"));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        ChatContextManager manager = manager();
        ChatSessionFlushScheduler scheduler = new ChatSessionFlushScheduler(manager);
        manager.add("3", new UserMessage("in flight"));

        CountDownLatch requestEntered = new CountDownLatch(1);
        CountDownLatch releaseRequest = new CountDownLatch(1);
        this.executor = Executors.newSingleThreadExecutor();
        Future<?> request = this.executor.submit(() -> manager.withConversationLock("3", () -> {
            requestEntered.countDown();
            try {
                releaseRequest.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));

        assertTrue(requestEntered.await(5, TimeUnit.SECONDS));
        scheduler.flushDirtySessions();
        verify(this.chatSessionMapper, never()).updateSnapshot(any(ChatSession.class));

        releaseRequest.countDown();
        request.get(5, TimeUnit.SECONDS);
        scheduler.flushDirtySessions();
        verify(this.chatSessionMapper).updateSnapshot(any(ChatSession.class));
    }

    @Test
    void shutdownFlushWaitsForAnInFlightConversation() throws Exception {
        when(this.chatSessionMapper.selectById(4L)).thenReturn(session(4L, "[]", "[]"));
        when(this.chatSessionMapper.updateSnapshot(any(ChatSession.class))).thenReturn(1);
        ChatContextManager manager = manager();
        ChatSessionFlushScheduler scheduler = new ChatSessionFlushScheduler(manager);
        manager.add("4", new UserMessage("shutdown"));

        CountDownLatch requestEntered = new CountDownLatch(1);
        CountDownLatch releaseRequest = new CountDownLatch(1);
        this.executor = Executors.newFixedThreadPool(2);
        Future<?> request = this.executor.submit(() -> manager.withConversationLock("4", () -> {
            requestEntered.countDown();
            try {
                releaseRequest.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        assertTrue(requestEntered.await(5, TimeUnit.SECONDS));

        Future<?> shutdown = this.executor.submit(scheduler::flushOnShutdown);
        assertFalse(shutdown.isDone());
        verify(this.chatSessionMapper, never()).updateSnapshot(any(ChatSession.class));

        releaseRequest.countDown();
        request.get(5, TimeUnit.SECONDS);
        shutdown.get(5, TimeUnit.SECONDS);
        verify(this.chatSessionMapper).updateSnapshot(any(ChatSession.class));
    }

    private ChatContextManager manager() {
        return new ChatContextManager(this.chatSessionMapper, new ObjectMapper());
    }

    private static ChatSession session(long id, String messages, String references) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setMessages(messages);
        session.setReferencedFileContents(references);
        return session;
    }
}
