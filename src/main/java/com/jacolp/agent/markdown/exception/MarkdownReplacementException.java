package com.jacolp.agent.markdown.exception;

/**
 * Thrown when a requested Markdown replacement is invalid or ambiguous.
 */
public final class MarkdownReplacementException extends MarkdownException {

    public MarkdownReplacementException(String message) {
        super(message);
    }
}
