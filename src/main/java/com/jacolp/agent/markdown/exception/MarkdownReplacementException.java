package com.jacolp.agent.markdown.exception;

/**
 * 当 Markdown 替换请求非法或匹配不唯一时抛出。
 */
public final class MarkdownReplacementException extends MarkdownException {

    /**
     * 创建替换请求异常。
     *
     * @param message 替换失败原因
     */
    public MarkdownReplacementException(String message) {
        super(message);
    }
}
