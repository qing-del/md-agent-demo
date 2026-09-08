package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import com.jacolp.agent.markdown.model.DocumentId;
import org.junit.jupiter.api.Test;

class MarkdownSectionAllTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void returnsTheCompleteSectionWhenItFitsInOnePage() {
        String source = "preamble\n\n# Root\nroot body\n\n## Child\nchild body\n\n# Sibling\nsibling\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);

        assertEquals("# Root\nroot body\n\n## Child\nchild body\n\n", manager.getSectionAll(DOCUMENT_ID, 1));
    }

    @Test
    void truncatesTheFirstPageAtUtf8BoundaryAndAddsEllipsis() {
        String source = "# Root\n" + "字".repeat(3000) + "\n\n# Sibling\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);

        String page = manager.getSectionAll(DOCUMENT_ID, 1);

        assertTrue(page.endsWith("..."));
        assertTrue(page.getBytes(StandardCharsets.UTF_8).length <= 5120);
        assertEquals(5119, page.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(page.substring(0, page.length() - 3).getBytes(StandardCharsets.UTF_8).length % 3 == 1);
    }
}
