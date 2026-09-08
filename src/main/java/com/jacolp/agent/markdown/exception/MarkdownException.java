package com.jacolp.agent.markdown.exception;

/**
 * Base exception for Markdown SDK failures.
 */
public class MarkdownException extends RuntimeException {

    public MarkdownException(String message) {
        super(message);
    }

    public MarkdownException(String message, Throwable cause) {
        super(message, cause);
    }
}
