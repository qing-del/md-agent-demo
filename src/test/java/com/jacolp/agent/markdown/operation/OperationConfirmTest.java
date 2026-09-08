package com.jacolp.agent.markdown.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.exception.MarkdownPersistenceException;
import com.jacolp.agent.markdown.model.ReplaceResult;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import org.junit.jupiter.api.Test;

class OperationConfirmTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void confirmsOnceAndMarksTheOperationCompleted() {
        MarkdownManager markdownManager = new MarkdownManager(snapshot -> { }, KEY, "# Root\nold\n");
        OperationManager manager = new OperationManager(markdownManager);
        Operation operation = manager.create(new SectionNodeRef(KEY, 1), "old", "new");

        ReplaceResult result = manager.confirm(operation.getOpId());

        assertEquals("# Root\nnew\n", result.getContext().getSource());
        assertEquals(OperationStatus.COMPLETED, manager.get(operation.getOpId()).getStatus());
        assertThrows(OperationStateException.class, () -> manager.confirm(operation.getOpId()));
    }

    @Test
    void marksTheOperationFailedWhenReplacementPersistenceFails() {
        MarkdownManager markdownManager = new MarkdownManager(snapshot -> {
            throw new IllegalStateException("database unavailable");
        }, KEY, "# Root\nold\n");
        OperationManager manager = new OperationManager(markdownManager);
        Operation operation = manager.create(new SectionNodeRef(KEY, 1), "old", "new");

        assertThrows(MarkdownPersistenceException.class, () -> manager.confirm(operation.getOpId()));
        assertEquals(OperationStatus.FAILED, manager.get(operation.getOpId()).getStatus());
    }

    @Test
    void concurrentConfirmationAllowsOnlyOneExecutor() throws Exception {
        CountDownLatch enteredStore = new CountDownLatch(1);
        CountDownLatch releaseStore = new CountDownLatch(1);
        MarkdownManager markdownManager = new MarkdownManager(snapshot -> {
            enteredStore.countDown();
            try {
                assertTrue(releaseStore.await(5, TimeUnit.SECONDS));
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("store interrupted", exception);
            }
        }, KEY, "# Root\nold\n");
        OperationManager manager = new OperationManager(markdownManager);
        Operation operation = manager.create(new SectionNodeRef(KEY, 1), "old", "new");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReplaceResult> first = executor.submit(() -> manager.confirm(operation.getOpId()));
            assertTrue(enteredStore.await(5, TimeUnit.SECONDS));
            Future<ReplaceResult> second = executor.submit(() -> manager.confirm(operation.getOpId()));
            assertThrows(ExecutionException.class, second::get);
            releaseStore.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertEquals(OperationStatus.COMPLETED, manager.get(operation.getOpId()).getStatus());
        }
        finally {
            releaseStore.countDown();
            executor.shutdownNow();
        }
    }
}
