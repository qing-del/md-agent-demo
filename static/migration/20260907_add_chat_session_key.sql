-- 为已有 chat_sessions 表添加前端生成的 UUID 会话标识。
-- 新建数据库可直接使用 ../init.sql；本脚本仅执行一次。

ALTER TABLE chat_sessions
    ADD COLUMN session_key CHAR(36) NULL AFTER id;

-- 为既有记录补齐 UUID，确保旧聊天记录仍可被查询和更新。
UPDATE chat_sessions
SET session_key = UUID()
WHERE session_key IS NULL;

ALTER TABLE chat_sessions
    MODIFY COLUMN session_key CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_chat_sessions_session_key (session_key);
