package com.jacolp.agent.markdown.operation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.exception.MarkdownPersistenceException;
import com.jacolp.agent.markdown.exception.MarkdownReplacementException;
import com.jacolp.agent.markdown.exception.MarkdownRevisionException;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import com.jacolp.pojo.vo.ChatOperationVO;
import org.junit.jupiter.api.Test;

class InsertionProposalTest {
    private static final DocumentId ID = new DocumentId(91);
    private static final String SOURCE = "# Parent\r\nold😀\r\n## Child\r\nchild\r\n";

    @Test
    void allFourPositionsUseTheDirectBodyAndPreserveSourceExactly() {
        for (String position : List.of("body_start", "body_end", "before", "after")) {
            List<MarkdownContext> saves = new ArrayList<>();
            MarkdownManager markdown = new MarkdownManager(saves::add, ID, SOURCE);
            OperationManager manager = new OperationManager(markdown);
            String anchor = position.equals("before") || position.equals("after") ? "old😀" : null;
            Operation proposal = manager.createInsert(ID, markdown.getEntity(ID).getRevision(), 1, position, anchor, "新增\r\n");
            String expected = switch (position) {
                case "body_start", "before" -> "# Parent\r\n新增\r\nold😀\r\n## Child\r\nchild\r\n";
                case "body_end" -> "# Parent\r\nold😀\r\n新增\r\n## Child\r\nchild\r\n";
                default -> "# Parent\r\nold😀新增\r\n\r\n## Child\r\nchild\r\n";
            };
            assertEquals(SOURCE, markdown.restoreMarkdown(ID));
            assertTrue(saves.isEmpty());
            assertEquals(expected, manager.preview(proposal.getOpId(), SOURCE).content());
            assertEquals(OperationStatus.PENDING, manager.get(proposal.getOpId()).getStatus());
            assertEquals(expected, manager.confirm(proposal.getOpId()).getContext().getSource());
            assertEquals(1, saves.size());
            assertThrows(OperationStateException.class, () -> manager.confirm(proposal.getOpId()));
        }
    }

    @Test
    void supportsEmptyBodiesAndEndOfFileWithoutInventingNewlines() {
        for (String source : List.of("# Root\n## Child\nbody", "# Root")) {
            MarkdownManager markdown = new MarkdownManager(snapshot -> { }, ID, source);
            OperationManager manager = new OperationManager(markdown);
            Operation proposal = manager.createInsert(ID, markdown.getEntity(ID).getRevision(), 1, "body_end", null, "\nadded\n");
            String expected = source.equals("# Root") ? "# Root\nadded\n" : "# Root\n\nadded\n## Child\nbody";
            assertEquals(expected, manager.preview(proposal.getOpId(), source).content());
        }
    }

    @Test
    void rejectsBadAnchorsPositionsAndStaleRevisionsBeforeCreatingProposals() {
        MarkdownManager markdown = new MarkdownManager(snapshot -> fail(), ID, "# Root\nx x\n## Child\nchild");
        OperationManager manager = new OperationManager(markdown);
        String revision = markdown.getEntity(ID).getRevision();
        for (String anchor : List.of("", "absent", "x", "child")) {
            assertThrows(MarkdownReplacementException.class,
                    () -> manager.createInsert(ID, revision, 1, "after", anchor, "new"));
        }
        assertThrows(MarkdownReplacementException.class, () -> manager.createInsert(ID, revision, 1, "after", null, "new"));
        assertThrows(MarkdownReplacementException.class, () -> manager.createInsert(ID, revision, 1, "unknown", null, "new"));
        assertThrows(MarkdownReplacementException.class, () -> manager.createInsert(ID, revision, 1, "body_end", null, ""));
        assertThrows(MarkdownRevisionException.class, () -> manager.createInsert(ID, "old", 1, "body_end", null, "new"));
        assertTrue(manager.operations().isEmpty());
    }

