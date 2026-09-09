package com.jacolp.pojo.vo;

import java.util.Objects;

import com.jacolp.agent.markdown.operation.Operation;

/**
 * 聊天响应中返回给前端的 Markdown 替换提案。
 *
 * <p>内部章节编号不会暴露给前端，前端只使用标题路径和精确文本生成本地 diff。</p>
 */
public final class ChatOperationVO {

    private final String opId;

    private final long documentId;

    private final String sectionText;

    private final String originalText;

    private final String newText;

    /**
     * 创建聊天响应操作。
     *
     * @param opId 操作 UUID 字符串
     * @param documentId 文档主键
     * @param sectionText 标题路径
     * @param originalText 原始文本
     * @param newText 替换文本
     */
    public ChatOperationVO(
            String opId,
            long documentId,
            String sectionText,
            String originalText,
            String newText) {
        this.opId = Objects.requireNonNull(opId, "opId cannot be null");
        this.sectionText = Objects.requireNonNull(sectionText, "sectionText cannot be null");
        this.originalText = Objects.requireNonNull(originalText, "originalText cannot be null");
        this.newText = Objects.requireNonNull(newText, "newText cannot be null");
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        this.documentId = documentId;
    }

    /**
     * 从内部操作快照和标题路径构造聊天响应操作。
     *
     * @param operation 内部操作快照
     * @param sectionText 当前文档中的标题路径
     * @return 前端响应对象
     */
    public static ChatOperationVO from(Operation operation, String sectionText) {
        Objects.requireNonNull(operation, "operation cannot be null");
        return new ChatOperationVO(
                operation.getOpId().toString(),
                operation.getSection().getDocumentId().value(),
                sectionText,
                operation.getOriginalText(),
                operation.getNewText());
    }

    public String getOpId() {
        return this.opId;
    }

    public long getDocumentId() {
        return this.documentId;
    }

    public String getSectionText() {
        return this.sectionText;
    }

    public String getOriginalText() {
        return this.originalText;
    }

    public String getNewText() {
        return this.newText;
    }
}
