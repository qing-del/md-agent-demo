-- md-agent database schema
-- Target: MySQL 8.x

CREATE DATABASE IF NOT EXISTS md_agent_demo
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE md_agent_demo;

CREATE TABLE IF NOT EXISTS md_documents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    file_size_bytes INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_md_documents_file_name (file_name)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Markdown documents, one current version per file name';

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NULL,
    messages JSON NOT NULL DEFAULT (JSON_ARRAY()),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_chat_sessions_updated_at (updated_at),
    CONSTRAINT chk_chat_sessions_messages_array
        CHECK (JSON_TYPE(messages) = 'ARRAY')
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Single-user chat sessions with message history stored as JSON';
