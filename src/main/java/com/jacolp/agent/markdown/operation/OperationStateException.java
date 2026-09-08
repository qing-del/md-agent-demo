package com.jacolp.agent.markdown.operation;

import com.jacolp.agent.markdown.exception.MarkdownException;

/**
 * Thrown when an operation lifecycle transition is not allowed.
 */
public final class OperationStateException extends MarkdownException {

    public OperationStateException(java.util.UUID opId, OperationStatus status, String action) {
        super("Operation cannot be " + action + ": opId=" + opId + ", status=" + status);
    }
}
