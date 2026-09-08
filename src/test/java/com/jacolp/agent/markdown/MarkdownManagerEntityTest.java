package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import com.jacolp.agent.markdown.exception.MarkdownContextNotFoundException;
import com.jacolp.agent.markdown.model.MarkdownContext;
import org.junit.jupiter.api.Test;

class MarkdownManagerEntityTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void returnsTheRegisteredImmutableSnapshot() {
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, KEY, "# Java\ncontent\n");

        MarkdownContext first = manager.getEntity(KEY);
        MarkdownContext second = manager.getEntity(KEY);

        assertSame(first, second);
        assertEquals("# Java\ncontent\n", first.getSource());
    }

    @Test
    void unknownUuidProducesExplicitContextError() {
        MarkdownManager manager = new MarkdownManager(snapshot -> { });

        assertThrows(MarkdownContextNotFoundException.class, () -> manager.getEntity(KEY));
    }
}
