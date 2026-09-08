package com.jacolp.agent.markdown.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 标识一个 Markdown 上下文中的指定章节。
 */
public final class SectionNodeRef {

    private final UUID key;

    private final int nodeNumber;

    /**
     * 创建章节引用。
     *
     * @param key 上下文 UUID
     * @param nodeNumber 节点编号
     */
    public SectionNodeRef(UUID key, int nodeNumber) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
        if (nodeNumber <= 0) {
            // 节点编号从 1 开始，非法编号不能参与查询或替换。
            throw new IllegalArgumentException("nodeNumber must be positive");
        }
        this.nodeNumber = nodeNumber;
    }

    /**
     * 获取引用对应的上下文 UUID。
     *
     * @return 上下文 UUID
     */
    public UUID getKey() {
        return this.key;
    }

    /**
     * 获取引用对应的节点编号。
     *
     * @return 节点编号
     */
    public int getNodeNumber() {
        return this.nodeNumber;
    }
}
