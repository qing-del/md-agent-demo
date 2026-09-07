package com.jacolp.service.impl;

import java.util.List;

import com.jacolp.mapper.ChatSessionMapper;
import com.jacolp.pojo.vo.ChatSessionSummaryVO;
import com.jacolp.service.ChatSessionService;
import org.springframework.stereotype.Service;

/**
 * 聊天会话查询业务的默认实现。
 */
@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionMapper mapper;

    /**
     * 创建聊天会话查询服务。
     *
     * @param mapper 聊天会话数据库访问对象
     */
    public ChatSessionServiceImpl(ChatSessionMapper mapper) {
        this.mapper = mapper;
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
