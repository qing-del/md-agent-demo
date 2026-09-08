package com.jacolp.agent.markdown.operation;

import java.util.UUID;

import com.jacolp.agent.markdown.exception.MarkdownException;

/**
 * Thrown when an operation UUID is unknown or expired.
 */
public final class OperationNotFoundException extends MarkdownException {

    public OperationNotFoundException(UUID opId) {
        super("Markdown operation not found or expired: " + opId);
    }
}
