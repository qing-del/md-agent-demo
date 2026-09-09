package com.jacolp.pojo.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ChatDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesStructuredChatRequest() throws Exception {
        String json = """
                {
                  "sessionKey": "550e8400-e29b-41d4-a716-446655440000",
                  "message": {
                    "content": "请检查文档",
                    "documentIds": [1, 2, 3],
                    "selections": [
                      {
                        "documentId": 2,
                        "originalText": "selected text",
                        "sectionText": "# Guide｜## Install"
                      }
                    ]
                  }
                }
                """;

        ChatRequestDTO request = this.objectMapper.readValue(json, ChatRequestDTO.class);

        assertEquals("550e8400-e29b-41d4-a716-446655440000", request.getSessionKey());
        assertEquals("请检查文档", request.getMessage().getContent());
        assertEquals(List.of(1L, 2L, 3L), request.getMessage().getDocumentIds());
        assertEquals(1, request.getMessage().getSelections().size());
        SelectionDTO selection = request.getMessage().getSelections().get(0);
        assertEquals(2L, selection.getDocumentId());
        assertEquals("selected text", selection.getOriginalText());
        assertEquals("# Guide｜## Install", selection.getSectionText());
    }

    @Test
    void treatsMissingOptionalArraysAsEmptyLists() throws Exception {
        ChatMessageDTO message = this.objectMapper.readValue(
                "{\"content\":\"hello\"}", ChatMessageDTO.class);

        assertTrue(message.getDocumentIds().isEmpty());
        assertTrue(message.getSelections().isEmpty());
    }

    @Test
    void treatsNullOptionalArraysAsEmptyLists() throws Exception {
        ChatMessageDTO message = this.objectMapper.readValue(
                "{\"content\":\"hello\",\"documentIds\":null,\"selections\":null}",
                ChatMessageDTO.class);

        assertTrue(message.getDocumentIds().isEmpty());
        assertTrue(message.getSelections().isEmpty());
    }
}
