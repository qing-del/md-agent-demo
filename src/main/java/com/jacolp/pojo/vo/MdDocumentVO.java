package com.jacolp.pojo.vo;

import java.time.LocalDateTime;

import com.jacolp.pojo.entity.MdDocument;

/**
 * Markdown 文档详情视图对象，包含文档正文。
 */
public class MdDocumentVO {

    private long id;
    private String fileName;
    private String content;
    private long fileSizeBytes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建空的文档详情对象，供框架序列化使用。
     */
    public MdDocumentVO() {
    }

    /**
     * 创建完整的文档详情对象。
     *
     * @param id 文档 ID
     * @param fileName 文档文件名
     * @param content 文档内容
     * @param fileSizeBytes 文档字节数
     * @param createdAt 创建时间
     * @param updatedAt 最后更新时间
     */
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

    /**
     * 将文档实体转换为详情视图。
     *
     * @param document Markdown 文档实体
     * @return 文档详情视图对象
     */
    public static MdDocumentVO from(MdDocument document) {
        return new MdDocumentVO(
                document.getId(),
                document.getFileName(),
                document.getContent(),
                document.getFileSizeBytes(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }

    /**
     * 获取文档 ID。
     *
     * @return 文档 ID
     */
    public long getId() {
        return id;
    }

    /**
     * 设置文档 ID。
     *
     * @param id 文档 ID
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * 获取文档文件名。
     *
     * @return 文档文件名
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 设置文档文件名。
     *
     * @param fileName 文档文件名
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 获取文档内容。
     *
     * @return 文档内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置文档内容。
     *
     * @param content 文档内容
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取文档字节数。
     *
     * @return 文档字节数
     */
    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    /**
     * 设置文档字节数。
     *
     * @param fileSizeBytes 文档字节数
     */
    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
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
