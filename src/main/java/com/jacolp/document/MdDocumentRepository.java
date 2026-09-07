package com.jacolp.document;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MdDocumentRepository {

    private static final RowMapper<MdDocument> ROW_MAPPER = (resultSet, rowNum) -> new MdDocument(
            resultSet.getLong("id"),
            resultSet.getString("file_name"),
            resultSet.getString("content"),
            resultSet.getLong("file_size_bytes"),
            resultSet.getObject("created_at", java.time.LocalDateTime.class),
            resultSet.getObject("updated_at", java.time.LocalDateTime.class));

    private static final RowMapper<MdDocumentSummary> SUMMARY_ROW_MAPPER = (resultSet, rowNum) -> new MdDocumentSummary(
            resultSet.getLong("id"),
            resultSet.getString("file_name"),
            resultSet.getLong("file_size_bytes"),
            resultSet.getObject("created_at", java.time.LocalDateTime.class),
            resultSet.getObject("updated_at", java.time.LocalDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public MdDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MdDocument upsert(String fileName, String content, long fileSizeBytes) {
        jdbcTemplate.update("""
                INSERT INTO md_documents (file_name, content, file_size_bytes)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    content = VALUES(content),
                    file_size_bytes = VALUES(file_size_bytes)
                """, fileName, content, fileSizeBytes);

        return findByFileName(fileName)
                .orElseThrow(() -> new IllegalStateException("document was not saved: " + fileName));
    }

    private Optional<MdDocument> findByFileName(String fileName) {
        return jdbcTemplate.query("""
                SELECT id, file_name, content, file_size_bytes, created_at, updated_at
                FROM md_documents
                WHERE file_name = ?
                """, ROW_MAPPER, fileName).stream().findFirst();
    }

    public List<MdDocumentSummary> findAll() {
        return jdbcTemplate.query("""
                SELECT id, file_name, file_size_bytes, created_at, updated_at
                FROM md_documents
                ORDER BY updated_at DESC, id DESC
                """, SUMMARY_ROW_MAPPER);
    }
}
