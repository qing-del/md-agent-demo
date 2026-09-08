package com.jacolp.agent.markdown.model;

import java.util.Objects;

/**
 * One byte-bounded page of a section.
 */
public final class SectionPage {

    private final String content;

    private final boolean hasMore;

    private final String nextCursor;

    public SectionPage(String content, boolean hasMore, String nextCursor) {
        this.content = Objects.requireNonNull(content, "content cannot be null");
        if (!hasMore && nextCursor != null) {
            throw new IllegalArgumentException("nextCursor must be null when hasMore is false");
        }
        if (hasMore && (nextCursor == null || nextCursor.isBlank())) {
            throw new IllegalArgumentException("nextCursor is required when hasMore is true");
        }
        this.hasMore = hasMore;
        this.nextCursor = nextCursor;
    }

    public String getContent() {
        return this.content;
    }

    public boolean isHasMore() {
        return this.hasMore;
    }

    public String getNextCursor() {
        return this.nextCursor;
    }
}
