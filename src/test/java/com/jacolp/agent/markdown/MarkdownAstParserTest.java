package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.agent.markdown.model.SectionNode;
import org.junit.jupiter.api.Test;

class MarkdownAstParserTest {

    private static final DocumentId DOCUMENT_ID = new DocumentId(1L);

    @Test
    void parsesTopLevelHeadingsAndBuildsStackBasedTree() {
        String source = "preamble\n\n# Java\nJava body\n\n## 安装\nJDK body\n\n### JDK 版本选择\nchoice\n\n## 开发工具\nIDE body\n\n# Python\nPython body\n";
        MarkdownContext context = new MarkdownAstParser().parse(DOCUMENT_ID, source);

        assertEquals(List.of(1, 5), context.getRootNodeIds());
        assertEquals(List.of(2, 4), context.getNodes().get(1).getChildren());
        assertEquals(List.of(3), context.getNodes().get(2).getChildren());
        assertEquals("Java", context.getNodes().get(1).getTitle());
        assertEquals("安装", context.getNodes().get(2).getTitle());

        SectionNode java = context.getNodes().get(1);
        assertEquals("# Java\n", source.substring(java.getHeadingStart(), java.getBodyStart()));
        assertEquals("Java body\n\n", source.substring(java.getBodyStart(), java.getDirectEnd()));
        assertEquals(source.indexOf("# Python"), java.getSectionEnd());
    }

    @Test
    void ignoresHeadingsInsideListsQuotesAndFencedCodeAndKeepsDuplicateTitles() {
        String source = "- # list\n\n> ## quote\n\n```markdown\n### code\n```\n\n# Java\nbody\n\n# Java\nbody 2\n";
        MarkdownContext context = new MarkdownAstParser().parse(DOCUMENT_ID, source);

        assertEquals(List.of(1, 2), context.getRootNodeIds());
        assertEquals("Java", context.getNodes().get(1).getTitle());
        assertEquals("Java", context.getNodes().get(2).getTitle());
        assertNotEquals(context.getNodes().get(1).getNumber(), context.getNodes().get(2).getNumber());
        assertFalse(context.getNodes().containsKey(3));
    }

    @Test
    void keepsSourceWhenNoHeadingExists() {
        String source = "plain text\n\nwithout a heading\n";
        MarkdownContext context = new MarkdownAstParser().parse(DOCUMENT_ID, source);

        assertEquals(source, context.getSource());
        assertEquals(List.of(), context.getRootNodeIds());
        assertEquals(0, context.getNodes().size());
    }
}
