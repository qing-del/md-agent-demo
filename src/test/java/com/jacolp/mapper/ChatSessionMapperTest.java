package com.jacolp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jacolp.pojo.entity.ChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.ai.openai.api-key=test-key")
class ChatSessionMapperTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatSessionMapper mapper;

    @BeforeEach
    void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS chat_sessions");
        jdbcTemplate.execute("""
                CREATE TABLE chat_sessions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    title VARCHAR(200),
                    messages LONGVARCHAR NOT NULL DEFAULT '[]',
                    referenced_file_contents LONGVARCHAR NOT NULL DEFAULT '[]',
                    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
    }

    @Test
    void persistsAndUpdatesChatSessionSnapshot() {
        ChatSession session = new ChatSession();
        session.setTitle("Spring AI");
        session.setMessages("[{\"role\":\"user\",\"content\":\"hello\"}]");
        session.setReferencedFileContents("[{\"fileName\":\"guide.md\"}]");

        assertEquals(1, mapper.insert(session));
        assertTrue(session.getId() > 0);

        ChatSession saved = mapper.selectById(session.getId());
        assertNotNull(saved);
        assertEquals("Spring AI", saved.getTitle());
        assertEquals(session.getMessages(), saved.getMessages());
        assertEquals(session.getReferencedFileContents(), saved.getReferencedFileContents());

        saved.setTitle("Updated title");
        saved.setMessages("[{\"role\":\"assistant\",\"content\":\"hi\"}]");
        saved.setReferencedFileContents("[]");
        assertEquals(1, mapper.updateSnapshot(saved));

        ChatSession updated = mapper.selectById(saved.getId());
        assertEquals("Updated title", updated.getTitle());
        assertEquals(saved.getMessages(), updated.getMessages());
        assertEquals(saved.getReferencedFileContents(), updated.getReferencedFileContents());
        assertEquals(1, mapper.selectAll().size());

        assertEquals(1, mapper.deleteById(saved.getId()));
        assertNull(mapper.selectById(saved.getId()));
    }
}
