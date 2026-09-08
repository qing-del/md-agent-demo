package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import com.jacolp.agent.markdown.exception.MarkdownCursorException;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.SectionPage;
import org.junit.jupiter.api.Test;

class MarkdownCursorPaginationTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void followsActualUtf8OffsetsUntilTheCompleteSectionIsRead() {
        String source = "# Root\n" + "字".repeat(3000) + "\n\n## Child\nchild\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);
        String expected = source;
        StringBuilder actual = new StringBuilder();

        SectionPage page = manager.getSectionAll(DOCUMENT_ID, 1, null);
        while (true) {
            String content = page.getContent();
            if (page.isHasMore()) {
                assertTrue(content.endsWith("..."));
                content = content.substring(0, content.length() - 3);
            }
            actual.append(content);
            if (!page.isHasMore()) {
                break;
            }
            assertTrue(page.getNextCursor() != null);
            page = manager.getSectionAll(DOCUMENT_ID, 1, page.getNextCursor());
        }

        assertEquals(expected, actual.toString());
        assertTrue(page.getContent().getBytes(StandardCharsets.UTF_8).length <= 5120);
        assertNull(page.getNextCursor());
    }

    @Test
    void rejectsMalformedOrMismatchedCursors() {
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, "# Root\nbody\n");

        assertThrows(MarkdownCursorException.class, () -> manager.getSectionAll(DOCUMENT_ID, 1, "not-a-cursor"));
        assertThrows(MarkdownCursorException.class, () -> manager.getSectionAll(DOCUMENT_ID, 1, "   "));
    }

    @Test
    void rejectsCursorWhenDocumentIdDoesNotMatch() {
        String source = "# Root\n" + "字".repeat(3000) + "\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);
        DocumentId otherDocumentId = new DocumentId(2L);
        manager.register(otherDocumentId, source);

        SectionPage firstPage = manager.getSectionAll(DOCUMENT_ID, 1, null);

        assertTrue(firstPage.isHasMore());
        assertThrows(MarkdownCursorException.class,
                () -> manager.getSectionAll(otherDocumentId, 1, firstPage.getNextCursor()));
    }
}
