package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import com.jacolp.agent.markdown.exception.MarkdownPersistenceException;
import com.jacolp.agent.markdown.exception.MarkdownReplacementException;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.agent.markdown.model.ReplaceResult;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import org.junit.jupiter.api.Test;

class MarkdownReplaceTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void replacesOnlyTheUniqueMatchInDirectBodyAndReparsesTheDocument() {
        String source = "# Root\nold root\n\n## Child\nold child\n";
        List<MarkdownContext> persisted = new ArrayList<>();
        MarkdownManager manager = new MarkdownManager(persisted::add, DOCUMENT_ID, source);

        ReplaceResult result = manager.replace(new SectionNodeRef(DOCUMENT_ID, 1), "old root", "new root");

        assertEquals("# Root\nnew root\n\n## Child\nold child\n", result.getContext().getSource());
        assertEquals(result.getContext().getSource(), manager.restoreMarkdown(DOCUMENT_ID));
        assertEquals(1, persisted.size());
        assertNotEquals(source, result.getContext().getSource());
        assertNotEquals(
                manager.getEntity(DOCUMENT_ID).getRevision(),
                new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source).getEntity(DOCUMENT_ID).getRevision()
        );
    }

    @Test
    void rejectsMissingAndAmbiguousMatchesWithoutPersisting() {
        String source = "# Root\nrepeat\nrepeat\n\n## Child\nrepeat\n";
        List<MarkdownContext> persisted = new ArrayList<>();
        MarkdownManager manager = new MarkdownManager(persisted::add, DOCUMENT_ID, source);

        assertThrows(MarkdownReplacementException.class,
                () -> manager.replace(new SectionNodeRef(DOCUMENT_ID, 1), "missing", "new"));
        assertThrows(MarkdownReplacementException.class,
                () -> manager.replace(new SectionNodeRef(DOCUMENT_ID, 1), "repeat", "new"));
        assertEquals(source, manager.restoreMarkdown(DOCUMENT_ID));
        assertEquals(List.of(), persisted);
    }

    @Test
    void persistenceFailureLeavesTheOldContextPublished() {
        String source = "# Root\nold\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> {
            throw new IllegalStateException("database unavailable");
        }, DOCUMENT_ID, source);

        assertThrows(MarkdownPersistenceException.class,
                () -> manager.replace(new SectionNodeRef(DOCUMENT_ID, 1), "old", "new"));
        assertEquals(source, manager.restoreMarkdown(DOCUMENT_ID));
    }

    @Test
    void emptyReplacementTextDeletesTheMatch() {
        String source = "# Root\nremove me\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);

        manager.replace(new SectionNodeRef(DOCUMENT_ID, 1), "remove me", "");

        assertEquals("# Root\n", manager.restoreMarkdown(DOCUMENT_ID));
    }
}
