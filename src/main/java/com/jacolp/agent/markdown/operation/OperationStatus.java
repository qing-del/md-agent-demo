package com.jacolp.agent.markdown.operation;

/**
 * Lifecycle states for a pending Markdown operation.
 */
public enum OperationStatus {

    PENDING,

    EXECUTING,

    COMPLETED,

    FAILED,

    CANCELLED
}
