package com.jacolp.pojo.dto;

/**
 * 接收 Markdown 文档写入请求的数据传输对象。
 */
public class MdDocumentDTO {

    private String fileName;
    private String content;
    private long fileSizeBytes;

    /**
     * 创建空的文档请求对象，供框架反序列化使用。
     */
    public MdDocumentDTO() {
    }

    /**
     * 创建包含文档基本信息的请求对象。
     *
     * @param fileName 文档文件名
     * @param content 文档内容
     * @param fileSizeBytes 文档字节数
     */
    public MdDocumentDTO(String fileName, String content, long fileSizeBytes) {
        this.fileName = fileName;
        this.content = content;
        this.fileSizeBytes = fileSizeBytes;
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
}
