package com.jacolp.agent.markdown.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies one section within one Markdown context.
 */
public final class SectionNodeRef {

    private final UUID key;

    private final int nodeNumber;

    public SectionNodeRef(UUID key, int nodeNumber) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
        if (nodeNumber <= 0) {
            throw new IllegalArgumentException("nodeNumber must be positive");
        }
        this.nodeNumber = nodeNumber;
    }

    public UUID getKey() {
        return this.key;
    }

    public int getNodeNumber() {
        return this.nodeNumber;
    }
}
