package com.jacolp.agent.markdown.exception;

/**
 * Thrown when a replacement cannot be persisted.
 */
public final class MarkdownPersistenceException extends MarkdownException {

    public MarkdownPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
