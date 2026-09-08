package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import com.jacolp.agent.markdown.exception.MarkdownContextNotFoundException;
import org.junit.jupiter.api.Test;

class MarkdownRestoreTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void returnsTheOriginalSourceIncludingPreambleWhitespaceAndMarkdownSyntax() {
        String source = "before heading\r\n\r\n# Java  ##\r\n\r\n```java\r\n// 中文 😀\r\n```\r\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, KEY, source);

        manager.getSectionAll(KEY, 1);

        assertEquals(source, manager.restoreMarkdown(KEY));
    }

    @Test
    void missingContextIsNotReportedAsAnEmptyDocument() {
        MarkdownManager manager = new MarkdownManager(snapshot -> { });

        assertThrows(MarkdownContextNotFoundException.class, () -> manager.restoreMarkdown(KEY));
    }
}
