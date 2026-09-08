package com.jacolp.agent.markdown.operation;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.jacolp.agent.markdown.model.SectionNodeRef;

/**
 * Mutable internal operation state with atomic lifecycle transitions.
 */
final class OperationState {

    private final UUID opId;

    private final SectionNodeRef section;

    private final String originalText;

    private final String newText;

    private final AtomicReference<OperationStatus> status = new AtomicReference<>(OperationStatus.PENDING);

    OperationState(UUID opId, SectionNodeRef section, String originalText, String newText) {
        this.opId = Objects.requireNonNull(opId, "opId cannot be null");
        this.section = Objects.requireNonNull(section, "section cannot be null");
        this.originalText = Objects.requireNonNull(originalText, "originalText cannot be null");
        this.newText = Objects.requireNonNull(newText, "newText cannot be null");
    }

    boolean transition(OperationStatus expected, OperationStatus replacement) {
        return this.status.compareAndSet(expected, replacement);
    }

    OperationStatus status() {
        return this.status.get();
    }

    Operation snapshot() {
        return new Operation(this.opId, this.section, this.originalText, this.newText, this.status());
    }
}
