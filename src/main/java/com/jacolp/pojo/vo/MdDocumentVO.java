package com.jacolp.pojo.vo;

import java.time.LocalDateTime;

import com.jacolp.pojo.entity.MdDocument;

public class MdDocumentVO {

    private long id;
    private String fileName;
    private String content;
    private long fileSizeBytes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MdDocumentVO() {
    }

    public MdDocumentVO(
            long id,
            String fileName,
            String content,
            long fileSizeBytes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.fileName = fileName;
        this.content = content;
        this.fileSizeBytes = fileSizeBytes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MdDocumentVO from(MdDocument document) {
        return new MdDocumentVO(
                document.getId(),
                document.getFileName(),
                document.getContent(),
                document.getFileSizeBytes(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
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
