package com.jacolp.agent.markdown.model;

import java.util.Objects;

/**
 * 章节的一页字节受限内容及其续页信息。
 */
public final class SectionPage {

    private final String content;

    private final boolean hasMore;

    private final String nextCursor;

    /**
     * 创建分页结果。
     *
     * @param content 当前页文本
     * @param hasMore 是否仍有后续内容
     * @param nextCursor 下一页 cursor；没有后续内容时必须为 {@code null}
     */
    public SectionPage(String content, boolean hasMore, String nextCursor) {
        this.content = Objects.requireNonNull(content, "content cannot be null");
        if (!hasMore && nextCursor != null) {
            // 最后一页没有下一页位置，避免返回相互矛盾的分页元数据。
            throw new IllegalArgumentException("nextCursor must be null when hasMore is false");
        }
        if (hasMore && (nextCursor == null || nextCursor.isBlank())) {
            // 尚有内容时必须提供可继续读取的 cursor。
            throw new IllegalArgumentException("nextCursor is required when hasMore is true");
        }
        this.hasMore = hasMore;
        this.nextCursor = nextCursor;
    }

    /**
     * 获取当前页文本。
     *
     * @return 当前页内容
     */
    public String getContent() {
        return this.content;
    }

    /**
     * 判断是否存在后续页。
     *
     * @return 存在后续内容时为 {@code true}
     */
    public boolean isHasMore() {
        return this.hasMore;
    }

    /**
     * 获取下一页 cursor。
     *
     * @return 下一页 cursor，最后一页返回 {@code null}
     */
    public String getNextCursor() {
        return this.nextCursor;
    }
}
