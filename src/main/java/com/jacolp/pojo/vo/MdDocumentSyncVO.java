package com.jacolp.pojo.vo;

import java.time.LocalDateTime;

/**
 * 文档草稿同步结果。
 */
public class MdDocumentSyncVO {

    private long documentId;

    private String status;

    private LocalDateTime updatedAt;

    /**
     * 创建空的同步结果对象，供框架序列化使用。
     */
    public MdDocumentSyncVO() {
    }

    /**
     * 创建同步成功结果。
     *
     * @param documentId 文档 ID
     * @param status 同步状态
     * @param updatedAt 数据库更新时间
     */
    public MdDocumentSyncVO(long documentId, String status, LocalDateTime updatedAt) {
        this.documentId = documentId;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    /**
     * 获取文档 ID。
     *
     * @return 文档 ID
     */
    public long getDocumentId() {
        return this.documentId;
    }

    /**
     * 设置文档 ID。
     *
     * @param documentId 文档 ID
     */
    public void setDocumentId(long documentId) {
        this.documentId = documentId;
    }

    /**
     * 获取同步状态。
     *
     * @return 同步状态
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * 设置同步状态。
     *
     * @param status 同步状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取数据库更新时间。
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    /**
     * 设置数据库更新时间。
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
