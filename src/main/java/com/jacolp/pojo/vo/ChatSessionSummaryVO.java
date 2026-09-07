package com.jacolp.pojo.vo;

import java.time.LocalDateTime;

import com.jacolp.pojo.entity.ChatSession;

/**
 * 聊天会话列表中的摘要视图对象，不包含消息和文件引用内容。
 */
public class ChatSessionSummaryVO {

    private String sessionKey;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建空的聊天会话摘要对象，供框架序列化使用。
     */
    public ChatSessionSummaryVO() {
    }

    /**
     * 创建完整的聊天会话摘要对象。
     *
     * @param sessionKey 前端生成的 UUID 会话标识
     * @param title 会话标题
     * @param createdAt 创建时间
     * @param updatedAt 最后更新时间
     */
    public ChatSessionSummaryVO(
            String sessionKey,
            String title,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.sessionKey = sessionKey;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 将聊天会话实体转换为列表摘要视图。
     *
     * @param session 聊天会话实体
     * @return 聊天会话摘要视图对象
     */
    public static ChatSessionSummaryVO from(ChatSession session) {
        return new ChatSessionSummaryVO(
                session.getSessionKey(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }

    /**
     * 获取 UUID 会话标识。
     *
     * @return UUID 会话标识
     */
    public String getSessionKey() {
        return sessionKey;
    }

    /**
     * 设置 UUID 会话标识。
     *
     * @param sessionKey UUID 会话标识
     */
    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    /**
     * 获取会话标题。
     *
     * @return 会话标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置会话标题。
     *
     * @param title 会话标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取最后更新时间。
     *
     * @return 最后更新时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置最后更新时间。
     *
     * @param updatedAt 最后更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
