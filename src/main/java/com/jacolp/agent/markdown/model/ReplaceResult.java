package com.jacolp.agent.markdown.model;

import java.util.Objects;

/**
 * Result of a successful Markdown replacement.
 */
public final class ReplaceResult {

    private final MarkdownContext context;

    public ReplaceResult(MarkdownContext context) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
    }

    public MarkdownContext getContext() {
        return this.context;
    }
}
