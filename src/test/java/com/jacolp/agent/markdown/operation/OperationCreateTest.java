package com.jacolp.agent.markdown.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.exception.MarkdownReplacementException;
import com.jacolp.agent.markdown.exception.SectionNotFoundException;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import org.junit.jupiter.api.Test;

class OperationCreateTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void createsAPendingOperationWithTheProposalFields() {
        OperationManager manager = new OperationManager(
                new MarkdownManager(snapshot -> { }, DOCUMENT_ID, "# Root\nold\n"));

        Operation operation = manager.create(new SectionNodeRef(DOCUMENT_ID, 1), "old", "new");

        assertNotNull(operation.getOpId());
        assertEquals(OperationStatus.PENDING, operation.getStatus());
        assertEquals(DOCUMENT_ID, operation.getSection().getDocumentId());
        assertEquals("old", operation.getOriginalText());
        assertEquals("new", operation.getNewText());
    }

    @Test
    void rejectsInvalidProposalTargetsAndEmptyOriginalText() {
        OperationManager manager = new OperationManager(
                new MarkdownManager(snapshot -> { }, DOCUMENT_ID, "# Root\nold\n"));

        assertThrows(SectionNotFoundException.class,
                () -> manager.create(new SectionNodeRef(DOCUMENT_ID, 2), "old", "new"));
        assertThrows(MarkdownReplacementException.class,
                () -> manager.create(new SectionNodeRef(DOCUMENT_ID, 1), "", "new"));
    }
}
