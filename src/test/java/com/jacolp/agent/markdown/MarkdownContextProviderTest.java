package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jacolp.agent.markdown.exception.MarkdownContextNotFoundException;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.mapper.MdDocumentMapper;
import com.jacolp.pojo.entity.MdDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkdownContextProviderTest {

    @Mock
    private MdDocumentMapper mapper;

    @Mock
    private MarkdownManager markdownManager;

    @Test
    void loadsMissingContextFromTheDocumentMapper() {
        DocumentId documentId = new DocumentId(7L);
        MdDocument document = new MdDocument(7L, "guide.md", "# Guide\n", 8L, null, null);
        MarkdownContext context = new MarkdownContext(
                documentId, "revision", document.getContent(), java.util.List.of(), java.util.Map.of());
        when(this.markdownManager.getEntity(documentId)).thenThrow(new MarkdownContextNotFoundException(documentId));
        when(this.mapper.selectById(7L)).thenReturn(document);
        when(this.markdownManager.register(documentId, document.getContent())).thenReturn(context);

        MarkdownContextProvider provider = new MarkdownContextProvider(this.mapper, this.markdownManager);

        assertEquals(documentId, provider.ensureLoaded(7L));
        verify(this.markdownManager).register(documentId, document.getContent());
    }

    @Test
    void reusesAnAlreadyLoadedContextWithoutReadingTheDatabase() {
        DocumentId documentId = new DocumentId(7L);
        MarkdownContext context = new MarkdownContext(
                documentId, "revision", "# Guide\n", java.util.List.of(), java.util.Map.of());
        when(this.markdownManager.getEntity(documentId)).thenReturn(context);

        MarkdownContextProvider provider = new MarkdownContextProvider(this.mapper, this.markdownManager);

        assertEquals(documentId, provider.ensureLoaded(7L));
        verify(this.mapper, never()).selectById(7L);
    }

    @Test
    void reportsMissingDatabaseDocumentsAsMissingContexts() {
        DocumentId documentId = new DocumentId(7L);
        when(this.markdownManager.getEntity(documentId)).thenThrow(new MarkdownContextNotFoundException(documentId));
        when(this.mapper.selectById(7L)).thenReturn(null);

        MarkdownContextProvider provider = new MarkdownContextProvider(this.mapper, this.markdownManager);

        assertThrows(MarkdownContextNotFoundException.class, () -> provider.ensureLoaded(7L));
    }
}
