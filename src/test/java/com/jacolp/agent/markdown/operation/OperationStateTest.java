package com.jacolp.agent.markdown.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import org.junit.jupiter.api.Test;

class OperationStateTest {

    @Test
    void startsPendingAndSupportsOnlyExpectedAtomicTransitions() {
        OperationState state = new OperationState(
                UUID.randomUUID(),
                new SectionNodeRef(new DocumentId(1L), 1),
                "old",
                "new");

        assertEquals(OperationStatus.PENDING, state.status());
        assertTrue(state.transition(OperationStatus.PENDING, OperationStatus.EXECUTING));
        assertEquals(OperationStatus.EXECUTING, state.status());
        assertTrue(!state.transition(OperationStatus.PENDING, OperationStatus.CANCELLED));
    }
}
