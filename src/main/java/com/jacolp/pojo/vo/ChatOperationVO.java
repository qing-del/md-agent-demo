package com.jacolp.pojo.vo;

import java.util.Objects;
import java.util.List;
import com.jacolp.agent.markdown.operation.OperationDetails;
import com.jacolp.agent.markdown.model.EditDiff;

import com.jacolp.agent.markdown.operation.Operation;

/**
 * 聊天响应中返回给前端的版本化 Markdown 编辑提案。
 *
 * <p>保留旧替换字段；新客户端使用 kind、baseRevision 和逐项差异，并通过 preview 试应用。</p>
 */
public final class ChatOperationVO {

    private final OperationDetails details;

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
        this(opId, documentId, sectionText, originalText, newText, null);
    }

    private ChatOperationVO(String opId, long documentId, String sectionText, String originalText,
            String newText, OperationDetails details) {
        this.details = details;
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
                operation.getNewText(), operation.details());
    }

    public String getKind() {
        return this.details == null ? "replace" : this.details.kind();
    }

    public String getBaseRevision() {
        return this.details == null ? null : this.details.baseRevision();
    }

    public List<EditDiff> getEdits() {
        return this.details == null ? List.of() : this.details.diffs();
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
