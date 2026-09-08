package com.jacolp.agent.markdown;

import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.jacolp.agent.markdown.exception.MarkdownContextNotFoundException;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.agent.markdown.model.SectionNode;

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

    /**
     * Gets the immutable snapshot registered under a UUID.
     *
     * @param key context UUID
     * @return current immutable context
     * @throws MarkdownContextNotFoundException when the UUID is unknown
     */
    public MarkdownContext getEntity(UUID key) {
        Objects.requireNonNull(key, "key cannot be null");
        MarkdownContext context = this.contexts.get(key);
        if (context == null) {
            throw new MarkdownContextNotFoundException(key);
        }
        return context;
    }

    /**
     * Renders the numbered heading tree in source order.
     *
     * @param key context UUID
     * @return one numbered heading per line, indented by logical tree depth
     */
    public String getHeadingTree(UUID key) {
        MarkdownContext context = getEntity(key);
        StringBuilder result = new StringBuilder();
        Set<Integer> rendered = new HashSet<>();
        for (Integer rootNodeId : context.getRootNodeIds()) {
            appendHeadingTree(context, rootNodeId, 0, result, rendered);
        }
        return result.toString();
    }

    private static void appendHeadingTree(
            MarkdownContext context,
            int nodeNumber,
            int depth,
            StringBuilder result,
            Set<Integer> rendered) {
        SectionNode node = context.getNodes().get(nodeNumber);
        if (node == null || !rendered.add(nodeNumber)) {
            return;
        }
        if (result.length() > 0) {
            result.append('\n');
        }
        result.append("  ".repeat(depth))
                .append(node.getNumber())
                .append(". ")
                .append(rawHeading(context, node));
        for (Integer child : node.getChildren()) {
            appendHeadingTree(context, child, depth + 1, result, rendered);
        }
    }

    private static String rawHeading(MarkdownContext context, SectionNode node) {
        String heading = context.getSource().substring(node.getHeadingStart(), node.getBodyStart());
        if (heading.endsWith("\r\n")) {
            return heading.substring(0, heading.length() - 2);
        }
        if (heading.endsWith("\r") || heading.endsWith("\n")) {
            return heading.substring(0, heading.length() - 1);
        }
        return heading;
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
