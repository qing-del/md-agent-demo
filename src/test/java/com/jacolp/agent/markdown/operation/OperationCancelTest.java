package com.jacolp.agent.markdown.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import org.junit.jupiter.api.Test;

class OperationCancelTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void cancelsPendingOperationWithoutChangingMarkdown() {
        MarkdownManager markdownManager = new MarkdownManager(snapshot -> { }, KEY, "# Root\nold\n");
        OperationManager manager = new OperationManager(markdownManager);
        Operation operation = manager.create(new SectionNodeRef(KEY, 1), "old", "new");

        Operation cancelled = manager.cancel(operation.getOpId());

        assertEquals(OperationStatus.CANCELLED, cancelled.getStatus());
        assertEquals("# Root\nold\n", markdownManager.restoreMarkdown(KEY));
        assertThrows(OperationStateException.class, () -> manager.cancel(operation.getOpId()));
        assertThrows(OperationStateException.class, () -> manager.confirm(operation.getOpId()));
    }
}
