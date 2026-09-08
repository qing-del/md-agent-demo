package com.jacolp.agent.markdown;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.jacolp.agent.markdown.exception.MarkdownContextNotFoundException;
import com.jacolp.agent.markdown.exception.MarkdownCursorException;
import com.jacolp.agent.markdown.exception.MarkdownPersistenceException;
import com.jacolp.agent.markdown.exception.MarkdownReplacementException;
import com.jacolp.agent.markdown.exception.SectionNotFoundException;
import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.agent.markdown.model.ReplaceResult;
import com.jacolp.agent.markdown.model.SectionNode;
import com.jacolp.agent.markdown.model.SectionNodeRef;
import com.jacolp.agent.markdown.model.SectionPage;

/**
 * 与框架无关的 Markdown 上下文管理器，负责按 DocumentId 保存快照并提供查询、分页和替换能力。
 */
public final class MarkdownManager {

    /** 单页原始内容的 UTF-8 字节上限。 */
    private static final int PAGE_BYTE_LIMIT = 5120;

    /** 分页发生截断时为省略号预留的 UTF-8 字节数。 */
    private static final int ELLIPSIS_BYTE_LENGTH = 3;

    private final MarkdownStore store;

    private final MarkdownAstParser parser = new MarkdownAstParser();

    private final ConcurrentMap<DocumentId, MarkdownContext> contexts = new ConcurrentHashMap<>();

    private final ConcurrentMap<DocumentId, ReentrantLock> replacementLocks = new ConcurrentHashMap<>();

