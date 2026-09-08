package com.jacolp.agent.markdown.exception;

import java.util.UUID;

/**
 * Thrown when a section number is not present in a context.
 */
public final class SectionNotFoundException extends MarkdownException {

    public SectionNotFoundException(UUID key, int nodeNumber) {
        super("Markdown section not found: key=" + key + ", nodeNumber=" + nodeNumber);
    }
}
