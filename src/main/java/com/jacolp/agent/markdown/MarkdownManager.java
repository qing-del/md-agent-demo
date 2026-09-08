package com.jacolp.agent.markdown;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.jacolp.agent.markdown.exception.MarkdownContextNotFoundException;
import com.jacolp.agent.markdown.exception.MarkdownCursorException;
import com.jacolp.agent.markdown.exception.SectionNotFoundException;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.agent.markdown.model.SectionNode;
import com.jacolp.agent.markdown.model.SectionPage;

/**
 * Framework-neutral manager for UUID-keyed Markdown contexts.
 */
public final class MarkdownManager {

    private static final int PAGE_BYTE_LIMIT = 5120;

    private static final int ELLIPSIS_BYTE_LENGTH = 3;

    private final MarkdownStore store;

    private final MarkdownAstParser parser = new MarkdownAstParser();

    private final ConcurrentMap<UUID, MarkdownContext> contexts = new ConcurrentHashMap<>();

    private final ConcurrentMap<UUID, ReentrantLock> replacementLocks = new ConcurrentHashMap<>();

    /**
     * Creates an empty manager.
     *
     * @param store persistence boundary used by replacements
     */
    public MarkdownManager(MarkdownStore store) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
    }

    /**
     * Creates a manager and immediately registers one initial Markdown snapshot.
     *
     * @param store persistence boundary used by replacements
     * @param key context UUID
     * @param source complete Markdown source
     */
    public MarkdownManager(MarkdownStore store, UUID key, String source) {
        this(store);
        register(key, source);
    }

    /**
     * Parses and publishes a current snapshot for a UUID.
     *
     * @param key context UUID
     * @param source complete Markdown source
     * @return newly published immutable context
     */
    public MarkdownContext register(UUID key, String source) {
        MarkdownContext context = this.parser.parse(key, source);
        this.contexts.put(key, context);
        return context;
    }

    /**
     * Gets the immutable snapshot registered under a UUID.
     *
     * @param key context UUID
     * @return current immutable context
     * @throws MarkdownContextNotFoundException when the UUID is unknown
     */
    public MarkdownContext getEntity(UUID key) {
        Objects.requireNonNull(key, "key cannot be null");
        MarkdownContext context = this.contexts.get(key);
        if (context == null) {
            throw new MarkdownContextNotFoundException(key);
        }
        return context;
    }

    /**
     * Renders the numbered heading tree in source order.
     *
     * @param key context UUID
     * @return one numbered heading per line, indented by logical tree depth
     */
    public String getHeadingTree(UUID key) {
        MarkdownContext context = getEntity(key);
        StringBuilder result = new StringBuilder();
        Set<Integer> rendered = new HashSet<>();
        for (Integer rootNodeId : context.getRootNodeIds()) {
            appendHeadingTree(context, rootNodeId, 0, result, rendered);
        }
        return result.toString();
    }

    /**
     * Returns a node's direct body and a collapsed list of its direct children.
     *
     * @param key context UUID
     * @param nodeNumber source-order node number
     * @return preview text with dynamic child ellipses
     */
    public String getSectionPreview(UUID key, int nodeNumber) {
        MarkdownContext context = getEntity(key);
        SectionNode node = requireNode(context, nodeNumber);
        String source = context.getSource();
        StringBuilder result = new StringBuilder();
        result.append(rawHeading(context, node));
        result.append(source, node.getBodyStart(), node.getDirectEnd());

        for (Integer childNumber : node.getChildren()) {
            SectionNode child = requireNode(context, childNumber);
            if (result.length() > 0 && !endsWithLineBreak(result)) {
                result.append('\n');
            }
            result.append(rawHeading(context, child));
            if (hasExpandableContent(context, child)) {
                result.append(" ...");
            }
        }
        return result.toString();
    }

    /**
     * Returns the first page of a node's complete section.
     *
     * @param key context UUID
     * @param nodeNumber source-order node number
     * @return first page, including a dynamic ellipsis when more bytes remain
     */
    public String getSectionAll(UUID key, int nodeNumber) {
        MarkdownContext context = getEntity(key);
        SectionNode node = requireNode(context, nodeNumber);
        PageSlice page = readPage(context, node, 0);
        return page.content() + (page.hasMore() ? "..." : "");
    }

    /**
     * Returns one byte-bounded page of a node's complete section.
     *
     * @param key context UUID
     * @param nodeNumber source-order node number
     * @param cursor opaque cursor returned by the previous page, or {@code null} for page zero
     * @return page content and continuation metadata
     */
    public SectionPage getSectionAll(UUID key, int nodeNumber, String cursor) {
        MarkdownContext context = getEntity(key);
        SectionNode node = requireNode(context, nodeNumber);
        int byteOffset = 0;
        if (cursor != null) {
            if (cursor.isBlank()) {
                throw new MarkdownCursorException("cursor must not be blank");
            }
            SectionCursor decoded = decodeCursor(cursor);
            if (!key.equals(decoded.key()) || nodeNumber != decoded.nodeNumber()) {
                throw new MarkdownCursorException("cursor does not match the requested section");
            }
            byteOffset = decoded.byteOffset();
        }

        PageSlice page = readPage(context, node, byteOffset);
        String content = page.content() + (page.hasMore() ? "..." : "");
        String nextCursor = page.hasMore()
                ? encodeCursor(key, nodeNumber, page.nextOffset())
                : null;
        return new SectionPage(content, page.hasMore(), nextCursor);
    }

    /**
     * Restores the complete source of the current snapshot without rendering.
     *
     * @param key context UUID
     * @return complete original Markdown source
     */
    public String restoreMarkdown(UUID key) {
        return getEntity(key).getSource();
    }

    private static SectionNode requireNode(MarkdownContext context, int nodeNumber) {
        SectionNode node = context.getNodes().get(nodeNumber);
        if (node == null) {
            throw new SectionNotFoundException(context.getKey(), nodeNumber);
        }
        return node;
    }

    private static boolean hasExpandableContent(MarkdownContext context, SectionNode node) {
        return !context.getSource().substring(node.getBodyStart(), node.getDirectEnd()).isBlank()
                || !node.getChildren().isEmpty();
    }

    private static boolean endsWithLineBreak(StringBuilder value) {
        int length = value.length();
        return length > 0 && (value.charAt(length - 1) == '\n' || value.charAt(length - 1) == '\r');
    }

    private static PageSlice readPage(MarkdownContext context, SectionNode node, int byteOffset) {
        String section = context.getSource().substring(node.getHeadingStart(), node.getSectionEnd());
        byte[] bytes = section.getBytes(StandardCharsets.UTF_8);
        if (byteOffset < 0 || byteOffset > bytes.length) {
            throw new MarkdownCursorException("section byte offset is out of bounds");
        }
        if (!isUtf8Boundary(bytes, byteOffset)) {
            throw new MarkdownCursorException("section byte offset is not a UTF-8 boundary");
        }

        int remaining = bytes.length - byteOffset;
        int originalBudget = remaining > PAGE_BYTE_LIMIT
                ? PAGE_BYTE_LIMIT - ELLIPSIS_BYTE_LENGTH
                : PAGE_BYTE_LIMIT;
        int end = utf8Boundary(bytes, byteOffset, originalBudget);
        boolean hasMore = end < bytes.length;
        String content = new String(bytes, byteOffset, end - byteOffset, StandardCharsets.UTF_8);
        return new PageSlice(content, end, hasMore);
    }

    private static boolean isUtf8Boundary(byte[] bytes, int offset) {
        if (offset == 0 || offset == bytes.length) {
            return true;
        }
        return (bytes[offset] & 0xC0) != 0x80;
    }

    private static String encodeCursor(UUID key, int nodeNumber, int byteOffset) {
        String value = key + ":" + nodeNumber + ":" + byteOffset;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static SectionCursor decodeCursor(String cursor) {
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] fields = value.split(":", -1);
            if (fields.length != 3) {
                throw new IllegalArgumentException("cursor must contain three fields");
            }
            UUID key = UUID.fromString(fields[0]);
            int nodeNumber = Integer.parseInt(fields[1]);
            int byteOffset = Integer.parseInt(fields[2]);
            if (nodeNumber <= 0 || byteOffset < 0) {
                throw new IllegalArgumentException("cursor fields are out of range");
            }
            return new SectionCursor(key, nodeNumber, byteOffset);
        }
        catch (IllegalArgumentException exception) {
            throw new MarkdownCursorException("cursor is malformed", exception);
        }
    }

    private static int utf8Boundary(byte[] bytes, int start, int budget) {
        int limit = Math.min(bytes.length, start + budget);
        int cursor = start;
        while (cursor < limit) {
            int width = utf8CharacterWidth(bytes[cursor]);
            if (cursor + width > limit) {
                break;
            }
            cursor += width;
        }
        return cursor;
    }

    private static int utf8CharacterWidth(byte value) {
        int unsigned = value & 0xFF;
        if ((unsigned & 0x80) == 0) {
            return 1;
        }
        if ((unsigned & 0xE0) == 0xC0) {
            return 2;
        }
        if ((unsigned & 0xF0) == 0xE0) {
            return 3;
        }
        return 4;
    }

    private record PageSlice(String content, int nextOffset, boolean hasMore) {
    }

    private record SectionCursor(UUID key, int nodeNumber, int byteOffset) {
    }

    private static void appendHeadingTree(
            MarkdownContext context,
            int nodeNumber,
            int depth,
            StringBuilder result,
            Set<Integer> rendered) {
        SectionNode node = context.getNodes().get(nodeNumber);
        if (node == null || !rendered.add(nodeNumber)) {
            return;
        }
        if (result.length() > 0) {
            result.append('\n');
        }
        result.append("  ".repeat(depth))
                .append(node.getNumber())
                .append(". ")
                .append(rawHeading(context, node));
        for (Integer child : node.getChildren()) {
            appendHeadingTree(context, child, depth + 1, result, rendered);
        }
    }

    private static String rawHeading(MarkdownContext context, SectionNode node) {
        String heading = context.getSource().substring(node.getHeadingStart(), node.getBodyStart());
        if (heading.endsWith("\r\n")) {
            return heading.substring(0, heading.length() - 2);
        }
        if (heading.endsWith("\r") || heading.endsWith("\n")) {
            return heading.substring(0, heading.length() - 1);
        }
        return heading;
    }

    MarkdownStore store() {
        return this.store;
    }

    MarkdownAstParser parser() {
        return this.parser;
    }

    ConcurrentMap<UUID, MarkdownContext> contexts() {
        return this.contexts;
    }

    ConcurrentMap<UUID, ReentrantLock> replacementLocks() {
        return this.replacementLocks;
    }
}
