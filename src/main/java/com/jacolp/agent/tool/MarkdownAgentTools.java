package com.jacolp.agent.tool;

import java.util.Objects;

import com.jacolp.agent.markdown.MarkdownContextProvider;
import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.DocumentSearchPage;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import com.jacolp.agent.markdown.model.SectionPage;
import com.jacolp.agent.markdown.model.SectionReplaceProposal;
import com.jacolp.agent.markdown.operation.Operation;
import com.jacolp.agent.markdown.operation.OperationManager;
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

    private final OperationManager operationManager;

    /**
     * 创建 Markdown Agent 工具集合。
     *
     * @param contextProvider Markdown 上下文提供器
     * @param markdownManager Markdown 上下文管理器
     * @param operationManager 替换提案管理器
     */
    public MarkdownAgentTools(
            MarkdownContextProvider contextProvider,
            MarkdownManager markdownManager,
            OperationManager operationManager) {
        this.contextProvider = Objects.requireNonNull(contextProvider, "contextProvider cannot be null");
        this.markdownManager = Objects.requireNonNull(markdownManager, "markdownManager cannot be null");
        this.operationManager = Objects.requireNonNull(operationManager, "operationManager cannot be null");
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
        return this.markdownManager.getVersionedHeadingTree(id);
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
        return this.markdownManager.getVersionedSectionPreview(id, nodeNumber);
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

    @Tool(name = "search_document_content",
            description = "在 Markdown 原文中精确搜索（区分大小写）。可限定章节及其子章节；每页最多10项。返回 revision 和定位信息。context 可能截断，修改前须读取足够原文；nextCursor 非空可续读。")
    public DocumentSearchPage searchDocumentContent(
            @ToolParam(description = "Markdown 文档 ID") long documentId,
            @ToolParam(description = "非空字面查询，最多256个Unicode码点") String query,
            @ToolParam(required = false, description = "可选章节编号，省略则全文搜索") Integer nodeNumber,
            @ToolParam(required = false, description = "上一页 nextCursor；参数或文档版本变化后需重新搜索") String cursor) {
        DocumentId id = this.contextProvider.ensureLoaded(documentId);
        return this.markdownManager.searchDocumentContent(id, query, nodeNumber, cursor);
    }

    @Tool(name = "propose_section_insert",
            description = "创建章节直属正文插入提案，不修改文档。必须使用读取结果的 baseRevision。body_end 位于子章节前；before/after 要求唯一原文锚点。newText 原样插入，须自行提供所需换行。")
    public SectionReplaceProposal proposeSectionInsert(
            @ToolParam(description = "Markdown 文档 ID") long documentId,
            @ToolParam(description = "读取结果中的 revision") String baseRevision,
            @ToolParam(description = "基础版本的章节编号") int nodeNumber,
            @ToolParam(description = "body_start/body_end/before/after") String position,
            @ToolParam(required = false, description = "before/after 必需的唯一原文锚点；其他位置省略") String anchorText,
            @ToolParam(description = "非空新增原文，包含所需换行") String newText) {
        DocumentId id = this.contextProvider.ensureLoaded(documentId);
        return SectionReplaceProposal.from(this.operationManager.createInsert(
                id, baseRevision, nodeNumber, position, anchorText, newText));
    }

    /**
     * 创建等待用户确认的 Markdown 替换提案。
     *
     * @param documentId 文档主键
     * @param nodeNumber 章节节点编号
     * @param originalText 直属正文中要精确匹配的原文
     * @param newText 替换文本；空字符串表示删除
     * @return 待确认替换提案
     */
    @Tool(
            name = "propose_section_replace",
            description = "创建 Markdown 章节替换提案。该工具只创建 PENDING 提案，不会直接修改文档；用户确认后才会执行。")
    public SectionReplaceProposal proposeSectionReplace(
            @ToolParam(description = "数据库中的 Markdown 文档 ID") long documentId,
            @ToolParam(description = "文章概览中返回的章节节点编号") int nodeNumber,
            @ToolParam(description = "目标章节直属正文中需要被唯一匹配的原始文本") String originalText,
            @ToolParam(description = "替换后的文本；传空字符串表示删除匹配文本") String newText) {
        DocumentId id = this.contextProvider.ensureLoaded(documentId);
        Operation operation = this.operationManager.create(
                new SectionNodeRef(id, nodeNumber), originalText, newText);
        return SectionReplaceProposal.from(operation);
    }
}
