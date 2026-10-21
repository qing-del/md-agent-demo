package com.jacolp.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.agent.context.ChatContextManager;
import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.DocumentEdit;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.operation.OperationManager;
import com.jacolp.agent.markdown.operation.OperationNotFoundException;
import com.jacolp.mapper.ChatSessionMapper;
import com.jacolp.pojo.dto.ChatMessageDTO;
import com.jacolp.pojo.dto.ChatRequestDTO;
import com.jacolp.pojo.entity.ChatSession;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;

class ChatDocumentProposalTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SuppressWarnings("unchecked")
    void chatReturnsCompleteTypedProposalsOrDiscardsThemAllOnFailure(boolean succeeds) {
        String sessionKey = "550e8400-e29b-41d4-a716-446655440000";
        ChatSession session = new ChatSession();
        session.setId(1L);
        session.setSessionKey(sessionKey);
        session.setMessages("[]");
        session.setReferencedFileContents("[]");
        ChatSessionMapper mapper = mock(ChatSessionMapper.class);
        when(mapper.selectBySessionKey(sessionKey)).thenReturn(session);
        ChatClient client = mock(ChatClient.class);
        var request = mock(ChatClient.ChatClientRequestSpec.class);
        var response = mock(ChatClient.CallResponseSpec.class);
        when(client.prompt()).thenReturn(request);
        when(request.advisors(any(Consumer.class))).thenReturn(request);
        when(request.user(any(String.class))).thenReturn(request);
        when(request.call()).thenReturn(response);

        DocumentId id = new DocumentId(401);
        MarkdownManager markdown = new MarkdownManager(snapshot -> fail("chat must not persist proposals"), id, "# Root\nalpha\nbeta\n");
        OperationManager operations = new OperationManager(markdown);
        List<UUID> created = new ArrayList<>();
        when(response.content()).thenAnswer(invocation -> {
            String revision = markdown.getEntity(id).getRevision();
            created.add(operations.createInsert(id, revision, 1, "body_end", null, "added\n").getOpId());
            created.add(operations.createPatch(id, revision, List.of(DocumentEdit.replace(1, "alpha", "A"),
                    DocumentEdit.replace(1, "beta", "B"))).getOpId());
            markdown.register(id, "# Concurrently changed\n");
            return succeeds ? "proposed" : "";
        });
        ChatController controller = new ChatController(client, new ChatContextManager(mapper, new ObjectMapper()), operations);
        ChatRequestDTO payload = new ChatRequestDTO(sessionKey, new ChatMessageDTO("edit", List.of(), List.of()));
        if (succeeds) {
            var result = controller.chat(payload);
            assertEquals(2, result.operations().size());
            assertEquals("insert", result.operations().get(0).getKind());
            assertEquals("patch", result.operations().get(1).getKind());
            assertEquals(2, result.operations().get(1).getEdits().size());
            assertEquals("# Root", result.operations().get(1).getSectionText());
        }
        else {
            assertThrows(IllegalStateException.class, () -> controller.chat(payload));
            assertEquals(2, created.size());
            created.forEach(opId -> assertThrows(OperationNotFoundException.class, () -> operations.get(opId)));
        }
    }
}
