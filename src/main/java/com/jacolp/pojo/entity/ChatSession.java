package com.jacolp.pojo.entity;

import java.time.LocalDateTime;

/**
 * 聊天会话实体，包含数据库中的对话和文件引用 JSON 快照。
 */
public class ChatSession {

    private long id;
    private String title;
    private String messages = "[]";
    private String referencedFileContents = "[]";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建带有默认空 JSON 快照的聊天会话。
     */
    public ChatSession() {
    }

    /**
     * 创建完整的聊天会话实体。
     *
     * @param id 聊天会话 ID
     * @param title 会话标题
     * @param messages 消息 JSON 快照
     * @param referencedFileContents 文件引用 JSON 快照
     * @param createdAt 创建时间
     * @param updatedAt 最后更新时间
     */
    public ChatSession(
            long id,
            String title,
            String messages,
            String referencedFileContents,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.messages = messages;
        this.referencedFileContents = referencedFileContents;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 获取聊天会话 ID。
     *
     * @return 聊天会话 ID
     */
    public long getId() {
        return id;
    }

    /**
     * 设置聊天会话 ID。
     *
     * @param id 聊天会话 ID
     */
    public void setId(long id) {
        this.id = id;
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
     * 获取消息 JSON 快照。
     *
     * @return 消息 JSON 快照
     */
    public String getMessages() {
        return messages;
    }

    /**
     * 设置消息 JSON 快照。
     *
     * @param messages 消息 JSON 快照
     */
    public void setMessages(String messages) {
        this.messages = messages;
    }

    /**
     * 获取文件引用 JSON 快照。
     *
     * @return 文件引用 JSON 快照
     */
    public String getReferencedFileContents() {
        return referencedFileContents;
    }

    /**
     * 设置文件引用 JSON 快照。
     *
     * @param referencedFileContents 文件引用 JSON 快照
     */
    public void setReferencedFileContents(String referencedFileContents) {
        this.referencedFileContents = referencedFileContents;
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
