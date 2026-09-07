package com.jacolp.agent.context;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时持久化内存上下文管理器中已变更的聊天会话。
 */
@Component
public class ChatSessionFlushScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatSessionFlushScheduler.class);

    private final ChatContextManager chatContextManager;

    /**
     * 创建聊天会话刷新任务。
     *
     * @param chatContextManager 聊天上下文管理器
     */
    public ChatSessionFlushScheduler(ChatContextManager chatContextManager) {
        this.chatContextManager = chatContextManager;
    }

    /**
     * 按配置的固定间隔刷新当前实例中已变更的会话。
     *
     * <p>刷新过程不会等待正在处理请求的会话，避免定时任务阻塞模型调用。</p>
     */
    @Scheduled(fixedDelayString = "${chat.persistence.flush-interval-ms:120000}")
    public void flushDirtySessions() {
        int flushedCount = this.chatContextManager.flushDirtySessions(false);
        // 没有成功刷新的会话时不输出普通日志，避免定时任务产生大量无效日志。
        if (flushedCount > 0) {
            LOGGER.debug("Flushed {} dirty chat session(s)", flushedCount);
        }
    }

    /**
     * 应用关闭前等待正在执行的会话请求，并刷新剩余脏数据。
     */
    @PreDestroy
    public void flushOnShutdown() {
        int flushedCount = this.chatContextManager.flushDirtySessions(true);
        if (flushedCount > 0) {
            LOGGER.info("Flushed {} dirty chat session(s) during shutdown", flushedCount);
        }
    }
}
