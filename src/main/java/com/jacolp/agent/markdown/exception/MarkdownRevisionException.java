package com.jacolp.agent.markdown.exception;

/** The supplied source no longer represents the proposal's base snapshot. */
public final class MarkdownRevisionException extends MarkdownException {
    public MarkdownRevisionException() {
        super("Document revision changed; read the current document and generate a new proposal");
    }
}
