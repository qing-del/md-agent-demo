package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jacolp.agent.markdown.exception.MarkdownContextNotFoundException;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.MarkdownContext;
import org.junit.jupiter.api.Test;

class MarkdownManagerEntityTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void returnsTheRegisteredImmutableSnapshot() {
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, "# Java\ncontent\n");

        MarkdownContext first = manager.getEntity(DOCUMENT_ID);
        MarkdownContext second = manager.getEntity(DOCUMENT_ID);

        assertSame(first, second);
        assertEquals("# Java\ncontent\n", first.getSource());
    }

    @Test
    void unknownDocumentIdProducesExplicitContextError() {
        MarkdownManager manager = new MarkdownManager(snapshot -> { });

        assertThrows(MarkdownContextNotFoundException.class, () -> manager.getEntity(DOCUMENT_ID));
    }
}
