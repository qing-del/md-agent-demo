package com.jacolp.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically persists dirty chat sessions held by the in-memory context manager.
 */
@Component
public class ChatSessionFlushScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatSessionFlushScheduler.class);

    private final ChatContextManager chatContextManager;

    public ChatSessionFlushScheduler(ChatContextManager chatContextManager) {
        this.chatContextManager = chatContextManager;
    }

    @Scheduled(fixedDelayString = "${chat.persistence.flush-interval-ms:120000}")
    public void flushDirtySessions() {
        int flushedCount = this.chatContextManager.flushDirtySessions(false);
        if (flushedCount > 0) {
            LOGGER.debug("Flushed {} dirty chat session(s)", flushedCount);
        }
    }

    @PreDestroy
    public void flushOnShutdown() {
        int flushedCount = this.chatContextManager.flushDirtySessions(true);
        if (flushedCount > 0) {
            LOGGER.info("Flushed {} dirty chat session(s) during shutdown", flushedCount);
        }
    }
}
