package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class MarkdownSectionPreviewTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    void returnsDirectBodyAndCollapsesExpandableDirectChildren() {
        String source = "# Java\nbody\n\n## 安装\n安装内容\n\n### JDK 安装\nlink\n\n## 开发工具\n\n## 技术栈\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, KEY, source);

        assertEquals(
                "# Java\nbody\n\n## 安装 ...\n## 开发工具\n## 技术栈",
                manager.getSectionPreview(KEY, 1));
    }

    @Test
    void doesNotMarkAChildThatOnlyContainsSeparatingWhitespace() {
        String source = "# Root\n\n## Empty\n\n## Body\ntext\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, KEY, source);

        assertEquals("# Root\n\n## Empty\n## Body ...", manager.getSectionPreview(KEY, 1));
    }
}
