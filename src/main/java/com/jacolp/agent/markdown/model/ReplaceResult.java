package com.jacolp.agent.markdown.model;

import java.util.Objects;

/**
 * Markdown 替换成功后的结果。
 */
public final class ReplaceResult {

    private final MarkdownContext context;

    /**
     * 创建包含新上下文快照的替换结果。
     *
     * @param context 替换并持久化成功后的上下文
     */
    public ReplaceResult(MarkdownContext context) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
    }

    /**
     * 获取替换后的上下文快照。
     *
     * @return 新 revision 对应的不可变上下文
     */
    public MarkdownContext getContext() {
        return this.context;
    }
}
