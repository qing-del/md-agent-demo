package com.jacolp.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

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

        MdDocument actual = new MdDocumentService(repository).upload(file);

        assertSame(expected, actual);
        verify(repository).upsert("README.md", expected.content(), bytes.length);
        assertEquals(bytes.length, actual.fileSizeBytes());
    }

    @Test
    void uploadUsesBasenameWhenClientSendsAPath() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "../notes.md", "text/markdown", "notes".getBytes());
        MdDocument expected = new MdDocument(2L, "notes.md", "notes", 5L, null, null);
        when(repository.upsert("notes.md", "notes", 5L)).thenReturn(expected);

        MdDocument actual = new MdDocumentService(repository).upload(file);

        assertEquals("notes.md", actual.fileName());
        verify(repository).upsert("notes.md", "notes", 5L);
    }
}