    @Test
    void previewAndConfirmationRejectChangedVersionsWithoutChangingState() {
        MarkdownManager markdown = new MarkdownManager(snapshot -> fail(), ID, SOURCE);
        OperationManager manager = new OperationManager(markdown);
        Operation proposal = manager.createInsert(ID, markdown.getEntity(ID).getRevision(), 1, "body_end", null, "new");
        assertThrows(MarkdownRevisionException.class, () -> manager.preview(proposal.getOpId(), SOURCE + "local"));
        markdown.register(ID, SOURCE + "saved");
        assertThrows(MarkdownRevisionException.class, () -> manager.confirm(proposal.getOpId()));
        assertEquals(OperationStatus.PENDING, manager.get(proposal.getOpId()).getStatus());
        assertEquals(SOURCE + "saved", markdown.restoreMarkdown(ID));
    }

    @Test
    void persistenceFailureKeepsTheOldSnapshotAndFailsTheProposal() {
        MarkdownManager markdown = new MarkdownManager(snapshot -> { throw new IllegalStateException("db"); }, ID, SOURCE);
        OperationManager manager = new OperationManager(markdown);
        Operation proposal = manager.createInsert(ID, markdown.getEntity(ID).getRevision(), 1, "body_end", null, "new");
        assertThrows(MarkdownPersistenceException.class, () -> manager.confirm(proposal.getOpId()));
        assertEquals(SOURCE, markdown.restoreMarkdown(ID));
        assertEquals(OperationStatus.FAILED, manager.get(proposal.getOpId()).getStatus());
    }

    @Test
    void requestFailureDiscardsInsertProposalsAndMetadataRemainsBoundToTheBaseSnapshot() {
        MarkdownManager markdown = new MarkdownManager(snapshot -> { }, ID, SOURCE);
        OperationManager manager = new OperationManager(markdown);
        Operation proposal;
        try (var capture = manager.capture()) {
            proposal = manager.createInsert(ID, markdown.getEntity(ID).getRevision(), 1, "body_end", null, "new");
            assertEquals(1, capture.operations().size());
            markdown.register(ID, "# Different\nchanged");
            assertEquals("# Parent", manager.sectionText(proposal));
            ChatOperationVO response = ChatOperationVO.from(proposal, manager.sectionText(proposal));
            assertEquals("insert", response.getKind());
            assertEquals(1, response.getEdits().size());
            assertEquals(proposal.details().baseRevision(), response.getBaseRevision());
        }
        assertThrows(OperationNotFoundException.class, () -> manager.get(proposal.getOpId()));
    }

    @Test
    void oldReplacementAlsoCapturesItsRevisionAndPreservesDeletionRules() {
        String source = "# Root\r\nold\r\nkeep\r\n";
        MarkdownManager markdown = new MarkdownManager(snapshot -> { }, ID, source);
        OperationManager manager = new OperationManager(markdown);
        Operation proposal = manager.create(new SectionNodeRef(ID, 1), "old", "");
        assertEquals("old", proposal.getOriginalText());
        assertEquals(markdown.getEntity(ID).getRevision(), proposal.details().baseRevision());
        assertEquals("old\r\n", proposal.details().diffs().get(0).originalText());
        assertEquals("# Root\r\nkeep\r\n", manager.confirm(proposal.getOpId()).getContext().getSource());
    }

    @Test
    void registeringANewSnapshotWaitsForAnInFlightConfirmation() throws Exception {
        CountDownLatch saving = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MarkdownManager markdown = new MarkdownManager(snapshot -> {
            saving.countDown();
            try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new IllegalStateException(e); }
        }, ID, SOURCE);
        OperationManager manager = new OperationManager(markdown);
        Operation proposal = manager.createInsert(ID, markdown.getEntity(ID).getRevision(), 1, "body_end", null, "new");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var confirm = executor.submit(() -> manager.confirm(proposal.getOpId()));
            assertTrue(saving.await(5, TimeUnit.SECONDS));
            var update = executor.submit(() -> markdown.register(ID, "# Later\nbody"));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> update.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            confirm.get(5, TimeUnit.SECONDS);
            update.get(5, TimeUnit.SECONDS);
            assertEquals("# Later\nbody", markdown.restoreMarkdown(ID));
        }
        finally { release.countDown(); executor.shutdownNow(); }
    }
}
