package com.jacolp.pojo.dto;

/**
 * 接收结构化聊天请求的数据传输对象。
 */
public class ChatRequestDTO {

    private String sessionKey;

    private ChatMessageDTO message;

    /**
     * 创建空的聊天请求对象，供框架反序列化使用。
     */
    public ChatRequestDTO() {
    }

    /**
     * 创建完整的聊天请求对象。
     *
     * @param sessionKey 前端生成的 UUID 会话标识
     * @param message 结构化聊天消息
     */
    public ChatRequestDTO(String sessionKey, ChatMessageDTO message) {
        this.sessionKey = sessionKey;
        this.message = message;
    }

    /**
     * 获取 UUID 会话标识。
     *
     * @return UUID 会话标识
     */
    public String getSessionKey() {
        return sessionKey;
    }

    /**
     * 设置 UUID 会话标识。
     *
     * @param sessionKey UUID 会话标识
     */
    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    /**
     * 获取结构化聊天消息。
     *
     * @return 结构化聊天消息
     */
    public ChatMessageDTO getMessage() {
        return message;
    }

    /**
     * 设置结构化聊天消息。
     *
     * @param message 结构化聊天消息
     */
    public void setMessage(ChatMessageDTO message) {
        this.message = message;
    }
}
