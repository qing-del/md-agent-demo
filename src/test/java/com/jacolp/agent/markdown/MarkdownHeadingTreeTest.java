package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class MarkdownHeadingTreeTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void rendersNumberedTreeWithLogicalIndentationAndOriginalHeadingSyntax() {
        String source = "# Java  ##\n\n### JDK\n\n## Tools\n\n# Python\n\n## 安装\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, KEY, source);

        assertEquals(
                "1. # Java  ##\n  2. ### JDK\n  3. ## Tools\n4. # Python\n  5. ## 安装",
                manager.getHeadingTree(KEY));
    }

    @Test
    void returnsAnEmptyTreeForAHeadinglessDocument() {
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, KEY, "plain text");

        assertEquals("", manager.getHeadingTree(KEY));
    }
}
