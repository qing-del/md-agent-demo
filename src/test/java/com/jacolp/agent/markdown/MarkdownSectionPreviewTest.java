package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jacolp.agent.markdown.model.DocumentId;
import org.junit.jupiter.api.Test;

class MarkdownSectionPreviewTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void returnsDirectBodyAndCollapsesExpandableDirectChildren() {
        String source = "# Java\nbody\n\n## 安装\n安装内容\n\n### JDK 安装\nlink\n\n## 开发工具\n\n## 技术栈\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);

        assertEquals(
                "# Java\nbody\n\n## 安装 ...\n## 开发工具\n## 技术栈",
                manager.getSectionPreview(DOCUMENT_ID, 1));
    }

    @Test
    void doesNotMarkAChildThatOnlyContainsSeparatingWhitespace() {
        String source = "# Root\n\n## Empty\n\n## Body\ntext\n";
        MarkdownManager manager = new MarkdownManager(snapshot -> { }, DOCUMENT_ID, source);

        assertEquals("# Root\n\n## Empty\n## Body ...", manager.getSectionPreview(DOCUMENT_ID, 1));
    }
}
