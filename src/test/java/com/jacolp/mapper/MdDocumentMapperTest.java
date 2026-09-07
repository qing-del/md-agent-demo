package com.jacolp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jacolp.pojo.entity.MdDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.ai.openai.api-key=test-key")
class MdDocumentMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MdDocumentMapper mapper;

    @BeforeEach
    void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS md_documents");
        jdbcTemplate.execute("""
                CREATE TABLE md_documents (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    file_name VARCHAR(255) NOT NULL UNIQUE,
                    content LONGVARCHAR NOT NULL,
                    file_size_bytes BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
    }

    @Test
    void mapperPersistsQueriesAndDeletesMarkdownDocuments() {
        MdDocument document = new MdDocument();
        document.setFileName("README.md");
        document.setContent("# 中文\n\n😀");
        document.setFileSizeBytes(document.getContent().getBytes(StandardCharsets.UTF_8).length);

        assertEquals(1, mapper.upsert(document));

        MdDocument saved = mapper.selectByFileName("README.md");
        assertNotNull(saved);
        assertEquals("# 中文\n\n😀", saved.getContent());
        assertEquals(document.getFileSizeBytes(), saved.getFileSizeBytes());

        document.setContent("# Updated");
        document.setFileSizeBytes(9L);
        assertEquals(2, mapper.upsert(document));

        MdDocument updated = mapper.selectById(saved.getId());
        assertEquals("# Updated", updated.getContent());
        assertEquals(9L, updated.getFileSizeBytes());
        List<MdDocument> documents = mapper.selectAll();
        assertEquals(1, documents.size());

        assertEquals(1, mapper.deleteById(saved.getId()));
        assertNull(mapper.selectById(saved.getId()));
    }
}
