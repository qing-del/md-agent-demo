package com.jacolp.service.impl;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.agent.context.ChatContextManager;
import com.jacolp.mapper.ChatSessionMapper;
import com.jacolp.pojo.vo.ChatMessageVO;
import com.jacolp.pojo.vo.ChatSessionDetailVO;
import com.jacolp.pojo.vo.ChatSessionSummaryVO;
import com.jacolp.service.ChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 聊天会话查询业务的默认实现。
 */
@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper mapper;

    private final ChatContextManager chatContextManager;

    /**
     * 创建聊天会话查询服务。
     *
     * @param mapper 聊天会话数据库访问对象
     */
    public ChatSessionServiceImpl(ChatSessionMapper mapper) {
        this(mapper, new ChatContextManager(mapper, new ObjectMapper()));
    }

    /**
     * 创建聊天会话查询服务，并绑定聊天上下文管理器以读取未刷盘快照。
     *
     * @param mapper 聊天会话数据库访问对象
     * @param chatContextManager 聊天上下文管理器
     */
    @Autowired
    public ChatSessionServiceImpl(ChatSessionMapper mapper, ChatContextManager chatContextManager) {
        this.mapper = mapper;
        this.chatContextManager = chatContextManager;
    }

    /**
     * 查询会话摘要并转换为接口视图对象。
     *
     * @param title 可选的标题查询词；为空或空白时查询全部会话
     * @return 按最近更新时间倒序排列的会话摘要列表
     */
    @Override
    public List<ChatSessionSummaryVO> list(String title) {
        return this.mapper.selectSummaryList(normalizeTitle(title)).stream()
                .map(ChatSessionSummaryVO::from)
                .toList();
    }

    /**
     * 查询指定会话的当前历史快照并转换为详情视图。
     *
     * @param sessionKey 前端传入的 UUID 会话标识
     * @return 会话详情及可展示的历史消息
     * @throws ResponseStatusException 会话标识非法或会话不存在时抛出
     */
    @Override
    public ChatSessionDetailVO getBySessionKey(String sessionKey) {
        String canonicalSessionKey = canonicalSessionKey(sessionKey);
        ChatContextManager.ChatSessionSnapshot snapshot = this.chatContextManager
                .getExistingSnapshot(canonicalSessionKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "session not found: " + canonicalSessionKey));

        List<ChatMessageVO> messages = snapshot.messages().stream()
                .map(message -> new ChatMessageVO(
                        message.getMessageType().getValue(), message.getText()))
                .toList();
        return new ChatSessionDetailVO(
                snapshot.sessionKey(),
                snapshot.title(),
                messages,
                snapshot.createdAt(),
                snapshot.updatedAt());
    }

    /**
     * 校验并规范化前端传入的 UUID 会话标识。
     *
     * @param sessionKey 原始会话标识
     * @return 小写、标准格式的 UUID 会话标识
     * @throws ResponseStatusException 会话标识为空或格式非法时抛出 400 异常
     */
    private static String canonicalSessionKey(String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionKey must be a UUID");
        }

        try {
            UUID uuid = UUID.fromString(sessionKey);
            // UUID.fromString 接受部分非标准缩写，因此严格要求完整标准格式。
            if (!uuid.toString().equalsIgnoreCase(sessionKey)) {
                throw new IllegalArgumentException("sessionKey must use the standard UUID format");
            }
            return uuid.toString();
        }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionKey must be a UUID", exception);
        }
    }

    /**
     * 规范化标题查询词，并将 SQL LIKE 通配符转义为普通字符。
     *
     * @param title 原始标题查询词
     * @return 可直接传给 Mapper 的查询词；无查询条件时返回 {@code null}
     */
    private static String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }

        String normalizedTitle = title.strip();
        // 空查询代表查询全部，避免为无意义的空字符串拼接 LIKE 条件。
        if (normalizedTitle.isEmpty()) {
            return null;
        }

        // 使用 ! 作为 LIKE 的转义字符，保证用户输入的 %、_ 和 ! 按字面值匹配。
        return normalizedTitle
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
