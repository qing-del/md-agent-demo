package com.jacolp.agent.markdown.exception;

/**
 * 当章节 cursor 格式错误或与当前请求不匹配时抛出。
 */
public final class MarkdownCursorException extends MarkdownException {

    /**
     * 创建带错误信息的 cursor 异常。
     *
     * @param message cursor 错误信息
     */
    public MarkdownCursorException(String message) {
        super(message);
    }

    /**
     * 创建带错误信息和根因的 cursor 异常。
     *
     * @param message cursor 错误信息
     * @param cause 原始解码异常
     */
    public MarkdownCursorException(String message, Throwable cause) {
        super(message, cause);
    }
}