    /**
     * 创建一个尚未注册 Markdown 上下文的管理器。
     *
     * @param store 替换操作使用的持久化边界
     */
    public MarkdownManager(MarkdownStore store) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
    }

    /**
     * 创建管理器并立即注册一个初始 Markdown 快照。
     *
     * @param store 替换操作使用的持久化边界
     * @param documentId 已持久化文档的标识
     * @param source 完整 Markdown 原文
     */
    public MarkdownManager(MarkdownStore store, DocumentId documentId, String source) {
        this(store);
        register(documentId, source);
    }

    /**
     * 解析 Markdown 原文，并发布指定 DocumentId 的当前不可变快照。
     *
     * @param documentId 已持久化文档的标识
     * @param source 完整 Markdown 原文
     * @return 新发布的不可变上下文
     */
    public MarkdownContext register(DocumentId documentId, String source) {
        // 先完整解析新 source，再一次性替换旧快照，避免上下文处于半解析状态。
        MarkdownContext context = this.parser.parse(documentId, source);
        this.contexts.put(documentId, context);
        return context;
    }

    /**
     * 移除指定文档的内存快照。
     *
     * @param documentId 要移除的文档标识
     */
    public void unregister(DocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId cannot be null");
        this.contexts.remove(documentId);
    }

    /**
     * 获取 DocumentId 对应的当前不可变快照。
     *
     * @param documentId 已持久化文档的标识
     * @return 当前不可变上下文
     * @throws MarkdownContextNotFoundException DocumentId 未注册时抛出
     */
    public MarkdownContext getEntity(DocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId cannot be null");
        MarkdownContext context = this.contexts.get(documentId);
        if (context == null) {
            // 不返回 null，调用方需要明确知道该 DocumentId 没有可用上下文。
            throw new MarkdownContextNotFoundException(documentId);
        }
        return context;
    }

    /**
     * 按原文顺序输出带编号和层级缩进的标题树。
     *
     * @param documentId 已持久化文档的标识
     * @return 每行一个标题，缩进表示逻辑树深度
     */
    public String getHeadingTree(DocumentId documentId) {
        MarkdownContext context = getEntity(documentId);
        StringBuilder result = new StringBuilder();
        Set<Integer> rendered = new HashSet<>();
        // 从所有根节点开始递归，保持多个一级标题在原文中的顺序。
        for (Integer rootNodeId : context.getRootNodeIds()) {
            appendHeadingTree(context, rootNodeId, 0, result, rendered);
        }
        return result.toString();
    }

    /**
     * 返回指定节点的直属正文和直属子标题摘要。
     *
     * @param documentId 已持久化文档的标识
     * @param nodeNumber 按原文顺序分配的节点编号
     * @return 带动态省略号的节点预览文本
     */
    public String getSectionPreview(DocumentId documentId, int nodeNumber) {
        MarkdownContext context = getEntity(documentId);
        SectionNode node = requireNode(context, nodeNumber);
        String source = context.getSource();
        StringBuilder result = new StringBuilder();
        // 标题和直属正文保留 source 原文，避免摘要改变 Markdown 写法。
        result.append(rawHeading(context, node));
        result.append(source, node.getBodyStart(), node.getDirectEnd());

        // 只展开直属子标题；子标题自身仍以一行标题和省略标记表示。
        for (Integer childNumber : node.getChildren()) {
            SectionNode child = requireNode(context, childNumber);
            if (result.length() > 0 && !endsWithLineBreak(result)) {
                // 父节点正文没有换行时，先补一行再追加子标题。
                result.append('\n');
            }
            result.append(rawHeading(context, child));
            if (hasExpandableContent(context, child)) {
                // 子标题还有正文或后代节点，使用省略号提示存在未展开内容。
                result.append(" ...");
            }
        }
        return result.toString();
    }

    /**
     * 返回指定节点完整章节的第一页分页结果。
     *
     * @param documentId 已持久化文档的标识
     * @param nodeNumber 按原文顺序分配的节点编号
     * @return 第一页 {@link SectionPage}；章节一次读取完毕时 {@code nextCursor} 为 {@code null}
     */
    public SectionPage getSectionAll(DocumentId documentId, int nodeNumber) {
        // 无 cursor 的快捷入口统一委托分页实现，确保第一页和后续页使用完全相同的边界规则。
        return getSectionAll(documentId, nodeNumber, null);
    }

    /**
     * 返回指定节点完整章节的一页字节受限文本。
     *
     * @param documentId 已持久化文档的标识
     * @param nodeNumber 按原文顺序分配的节点编号
     * @param cursor 上一页返回的不透明 cursor；{@code null} 表示第一页
     * @return 当前页文本及下一页 cursor 信息
     */
    public SectionPage getSectionAll(DocumentId documentId, int nodeNumber, String cursor) {
        MarkdownContext context = getEntity(documentId);
        SectionNode node = requireNode(context, nodeNumber);
        int byteOffset = 0;
        if (cursor != null) {
            if (cursor.isBlank()) {
                // 非 null cursor 必须包含可解码的分页位置，空白值视为非法请求。
                throw new MarkdownCursorException("cursor must not be blank");
            }
            SectionCursor decoded = decodeCursor(cursor);
            if (!documentId.equals(decoded.documentId()) || nodeNumber != decoded.nodeNumber()) {
                // cursor 绑定 DocumentId 和节点编号，不能跨上下文或跨节点复用。
                throw new MarkdownCursorException("cursor does not match the requested section");
            }
            byteOffset = decoded.byteOffset();
        }

        // 读取实际字节区间，只有确实还有内容时才生成下一页 cursor。
        PageSlice page = readPage(context, node, byteOffset);
        String nextCursor = page.hasMore()
                ? encodeCursor(documentId, nodeNumber, page.nextOffset())
                : null;
        // 对外以 nextCursor 是否存在作为续读依据，hasMore 仅保持兼容且与其同步。
        boolean hasMore = nextCursor != null;
        String content = page.content() + (hasMore ? "..." : "");
        return new SectionPage(content, hasMore, nextCursor);
    }

    /**
     * 无损还原当前快照保存的完整 Markdown 原文。
     *
     * @param documentId 已持久化文档的标识
     * @return 完整 Markdown 原文，不分页、不截断且不重新渲染
     */
    public String restoreMarkdown(DocumentId documentId) {
        return getEntity(documentId).getSource();
    }

    /**
     * 在节点直属正文中唯一替换一段精确文本，并发布新的上下文快照。
     *
     * @param section 目标上下文和节点
     * @param originalText 要替换的非空精确文本
     * @param newText 替换文本；空字符串表示删除匹配内容
     * @return 包含新 Markdown 上下文的替换结果
     */
    public ReplaceResult replace(SectionNodeRef section, String originalText, String newText) {
        Objects.requireNonNull(section, "section cannot be null");
        Objects.requireNonNull(originalText, "originalText cannot be null");
        Objects.requireNonNull(newText, "newText cannot be null");
        if (originalText.isEmpty()) {
            // 空原文会产生无限匹配语义，因此不允许作为替换目标。
            throw new MarkdownReplacementException("originalText must not be empty");
        }

        DocumentId documentId = section.getDocumentId();
        // 同一 DocumentId 的替换串行执行，避免两个请求基于同一旧快照互相覆盖。
        ReentrantLock lock = this.replacementLocks.computeIfAbsent(documentId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            MarkdownContext current = getEntity(documentId);
            SectionNode node = requireNode(current, section.getNodeNumber());
            String source = current.getSource();
            // 只截取当前节点的直属正文，子节点正文不会被本次替换命中。
            String directBody = source.substring(node.getBodyStart(), node.getDirectEnd());
            int matchStart = uniqueMatchStart(directBody, originalText);
            int absoluteMatchStart = node.getBodyStart() + matchStart;
            int absoluteMatchEnd = absoluteMatchStart + originalText.length();
            String updatedSource = source.substring(0, absoluteMatchStart)
                    + newText
                    + source.substring(absoluteMatchEnd);
            // 替换后重新解析完整文档，使标题编号、source span 和 revision 全部同步更新。
            MarkdownContext updated = this.parser.parse(documentId, updatedSource);

            try {
                // 先持久化新快照，成功后才对内存上下文发布，保证失败时旧快照仍可读。
                this.store.save(updated);
            }
            catch (RuntimeException exception) {
                // 持久化异常转换为领域异常；此时 contexts 仍保留旧版本。
                throw new MarkdownPersistenceException(
                        "Unable to persist Markdown replacement: " + documentId, exception);
            }
            this.contexts.put(documentId, updated);
            return new ReplaceResult(updated);
        }
        finally {
            // 无论成功还是失败都释放 DocumentId 锁，避免后续替换永久阻塞。
            lock.unlock();
        }
    }

    private static SectionNode requireNode(MarkdownContext context, int nodeNumber) {
        SectionNode node = context.getNodes().get(nodeNumber);
        if (node == null) {
            // 节点编号不属于当前快照时，统一转换为明确的领域异常。
            throw new SectionNotFoundException(context.getDocumentId(), nodeNumber);
        }
        return node;
    }

    private static boolean hasExpandableContent(MarkdownContext context, SectionNode node) {
        // 直属正文非空或存在后代时，预览只显示标题并提示尚有未展开内容。
        return !context.getSource().substring(node.getBodyStart(), node.getDirectEnd()).isBlank()
                || !node.getChildren().isEmpty();
    }

    private static int uniqueMatchStart(String directBody, String originalText) {
        int firstMatch = directBody.indexOf(originalText);
        if (firstMatch < 0) {
            // 找不到精确文本时不执行任何修改。
            throw new MarkdownReplacementException("originalText was not found in the direct body");
        }
        int secondMatch = directBody.indexOf(originalText, firstMatch + 1);
        if (secondMatch >= 0) {
            // 第二次命中表示替换目标不唯一，避免误改正文。
            throw new MarkdownReplacementException("originalText matched more than once in the direct body");
        }
        return firstMatch;
    }

    private static boolean endsWithLineBreak(StringBuilder value) {
        int length = value.length();
        // CR 和 LF 都视为已有换行，避免预览额外插入空行。
        return length > 0 && (value.charAt(length - 1) == '\n' || value.charAt(length - 1) == '\r');
    }

    private static PageSlice readPage(MarkdownContext context, SectionNode node, int byteOffset) {
        // 章节边界使用 UTF-16 索引截取，再转换为 UTF-8 字节以执行分页协议。
        String section = context.getSource().substring(node.getHeadingStart(), node.getSectionEnd());
        byte[] bytes = section.getBytes(StandardCharsets.UTF_8);
        if (byteOffset < 0 || byteOffset > bytes.length) {
            // cursor 偏移必须落在当前章节字节数组范围内。
            throw new MarkdownCursorException("section byte offset is out of bounds");
        }
        if (!isUtf8Boundary(bytes, byteOffset)) {
            // 不允许从多字节字符的中间开始，防止分页结果产生替换字符。
            throw new MarkdownCursorException("section byte offset is not a UTF-8 boundary");
        }

        int remaining = bytes.length - byteOffset;
        // 有后续页时先预留省略号的 3 个字节，保证返回内容不超过 5120 字节。
        int originalBudget = remaining > PAGE_BYTE_LIMIT
                ? PAGE_BYTE_LIMIT - ELLIPSIS_BYTE_LENGTH
                : PAGE_BYTE_LIMIT;
        int end = utf8Boundary(bytes, byteOffset, originalBudget);
        boolean hasMore = end < bytes.length;
        // 只将完整 UTF-8 字符解码成字符串，下一页从 end 继续读取。
        String content = new String(bytes, byteOffset, end - byteOffset, StandardCharsets.UTF_8);
        return new PageSlice(content, end, hasMore);
    }

    private static boolean isUtf8Boundary(byte[] bytes, int offset) {
        if (offset == 0 || offset == bytes.length) {
            // 数组首尾天然是合法边界。
            return true;
        }
        // UTF-8 延续字节以 10 开头，若当前位置不是延续字节即为字符边界。
        return (bytes[offset] & 0xC0) != 0x80;
    }

    private static String encodeCursor(DocumentId documentId, int nodeNumber, int byteOffset) {
        // cursor 内含十进制 DocumentId、节点编号和下一页实际字节偏移，再做 URL-safe Base64 编码。
        String value = documentId.value() + ":" + nodeNumber + ":" + byteOffset;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static SectionCursor decodeCursor(String cursor) {
        try {
            // 先解码 cursor，再校验字段数量、DocumentId 格式和非负范围。
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] fields = value.split(":", -1);
            if (fields.length != 3) {
                // 三个字段分别表示 DocumentId、节点编号和字节偏移。
                throw new IllegalArgumentException("cursor must contain three fields");
            }
            DocumentId documentId = new DocumentId(Long.parseLong(fields[0]));
            int nodeNumber = Integer.parseInt(fields[1]);
            int byteOffset = Integer.parseInt(fields[2]);
            if (nodeNumber <= 0 || byteOffset < 0) {
                // 节点编号必须为正，字节偏移只能从零开始向后移动。
                throw new IllegalArgumentException("cursor fields are out of range");
            }
            return new SectionCursor(documentId, nodeNumber, byteOffset);
        }
        catch (IllegalArgumentException exception) {
            // Base64、DocumentId 或数字解析失败都统一报告为非法 cursor。
            throw new MarkdownCursorException("cursor is malformed", exception);
        }
    }

    private static int utf8Boundary(byte[] bytes, int start, int budget) {
        int limit = Math.min(bytes.length, start + budget);
        int cursor = start;
        // 在预算内逐个消费完整 UTF-8 字符，不能简单按 byte 截断。
        while (cursor < limit) {
            int width = utf8CharacterWidth(bytes[cursor]);
            if (cursor + width > limit) {
                // 当前字符会越过预算，留给下一页继续读取。
                break;
            }
            cursor += width;
        }
        return cursor;
    }

    private static int utf8CharacterWidth(byte value) {
        int unsigned = value & 0xFF;
        if ((unsigned & 0x80) == 0) {
            // ASCII 字符占一个字节。
            return 1;
        }
        if ((unsigned & 0xE0) == 0xC0) {
            // 以 110 开头的字符占两个字节。
            return 2;
        }
        if ((unsigned & 0xF0) == 0xE0) {
            // 以 1110 开头的字符占三个字节。
            return 3;
        }
        // 其余合法首字节按四字节字符处理。
        return 4;
    }

    private record PageSlice(String content, int nextOffset, boolean hasMore) {
    }

    private record SectionCursor(DocumentId documentId, int nodeNumber, int byteOffset) {
    }

    private static void appendHeadingTree(
            MarkdownContext context,
            int nodeNumber,
            int depth,
            StringBuilder result,
            Set<Integer> rendered) {
        SectionNode node = context.getNodes().get(nodeNumber);
        if (node == null || !rendered.add(nodeNumber)) {
            // 节点缺失或已经渲染过时停止递归，避免异常数据造成循环。
            return;
        }
        if (result.length() > 0) {
            // 每个标题占一行，根节点之间也保持换行分隔。
            result.append('\n');
        }
        result.append("  ".repeat(depth))
                .append(node.getNumber())
                .append(". ")
                .append(rawHeading(context, node));
        for (Integer child : node.getChildren()) {
            // 按 children 保存的原文顺序递归输出直属子标题。
            appendHeadingTree(context, child, depth + 1, result, rendered);
        }
    }

    private static String rawHeading(MarkdownContext context, SectionNode node) {
        // 从 source 中取出标题原始语法，并去掉仅用于定位正文的行尾换行。
        String heading = context.getSource().substring(node.getHeadingStart(), node.getBodyStart());
        if (heading.endsWith("\r\n")) {
            // CRLF 需要整体移除，保持标题文本本身的原始空格和符号。
            return heading.substring(0, heading.length() - 2);
        }
        if (heading.endsWith("\r") || heading.endsWith("\n")) {
            // 单独 CR 或 LF 只移除一个换行字符。
            return heading.substring(0, heading.length() - 1);
        }
        // 没有行尾换行时直接返回标题片段。
        return heading;
    }

    MarkdownStore store() {
        return this.store;
    }

    MarkdownAstParser parser() {
        return this.parser;
    }

    ConcurrentMap<DocumentId, MarkdownContext> contexts() {
        return this.contexts;
    }

    ConcurrentMap<DocumentId, ReentrantLock> replacementLocks() {
        return this.replacementLocks;
    }
}
