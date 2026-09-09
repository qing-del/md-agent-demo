package com.jacolp.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.agent.markdown.MarkdownContextProvider;
import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import com.jacolp.agent.markdown.model.SectionPage;
import com.jacolp.agent.markdown.model.SectionReplaceProposal;
import com.jacolp.agent.markdown.operation.Operation;
import com.jacolp.agent.markdown.operation.OperationManager;
import com.jacolp.agent.markdown.operation.OperationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkdownAgentToolsTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(7L);

    @Mock
    private MarkdownContextProvider contextProvider;

    @Mock
    private MarkdownManager markdownManager;

    @Mock
    private OperationManager operationManager;

    private MarkdownAgentTools tools;

    @BeforeEach
    void setUp() {
        this.tools = new MarkdownAgentTools(this.contextProvider, this.markdownManager, this.operationManager);
    }

    @Test
    void readsArticleOverviewAfterEnsuringTheDocumentIsLoaded() {
        when(this.contextProvider.ensureLoaded(7L)).thenReturn(DOCUMENT_ID);
        when(this.markdownManager.getHeadingTree(DOCUMENT_ID)).thenReturn("1. # Root");

        assertEquals("1. # Root", this.tools.getArticleOverview(7L));

        verify(this.markdownManager).getHeadingTree(DOCUMENT_ID);
    }

    @Test
    void readsSectionPreview() {
        when(this.contextProvider.ensureLoaded(7L)).thenReturn(DOCUMENT_ID);
        when(this.markdownManager.getSectionPreview(DOCUMENT_ID, 2)).thenReturn("## Child\nbody");

        assertEquals("## Child\nbody", this.tools.getSectionPreview(7L, 2));

        verify(this.markdownManager).getSectionPreview(DOCUMENT_ID, 2);
    }

    @Test
    void readsSectionContentWithTheProvidedCursor() {
        when(this.contextProvider.ensureLoaded(7L)).thenReturn(DOCUMENT_ID);
        SectionPage expected = new SectionPage("page...", true, "next");
        when(this.markdownManager.getSectionAll(DOCUMENT_ID, 2, "cursor")).thenReturn(expected);

        assertEquals(expected, this.tools.getSectionContent(7L, 2, "cursor"));

        verify(this.markdownManager).getSectionAll(DOCUMENT_ID, 2, "cursor");
    }

    @Test
    void createsPendingReplacementProposalWithoutExecutingReplacement() {
        when(this.contextProvider.ensureLoaded(7L)).thenReturn(DOCUMENT_ID);
        Operation operation = new Operation(
                java.util.UUID.randomUUID(),
                new SectionNodeRef(DOCUMENT_ID, 2),
                "old",
                "new",
                OperationStatus.PENDING);
        when(this.operationManager.create(any(SectionNodeRef.class),
                org.mockito.ArgumentMatchers.eq("old"),
                org.mockito.ArgumentMatchers.eq("new")))
                .thenReturn(operation);

        SectionReplaceProposal proposal = this.tools.proposeSectionReplace(7L, 2, "old", "new");

        assertEquals(operation.getOpId().toString(), proposal.getOpId());
        assertEquals(7L, proposal.getDocumentId());
        assertEquals(2, proposal.getNodeNumber());
        assertEquals(OperationStatus.PENDING, proposal.getStatus());
        verify(this.operationManager).create(any(SectionNodeRef.class), eq("old"), eq("new"));
    }
}
