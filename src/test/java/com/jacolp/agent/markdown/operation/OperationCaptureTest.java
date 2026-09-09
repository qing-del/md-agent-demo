package com.jacolp.agent.markdown.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import org.junit.jupiter.api.Test;

class OperationCaptureTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void capturesOnlyOperationsCreatedWithinTheRequestScope() {
        OperationManager manager = new OperationManager(
                new MarkdownManager(snapshot -> { }, DOCUMENT_ID, "# Root\nold\n"));

        try (OperationManager.RequestScope scope = manager.capture()) {
            Operation operation = manager.create(new SectionNodeRef(DOCUMENT_ID, 1), "old", "new");

            assertEquals(1, scope.operations().size());
            assertEquals(operation.getOpId(), scope.operations().get(0).getOpId());
            scope.commit();
        }
    }

    @Test
    void discardsOperationsWhenTheRequestScopeIsNotCommitted() {
        OperationManager manager = new OperationManager(
                new MarkdownManager(snapshot -> { }, DOCUMENT_ID, "# Root\nold\n"));
        UUID operationId;

        try (OperationManager.RequestScope scope = manager.capture()) {
            operationId = manager.create(new SectionNodeRef(DOCUMENT_ID, 1), "old", "new").getOpId();
        }

        UUID discardedId = operationId;
        assertThrows(OperationNotFoundException.class, () -> manager.get(discardedId));
    }
}
