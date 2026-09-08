package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.jacolp.agent.markdown.exception.MarkdownCursorException;
import com.jacolp.agent.markdown.model.SectionPage;
import org.junit.jupiter.api.Test;

class MarkdownCursorPaginationTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void followsActualUtf8OffsetsUntilTheCompleteSectionIsRead() {
        String source = "# Root\n" + "字".repeat(3000) + "\n\n## Child\nchild\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, KEY, source);
        String expected = source;
        StringBuilder actual = new StringBuilder();

        SectionPage page = manager.getSectionAll(KEY, 1, null);
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
            page = manager.getSectionAll(KEY, 1, page.getNextCursor());
        }

        assertEquals(expected, actual.toString());
        assertTrue(page.getContent().getBytes(StandardCharsets.UTF_8).length <= 5120);
        assertNull(page.getNextCursor());
    }

    @Test
    void rejectsMalformedOrMismatchedCursors() {
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, KEY, "# Root\nbody\n");

        assertThrows(MarkdownCursorException.class, () -> manager.getSectionAll(KEY, 1, "not-a-cursor"));
        assertThrows(MarkdownCursorException.class, () -> manager.getSectionAll(KEY, 1, "   "));
    }
}
