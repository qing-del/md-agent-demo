package com.jacolp.pojo.dto;

public class MdDocumentDTO {

    private String fileName;
    private String content;
    private long fileSizeBytes;

    public MdDocumentDTO() {
    }

    public MdDocumentDTO(String fileName, String content, long fileSizeBytes) {
        this.fileName = fileName;
        this.content = content;
        this.fileSizeBytes = fileSizeBytes;
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
}
