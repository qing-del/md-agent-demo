package com.jacolp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.mapper.ChatSessionMapper;
import com.jacolp.pojo.entity.ChatSession;
import com.jacolp.pojo.vo.ChatSessionSummaryVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTest {

    @Mock
    private ChatSessionMapper mapper;

    @Test
    void listMapsOnlySummaryFieldsAndNormalizesTitle() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 7, 12, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 7, 12, 5);
        ChatSession session = new ChatSession();
        session.setSessionKey("550e8400-e29b-41d4-a716-446655440000");
        session.setTitle("Spring AI");
        session.setMessages("[{\"role\":\"user\",\"content\":\"hidden\"}]");
        session.setReferencedFileContents("[{\"fileName\":\"guide.md\"}]");
        session.setCreatedAt(createdAt);
        session.setUpdatedAt(updatedAt);
        when(mapper.selectSummaryList("Spring")).thenReturn(List.of(session));

        List<ChatSessionSummaryVO> actual = new ChatSessionServiceImpl(mapper).list("  Spring  ");

        assertEquals(1, actual.size());
        assertEquals(session.getSessionKey(), actual.get(0).getSessionKey());
        assertEquals(session.getTitle(), actual.get(0).getTitle());
        assertEquals(createdAt, actual.get(0).getCreatedAt());
        assertEquals(updatedAt, actual.get(0).getUpdatedAt());
        verify(mapper).selectSummaryList("Spring");
    }

    @Test
    void blankTitleMeansNoFilter() {
        when(mapper.selectSummaryList(null)).thenReturn(List.of());

        assertEquals(List.of(), new ChatSessionServiceImpl(mapper).list("  "));

        verify(mapper).selectSummaryList(null);
    }

    @Test
    void escapesLikeWildcardsBeforeQuerying() {
        when(mapper.selectSummaryList("100!%"))
                .thenReturn(List.of());

        new ChatSessionServiceImpl(mapper).list("100%");

        verify(mapper).selectSummaryList("100!%");
    }
}
