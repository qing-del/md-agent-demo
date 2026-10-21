package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import com.jacolp.agent.markdown.exception.MarkdownCursorException;
import com.jacolp.agent.markdown.exception.SectionNotFoundException;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.DocumentSearchPage;
import org.junit.jupiter.api.Test;

class MarkdownSearchTest {
    private final DocumentId id = new DocumentId(1);

    private MarkdownManager manager(String source) {
        return new MarkdownManager(snapshot -> fail("search must never persist"), id, source);
    }

    @Test
    void findsRawTextWithChapterOwnershipIncludingPreambleHeadingsAndCode() {
        MarkdownManager manager = manager("needle preface\n# needle\nbody needle\n## Child\n```\nneedle\n```\n# Other\nneedle");
        DocumentSearchPage page = manager.searchDocumentContent(id, "needle", null, null);
        assertEquals(5, page.matches().size());
        assertNull(page.matches().get(0).nodeNumber());
        assertEquals("preamble", page.matches().get(0).locationKind());
        assertEquals("heading", page.matches().get(1).locationKind());
        assertEquals(List.of("needle", "Child"), page.matches().get(3).sectionPath());
        assertEquals("body", page.matches().get(3).locationKind());
        assertEquals(3, manager.searchDocumentContent(id, "needle", 1, null).matches().size());
        assertTrue(manager.searchDocumentContent(id, "Needle", null, null).matches().isEmpty());
        assertEquals(manager.getEntity(id).getRevision(), page.revision());
    }

    @Test
    void pagesOverlappingMatchesWithoutGapsOrDuplicates() {
        MarkdownManager manager = manager("a".repeat(26));
        List<DocumentSearchPage.Match> all = new ArrayList<>();
        String cursor = null;
        do {
            DocumentSearchPage page = manager.searchDocumentContent(id, "aa", null, cursor);
            assertTrue(page.matches().size() <= 10);
            all.addAll(page.matches());
            cursor = page.nextCursor();
        } while (cursor != null);
        assertEquals(25, all.size());
        assertTrue(all.stream().allMatch(match -> match.nodeNumber() == null));
    }

    @Test
    void boundsContextByCodePointsAndPreservesUnicodeAndLineEndings() {
        String source = "😀".repeat(100) + "中文\r\n" + "😀".repeat(100);
        DocumentSearchPage.Match hit = manager(source).searchDocumentContent(id, "中文\r\n", null, null).matches().get(0);
        assertEquals("😀".repeat(80) + "中文\r\n" + "😀".repeat(80), hit.context());
        assertTrue(hit.contextTruncated());
        assertEquals("中文\r\n", hit.matchedText());
        assertEquals(10, manager(source).searchDocumentContent(id, "😀", null, null).matches().size());
    }

    @Test
    void rejectsInvalidAndStaleCursorsOrChangedParameters() {
        MarkdownManager manager = manager("# Root\n" + "hit ".repeat(12));
        String cursor = manager.searchDocumentContent(id, "hit", null, null).nextCursor();
        assertNotNull(cursor);
        assertThrows(MarkdownCursorException.class, () -> manager.searchDocumentContent(id, "other", null, cursor));
        assertThrows(MarkdownCursorException.class, () -> manager.searchDocumentContent(id, "hit", 1, cursor));
        assertThrows(MarkdownCursorException.class, () -> manager.searchDocumentContent(id, "hit", null, "!"));
        manager.register(id, "# Root\n" + "hit ".repeat(13));
        assertThrows(MarkdownCursorException.class, () -> manager.searchDocumentContent(id, "hit", null, cursor));
    }

    @Test
    void validatesQueriesScopesAndReadingRevisions() {
        MarkdownManager manager = manager("# Root\ncontent\n");
        assertThrows(IllegalArgumentException.class, () -> manager.searchDocumentContent(id, " ", null, null));
        assertThrows(IllegalArgumentException.class, () -> manager.searchDocumentContent(id, "😀".repeat(257), null, null));
        assertThrows(SectionNotFoundException.class, () -> manager.searchDocumentContent(id, "x", 99, null));
        assertTrue(manager.searchDocumentContent(id, "absent", null, null).matches().isEmpty());
        String prefix = "revision: " + manager.getEntity(id).getRevision() + "\n";
        assertEquals(prefix + manager.getHeadingTree(id), manager.getVersionedHeadingTree(id));
        assertEquals(prefix + manager.getSectionPreview(id, 1), manager.getVersionedSectionPreview(id, 1));
        assertEquals(manager.getEntity(id).getRevision(), manager.getSectionAll(id, 1).getRevision());
    }
}
