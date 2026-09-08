package com.jacolp.agent.markdown.exception;

import com.jacolp.agent.markdown.model.DocumentId;

/**
 * 当 Markdown 上下文对应的文档 ID 未注册时抛出。
 */
public final class MarkdownContextNotFoundException extends MarkdownException {

    /**
     * 创建上下文不存在异常。
     *
     * @param documentId 未找到的文档标识
     */
    public MarkdownContextNotFoundException(DocumentId documentId) {
        super("Markdown context not found: " + documentId);
    }
}
