package com.jacolp.pojo.dto;

/**
 * 聊天请求中添加到上下文的 Markdown 选区。
 */
public class SelectionDTO {

    private Long documentId;

    private String originalText;

    private String sectionText;

    /**
     * 创建空的选区对象，供框架反序列化使用。
     */
    public SelectionDTO() {
    }

    /**
     * 创建完整的选区对象。
     *
     * @param documentId 选区所属文档 ID
     * @param originalText 前端实际选中的原始文本
     * @param sectionText 选区所属的标题路径
     */
    public SelectionDTO(Long documentId, String originalText, String sectionText) {
        this.documentId = documentId;
        this.originalText = originalText;
        this.sectionText = sectionText;
    }

    /**
     * 获取选区所属文档 ID。
     *
     * @return 文档 ID
     */
    public Long getDocumentId() {
        return documentId;
    }

    /**
     * 设置选区所属文档 ID。
     *
     * @param documentId 文档 ID
     */
    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    /**
     * 获取前端实际选中的原始文本。
     *
     * @return 原始文本
     */
    public String getOriginalText() {
        return originalText;
    }

    /**
     * 设置前端实际选中的原始文本。
     *
     * @param originalText 原始文本
     */
    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    /**
     * 获取选区所属的标题路径。
     *
     * @return 标题路径
     */
    public String getSectionText() {
        return sectionText;
    }

    /**
     * 设置选区所属的标题路径。
     *
     * @param sectionText 标题路径
     */
    public void setSectionText(String sectionText) {
        this.sectionText = sectionText;
    }
}
