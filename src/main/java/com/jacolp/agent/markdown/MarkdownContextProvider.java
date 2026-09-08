package com.jacolp.agent.markdown;

import java.util.Objects;

import com.jacolp.agent.markdown.exception.MarkdownContextNotFoundException;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.pojo.entity.MdDocument;
import com.jacolp.mapper.MdDocumentMapper;
import org.springframework.stereotype.Service;

/**
 * 负责将数据库中的 Markdown 文档加载为 SDK 可查询的上下文快照。
 */
@Service
public class MarkdownContextProvider {

    private final MdDocumentMapper mapper;

    private final MarkdownManager markdownManager;

    /**
     * 创建 Markdown 上下文提供器。
     *
     * @param mapper Markdown 文档数据库访问对象
     * @param markdownManager Markdown 上下文管理器
     */
    public MarkdownContextProvider(MdDocumentMapper mapper, MarkdownManager markdownManager) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
        this.markdownManager = Objects.requireNonNull(markdownManager, "markdownManager cannot be null");
    }

    /**
     * 确保指定文档已经注册到 Markdown SDK，并返回稳定的值对象标识。
     *
     * @param documentId 数据库文档主键
     * @return 已注册的文档标识
     */
    public DocumentId ensureLoaded(long documentId) {
        DocumentId id = new DocumentId(documentId);
        try {
            this.markdownManager.getEntity(id);
            return id;
        }
        catch (MarkdownContextNotFoundException ignored) {
            // 首次访问或应用重启后，从数据库恢复当前文档快照。
        }

        MdDocument document = this.mapper.selectById(documentId);
        if (document == null || document.getContent() == null) {
            throw new MarkdownContextNotFoundException(id);
        }
        this.markdownManager.register(id, document.getContent());
        return id;
    }

    /**
     * 在文档上传或更新成功后刷新内存上下文。
     *
     * @param document 数据库中的最新文档
     */
    public void refresh(MdDocument document) {
        Objects.requireNonNull(document, "document cannot be null");
        this.markdownManager.register(new DocumentId(document.getId()), document.getContent());
    }

    /**
     * 在文档删除成功后移除内存上下文。
     *
     * @param documentId 数据库文档主键
     */
    public void remove(long documentId) {
        this.markdownManager.unregister(new DocumentId(documentId));
    }

    /**
     * 获取已注册文档的当前快照。
     *
     * @param documentId 数据库文档主键
     * @return 当前不可变上下文
     */
    public MarkdownContext get(long documentId) {
        return this.markdownManager.getEntity(ensureLoaded(documentId));
    }
}
