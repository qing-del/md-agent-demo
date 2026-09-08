package com.jacolp.agent.markdown.operation;

/**
 * 待确认 Markdown 操作的生命周期状态。
 */
public enum OperationStatus {

    /** 等待前端确认，尚未执行替换。 */
    PENDING,

    /** 已获得确认，正在执行替换和持久化。 */
    EXECUTING,

    /** 替换和持久化均已成功。 */
    COMPLETED,

    /** 替换或持久化失败。 */
    FAILED,

    /** 前端拒绝提案，未修改 Markdown。 */
    CANCELLED
}
