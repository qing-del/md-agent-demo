package com.jacolp.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.mapper.MdDocumentMapper;
import com.jacolp.service.impl.MdDocumentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MdDocumentServiceTest {

    @Mock
    private MdDocumentMapper mapper;

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

        MdDocument actual = new MdDocumentServiceImpl(mapper).upload(file);

        assertSame(expected, actual);
        verify(mapper).upsert("README.md", expected.content(), bytes.length);
        verify(mapper).selectByFileName("README.md");
        assertEquals(bytes.length, actual.fileSizeBytes());
    }

    @Test
    void uploadUsesBasenameWhenClientSendsAPath() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "../notes.md", "text/markdown", "notes".getBytes());
        MdDocument expected = new MdDocument(2L, "notes.md", "notes", 5L, null, null);
        when(mapper.selectByFileName("notes.md")).thenReturn(expected);

        MdDocument actual = new MdDocumentServiceImpl(mapper).upload(file);

        assertEquals("notes.md", actual.fileName());
        verify(mapper).upsert("notes.md", "notes", 5L);
    }

    @Test
    void listReturnsRepositorySummariesInRepositoryOrder() {
        MdDocumentSummary first = new MdDocumentSummary(1L, "first.md", 10L, null, null);
        MdDocumentSummary second = new MdDocumentSummary(2L, "second.md", 20L, null, null);
        when(mapper.selectAll()).thenReturn(List.of(first, second));

        List<MdDocumentSummary> actual = new MdDocumentServiceImpl(mapper).list();

        assertEquals(List.of(first, second), actual);
        verify(mapper).selectAll();
    }

    @Test
    void getByIdReturnsCompleteDocument() {
        MdDocument expected = new MdDocument(3L, "guide.md", "# Guide", 7L, null, null);
        when(mapper.selectById(3L)).thenReturn(expected);

        MdDocument actual = new MdDocumentServiceImpl(mapper).getById(3L);

        assertSame(expected, actual);
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
