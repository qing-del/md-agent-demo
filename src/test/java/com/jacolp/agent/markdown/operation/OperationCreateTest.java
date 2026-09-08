package com.jacolp.agent.markdown.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.exception.MarkdownReplacementException;
import com.jacolp.agent.markdown.exception.SectionNotFoundException;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import org.junit.jupiter.api.Test;

class OperationCreateTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void createsAPendingOperationWithTheProposalFields() {
        OperationManager manager = new OperationManager(
                new MarkdownManager(snapshot -> { }, KEY, "# Root\nold\n"));

        Operation operation = manager.create(new SectionNodeRef(KEY, 1), "old", "new");

        assertNotNull(operation.getOpId());
        assertEquals(OperationStatus.PENDING, operation.getStatus());
        assertEquals("old", operation.getOriginalText());
        assertEquals("new", operation.getNewText());
    }

    @Test
    void rejectsInvalidProposalTargetsAndEmptyOriginalText() {
        OperationManager manager = new OperationManager(
                new MarkdownManager(snapshot -> { }, KEY, "# Root\nold\n"));

        assertThrows(SectionNotFoundException.class,
                () -> manager.create(new SectionNodeRef(KEY, 2), "old", "new"));
        assertThrows(MarkdownReplacementException.class,
                () -> manager.create(new SectionNodeRef(KEY, 1), "", "new"));
    }
}
