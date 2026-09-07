package com.jacolp.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    private MdDocumentRepository repository;

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
        when(repository.upsert("README.md", expected.content(), bytes.length)).thenReturn(expected);

        MdDocument actual = new MdDocumentServiceImpl(repository).upload(file);

        assertSame(expected, actual);
        verify(repository).upsert("README.md", expected.content(), bytes.length);
        assertEquals(bytes.length, actual.fileSizeBytes());
    }

    @Test
    void uploadUsesBasenameWhenClientSendsAPath() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "../notes.md", "text/markdown", "notes".getBytes());
        MdDocument expected = new MdDocument(2L, "notes.md", "notes", 5L, null, null);
        when(repository.upsert("notes.md", "notes", 5L)).thenReturn(expected);

        MdDocument actual = new MdDocumentServiceImpl(repository).upload(file);

        assertEquals("notes.md", actual.fileName());
        verify(repository).upsert("notes.md", "notes", 5L);
    }

    @Test
    void listReturnsRepositorySummariesInRepositoryOrder() {
        MdDocumentSummary first = new MdDocumentSummary(1L, "first.md", 10L, null, null);
        MdDocumentSummary second = new MdDocumentSummary(2L, "second.md", 20L, null, null);
        when(repository.findAll()).thenReturn(List.of(first, second));

        List<MdDocumentSummary> actual = new MdDocumentServiceImpl(repository).list();

        assertEquals(List.of(first, second), actual);
        verify(repository).findAll();
    }

    @Test
    void getByIdReturnsCompleteDocument() {
        MdDocument expected = new MdDocument(3L, "guide.md", "# Guide", 7L, null, null);
        when(repository.findById(3L)).thenReturn(Optional.of(expected));

        MdDocument actual = new MdDocumentServiceImpl(repository).getById(3L);

        assertSame(expected, actual);
        verify(repository).findById(3L);
    }

    @Test
    void getByIdReturnsNotFoundWhenDocumentDoesNotExist() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new MdDocumentServiceImpl(repository).getById(404L));

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void deleteByIdDeletesAnExistingDocument() {
        when(repository.deleteById(7L)).thenReturn(true);

        new MdDocumentServiceImpl(repository).deleteById(7L);

        verify(repository).deleteById(7L);
    }

    @Test
    void deleteByIdReturnsNotFoundWhenDocumentDoesNotExist() {
        when(repository.deleteById(404L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new MdDocumentServiceImpl(repository).deleteById(404L));

        assertEquals(404, exception.getStatusCode().value());
    }
}
