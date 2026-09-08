package com.jacolp.agent.markdown.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable Markdown snapshot and its parsed section index.
 */
public final class MarkdownContext {

    private final UUID key;

    private final String revision;

    private final String source;

    private final List<Integer> rootNodeIds;

    private final Map<Integer, SectionNode> nodes;

    /**
     * Creates a Markdown snapshot.
     *
     * @param key context UUID
     * @param revision content revision
     * @param source complete original Markdown source
     * @param rootNodeIds root section numbers in source order
     * @param nodes section nodes indexed by section number
     */
    public MarkdownContext(
            UUID key,
            String revision,
            String source,
            List<Integer> rootNodeIds,
            Map<Integer, SectionNode> nodes) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
        this.revision = Objects.requireNonNull(revision, "revision cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.rootNodeIds = List.copyOf(Objects.requireNonNull(rootNodeIds, "rootNodeIds cannot be null"));

        Map<Integer, SectionNode> copiedNodes = new LinkedHashMap<>(
                Objects.requireNonNull(nodes, "nodes cannot be null"));
        if (copiedNodes.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new IllegalArgumentException("nodes cannot contain null keys or values");
        }
        this.nodes = Collections.unmodifiableMap(copiedNodes);
    }

    public UUID getKey() {
        return this.key;
    }

    public String getRevision() {
        return this.revision;
    }

    public String getSource() {
        return this.source;
    }

    public List<Integer> getRootNodeIds() {
        return this.rootNodeIds;
    }

    public Map<Integer, SectionNode> getNodes() {
        return this.nodes;
    }
}
