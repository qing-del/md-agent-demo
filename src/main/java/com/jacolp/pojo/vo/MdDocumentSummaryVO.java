package com.jacolp.pojo.vo;

import java.time.LocalDateTime;

import com.jacolp.pojo.entity.MdDocument;

public class MdDocumentSummaryVO {

    private long id;
    private String fileName;
    private long fileSizeBytes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MdDocumentSummaryVO() {
    }

    public MdDocumentSummaryVO(
            long id,
            String fileName,
            long fileSizeBytes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MdDocumentSummaryVO from(MdDocument document) {
        return new MdDocumentSummaryVO(
                document.getId(),
                document.getFileName(),
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
