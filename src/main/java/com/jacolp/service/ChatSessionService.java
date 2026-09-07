package com.jacolp.service;

import java.util.List;

import com.jacolp.pojo.vo.ChatSessionSummaryVO;

/**
 * 定义聊天会话查询业务。
 */
public interface ChatSessionService {

    /**
     * 查询聊天会话摘要列表。
     *
     * @param title 可选的标题查询词；为空时查询全部会话
     * @return 按最近更新时间倒序排列的会话摘要列表
     */
    List<ChatSessionSummaryVO> list(String title);
}
