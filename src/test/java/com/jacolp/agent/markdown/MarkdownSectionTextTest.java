package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jacolp.agent.markdown.model.DocumentId;
import org.junit.jupiter.api.Test;

class MarkdownSectionTextTest {

    @Test
    void returnsTheRawMarkdownHeadingPathForANode() {
        MarkdownManager manager = new MarkdownManager(
                snapshot -> { },
                new DocumentId(1L),
                "# Java\n\n## 安装\n\n### JDK 选择\n");

        assertEquals("# Java | ## 安装 | ### JDK 选择",
                manager.getSectionText(new DocumentId(1L), 3));
    }
}
