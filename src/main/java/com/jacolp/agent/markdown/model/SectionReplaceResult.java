package com.jacolp.agent.markdown.model;

import java.util.Objects;

import com.jacolp.agent.markdown.operation.Operation;
import com.jacolp.agent.markdown.operation.OperationStatus;

/**
 * 已确认 Markdown 替换的结果摘要。
 */
public final class SectionReplaceResult {

    private final String opId;

    private final long documentId;

    private final int nodeNumber;

    private final OperationStatus status;

    private final String revision;

    /**
     * 创建替换结果摘要。
     *
     * @param opId 操作 UUID 字符串
     * @param documentId 文档主键
     * @param nodeNumber 章节节点编号
     * @param status 操作状态
     * @param revision 替换后文档 revision
     */
    public SectionReplaceResult(
            String opId,
            long documentId,
            int nodeNumber,
            OperationStatus status,
            String revision) {
        this.opId = Objects.requireNonNull(opId, "opId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.revision = Objects.requireNonNull(revision, "revision cannot be null");
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
     * 从确认后的内部结果创建对外摘要。
     *
     * @param operation 更新后的操作快照
     * @param result 替换结果
     * @return 替换结果摘要
     */
    public static SectionReplaceResult from(Operation operation, ReplaceResult result) {
        Objects.requireNonNull(operation, "operation cannot be null");
        Objects.requireNonNull(result, "result cannot be null");
        SectionNodeRef section = operation.getSection();
        return new SectionReplaceResult(
                operation.getOpId().toString(),
                section.getDocumentId().value(),
                section.getNodeNumber(),
                operation.getStatus(),
                result.getContext().getRevision());
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

    public OperationStatus getStatus() {
        return this.status;
    }

    public String getRevision() {
        return this.revision;
    }
}
