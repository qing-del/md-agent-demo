package com.jacolp.agent.markdown.exception;

import java.util.UUID;

/**
 * 当 Markdown 上下文 UUID 未注册时抛出。
 */
public final class MarkdownContextNotFoundException extends MarkdownException {

    /**
     * 创建上下文不存在异常。
     *
     * @param key 未找到的上下文 UUID
     */
    public MarkdownContextNotFoundException(UUID key) {
        super("Markdown context not found: " + key);
    }
}
