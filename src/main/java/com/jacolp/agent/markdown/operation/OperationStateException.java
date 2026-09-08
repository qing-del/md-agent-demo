package com.jacolp.agent.markdown.operation;

import com.jacolp.agent.markdown.exception.MarkdownException;

/**
 * 当操作生命周期转换不被允许时抛出。
 */
public final class OperationStateException extends MarkdownException {

    /**
     * 创建非法状态转换异常。
     *
     * @param opId 操作 UUID
     * @param status 当前状态
     * @param action 尝试执行的动作
     */
    public OperationStateException(java.util.UUID opId, OperationStatus status, String action) {
        super("Operation cannot be " + action + ": opId=" + opId + ", status=" + status);
    }
}
