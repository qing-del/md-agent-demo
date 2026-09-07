package com.jacolp.pojo.entity;

import java.time.LocalDateTime;

public class ChatSession {

    private long id;
    private String title;
    private String messages = "[]";
    private String referencedFileContents = "[]";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ChatSession() {
    }

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

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessages() {
        return messages;
    }

    public void setMessages(String messages) {
        this.messages = messages;
    }

    public String getReferencedFileContents() {
        return referencedFileContents;
    }

    public void setReferencedFileContents(String referencedFileContents) {
        this.referencedFileContents = referencedFileContents;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
