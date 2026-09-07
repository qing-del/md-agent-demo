package com.jacolp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

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
                    session_key VARCHAR(36) NOT NULL UNIQUE,
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
        session.setSessionKey("550e8400-e29b-41d4-a716-446655440000");
        session.setTitle("Spring AI");
        session.setMessages("[{\"role\":\"user\",\"content\":\"hello\"}]");
        session.setReferencedFileContents("[{\"fileName\":\"guide.md\"}]");

        assertEquals(1, mapper.insert(session));
        assertTrue(session.getId() > 0);

        ChatSession saved = mapper.selectById(session.getId());
        assertNotNull(saved);
        assertEquals(session.getSessionKey(), saved.getSessionKey());
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
        assertEquals(saved.getId(), mapper.selectBySessionKey(session.getSessionKey()).getId());

        assertEquals(1, mapper.deleteById(saved.getId()));
        assertNull(mapper.selectById(saved.getId()));
    }

    @Test
    void listsSummaryRecordsByOptionalLiteralTitleAndStableOrder() {
        LocalDateTime oldTime = LocalDateTime.of(2026, 9, 7, 12, 0);
        LocalDateTime newestTime = LocalDateTime.of(2026, 9, 7, 12, 5);
        insertSession("550e8400-e29b-41d4-a716-446655440001", "Old Spring", oldTime);
        insertSession("550e8400-e29b-41d4-a716-446655440002", "Spring Guide", newestTime);
        insertSession("550e8400-e29b-41d4-a716-446655440003", "Another title", newestTime);
        insertSession("550e8400-e29b-41d4-a716-446655440004", "100% guide v1_guide", oldTime);

        List<ChatSession> all = mapper.selectSummaryList(null);
        assertEquals(4, all.size());
        assertEquals("550e8400-e29b-41d4-a716-446655440003", all.get(0).getSessionKey());
        assertEquals("550e8400-e29b-41d4-a716-446655440002", all.get(1).getSessionKey());
        assertEquals("550e8400-e29b-41d4-a716-446655440004", all.get(2).getSessionKey());
        assertEquals("550e8400-e29b-41d4-a716-446655440001", all.get(3).getSessionKey());
        assertEquals("[]", all.get(0).getMessages());
        assertEquals("[]", all.get(0).getReferencedFileContents());

        List<ChatSession> spring = mapper.selectSummaryList("Spring");
        assertEquals(List.of(
                "550e8400-e29b-41d4-a716-446655440002",
                "550e8400-e29b-41d4-a716-446655440001"),
                spring.stream().map(ChatSession::getSessionKey).toList());

        assertEquals(4, mapper.selectSummaryList("").size());
        assertEquals(1, mapper.selectSummaryList("100%").size());
        assertEquals(1, mapper.selectSummaryList("v1_").size());
    }

    private void insertSession(String sessionKey, String title, LocalDateTime updatedAt) {
        jdbcTemplate.update("""
                INSERT INTO chat_sessions (
                    session_key, title, messages, referenced_file_contents, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, sessionKey, title, "[{\"role\":\"user\",\"content\":\"hidden\"}]",
                "[{\"fileName\":\"guide.md\"}]", Timestamp.valueOf(updatedAt), Timestamp.valueOf(updatedAt));
    }
}
