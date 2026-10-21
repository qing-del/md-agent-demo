package com.jacolp.agent.markdown.operation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.exception.MarkdownPersistenceException;
import com.jacolp.agent.markdown.exception.MarkdownReplacementException;
import com.jacolp.agent.markdown.exception.MarkdownRevisionException;
import com.jacolp.agent.markdown.exception.SectionNotFoundException;
import com.jacolp.agent.markdown.model.DocumentEdit;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.pojo.vo.ChatOperationVO;
import org.junit.jupiter.api.Test;

class DocumentPatchTest {
    private static final DocumentId ID = new DocumentId(201);
    private static final String SOURCE = "# One\nalpha\nbeta\n## Child\ngamma\n# Two\ndelta\n";

    private static List<DocumentEdit> mixedEdits() {
        return List.of(DocumentEdit.replace(1, "alpha", "A-long"), DocumentEdit.replace(1, "beta", ""),
                DocumentEdit.insert(2, "after", "gamma", "\nextra"), DocumentEdit.replace(3, "delta", "D"));
    }

    @Test
    void mixedEditsProduceOneCandidateAndOneSaveRegardlessOfInputOrder() {
        String expected = "# One\nA-long\n## Child\ngamma\nextra\n# Two\nD\n";
        for (boolean reverse : List.of(false, true)) {
            List<MarkdownContext> saves = new ArrayList<>();
            MarkdownManager markdown = new MarkdownManager(saves::add, ID, SOURCE);
            OperationManager manager = new OperationManager(markdown);
            List<DocumentEdit> edits = new ArrayList<>(mixedEdits());
            if (reverse) { Collections.reverse(edits); }
            Operation proposal = manager.createPatch(ID, markdown.getEntity(ID).getRevision(), edits);
            assertEquals("patch", proposal.details().kind());
            assertEquals(edits.get(0).nodeNumber(), proposal.details().diffs().get(0).nodeNumber());
            edits.clear(); // Proposals must not retain the caller's mutable request list.
            assertEquals(4, proposal.details().edits().size());
            assertEquals(expected, manager.preview(proposal.getOpId(), SOURCE).content());
            assertEquals(SOURCE, markdown.restoreMarkdown(ID));
            assertTrue(saves.isEmpty());
            assertEquals(expected, manager.confirm(proposal.getOpId()).getContext().getSource());
            assertEquals(1, saves.size());
            assertEquals(OperationStatus.COMPLETED, manager.get(proposal.getOpId()).getStatus());
        }
    }

    @Test
    void newlyInsertedHeadingsDoNotChangeTheMeaningOfLaterBaseNodeNumbers() {
        MarkdownManager markdown = new MarkdownManager(snapshot -> { }, ID, SOURCE);
        OperationManager manager = new OperationManager(markdown);
        Operation proposal = manager.createPatch(ID, markdown.getEntity(ID).getRevision(), List.of(
                DocumentEdit.insert(1, "body_start", null, "# New\n"), DocumentEdit.replace(3, "delta", "changed")));
        var result = manager.confirm(proposal.getOpId()).getContext();
        assertEquals("# One\n# New\nalpha\nbeta\n## Child\ngamma\n# Two\nchanged\n", result.getSource());
        assertEquals(4, result.getNodes().size());
        assertEquals("Two", result.getNodes().get(4).getTitle());
    }

    @Test
    void rejectsOverlapInsertionBoundariesAndDependenciesWithoutPartialChanges() {
        List<List<DocumentEdit>> conflicts = List.of(
                List.of(DocumentEdit.replace(1, "alpha", "A"), DocumentEdit.replace(1, "alp", "B")),
                List.of(DocumentEdit.replace(1, "alpha", "A"), DocumentEdit.insert(1, "before", "alpha", "B")),
                List.of(DocumentEdit.replace(1, "alpha", "A"), DocumentEdit.insert(1, "after", "alpha", "B")),
                List.of(DocumentEdit.replace(1, "alpha", "A"), DocumentEdit.insert(1, "after", "al", "B")),
                List.of(DocumentEdit.insert(1, "body_start", null, "A"), DocumentEdit.insert(1, "before", "alpha", "B")),
                List.of(DocumentEdit.replace(1, "alpha", "A"), DocumentEdit.replace(1, "A", "B")),
                List.of(DocumentEdit.replace(1, "alpha", ""), DocumentEdit.replace(1, "\nbeta", "B")));
        for (List<DocumentEdit> edits : conflicts) {
            MarkdownManager markdown = new MarkdownManager(snapshot -> fail("must not persist"), ID, SOURCE);
            OperationManager manager = new OperationManager(markdown);
            assertThrows(MarkdownReplacementException.class,
                    () -> manager.createPatch(ID, markdown.getEntity(ID).getRevision(), edits));
            assertTrue(manager.operations().isEmpty());
            assertEquals(SOURCE, markdown.restoreMarkdown(ID));
        }
    }

