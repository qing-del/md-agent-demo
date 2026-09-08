package com.jacolp.agent.tool;

import java.util.Objects;

import com.jacolp.agent.markdown.MarkdownContextProvider;
import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.DocumentId;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 提供给 Agent 的 Markdown 读取和替换提案工具。
 */
@Component
public class MarkdownAgentTools {

    private final MarkdownContextProvider contextProvider;

    private final MarkdownManager markdownManager;

    /**
     * 创建 Markdown Agent 工具集合。
     *
     * @param contextProvider Markdown 上下文提供器
     * @param markdownManager Markdown 上下文管理器
     */
    public MarkdownAgentTools(
            MarkdownContextProvider contextProvider,
            MarkdownManager markdownManager) {
        this.contextProvider = Objects.requireNonNull(contextProvider, "contextProvider cannot be null");
        this.markdownManager = Objects.requireNonNull(markdownManager, "markdownManager cannot be null");
    }

    /**
     * 读取 Markdown 文章的编号标题树。
     *
     * @param documentId 文档主键
     * @return 带节点编号和层级缩进的标题树
     */
    @Tool(
            name = "get_article_overview",
            description = "读取 Markdown 文章的标题节点树。返回的节点编号可用于后续章节读取或替换提案。")
    public String getArticleOverview(
            @ToolParam(description = "数据库中的 Markdown 文档 ID") long documentId) {
        DocumentId id = this.contextProvider.ensureLoaded(documentId);
        return this.markdownManager.getHeadingTree(id);
    }
}
