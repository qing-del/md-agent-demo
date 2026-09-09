package com.jacolp.pojo.vo;

import java.util.Objects;

/**
 * 历史聊天记录中的单条可展示消息。
 */
public final class ChatMessageVO {

    private final String role;

    private final String content;

    /**
     * 创建一条历史聊天消息。
     *
     * @param role 消息角色，只允许 user 或 assistant
     * @param content 消息正文
     */
    public ChatMessageVO(String role, String content) {
        this.role = Objects.requireNonNull(role, "role cannot be null");
        this.content = Objects.requireNonNull(content, "content cannot be null");
    }

    /**
     * 获取消息角色。
     *
     * @return 消息角色
     */
    public String getRole() {
        return this.role;
    }

    /**
     * 获取消息正文。
     *
     * @return 消息正文
     */
    public String getContent() {
        return this.content;
    }
}
