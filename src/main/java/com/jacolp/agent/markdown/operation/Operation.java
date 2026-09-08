package com.jacolp.agent.markdown.operation;

import java.util.Objects;
import java.util.UUID;

import com.jacolp.agent.markdown.model.SectionNodeRef;

/**
 * 操作提案及其状态的不可变公开快照。
 */
public final class Operation {

    private final UUID opId;

    private final SectionNodeRef section;

    private final String originalText;

    private final String newText;

    private final OperationStatus status;

    /**
     * 创建一个操作快照。
     *
     * @param opId 操作 UUID
     * @param section 待替换的章节引用
     * @param originalText 原始匹配文本
     * @param newText 替换文本
     * @param status 当前操作状态
     */
    public Operation(
            UUID opId,
            SectionNodeRef section,
            String originalText,
            String newText,
            OperationStatus status) {
        this.opId = Objects.requireNonNull(opId, "opId cannot be null");
        this.section = Objects.requireNonNull(section, "section cannot be null");
        this.originalText = Objects.requireNonNull(originalText, "originalText cannot be null");
        this.newText = Objects.requireNonNull(newText, "newText cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    /**
     * 获取操作 UUID。
     *
     * @return 操作 UUID
     */
    public UUID getOpId() {
        return this.opId;
    }

    /**
     * 获取待替换章节引用。
     *
     * @return 章节引用
     */
    public SectionNodeRef getSection() {
        return this.section;
    }

    /**
     * 获取提案中的原始匹配文本。
     *
     * @return 原始文本
     */
    public String getOriginalText() {
        return this.originalText;
    }

    /**
     * 获取提案中的替换文本。
     *
     * @return 新文本
     */
    public String getNewText() {
        return this.newText;
    }

    /**
     * 获取操作当前状态。
     *
     * @return 操作状态
     */
    public OperationStatus getStatus() {
        return this.status;
    }
}
