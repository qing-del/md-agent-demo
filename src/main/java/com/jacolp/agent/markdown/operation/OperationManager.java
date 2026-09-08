package com.jacolp.agent.markdown.operation;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.exception.MarkdownReplacementException;
import com.jacolp.agent.markdown.exception.SectionNotFoundException;
import com.jacolp.agent.markdown.model.ReplaceResult;
import com.jacolp.agent.markdown.model.SectionNodeRef;

/**
 * In-memory manager for Markdown replacement proposals awaiting confirmation.
 */
public final class OperationManager {

    private final MarkdownManager markdownManager;

    private final ConcurrentMap<UUID, OperationState> operations = new ConcurrentHashMap<>();

    public OperationManager(MarkdownManager markdownManager) {
        this.markdownManager = Objects.requireNonNull(markdownManager, "markdownManager cannot be null");
    }

    /**
     * Creates a pending replacement proposal.
     *
     * @param section target context and node
     * @param originalText exact text proposed for replacement
     * @param newText replacement text; an empty value is allowed
     * @return pending operation snapshot
     */
    public Operation create(SectionNodeRef section, String originalText, String newText) {
        Objects.requireNonNull(section, "section cannot be null");
        Objects.requireNonNull(originalText, "originalText cannot be null");
        Objects.requireNonNull(newText, "newText cannot be null");
        if (originalText.isEmpty()) {
            throw new MarkdownReplacementException("originalText must not be empty");
        }

        if (!this.markdownManager.getEntity(section.getKey()).getNodes().containsKey(section.getNodeNumber())) {
            throw new SectionNotFoundException(section.getKey(), section.getNodeNumber());
        }

        UUID opId = UUID.randomUUID();
        OperationState state = new OperationState(opId, section, originalText, newText);
        this.operations.put(opId, state);
        return state.snapshot();
    }

    /**
     * Gets the latest immutable snapshot for an operation.
     *
     * @param opId operation UUID
     * @return current operation snapshot
     * @throws OperationNotFoundException when the operation is unknown
     */
    public Operation get(UUID opId) {
        Objects.requireNonNull(opId, "opId cannot be null");
        OperationState state = this.operations.get(opId);
        if (state == null) {
            throw new OperationNotFoundException(opId);
        }
        return state.snapshot();
    }

    /**
     * Atomically confirms and executes one pending replacement proposal.
     *
     * @param opId operation UUID
     * @return replacement result containing the new Markdown snapshot
     * @throws OperationNotFoundException when the operation is unknown
     * @throws OperationStateException when it has already left PENDING
     */
    public ReplaceResult confirm(UUID opId) {
        Objects.requireNonNull(opId, "opId cannot be null");
        OperationState state = this.operations.get(opId);
        if (state == null) {
            throw new OperationNotFoundException(opId);
        }
        if (!state.transition(OperationStatus.PENDING, OperationStatus.EXECUTING)) {
            throw new OperationStateException(opId, state.status(), "confirmed");
        }

        try {
            ReplaceResult result = this.markdownManager.replace(
                    state.section(), state.originalText(), state.newText());
            state.transition(OperationStatus.EXECUTING, OperationStatus.COMPLETED);
            return result;
        }
        catch (RuntimeException exception) {
            state.transition(OperationStatus.EXECUTING, OperationStatus.FAILED);
            throw exception;
        }
    }

    /**
     * Cancels a pending operation without changing Markdown content.
     *
     * @param opId operation UUID
     * @return cancelled operation snapshot
     * @throws OperationNotFoundException when the operation is unknown
     * @throws OperationStateException when it has already left PENDING
     */
    public Operation cancel(UUID opId) {
        Objects.requireNonNull(opId, "opId cannot be null");
        OperationState state = this.operations.get(opId);
        if (state == null) {
            throw new OperationNotFoundException(opId);
        }
        if (!state.transition(OperationStatus.PENDING, OperationStatus.CANCELLED)) {
            throw new OperationStateException(opId, state.status(), "cancelled");
        }
        return state.snapshot();
    }

    MarkdownManager markdownManager() {
        return this.markdownManager;
    }

    ConcurrentMap<UUID, OperationState> operations() {
        return this.operations;
    }
}
