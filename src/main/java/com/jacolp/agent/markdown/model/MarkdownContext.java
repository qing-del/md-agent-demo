package com.jacolp.agent.markdown.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变的 Markdown 快照及其解析后的章节索引。
 */
public final class MarkdownContext {

    private final DocumentId documentId;

    private final String revision;

    private final String source;

    private final List<Integer> rootNodeIds;

    private final Map<Integer, SectionNode> nodes;

    /**
     * 创建一个 Markdown 上下文快照。
     *
     * @param documentId 已持久化文档的标识
     * @param revision 当前内容 revision
     * @param source 完整 Markdown 原文
     * @param rootNodeIds 按原文顺序排列的根节点编号
     * @param nodes 按节点编号索引的章节节点
     */
    public MarkdownContext(
            DocumentId documentId,
            String revision,
            String source,
            List<Integer> rootNodeIds,
            Map<Integer, SectionNode> nodes) {
        this.documentId = Objects.requireNonNull(documentId, "documentId cannot be null");
        this.revision = Objects.requireNonNull(revision, "revision cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.rootNodeIds = List.copyOf(Objects.requireNonNull(rootNodeIds, "rootNodeIds cannot be null"));

        // 复制并冻结节点 Map，防止调用方通过原始集合修改快照内容。
        Map<Integer, SectionNode> copiedNodes = new LinkedHashMap<>(
                Objects.requireNonNull(nodes, "nodes cannot be null"));
        if (copiedNodes.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            // 节点索引中的键和值都必须存在，否则无法可靠地按编号查询。
            throw new IllegalArgumentException("nodes cannot contain null keys or values");
        }
        this.nodes = Collections.unmodifiableMap(copiedNodes);
    }

    /**
     * 获取已持久化文档的标识。
     *
     * @return 文档标识
     */
    public DocumentId getDocumentId() {
        return this.documentId;
    }

    /**
     * 获取当前 source 的内容指纹。
     *
     * @return revision 字符串
     */
    public String getRevision() {
        return this.revision;
    }

    /**
     * 获取当前快照保存的完整 Markdown 原文。
     *
     * @return 未重新渲染的 source 文本
     */
    public String getSource() {
        return this.source;
    }

    /**
     * 获取根节点编号列表。
     *
     * @return 不可修改的根节点编号列表
     */
    public List<Integer> getRootNodeIds() {
        return this.rootNodeIds;
    }

    /**
     * 获取按编号索引的章节节点。
     *
     * @return 不可修改的节点 Map
     */
    public Map<Integer, SectionNode> getNodes() {
        return this.nodes;
    }
}
