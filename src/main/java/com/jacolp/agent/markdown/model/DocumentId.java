package com.jacolp.agent.markdown.model;

/**
 * 已持久化 Markdown 文档的稳定标识。
 *
 * <p>该值对象包装数据库中的正数文档主键，避免把文档 ID 与其他数值 ID 混用。</p>
 *
 * @param value 数据库文档主键值
 */
public record DocumentId(long value) {

    /**
     * 校验文档 ID 必须是正数。
     */
    public DocumentId {
        if (value <= 0) {
            throw new IllegalArgumentException("document id must be positive");
        }
    }
}
