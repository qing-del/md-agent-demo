package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jacolp.agent.markdown.model.DocumentId;
import org.junit.jupiter.api.Test;

class MarkdownHeadingTreeTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void rendersNumberedTreeWithLogicalIndentationAndOriginalHeadingSyntax() {
        String source = "# Java  ##\n\n### JDK\n\n## Tools\n\n# Python\n\n## 安装\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);

        assertEquals(
                "1. # Java  ##\n  2. ### JDK\n  3. ## Tools\n4. # Python\n  5. ## 安装",
                manager.getHeadingTree(DOCUMENT_ID));
    }

    @Test
    void returnsAnEmptyTreeForAHeadinglessDocument() {
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, "plain text");

        assertEquals("", manager.getHeadingTree(DOCUMENT_ID));
    }
}
