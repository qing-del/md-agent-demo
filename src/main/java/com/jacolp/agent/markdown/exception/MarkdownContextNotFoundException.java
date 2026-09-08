package com.jacolp.agent.markdown.exception;

import java.util.UUID;

/**
 * Thrown when a Markdown context UUID is not registered.
 */
public final class MarkdownContextNotFoundException extends MarkdownException {

    public MarkdownContextNotFoundException(UUID key) {
        super("Markdown context not found: " + key);
    }
}
