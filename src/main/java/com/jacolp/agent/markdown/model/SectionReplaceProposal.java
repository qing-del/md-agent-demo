package com.jacolp.agent.markdown.model;

import java.util.Objects;

import com.jacolp.agent.markdown.operation.Operation;
import com.jacolp.agent.markdown.operation.OperationStatus;

/**
 * 面向 Agent 和前端的 Markdown 替换提案摘要。
 */
public final class SectionReplaceProposal {

    private final String opId;

    private final long documentId;

    private final int nodeNumber;

    private final String originalText;

    private final String newText;

    private final OperationStatus status;

    /**
     * 创建替换提案摘要。
     *
     * @param opId 操作 UUID 字符串
     * @param documentId 文档主键
     * @param nodeNumber 章节节点编号
     * @param originalText 原始匹配文本
     * @param newText 替换文本
     * @param status 当前操作状态
     */
    public SectionReplaceProposal(
            String opId,
            long documentId,
            int nodeNumber,
            String originalText,
            String newText,
            OperationStatus status) {
        this.opId = Objects.requireNonNull(opId, "opId cannot be null");
        this.originalText = Objects.requireNonNull(originalText, "originalText cannot be null");
        this.newText = Objects.requireNonNull(newText, "newText cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        if (documentId <= 0) {
            throw new IllegalArgumentException("documentId must be positive");
        }
        if (nodeNumber <= 0) {
            throw new IllegalArgumentException("nodeNumber must be positive");
        }
        this.documentId = documentId;
        this.nodeNumber = nodeNumber;
    }

    /**
     * 从内部操作快照创建对外提案摘要。
     *
     * @param operation 内部操作快照
     * @return 对外提案摘要
     */
    public static SectionReplaceProposal from(Operation operation) {
        Objects.requireNonNull(operation, "operation cannot be null");
        SectionNodeRef section = operation.getSection();
        return new SectionReplaceProposal(
                operation.getOpId().toString(),
                section.getDocumentId().value(),
                section.getNodeNumber(),
                operation.getOriginalText(),
                operation.getNewText(),
                operation.getStatus());
    }

    public String getOpId() {
        return this.opId;
    }

    public long getDocumentId() {
        return this.documentId;
    }

    public int getNodeNumber() {
        return this.nodeNumber;
    }

    public String getOriginalText() {
        return this.originalText;
    }

    public String getNewText() {
        return this.newText;
    }

    public OperationStatus getStatus() {
        return this.status;
    }
}
