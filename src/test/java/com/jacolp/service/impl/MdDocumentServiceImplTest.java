package com.jacolp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.mapper.MdDocumentMapper;
import com.jacolp.agent.markdown.MarkdownContextProvider;
import com.jacolp.pojo.entity.MdDocument;
import com.jacolp.pojo.vo.MdDocumentSummaryVO;
import com.jacolp.pojo.vo.MdDocumentSyncVO;
import com.jacolp.pojo.vo.MdDocumentVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MdDocumentServiceImplTest {

    @Mock
    private MdDocumentMapper mapper;

    @Mock
    private MarkdownContextProvider contextProvider;

    @Test
    void uploadReadsUtf8MarkdownAndUpsertsByOriginalFileName() throws Exception {
        byte[] bytes = "# 中文\n\n😀\n\n```java\nSystem.out.println(\"md\");\n```\n"
                .getBytes(StandardCharsets.UTF_8);
        MultipartFile file = new MockMultipartFile("file", "README.md", "text/markdown", bytes);
        MdDocument expected = new MdDocument(
                1L,
                "README.md",
                new String(bytes, StandardCharsets.UTF_8),
                bytes.length,
                LocalDateTime.now(),
                LocalDateTime.now());
        when(mapper.selectByFileName("README.md")).thenReturn(expected);

        MdDocumentVO actual = new MdDocumentServiceImpl(mapper).upload(file);

        assertEquals(expected.getFileName(), actual.getFileName());
        assertEquals(expected.getContent(), actual.getContent());
        ArgumentCaptor<MdDocument> documentCaptor = ArgumentCaptor.forClass(MdDocument.class);
        verify(mapper).upsert(documentCaptor.capture());
        assertEquals("README.md", documentCaptor.getValue().getFileName());
        assertEquals(expected.getContent(), documentCaptor.getValue().getContent());
        assertEquals(bytes.length, documentCaptor.getValue().getFileSizeBytes());
        verify(mapper).selectByFileName("README.md");
    }

    @Test
    void uploadUsesBasenameWhenClientSendsAPath() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "../notes.md", "text/markdown", "notes".getBytes());
        MdDocument expected = new MdDocument(2L, "notes.md", "notes", 5L, null, null);
        when(mapper.selectByFileName("notes.md")).thenReturn(expected);

        MdDocumentVO actual = new MdDocumentServiceImpl(mapper).upload(file);

        assertEquals("notes.md", actual.getFileName());
        verify(mapper).selectByFileName("notes.md");
    }

    @Test
    void listReturnsSummaryViewsInMapperOrder() {
        MdDocument first = new MdDocument(1L, "first.md", null, 10L, null, null);
        MdDocument second = new MdDocument(2L, "second.md", null, 20L, null, null);
        when(mapper.selectAll()).thenReturn(List.of(first, second));

        List<MdDocumentSummaryVO> actual = new MdDocumentServiceImpl(mapper).list();

        assertEquals("first.md", actual.get(0).getFileName());
        assertEquals("second.md", actual.get(1).getFileName());
        verify(mapper).selectAll();
    }

    @Test
    void getByIdReturnsCompleteDocumentView() {
        MdDocument expected = new MdDocument(3L, "guide.md", "# Guide", 7L, null, null);
        when(mapper.selectById(3L)).thenReturn(expected);

        MdDocumentVO actual = new MdDocumentServiceImpl(mapper).getById(3L);

        assertEquals(expected.getContent(), actual.getContent());
        verify(mapper).selectById(3L);
    }

    @Test
    void getByIdReturnsNotFoundWhenDocumentDoesNotExist() {
        when(mapper.selectById(404L)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new MdDocumentServiceImpl(mapper).getById(404L));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void syncContentUpdatesTheFullDraftAndRefreshesMarkdownContext() {
        String content = "# Updated\n\n中文";
        long fileSizeBytes = content.getBytes(StandardCharsets.UTF_8).length;
        MdDocument existing = new MdDocument(7L, "guide.md", "# Old", 6L, null, null);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 9, 12, 0);
        MdDocument updated = new MdDocument(7L, "guide.md", content, fileSizeBytes, null, updatedAt);
        when(this.mapper.selectById(7L)).thenReturn(existing, updated);
        when(this.mapper.updateContentById(7L, content, fileSizeBytes)).thenReturn(1);

        MdDocumentSyncVO actual = new MdDocumentServiceImpl(this.mapper, this.contextProvider)
                .syncContent(7L, content);

        assertEquals(7L, actual.getDocumentId());
        assertEquals("SYNCED", actual.getStatus());
        assertEquals(updatedAt, actual.getUpdatedAt());
        verify(this.mapper).updateContentById(7L, content, fileSizeBytes);
        verify(this.contextProvider).refresh(updated);
    }

    @Test
    void syncContentAllowsAnEmptyMarkdownDocument() {
        MdDocument existing = new MdDocument(8L, "empty.md", "old", 3L, null, null);
        MdDocument updated = new MdDocument(8L, "empty.md", "", 0L, null, null);
        when(this.mapper.selectById(8L)).thenReturn(existing, updated);
        when(this.mapper.updateContentById(8L, "", 0L)).thenReturn(1);

        MdDocumentSyncVO actual = new MdDocumentServiceImpl(this.mapper).syncContent(8L, "");

        assertEquals(8L, actual.getDocumentId());
        assertEquals("SYNCED", actual.getStatus());
        verify(this.mapper).updateContentById(8L, "", 0L);
    }

    @Test
    void syncContentRejectsMissingContentBeforeReadingTheDatabase() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new MdDocumentServiceImpl(this.mapper).syncContent(7L, null));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(this.mapper);
    }

    @Test
    void syncContentReturnsNotFoundWithoutUpdatingAnUnknownDocument() {
        when(this.mapper.selectById(404L)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new MdDocumentServiceImpl(this.mapper).syncContent(404L, "# Missing"));

        assertEquals(404, exception.getStatusCode().value());
        verify(this.mapper).selectById(404L);
    }

    @Test
    void deleteByIdDeletesAnExistingDocument() {
        when(mapper.deleteById(7L)).thenReturn(1);

        new MdDocumentServiceImpl(mapper).deleteById(7L);

        verify(mapper).deleteById(7L);
    }

    @Test
    void deleteByIdReturnsNotFoundWhenDocumentDoesNotExist() {
        when(mapper.deleteById(404L)).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new MdDocumentServiceImpl(mapper).deleteById(404L));

        assertEquals(404, exception.getStatusCode().value());
    }
}
