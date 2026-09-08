package com.jacolp.agent.markdown.persistence;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.jacolp.agent.markdown.MarkdownStore;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.mapper.MdDocumentMapper;
import org.springframework.stereotype.Component;

/**
 * 将 Markdown SDK 生成的新快照写回现有文档表。
 */
@Component
public class MdDocumentMarkdownStore implements MarkdownStore {

    private static final long MAX_FILE_SIZE_BYTES = 0xFFFF_FFFFL;

    private final MdDocumentMapper mapper;

    /**
     * 创建 Markdown 快照持久化适配器。
     *
     * @param mapper Markdown 文档数据库访问对象
     */
    public MdDocumentMarkdownStore(MdDocumentMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
    }

    /**
     * 按文档主键更新正文，确保 SDK 的 DocumentId 与数据库记录保持一致。
     *
     * @param context 待持久化的 Markdown 快照
     */
    @Override
    public void save(MarkdownContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        String source = context.getSource();
        long fileSizeBytes = source.getBytes(StandardCharsets.UTF_8).length;
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("Markdown content is too large");
        }

        int updatedRows = this.mapper.updateContentById(
                context.getDocumentId().value(), source, fileSizeBytes);
        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Markdown document was not updated: " + context.getDocumentId().value());
        }
    }
}
