package com.jacolp.agent.markdown.operation;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.jacolp.agent.markdown.model.SectionNodeRef;

/**
 * 使用原子生命周期转换的内部可变操作状态。
 */
final class OperationState {

    private final UUID opId;

    private final SectionNodeRef section;

    private final String originalText;

    private final String newText;

    private final AtomicReference<OperationStatus> status = new AtomicReference<>(OperationStatus.PENDING);

    /**
     * 创建初始状态为 {@link OperationStatus#PENDING} 的内部操作。
     */
    OperationState(UUID opId, SectionNodeRef section, String originalText, String newText) {
        this.opId = Objects.requireNonNull(opId, "opId cannot be null");
        this.section = Objects.requireNonNull(section, "section cannot be null");
        this.originalText = Objects.requireNonNull(originalText, "originalText cannot be null");
        this.newText = Objects.requireNonNull(newText, "newText cannot be null");
    }

    boolean transition(OperationStatus expected, OperationStatus replacement) {
        // compareAndSet 确保同一个 OpId 只有一个请求能成功推进状态。
        return this.status.compareAndSet(expected, replacement);
    }

    OperationStatus status() {
        // 读取原子状态，供查询和失败分支生成最新操作快照。
        return this.status.get();
    }

    SectionNodeRef section() {
        return this.section;
    }

    String originalText() {
        return this.originalText;
    }

    String newText() {
        return this.newText;
    }

    Operation snapshot() {
        // 对外只返回不可变副本，隐藏内部 AtomicReference 和状态写入能力。
        return new Operation(this.opId, this.section, this.originalText, this.newText, this.status());
    }
}
