package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.jacolp.OfflineHttpTestConfiguration;
import com.jacolp.agent.markdown.exception.MarkdownPersistenceException;
import com.jacolp.agent.markdown.model.DocumentEdit;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.operation.OperationManager;
import com.jacolp.agent.markdown.operation.OperationStatus;
import com.jacolp.agent.markdown.persistence.MdDocumentMarkdownStore;
import com.jacolp.mapper.MdDocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "QWEN_API_KEY=test-key")
@Import(OfflineHttpTestConfiguration.class)
class DocumentPatchPersistenceTest {
    private static final DocumentId ID = new DocumentId(1);
    private static final String SOURCE = "# Root\nalpha\nbeta\n";

    @Autowired JdbcTemplate jdbc;
    @Autowired MdDocumentMapper mapper;

    @BeforeEach
    void createDocument() {
        jdbc.execute("DROP TABLE IF EXISTS md_documents");
        jdbc.execute("""
                CREATE TABLE md_documents (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    file_name VARCHAR(255) NOT NULL UNIQUE,
                    content LONGVARCHAR NOT NULL CHECK (content NOT LIKE '%REJECT%'),
                    file_size_bytes BIGINT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("INSERT INTO md_documents(id, file_name, content, file_size_bytes) VALUES (1, 'root.md', ?, ?)",
                SOURCE, SOURCE.length());
    }

    @Test
    void aRejectedCandidateDoesNotPersistEvenItsFirstEdit() {
        MarkdownManager markdown = new MarkdownManager(new MdDocumentMarkdownStore(mapper), ID, SOURCE);
        OperationManager manager = new OperationManager(markdown);
        var proposal = manager.createPatch(ID, markdown.getEntity(ID).getRevision(), List.of(
                DocumentEdit.replace(1, "alpha", "first edit"), DocumentEdit.replace(1, "beta", "REJECT")));
        assertThrows(MarkdownPersistenceException.class, () -> manager.confirm(proposal.getOpId()));
        assertEquals(SOURCE, mapper.selectById(ID.value()).getContent());
        assertEquals(SOURCE, markdown.restoreMarkdown(ID));
        assertEquals(OperationStatus.FAILED, manager.get(proposal.getOpId()).getStatus());
    }

    @Test
    void aSuccessfulPatchPublishesTheSameWholeCandidateToDatabaseAndMemory() {
        MarkdownManager markdown = new MarkdownManager(new MdDocumentMarkdownStore(mapper), ID, SOURCE);
        OperationManager manager = new OperationManager(markdown);
        var proposal = manager.createPatch(ID, markdown.getEntity(ID).getRevision(), List.of(
                DocumentEdit.replace(1, "alpha", "A"), DocumentEdit.replace(1, "beta", "B")));
        assertEquals(SOURCE, mapper.selectById(ID.value()).getContent());
        var result = manager.confirm(proposal.getOpId());
        assertEquals("# Root\nA\nB\n", mapper.selectById(ID.value()).getContent());
        assertEquals(result.getContext().getSource(), markdown.restoreMarkdown(ID));
    }
}
