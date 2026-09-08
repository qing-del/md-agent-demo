package com.jacolp.agent.tool;

import java.util.Objects;

import com.jacolp.agent.markdown.MarkdownContextProvider;
import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.SectionPage;
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

    /**
     * 读取指定章节的直接正文和直属子标题预览。
     *
     * @param documentId 文档主键
     * @param nodeNumber 章节节点编号
     * @return 章节预览文本
     */
    @Tool(
            name = "get_section_preview",
            description = "读取指定 Markdown 章节的标题、直属正文和直属子标题预览，不展开孙级章节。")
    public String getSectionPreview(
            @ToolParam(description = "数据库中的 Markdown 文档 ID") long documentId,
            @ToolParam(description = "文章概览中返回的章节节点编号") int nodeNumber) {
        DocumentId id = this.contextProvider.ensureLoaded(documentId);
        return this.markdownManager.getSectionPreview(id, nodeNumber);
    }

    /**
     * 分页读取指定章节的完整内容。
     *
     * @param documentId 文档主键
     * @param nodeNumber 章节节点编号
     * @param cursor 上一页返回的 cursor；首次读取传 null
     * @return 一页章节内容及续读信息
     */
    @Tool(
            name = "get_section_content",
            description = "分页读取指定 Markdown 章节的完整内容。cursor 为空时读取第一页；返回 nextCursor 时必须继续调用本工具读取后续内容。")
    public SectionPage getSectionContent(
            @ToolParam(description = "数据库中的 Markdown 文档 ID") long documentId,
            @ToolParam(description = "文章概览中返回的章节节点编号") int nodeNumber,
            @ToolParam(required = false, description = "上一页返回的 nextCursor，首次读取时省略") String cursor) {
        DocumentId id = this.contextProvider.ensureLoaded(documentId);
        return this.markdownManager.getSectionAll(id, nodeNumber, cursor);
    }
}
