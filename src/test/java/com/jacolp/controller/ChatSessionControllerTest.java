package com.jacolp.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.pojo.vo.ChatMessageVO;
import com.jacolp.pojo.vo.ChatSessionDetailVO;
import com.jacolp.pojo.vo.ChatSessionSummaryVO;
import com.jacolp.service.ChatSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ChatSessionControllerTest {

    @Mock
    private ChatSessionService service;

    @Test
    void listsSessionSummariesWithOptionalTitle() throws Exception {
        ChatSessionSummaryVO summary = new ChatSessionSummaryVO(
                "550e8400-e29b-41d4-a716-446655440000",
                "Spring AI",
                LocalDateTime.of(2026, 9, 7, 12, 0),
                LocalDateTime.of(2026, 9, 7, 12, 5));
        when(service.list("Spring")).thenReturn(List.of(summary));

        mockMvc().perform(get("/api/sessions").param("title", "Spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionKey").value(summary.getSessionKey()))
                .andExpect(jsonPath("$[0].title").value(summary.getTitle()))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].updatedAt").exists())
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].messages").doesNotExist())
                .andExpect(jsonPath("$[0].referencedFileContents").doesNotExist());

        verify(service).list("Spring");
    }

    @Test
    void returnsEmptyArrayWhenThereAreNoMatches() throws Exception {
        when(service.list(null)).thenReturn(List.of());

        mockMvc().perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(service).list(null);
    }

    @Test
    void getsSessionDetailForHistoryRestore() throws Exception {
        ChatSessionDetailVO detail = new ChatSessionDetailVO(
                "550e8400-e29b-41d4-a716-446655440000",
                "Spring AI",
                List.of(
                        new ChatMessageVO("user", "请介绍一下 Spring AI"),
                        new ChatMessageVO("assistant", "Spring AI 是一个用于构建 AI 应用的 Spring 项目。")),
                LocalDateTime.of(2026, 9, 9, 12, 0),
                LocalDateTime.of(2026, 9, 9, 12, 5));
        when(service.getBySessionKey(detail.getSessionKey())).thenReturn(detail);

        mockMvc().perform(get("/api/sessions/{sessionKey}", detail.getSessionKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionKey").value(detail.getSessionKey()))
                .andExpect(jsonPath("$.title").value(detail.getTitle()))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("请介绍一下 Spring AI"))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        verify(service).getBySessionKey(detail.getSessionKey());
    }

    @Test
    void returnsAnEmptyMessageArrayForAnEmptySession() throws Exception {
        String sessionKey = "550e8400-e29b-41d4-a716-446655440000";
        ChatSessionDetailVO detail = new ChatSessionDetailVO(
                sessionKey,
                null,
                List.of(),
                LocalDateTime.of(2026, 9, 9, 12, 0),
                LocalDateTime.of(2026, 9, 9, 12, 0));
        when(service.getBySessionKey(sessionKey)).thenReturn(detail);

        mockMvc().perform(get("/api/sessions/{sessionKey}", sessionKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isEmpty())
                .andExpect(jsonPath("$.title").doesNotExist());

        verify(service).getBySessionKey(sessionKey);
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new ChatSessionController(service)).build();
    }
}
