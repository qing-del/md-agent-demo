package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.SectionPage;
import org.junit.jupiter.api.Test;

class MarkdownSectionAllTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void returnsTheCompleteSectionWhenItFitsInOnePage() {
        String source = "preamble\n\n# Root\nroot body\n\n## Child\nchild body\n\n# Sibling\nsibling\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);

        SectionPage page = manager.getSectionAll(DOCUMENT_ID, 1);

        assertEquals("# Root\nroot body\n\n## Child\nchild body\n\n", page.getContent());
        assertFalse(page.isHasMore());
        assertNull(page.getNextCursor());
    }

    @Test
    void truncatesTheFirstPageAtUtf8BoundaryAndAddsEllipsis() {
        String source = "# Root\n" + "字".repeat(3000) + "\n\n# Sibling\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);

        SectionPage page = manager.getSectionAll(DOCUMENT_ID, 1);

        assertTrue(page.isHasMore());
        assertNotNull(page.getNextCursor());
        assertTrue(page.getContent().endsWith("..."));
        assertTrue(page.getContent().getBytes(StandardCharsets.UTF_8).length <= 5120);
        assertEquals(5119, page.getContent().getBytes(StandardCharsets.UTF_8).length);
        assertTrue(page.getContent().substring(0, page.getContent().length() - 3)
                .getBytes(StandardCharsets.UTF_8).length % 3 == 1);
    }

    @Test
    void noCursorOverloadMatchesExplicitNullCursor() {
        String source = "# Root\n" + "字".repeat(3000) + "\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);

        SectionPage shortcutPage = manager.getSectionAll(DOCUMENT_ID, 1);
        SectionPage explicitPage = manager.getSectionAll(DOCUMENT_ID, 1, null);

        assertEquals(shortcutPage.getContent(), explicitPage.getContent());
        assertEquals(shortcutPage.isHasMore(), explicitPage.isHasMore());
        assertEquals(shortcutPage.getNextCursor(), explicitPage.getNextCursor());
    }
}
