package com.jacolp.controller;

import java.util.List;

import com.jacolp.pojo.vo.ChatSessionSummaryVO;
import com.jacolp.service.ChatSessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供聊天会话历史列表查询接口。
 */
@RestController
@RequestMapping("/api/sessions")
public class ChatSessionController {

    private final ChatSessionService service;

    /**
     * 创建聊天会话控制器。
     *
     * @param service 聊天会话查询业务服务
     */
    public ChatSessionController(ChatSessionService service) {
        this.service = service;
    }

    /**
     * 查询聊天会话摘要列表。
     *
     * @param title 可选的标题模糊查询词
     * @return 聊天会话摘要列表
     */
    @GetMapping
    public List<ChatSessionSummaryVO> list(
            @RequestParam(value = "title", required = false) String title) {
        return this.service.list(title);
    }
}
