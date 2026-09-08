package com.jacolp.agent.markdown.exception;

import java.util.UUID;

/**
 * 当上下文中不存在指定章节编号时抛出。
 */
public final class SectionNotFoundException extends MarkdownException {

    /**
     * 创建章节不存在异常。
     *
     * @param key 上下文 UUID
     * @param nodeNumber 未找到的节点编号
     */
    public SectionNotFoundException(UUID key, int nodeNumber) {
        super("Markdown section not found: key=" + key + ", nodeNumber=" + nodeNumber);
    }
}
