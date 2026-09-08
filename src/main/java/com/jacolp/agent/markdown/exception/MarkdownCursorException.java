package com.jacolp.agent.markdown.exception;

/**
 * Thrown when a section cursor is malformed or does not match the request.
 */
public final class MarkdownCursorException extends MarkdownException {

    public MarkdownCursorException(String message) {
        super(message);
    }

    public MarkdownCursorException(String message, Throwable cause) {
        super(message, cause);
    }
}
