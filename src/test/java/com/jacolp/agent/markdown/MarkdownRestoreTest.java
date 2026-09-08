package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jacolp.agent.markdown.exception.MarkdownContextNotFoundException;
import com.jacolp.agent.markdown.model.DocumentId;
import org.junit.jupiter.api.Test;

class MarkdownRestoreTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void returnsTheOriginalSourceIncludingPreambleWhitespaceAndMarkdownSyntax() {
        String source = "before heading\r\n\r\n# Java  ##\r\n\r\n```java\r\n// 中文 😀\r\n```\r\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);

        manager.getSectionAll(DOCUMENT_ID, 1);

        assertEquals(source, manager.restoreMarkdown(DOCUMENT_ID));
    }

    @Test
    void missingContextIsNotReportedAsAnEmptyDocument() {
        MarkdownManager manager = new MarkdownManager(snapshot -> { });

        assertThrows(MarkdownContextNotFoundException.class, () -> manager.restoreMarkdown(DOCUMENT_ID));
    }
}
