package com.jacolp.agent.markdown;

import com.jacolp.agent.markdown.model.MarkdownContext;

/**
 * Markdown SDK 使用的持久化边界。
 */
@FunctionalInterface
public interface MarkdownStore {

    /**
     * 持久化一个新生成的 Markdown 快照。
     *
     * <p>应用层适配器负责将上下文 DocumentId 映射到已有文档记录。</p>
     *
     * @param context 待持久化的快照
     */
    void save(MarkdownContext context);
}
