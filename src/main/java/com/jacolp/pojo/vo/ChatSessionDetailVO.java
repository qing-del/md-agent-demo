package com.jacolp.pojo.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 聊天会话详情视图对象，供前端恢复历史聊天窗口使用。
 */
public final class ChatSessionDetailVO {

    private final String sessionKey;

    private final String title;

    private final List<ChatMessageVO> messages;

    private final LocalDateTime createdAt;

    private final LocalDateTime updatedAt;

    /**
     * 创建聊天会话详情对象。
     *
     * @param sessionKey UUID 会话标识
     * @param title 会话标题
     * @param messages 可展示的历史消息
     * @param createdAt 会话创建时间
     * @param updatedAt 会话最后更新时间
     */
    public ChatSessionDetailVO(
            String sessionKey,
            String title,
            List<ChatMessageVO> messages,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.sessionKey = Objects.requireNonNull(sessionKey, "sessionKey cannot be null");
        this.title = title;
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 获取 UUID 会话标识。
     *
     * @return UUID 会话标识
     */
    public String getSessionKey() {
        return this.sessionKey;
    }

    /**
     * 获取会话标题。
     *
     * @return 会话标题
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * 获取可展示的历史消息。
     *
     * @return 历史消息列表
     */
    public List<ChatMessageVO> getMessages() {
        return this.messages;
    }

    /**
     * 获取会话创建时间。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    /**
     * 获取会话最后更新时间。
     *
     * @return 最后更新时间
     */
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }
}
