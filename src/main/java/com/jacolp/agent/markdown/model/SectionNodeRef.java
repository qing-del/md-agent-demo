package com.jacolp.agent.markdown.model;

import java.util.Objects;

/**
 * 标识一个 Markdown 上下文中的指定章节。
 */
public final class SectionNodeRef {

    private final DocumentId documentId;

    private final int nodeNumber;

    /**
     * 创建章节引用。
     *
     * @param documentId 已持久化文档的标识
     * @param nodeNumber 节点编号
     */
    public SectionNodeRef(DocumentId documentId, int nodeNumber) {
        this.documentId = Objects.requireNonNull(documentId, "documentId cannot be null");
        if (nodeNumber <= 0) {
            // 节点编号从 1 开始，非法编号不能参与查询或替换。
            throw new IllegalArgumentException("nodeNumber must be positive");
        }
        this.nodeNumber = nodeNumber;
    }

    /**
     * 获取引用对应的文档标识。
     *
     * @return 文档标识
     */
    public DocumentId getDocumentId() {
        return this.documentId;
    }

    /**
     * 获取引用对应的节点编号。
     *
     * @return 节点编号
     */
    public int getNodeNumber() {
        return this.nodeNumber;
    }
}
