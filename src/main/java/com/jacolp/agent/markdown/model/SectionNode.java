package com.jacolp.agent.markdown.model;

import java.util.List;
import java.util.Objects;

/**
 * An immutable heading section in a Markdown snapshot.
 */
public final class SectionNode {

    private final int number;

    private final int level;

    private final String title;

    private final List<Integer> children;

    private final int headingStart;

    private final int bodyStart;

    private final int directEnd;

    private final int sectionEnd;

    /**
     * Creates a section node using UTF-16 source offsets.
     *
     * @param number source-order node number
     * @param level Markdown heading level
     * @param title semantic heading title
     * @param children direct child node numbers
     * @param headingStart heading start offset, inclusive
     * @param bodyStart direct body start offset, inclusive
     * @param directEnd direct body end offset, exclusive
     * @param sectionEnd complete section end offset, exclusive
     */
    public SectionNode(
            int number,
            int level,
            String title,
            List<Integer> children,
            int headingStart,
            int bodyStart,
            int directEnd,
            int sectionEnd) {
        if (number <= 0) {
            throw new IllegalArgumentException("number must be positive");
        }
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException("level must be between 1 and 6");
        }
        if (headingStart < 0
                || bodyStart < headingStart
                || directEnd < bodyStart
                || sectionEnd < directEnd) {
            throw new IllegalArgumentException("section offsets must be ordered and non-negative");
        }
        this.number = number;
        this.level = level;
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.children = List.copyOf(Objects.requireNonNull(children, "children cannot be null"));
        this.headingStart = headingStart;
        this.bodyStart = bodyStart;
        this.directEnd = directEnd;
        this.sectionEnd = sectionEnd;
    }

    public int getNumber() {
        return this.number;
    }

    public int getLevel() {
        return this.level;
    }

    public String getTitle() {
        return this.title;
    }

    public List<Integer> getChildren() {
        return this.children;
    }

    public int getHeadingStart() {
        return this.headingStart;
    }

    public int getBodyStart() {
        return this.bodyStart;
    }

    public int getDirectEnd() {
        return this.directEnd;
    }

    public int getSectionEnd() {
        return this.sectionEnd;
    }
}
