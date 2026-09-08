package com.jacolp.agent.markdown.model;

import java.util.List;
import java.util.Objects;

/**
 * Markdown 快照中的不可变标题章节节点。
 */
public final class SectionNode {

    private final int number;

    private final int level;

    private final String title;

    private final List<Integer> children;

    private final int headingStart;

    private final int bodyStart;

    private final int directEnd;

    private final int sectionEnd;

    /**
     * 使用 UTF-16 source 偏移创建章节节点。
     *
     * @param number 按原文顺序分配的节点编号
     * @param level Markdown 标题级别
     * @param title 标题语义文本
     * @param children 直属子节点编号
     * @param headingStart 标题起点（包含）
     * @param bodyStart 直属正文起点（包含）
     * @param directEnd 直属正文终点（不包含）
     * @param sectionEnd 完整章节终点（不包含）
     */
    public SectionNode(
            int number,
            int level,
            String title,
            List<Integer> children,
            int headingStart,
            int bodyStart,
            int directEnd,
        int sectionEnd) {
        if (number <= 0) {
            // 节点编号从 1 开始，零和负数都不能用于索引章节。
            throw new IllegalArgumentException("number must be positive");
        }
        if (level < 1 || level > 6) {
            // CommonMark 标题级别的合法范围是 1 到 6。
            throw new IllegalArgumentException("level must be between 1 and 6");
        }
        if (headingStart < 0
                || bodyStart < headingStart
                || directEnd < bodyStart
                || sectionEnd < directEnd) {
            // 四个偏移必须按标题、直属正文、完整章节的顺序递增。
            throw new IllegalArgumentException("section offsets must be ordered and non-negative");
        }
        this.number = number;
        this.level = level;
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.children = List.copyOf(Objects.requireNonNull(children, "children cannot be null"));
        this.headingStart = headingStart;
        this.bodyStart = bodyStart;
        this.directEnd = directEnd;
        this.sectionEnd = sectionEnd;
    }

    /**
     * 获取节点编号。
     *
     * @return 按原文顺序分配的编号
     */
    public int getNumber() {
        return this.number;
    }

    /**
     * 获取 Markdown 标题级别。
     *
     * @return 1 到 6 的标题级别
     */
    public int getLevel() {
        return this.level;
    }

    /**
     * 获取标题语义文本。
     *
     * @return 不包含标题标记的标题内容
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * 获取直属子节点编号。
     *
     * @return 不可修改的子节点列表
     */
    public List<Integer> getChildren() {
        return this.children;
    }

    /**
     * 获取标题起点的 UTF-16 偏移。
     *
     * @return 标题起点（包含）
     */
    public int getHeadingStart() {
        return this.headingStart;
    }

    /**
     * 获取直属正文起点的 UTF-16 偏移。
     *
     * @return 正文起点（包含）
     */
    public int getBodyStart() {
        return this.bodyStart;
    }

    /**
     * 获取直属正文终点的 UTF-16 偏移。
     *
     * @return 直属正文终点（不包含）
     */
    public int getDirectEnd() {
        return this.directEnd;
    }

    /**
     * 获取完整章节终点的 UTF-16 偏移。
     *
     * @return 完整章节终点（不包含）
     */
    public int getSectionEnd() {
        return this.sectionEnd;
    }
}
