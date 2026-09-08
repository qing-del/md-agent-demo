package com.jacolp.agent.markdown.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.mapper.MdDocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MdDocumentMarkdownStoreTest {

    @Mock
    private MdDocumentMapper mapper;

    @Test
    void persistsTheUpdatedSourceAndUtf8ByteSizeByDocumentId() {
        MarkdownContext context = new MarkdownContext(
                new DocumentId(7L), "revision", "# 中文\n😀", List.of(), Map.of());
        when(this.mapper.updateContentById(7L, context.getSource(), 13L)).thenReturn(1);

        new MdDocumentMarkdownStore(this.mapper).save(context);

        verify(this.mapper).updateContentById(7L, context.getSource(), 13L);
    }

    @Test
    void rejectsPersistenceWhenNoDocumentWasUpdated() {
        MarkdownContext context = new MarkdownContext(
                new DocumentId(7L), "revision", "# Root", List.of(), Map.of());
        when(this.mapper.updateContentById(7L, context.getSource(), 6L)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> new MdDocumentMarkdownStore(this.mapper).save(context));
    }
}
