package com.jacolp.agent.markdown.exception;

/**
 * 当 Markdown 替换结果无法持久化时抛出。
 */
public final class MarkdownPersistenceException extends MarkdownException {

    /**
     * 创建带错误信息和持久化根因的异常。
     *
     * @param message 持久化错误信息
     * @param cause 底层持久化异常
     */
    public MarkdownPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
