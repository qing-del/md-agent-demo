package com.jacolp.agent.markdown.operation;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.jacolp.agent.markdown.MarkdownManager;

/**
 * In-memory manager for Markdown replacement proposals awaiting confirmation.
 */
public final class OperationManager {

    private final MarkdownManager markdownManager;

    private final ConcurrentMap<UUID, OperationState> operations = new ConcurrentHashMap<>();

    public OperationManager(MarkdownManager markdownManager) {
        this.markdownManager = Objects.requireNonNull(markdownManager, "markdownManager cannot be null");
    }

    MarkdownManager markdownManager() {
        return this.markdownManager;
    }

    ConcurrentMap<UUID, OperationState> operations() {
        return this.operations;
    }
}
