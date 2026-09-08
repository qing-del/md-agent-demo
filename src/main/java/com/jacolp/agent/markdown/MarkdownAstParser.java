package com.jacolp.agent.markdown;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.agent.markdown.model.SectionNode;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/**
 * 使用 CommonMark AST 将 Markdown 顶层标题解析为不可变的章节索引。
 */
final class MarkdownAstParser {

    // 开启块级 source span，使 AST 节点可以回指原文中的 UTF-16 区间。
    private final Parser parser = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS)
            .build();

    /**
     * 解析指定 source，并生成包含标题树、位置索引和 revision 的上下文快照。
     *
     * @param documentId 已持久化文档的标识
     * @param source 完整 Markdown 原文
     * @return 解析后的不可变上下文
     */
    MarkdownContext parse(DocumentId documentId, String source) {
        Objects.requireNonNull(documentId, "documentId cannot be null");
        Objects.requireNonNull(source, "source cannot be null");

        // 先构建带位置索引的 AST，再从文档第一层提取真正的标题节点。
        Node document = this.parser.parse(source);
        List<HeadingCandidate> headings = topLevelHeadings(document, source);
        List<Integer> rootNodeIds = new ArrayList<>();
        Map<Integer, MutableSectionNode> mutableNodes = new LinkedHashMap<>();
        Deque<MutableSectionNode> stack = new ArrayDeque<>();

        // 第一遍按原文顺序编号，并用标题级别栈建立父子关系。
        for (int index = 0; index < headings.size(); index++) {
            HeadingCandidate heading = headings.get(index);
            int number = index + 1;
            MutableSectionNode node = new MutableSectionNode(number, heading.heading().getLevel(),
                    heading.title(), heading.headingStart(), heading.bodyStart());
            mutableNodes.put(number, node);

            // 同级或更深层标题不能继续作为当前标题的祖先，先从栈顶移除。
            while (!stack.isEmpty() && stack.peek().level >= node.level) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                // 栈为空表示没有更高层标题，当前节点是目录根节点。
                rootNodeIds.add(number);
            }
            else {
                // 栈顶是最近的低级标题，当前节点挂到它的直属 children 下。
                stack.peek().children.add(number);
            }
            stack.push(node);
        }

        Map<Integer, SectionNode> nodes = new LinkedHashMap<>();
        // 第二遍计算正文范围：directEnd 截止下一个标题，sectionEnd 越过所有子标题。
        for (int index = 0; index < headings.size(); index++) {
            HeadingCandidate heading = headings.get(index);
            MutableSectionNode mutableNode = mutableNodes.get(index + 1);
            int directEnd = index + 1 < headings.size()
                    ? headings.get(index + 1).headingStart()
                    : source.length();
            int sectionEnd = source.length();
            for (int next = index + 1; next < headings.size(); next++) {
                // 遇到同级或更高标题时，当前章节及其后代范围结束。
                if (headings.get(next).heading().getLevel() <= heading.heading().getLevel()) {
                    sectionEnd = headings.get(next).headingStart();
                    break;
                }
            }
            nodes.put(mutableNode.number, new SectionNode(
                    mutableNode.number,
                    mutableNode.level,
                    mutableNode.title,
                    mutableNode.children,
                    mutableNode.headingStart,
                    mutableNode.bodyStart,
                    directEnd,
                    sectionEnd));
        }

        // 节点树和 source 共同组成当前 revision 的不可变快照。
        return new MarkdownContext(documentId, revisionOf(source), source, rootNodeIds, nodes);
    }

    private static List<HeadingCandidate> topLevelHeadings(Node document, String source) {
        List<HeadingCandidate> headings = new ArrayList<>();
        // 只检查文档第一层 block，引用、列表和代码块中的标题不会进入目录。
        for (Node child = document.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Heading heading) {
                // 保存原始标题区间和标题行结束后的正文起点，避免重新渲染 Markdown。
                int[] bounds = sourceBounds(heading, source.length());
                headings.add(new HeadingCandidate(
                        heading,
                        headingTitle(heading),
                        bounds[0],
                        afterLineBreak(source, bounds[1])));
            }
        }
        return headings;
    }

    private static int[] sourceBounds(Node node, int sourceLength) {
        List<SourceSpan> spans = node.getSourceSpans();
        if (spans == null || spans.isEmpty()) {
            // 没有 source span 时无法保证替换和分页的原文位置，直接终止解析。
            throw new IllegalStateException("Markdown node has no source span");
        }
        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        // 多个 span 可能覆盖一个节点，取最小起点和最大终点组成完整范围。
        for (SourceSpan span : spans) {
            start = Math.min(start, span.getInputIndex());
            end = Math.max(end, span.getInputIndex() + span.getLength());
        }
        if (start < 0 || end < start || end > sourceLength) {
            // source span 超出原文说明解析器坐标异常，不能继续构造错误索引。
            throw new IllegalStateException("Markdown node source span is out of bounds");
        }
        return new int[] { start, end };
    }

    private static int afterLineBreak(String source, int offset) {
        if (offset >= source.length()) {
            // 标题已经位于文档末尾时，正文从 source 末尾开始。
            return source.length();
        }
        if (source.charAt(offset) == '\r') {
            // CRLF 要一次跳过两个 UTF-16 code unit，单独 CR 只跳过一个。
            return offset + (offset + 1 < source.length() && source.charAt(offset + 1) == '\n' ? 2 : 1);
        }
        if (source.charAt(offset) == '\n') {
            // LF 标题行只需要向后移动一个 code unit。
            return offset + 1;
        }
        // 没有换行符时保留 span 终点作为正文起点。
        return offset;
    }

    private static String headingTitle(Heading heading) {
        StringBuilder title = new StringBuilder();
        // 从 AST 内联节点提取语义文本，标题语法本身仍从 source 区间读取。
        appendText(heading.getFirstChild(), title);
        return title.toString();
    }

    private static void appendText(Node node, StringBuilder target) {
        for (Node current = node; current != null; current = current.getNext()) {
            if (current instanceof Text text) {
                // 普通文本直接作为标题语义内容。
                target.append(text.getLiteral());
            }
            else if (current instanceof Code code) {
                // 行内代码保留其字面值，不带反引号语法。
                target.append(code.getLiteral());
            }
            else {
                // 链接、强调等容器节点递归读取其子节点文本。
                appendText(current.getFirstChild(), target);
            }
        }
    }

    private static String revisionOf(String source) {
        try {
            // revision 由完整 UTF-8 source 的 SHA-256 指纹生成，用于标识当前快照。
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            // JDK 必须提供 SHA-256；若运行环境违反该前提则作为配置错误抛出。
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record HeadingCandidate(
            Heading heading,
            String title,
            int headingStart,
            int bodyStart) {
    }

    /** 解析阶段暂存可变 children，构造不可变 SectionNode 后即不再使用。 */
    private static final class MutableSectionNode {

        private final int number;

        private final int level;

        private final String title;

        private final List<Integer> children = new ArrayList<>();

        private final int headingStart;

        private final int bodyStart;

        private MutableSectionNode(int number, int level, String title, int headingStart, int bodyStart) {
            this.number = number;
            this.level = level;
            this.title = title;
            this.headingStart = headingStart;
            this.bodyStart = bodyStart;
        }
    }
}
