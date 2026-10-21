package com.jacolp.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.agent.markdown.MarkdownContextProvider;
import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.operation.OperationManager;
import com.jacolp.mapper.MdDocumentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

class DocumentToolProtocolTest {
    @Test
    void actualToolCallbacksDeserializeSearchInsertAndPatchRequestsAndReturnStructuredProposals() throws Exception {
        DocumentId id = new DocumentId(301);
        String source = "# Root\nalpha\nbeta\n";
        MarkdownManager markdown = new MarkdownManager(snapshot -> fail("tools must only propose"), id, source);
        OperationManager manager = new OperationManager(markdown);
        MarkdownAgentTools tools = new MarkdownAgentTools(
                new MarkdownContextProvider(mock(MdDocumentMapper.class), markdown), markdown, manager);
        Map<String, ToolCallback> callbacks = Arrays.stream(ToolCallbacks.from(tools))
                .collect(Collectors.toMap(t -> t.getToolDefinition().name(), Function.identity()));
        assertEquals(7, callbacks.size());
        ObjectMapper json = new ObjectMapper();
        var search = json.readTree(callbacks.get("search_document_content").call("{\"documentId\":301,\"query\":\"alpha\"}"));
        String revision = search.get("revision").asText();
        assertEquals("alpha", search.at("/matches/0/matchedText").asText());
        String insertRequest = json.writeValueAsString(Map.of("documentId", 301, "baseRevision", revision,
                "nodeNumber", 1, "position", "body_end", "newText", "added\n"));
        assertEquals("insert", json.readTree(callbacks.get("propose_section_insert").call(insertRequest)).get("kind").asText());
        String patchRequest = "{\"documentId\":301,\"baseRevision\":\"" + revision + "\",\"edits\":["
                + "{\"type\":\"replace\",\"nodeNumber\":1,\"originalText\":\"alpha\",\"newText\":\"A\"},"
                + "{\"type\":\"insert\",\"nodeNumber\":1,\"position\":\"body_end\",\"newText\":\"added\\n\"}]}";
        var patch = json.readTree(callbacks.get("propose_document_patch").call(patchRequest));
        assertEquals("patch", patch.get("kind").asText());
        assertEquals(2, patch.get("edits").size());
        assertEquals(revision, patch.get("baseRevision").asText());
        assertEquals(source, markdown.restoreMarkdown(id));
    }
}
