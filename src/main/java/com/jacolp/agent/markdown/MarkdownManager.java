package com.jacolp.agent.markdown;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.jacolp.agent.markdown.model.MarkdownContext;

/**
 * Framework-neutral manager for UUID-keyed Markdown contexts.
 */
public final class MarkdownManager {

    private final MarkdownStore store;

    private final MarkdownAstParser parser = new MarkdownAstParser();

    private final ConcurrentMap<UUID, MarkdownContext> contexts = new ConcurrentHashMap<>();

    private final ConcurrentMap<UUID, ReentrantLock> replacementLocks = new ConcurrentHashMap<>();

    /**
     * Creates an empty manager.
     *
     * @param store persistence boundary used by replacements
     */
    public MarkdownManager(MarkdownStore store) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
    }

    /**
     * Creates a manager and immediately registers one initial Markdown snapshot.
     *
     * @param store persistence boundary used by replacements
     * @param key context UUID
     * @param source complete Markdown source
     */
    public MarkdownManager(MarkdownStore store, UUID key, String source) {
        this(store);
        register(key, source);
    }

    /**
     * Parses and publishes a current snapshot for a UUID.
     *
     * @param key context UUID
     * @param source complete Markdown source
     * @return newly published immutable context
     */
    public MarkdownContext register(UUID key, String source) {
        MarkdownContext context = this.parser.parse(key, source);
        this.contexts.put(key, context);
        return context;
    }

    MarkdownStore store() {
        return this.store;
    }

    MarkdownAstParser parser() {
        return this.parser;
    }

    ConcurrentMap<UUID, MarkdownContext> contexts() {
        return this.contexts;
    }

    ConcurrentMap<UUID, ReentrantLock> replacementLocks() {
        return this.replacementLocks;
    }
}
