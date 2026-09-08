package com.jacolp.agent.markdown.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import org.junit.jupiter.api.Test;

class OperationCancelTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void cancelsPendingOperationWithoutChangingMarkdown() {
        MarkdownManager markdownManager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, "# Root\nold\n");
        OperationManager manager = new OperationManager(markdownManager);
        Operation operation = manager.create(new SectionNodeRef(DOCUMENT_ID, 1), "old", "new");

        Operation cancelled = manager.cancel(operation.getOpId());

        assertEquals(OperationStatus.CANCELLED, cancelled.getStatus());
        assertEquals("# Root\nold\n", markdownManager.restoreMarkdown(DOCUMENT_ID));
        assertThrows(OperationStateException.class, () -> manager.cancel(operation.getOpId()));
        assertThrows(OperationStateException.class, () -> manager.confirm(operation.getOpId()));
    }
}
