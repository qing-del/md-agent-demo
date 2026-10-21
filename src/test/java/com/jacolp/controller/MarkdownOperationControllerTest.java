package com.jacolp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.SectionReplaceProposal;
import com.jacolp.agent.markdown.model.SectionReplaceResult;
import com.jacolp.agent.markdown.operation.OperationManager;
import com.jacolp.agent.markdown.operation.OperationStatus;
import com.jacolp.agent.markdown.model.DocumentId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MarkdownOperationControllerTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void confirmsAndCancelsReplacementOperationsThroughTheApiController() {
        MarkdownManager markdownManager = new MarkdownManager(
                snapshot -> { }, DOCUMENT_ID, "# Root\nold\n");
        OperationManager operationManager = new OperationManager(markdownManager);
        MarkdownOperationController controller = new MarkdownOperationController(operationManager);

        SectionReplaceProposal pending = com.jacolp.agent.markdown.model.SectionReplaceProposal.from(
                operationManager.create(
                        new com.jacolp.agent.markdown.model.SectionNodeRef(DOCUMENT_ID, 1),
                        "old",
                        "new"));
        SectionReplaceResult completed = controller.confirm(UUID.fromString(pending.getOpId()));

        assertEquals(OperationStatus.COMPLETED, completed.getStatus());
        assertEquals("# Root\nnew\n", markdownManager.restoreMarkdown(DOCUMENT_ID));

        SectionReplaceProposal cancelled = SectionReplaceProposal.from(
                operationManager.cancel(operationManager.create(
                        new com.jacolp.agent.markdown.model.SectionNodeRef(DOCUMENT_ID, 1),
                        "new",
                        "updated").getOpId()));
        assertEquals(OperationStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void mapsUnknownOperationsToNotFound() {
        MarkdownOperationController controller = new MarkdownOperationController(
                new OperationManager(new MarkdownManager(snapshot -> { })));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.get(UUID.randomUUID()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void previewsInsertionsThroughHttpWithoutMutatingAndReportsDraftConflicts() throws Exception {
        String source = "# Root\nold\n";
        MarkdownManager markdown = new MarkdownManager(snapshot -> { throw new AssertionError("preview wrote"); }, DOCUMENT_ID, source);
        OperationManager manager = new OperationManager(markdown);
        var operation = manager.createInsert(DOCUMENT_ID, markdown.getEntity(DOCUMENT_ID).getRevision(),
                1, "after", "old", " added");
        var mvc = MockMvcBuilders.standaloneSetup(new MarkdownOperationController(manager)).build();
        String endpoint = "/api/markdown-operations/" + operation.getOpId() + "/preview";
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"# Root\\nold\\n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("# Root\nold added\n"))
                .andExpect(jsonPath("$.baseRevision").value(operation.details().baseRevision()))
                .andExpect(jsonPath("$.edits[0].type").value("insert"));
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"changed\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(source, markdown.restoreMarkdown(DOCUMENT_ID));
        assertEquals(OperationStatus.PENDING, manager.get(operation.getOpId()).getStatus());
    }
}