    @Test
    void adjacentReplacementsAreAllowedAndStillUseOriginalCoordinates() {
        MarkdownManager markdown = new MarkdownManager(snapshot -> { }, ID, "# Root\nabcdef");
        OperationManager manager = new OperationManager(markdown);
        Operation proposal = manager.createPatch(ID, markdown.getEntity(ID).getRevision(), List.of(
                DocumentEdit.replace(1, "abc", "LONG"), DocumentEdit.replace(1, "def", "D")));
        assertEquals("# Root\nLONGD", manager.confirm(proposal.getOpId()).getContext().getSource());
    }

    @Test
    void validatesWholeRequestIncludingLimitsAndUnknownSections() {
        MarkdownManager markdown = new MarkdownManager(snapshot -> fail(), ID, SOURCE);
        OperationManager manager = new OperationManager(markdown);
        String revision = markdown.getEntity(ID).getRevision();
        assertThrows(MarkdownReplacementException.class, () -> manager.createPatch(ID, revision, null));
        assertThrows(MarkdownReplacementException.class, () -> manager.createPatch(ID, revision, List.of()));
        assertThrows(MarkdownReplacementException.class, () -> manager.createPatch(ID, revision,
                Collections.nCopies(51, DocumentEdit.replace(1, "alpha", "A"))));
        assertThrows(MarkdownReplacementException.class, () -> manager.createPatch(ID, revision, Collections.singletonList(null)));
        assertThrows(SectionNotFoundException.class, () -> manager.createPatch(ID, revision,
                List.of(DocumentEdit.replace(1, "alpha", "A"), DocumentEdit.replace(99, "anything", "B"))));
        assertThrows(MarkdownReplacementException.class, () -> manager.createPatch(ID, revision,
                List.of(new DocumentEdit("move", 1, null, null, "alpha", "B"))));
        assertTrue(manager.operations().isEmpty());
    }

    @Test
    void acceptsFiftyIndependentEdits() {
        StringBuilder source = new StringBuilder("# Root\n");
        List<DocumentEdit> edits = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            source.append("[item").append(i).append("]\n");
            edits.add(DocumentEdit.replace(1, "[item" + i + "]", "value" + i));
        }
        MarkdownManager markdown = new MarkdownManager(snapshot -> { }, ID, source.toString());
        OperationManager manager = new OperationManager(markdown);
        Operation proposal = manager.createPatch(ID, markdown.getEntity(ID).getRevision(), edits);
        assertEquals(50, proposal.details().diffs().size());
        assertTrue(manager.confirm(proposal.getOpId()).getContext().getSource().contains("value49"));
    }

    @Test
    void versionConflictsDoNotWriteOrAdvanceThePendingProposal() {
        MarkdownManager markdown = new MarkdownManager(snapshot -> fail(), ID, SOURCE);
        OperationManager manager = new OperationManager(markdown);
        assertThrows(MarkdownRevisionException.class, () -> manager.createPatch(ID, "stale", mixedEdits()));
        Operation proposal = manager.createPatch(ID, markdown.getEntity(ID).getRevision(), mixedEdits());
        assertThrows(MarkdownRevisionException.class, () -> manager.preview(proposal.getOpId(), SOURCE + "draft"));
        markdown.register(ID, SOURCE + "saved");
        assertThrows(MarkdownRevisionException.class, () -> manager.confirm(proposal.getOpId()));
        assertEquals(OperationStatus.PENDING, manager.get(proposal.getOpId()).getStatus());
    }

    @Test
    void failedSingleSaveLeavesStorageAndPublishedSourceUnchanged() {
        AtomicReference<String> storage = new AtomicReference<>(SOURCE);
        AtomicInteger saves = new AtomicInteger();
        MarkdownManager markdown = new MarkdownManager(snapshot -> {
            saves.incrementAndGet();
            throw new IllegalStateException("database rejected update");
        }, ID, storage.get());
        OperationManager manager = new OperationManager(markdown);
        Operation proposal = manager.createPatch(ID, markdown.getEntity(ID).getRevision(), mixedEdits());
        assertThrows(MarkdownPersistenceException.class, () -> manager.confirm(proposal.getOpId()));
        assertEquals(1, saves.get());
        assertEquals(SOURCE, storage.get());
        assertEquals(SOURCE, markdown.restoreMarkdown(ID));
        assertEquals(OperationStatus.FAILED, manager.get(proposal.getOpId()).getStatus());
    }

    @Test
    void capturesAndDiscardsOneWholeProposalWithItsStableResponseMetadata() {
        MarkdownManager markdown = new MarkdownManager(snapshot -> { }, ID, SOURCE);
        OperationManager manager = new OperationManager(markdown);
        Operation proposal;
        try (var capture = manager.capture()) {
            proposal = manager.createPatch(ID, markdown.getEntity(ID).getRevision(), mixedEdits());
            assertEquals(1, capture.operations().size());
            markdown.register(ID, "# Changed\n");
            ChatOperationVO response = ChatOperationVO.from(proposal, manager.sectionText(proposal));
            assertEquals("patch", response.getKind());
            assertEquals(4, response.getEdits().size());
            assertEquals("# One | ## Child", response.getEdits().get(2).sectionText());
        }
        assertThrows(OperationNotFoundException.class, () -> manager.get(proposal.getOpId()));
    }
}
