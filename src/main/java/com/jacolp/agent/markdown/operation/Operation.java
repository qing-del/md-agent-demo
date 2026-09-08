package com.jacolp.agent.markdown.operation;

import java.util.Objects;
import java.util.UUID;

import com.jacolp.agent.markdown.model.SectionNodeRef;

/**
 * Immutable public snapshot of an operation proposal and its status.
 */
public final class Operation {

    private final UUID opId;

    private final SectionNodeRef section;

    private final String originalText;

    private final String newText;

    private final OperationStatus status;

    public Operation(
            UUID opId,
            SectionNodeRef section,
            String originalText,
            String newText,
            OperationStatus status) {
        this.opId = Objects.requireNonNull(opId, "opId cannot be null");
        this.section = Objects.requireNonNull(section, "section cannot be null");
        this.originalText = Objects.requireNonNull(originalText, "originalText cannot be null");
        this.newText = Objects.requireNonNull(newText, "newText cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public UUID getOpId() {
        return this.opId;
    }

    public SectionNodeRef getSection() {
        return this.section;
    }

    public String getOriginalText() {
        return this.originalText;
    }

    public String getNewText() {
        return this.newText;
    }

    public OperationStatus getStatus() {
        return this.status;
    }
}
