package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.jacolp.agent.markdown.exception.MarkdownPersistenceException;
import com.jacolp.agent.markdown.exception.MarkdownReplacementException;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.agent.markdown.model.ReplaceResult;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import org.junit.jupiter.api.Test;

class MarkdownReplaceTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void replacesOnlyTheUniqueMatchInDirectBodyAndReparsesTheDocument() {
        String source = "# Root\nold root\n\n## Child\nold child\n";
        List<MarkdownContext> persisted = new ArrayList<>();
        MarkdownManager manager = new MarkdownManager(persisted::add, KEY, source);

        ReplaceResult result = manager.replace(new SectionNodeRef(KEY, 1), "old root", "new root");

        assertEquals("# Root\nnew root\n\n## Child\nold child\n", result.getContext().getSource());
        assertEquals(result.getContext().getSource(), manager.restoreMarkdown(KEY));
        assertEquals(1, persisted.size());
        assertNotEquals(source, result.getContext().getSource());
        assertNotEquals(
                manager.getEntity(KEY).getRevision(),
                new MarkdownManager(snapshot -> { }, KEY, source).getEntity(KEY).getRevision()
        );
    }

    @Test
    void rejectsMissingAndAmbiguousMatchesWithoutPersisting() {
        String source = "# Root\nrepeat\nrepeat\n\n## Child\nrepeat\n";
        List<MarkdownContext> persisted = new ArrayList<>();
        MarkdownManager manager = new MarkdownManager(persisted::add, KEY, source);

        assertThrows(MarkdownReplacementException.class,
                () -> manager.replace(new SectionNodeRef(KEY, 1), "missing", "new"));
        assertThrows(MarkdownReplacementException.class,
                () -> manager.replace(new SectionNodeRef(KEY, 1), "repeat", "new"));
        assertEquals(source, manager.restoreMarkdown(KEY));
        assertEquals(List.of(), persisted);
    }

    @Test
    void persistenceFailureLeavesTheOldContextPublished() {
        String source = "# Root\nold\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> {
            throw new IllegalStateException("database unavailable");
        }, KEY, source);

        assertThrows(MarkdownPersistenceException.class,
                () -> manager.replace(new SectionNodeRef(KEY, 1), "old", "new"));
        assertEquals(source, manager.restoreMarkdown(KEY));
    }

    @Test
    void emptyReplacementTextDeletesTheMatch() {
        String source = "# Root\nremove me\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, KEY, source);

        manager.replace(new SectionNodeRef(KEY, 1), "remove me", "");

        assertEquals("# Root\n", manager.restoreMarkdown(KEY));
    }
}
