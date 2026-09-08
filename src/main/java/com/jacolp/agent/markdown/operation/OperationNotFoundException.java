package com.jacolp.agent.markdown.operation;

import java.util.UUID;

import com.jacolp.agent.markdown.exception.MarkdownException;

/**
 * 当操作 UUID 未知或已失效时抛出。
 */
public final class OperationNotFoundException extends MarkdownException {

    /**
     * 创建操作不存在异常。
     *
     * @param opId 未找到的操作 UUID
     */
    public OperationNotFoundException(UUID opId) {
        super("Markdown operation not found or expired: " + opId);
    }
}
