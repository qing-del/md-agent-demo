package com.jacolp.agent.markdown.exception;

import com.jacolp.agent.markdown.model.DocumentId;

/**
 * 当上下文中不存在指定章节编号时抛出。
 */
public final class SectionNotFoundException extends MarkdownException {

    /**
     * 创建章节不存在异常。
     *
     * @param documentId 上下文对应的文档标识
     * @param nodeNumber 未找到的节点编号
     */
    public SectionNotFoundException(DocumentId documentId, int nodeNumber) {
        super("Markdown section not found: documentId=" + documentId + ", nodeNumber=" + nodeNumber);
    }
}
