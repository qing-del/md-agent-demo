package com.jacolp.agent.context;

import java.util.List;
import java.util.Objects;

import com.jacolp.pojo.dto.ChatMessageDTO;
import com.jacolp.pojo.dto.SelectionDTO;

/**
 * 将前端结构化聊天消息整理为发送给 LLM 的用户提示。
 *
 * <p>数据库中的聊天历史仍保存原始问题；该类只负责当前请求的临时上下文拼装。</p>
 */
public final class ChatPromptAssembler {

    /**
     * 组装当前请求的 LLM 用户提示。
     *
     * @param message 结构化聊天消息
     * @return 包含当前问题及可选引用上下文的提示文本
     */
    public String assemble(ChatMessageDTO message) {
        Objects.requireNonNull(message, "message cannot be null");
        String content = Objects.requireNonNull(message.getContent(), "message.content cannot be null");
        StringBuilder prompt = new StringBuilder(content);

        List<Long> documentIds = message.getDocumentIds() == null ? List.of() : message.getDocumentIds();
        if (!documentIds.isEmpty()) {
            appendDocumentIds(prompt, documentIds);
        }

        List<SelectionDTO> selections = message.getSelections() == null ? List.of() : message.getSelections();
        if (!selections.isEmpty()) {
            appendSelections(prompt, selections);
        }
        return prompt.toString();
    }

    private static void appendDocumentIds(StringBuilder prompt, List<Long> documentIds) {
        appendContextSeparator(prompt);
        prompt.append("<documentIds>\n");
        for (Long documentId : documentIds) {
            prompt.append("  <documentId>")
                    .append(documentId)
                    .append("</documentId>\n");
        }
        prompt.append("</documentIds>");
    }

    private static void appendSelections(StringBuilder prompt, List<SelectionDTO> selections) {
        appendContextSeparator(prompt);
        prompt.append("<selections>\n");
        for (SelectionDTO selection : selections) {
            Objects.requireNonNull(selection, "message.selections cannot contain null");
            prompt.append("  <selection>\n")
                    .append("    <documentId>")
                    .append(selection.getDocumentId())
                    .append("</documentId>\n")
                    .append("    <sectionText>")
                    .append(escapeXml(selection.getSectionText()))
                    .append("</sectionText>\n")
                    .append("    <originalText>")
                    .append(escapeXml(selection.getOriginalText()))
                    .append("</originalText>\n")
                    .append("  </selection>\n");
        }
        prompt.append("</selections>");
    }

    private static void appendContextSeparator(StringBuilder prompt) {
        if (!prompt.isEmpty()) {
            prompt.append("\n\n");
        }
    }

    /**
     * 转义 XML 文本节点中不能直接出现的字符。
     *
     * @param value 待转义文本
     * @return XML 安全文本
     */
    static String escapeXml(String value) {
        Objects.requireNonNull(value, "XML value cannot be null");
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            switch (value.charAt(index)) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(value.charAt(index));
            }
        }
        return escaped.toString();
    }
}
