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
import java.util.UUID;

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
 * Parses top-level Markdown headings into an immutable section index.
 */
final class MarkdownAstParser {

    private final Parser parser = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS)
            .build();

    MarkdownContext parse(UUID key, String source) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(source, "source cannot be null");

        Node document = this.parser.parse(source);
        List<HeadingCandidate> headings = topLevelHeadings(document, source);
        List<Integer> rootNodeIds = new ArrayList<>();
        Map<Integer, MutableSectionNode> mutableNodes = new LinkedHashMap<>();
        Deque<MutableSectionNode> stack = new ArrayDeque<>();

        for (int index = 0; index < headings.size(); index++) {
            HeadingCandidate heading = headings.get(index);
            int number = index + 1;
            MutableSectionNode node = new MutableSectionNode(number, heading.heading().getLevel(),
                    heading.title(), heading.headingStart(), heading.bodyStart());
            mutableNodes.put(number, node);

            while (!stack.isEmpty() && stack.peek().level >= node.level) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                rootNodeIds.add(number);
            }
            else {
                stack.peek().children.add(number);
            }
            stack.push(node);
        }

        Map<Integer, SectionNode> nodes = new LinkedHashMap<>();
        for (int index = 0; index < headings.size(); index++) {
            HeadingCandidate heading = headings.get(index);
            MutableSectionNode mutableNode = mutableNodes.get(index + 1);
            int directEnd = index + 1 < headings.size()
                    ? headings.get(index + 1).headingStart()
                    : source.length();
            int sectionEnd = source.length();
            for (int next = index + 1; next < headings.size(); next++) {
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

        return new MarkdownContext(key, revisionOf(source), source, rootNodeIds, nodes);
    }

    private static List<HeadingCandidate> topLevelHeadings(Node document, String source) {
        List<HeadingCandidate> headings = new ArrayList<>();
        for (Node child = document.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Heading heading) {
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
            throw new IllegalStateException("Markdown node has no source span");
        }
        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        for (SourceSpan span : spans) {
            start = Math.min(start, span.getInputIndex());
            end = Math.max(end, span.getInputIndex() + span.getLength());
        }
        if (start < 0 || end < start || end > sourceLength) {
            throw new IllegalStateException("Markdown node source span is out of bounds");
        }
        return new int[] { start, end };
    }

    private static int afterLineBreak(String source, int offset) {
        if (offset >= source.length()) {
            return source.length();
        }
        if (source.charAt(offset) == '\r') {
            return offset + (offset + 1 < source.length() && source.charAt(offset + 1) == '\n' ? 2 : 1);
        }
        if (source.charAt(offset) == '\n') {
            return offset + 1;
        }
        return offset;
    }

    private static String headingTitle(Heading heading) {
        StringBuilder title = new StringBuilder();
        appendText(heading.getFirstChild(), title);
        return title.toString();
    }

    private static void appendText(Node node, StringBuilder target) {
        for (Node current = node; current != null; current = current.getNext()) {
            if (current instanceof Text text) {
                target.append(text.getLiteral());
            }
            else if (current instanceof Code code) {
                target.append(code.getLiteral());
            }
            else {
                appendText(current.getFirstChild(), target);
            }
        }
    }

    private static String revisionOf(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record HeadingCandidate(
            Heading heading,
            String title,
            int headingStart,
            int bodyStart) {
    }

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
