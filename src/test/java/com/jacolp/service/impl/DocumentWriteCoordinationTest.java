package com.jacolp.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Future;

import com.jacolp.agent.markdown.MarkdownContextProvider;
import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.mapper.MdDocumentMapper;
import com.jacolp.pojo.entity.MdDocument;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class DocumentWriteCoordinationTest {
    @ParameterizedTest
    @ValueSource(strings = {"sync", "upload", "delete"})
    void applicationWritesWaitForTheSameDocumentLockAsProposals(String mode) throws Exception {
        DocumentId id = new DocumentId(101);
        String source = "# Root\nnew\n";
        var document = new MdDocument(id.value(), "root.md", source,
                source.getBytes(StandardCharsets.UTF_8).length, null, null);
        MdDocumentMapper mapper = mock(MdDocumentMapper.class);
        when(mapper.selectById(id.value())).thenReturn(document);
        when(mapper.selectByFileName("root.md")).thenReturn(document);
        when(mapper.updateContentById(anyLong(), anyString(), anyLong())).thenReturn(1);
        when(mapper.deleteById(id.value())).thenReturn(1);
        MarkdownManager markdown = new MarkdownManager(snapshot -> { }, id, source);
        var service = new MdDocumentServiceImpl(mapper, new MarkdownContextProvider(mapper, markdown));
        var executor = Executors.newSingleThreadExecutor();
        AtomicReference<Future<?>> task = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        try {
            markdown.withDocumentLock(id, () -> {
                task.set(executor.submit(() -> {
                    started.countDown();
                    switch (mode) {
                        case "sync" -> service.syncContent(id.value(), source);
                        case "delete" -> service.deleteById(id.value());
                        default -> service.upload(new MockMultipartFile("file", "root.md", "text/markdown",
                                source.getBytes(StandardCharsets.UTF_8)));
                    }
                }));
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> task.get().get(100, TimeUnit.MILLISECONDS));
                }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
                verify(mapper, never()).updateContentById(anyLong(), anyString(), anyLong());
                verify(mapper, never()).upsert(any());
                verify(mapper, never()).deleteById(anyLong());
                return null;
            });
            task.get().get(5, TimeUnit.SECONDS);
            switch (mode) {
                case "sync" -> verify(mapper).updateContentById(eq(id.value()), eq(source), anyLong());
                case "delete" -> verify(mapper).deleteById(id.value());
                default -> verify(mapper).upsert(any());
            }
        }
        finally { executor.shutdownNow(); }
    }
}
