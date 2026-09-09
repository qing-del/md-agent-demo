package com.jacolp.pojo.dto;

/**
 * 接收前端完整 Markdown 草稿的请求对象。
 */
public class MdDocumentDraftDTO {

    private String content;

    /**
     * 创建空的草稿请求对象，供框架反序列化使用。
     */
    public MdDocumentDraftDTO() {
    }

    /**
     * 创建包含 Markdown 正文的草稿请求对象。
     *
     * @param content 当前完整 Markdown 草稿
     */
    public MdDocumentDraftDTO(String content) {
        this.content = content;
    }

    /**
     * 获取完整 Markdown 正文。
     *
     * @return Markdown 正文
     */
    public String getContent() {
        return this.content;
    }

    /**
     * 设置完整 Markdown 正文。
     *
     * @param content Markdown 正文
     */
    public void setContent(String content) {
        this.content = content;
    }
}
