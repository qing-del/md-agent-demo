package com.jacolp.agent.markdown.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import org.junit.jupiter.api.Test;

class OperationGetTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void returnsTheLatestOperationSnapshot() {
        OperationManager manager = new OperationManager(
                new MarkdownManager(snapshot -> { }, DOCUMENT_ID, "# Root\nold\n"));
        Operation created = manager.create(new SectionNodeRef(DOCUMENT_ID, 1), "old", "new");

        Operation loaded = manager.get(created.getOpId());

        assertEquals(created.getOpId(), loaded.getOpId());
        assertEquals(OperationStatus.PENDING, loaded.getStatus());
    }

    @Test
    void unknownOperationProducesAnExplicitError() {
        OperationManager manager = new OperationManager(new MarkdownManager(snapshot -> { }));

        assertThrows(OperationNotFoundException.class, () -> manager.get(UUID.randomUUID()));
    }
}
