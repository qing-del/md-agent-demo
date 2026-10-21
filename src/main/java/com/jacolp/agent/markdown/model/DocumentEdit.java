package com.jacolp.agent.markdown.model;

import org.springframework.ai.tool.annotation.ToolParam;

/** An edit located by source text in one revision, never by model-generated offsets. */
public record DocumentEdit(
        @ToolParam(description = "replace 或 insert") String type,
        @ToolParam(description = "基础版本内的章节编号") int nodeNumber,
        @ToolParam(required = false, description = "insert: body_start/body_end/before/after") String position,
        @ToolParam(required = false, description = "before/after 必需的唯一原文锚点") String anchorText,
        @ToolParam(required = false, description = "replace 必需的非空唯一原文") String originalText,
        @ToolParam(description = "替换或插入的原始文本；replace 时空字符串表示删除") String newText) {
    public static DocumentEdit replace(int nodeNumber, String originalText, String newText) {
        return new DocumentEdit("replace", nodeNumber, null, null, originalText, newText);
    }

    public static DocumentEdit insert(int nodeNumber, String position, String anchorText, String newText) {
        return new DocumentEdit("insert", nodeNumber, position, anchorText, null, newText);
    }
}
