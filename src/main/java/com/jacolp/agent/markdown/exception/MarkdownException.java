package com.jacolp.agent.markdown.exception;

/**
 * Markdown SDK 领域异常基类。
 */
public class MarkdownException extends RuntimeException {

    /**
     * 创建带错误信息的领域异常。
     *
     * @param message 错误信息
     */
    public MarkdownException(String message) {
        super(message);
    }

    /**
     * 创建带错误信息和根因的领域异常。
     *
     * @param message 错误信息
     * @param cause 原始异常
     */
    public MarkdownException(String message, Throwable cause) {
        super(message, cause);
    }
}
