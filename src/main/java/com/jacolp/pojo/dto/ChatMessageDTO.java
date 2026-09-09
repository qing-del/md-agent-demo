package com.jacolp.pojo.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化聊天消息，承载用户输入及其关联的文档元数据。
 */
public class ChatMessageDTO {

    private String content;

    private List<Long> documentIds = new ArrayList<>();

    private List<SelectionDTO> selections = new ArrayList<>();

    /**
     * 创建空的聊天消息对象，供框架反序列化使用。
     */
    public ChatMessageDTO() {
    }

    /**
     * 创建完整的聊天消息对象。
     *
     * @param content 用户输入的问题或指令
     * @param documentIds 用户通过 {@code @} 引用的文档 ID 列表
     * @param selections 用户添加到聊天中的选区列表
     */
    public ChatMessageDTO(String content, List<Long> documentIds, List<SelectionDTO> selections) {
        this.content = content;
        setDocumentIds(documentIds);
        setSelections(selections);
    }

    /**
     * 获取用户输入。
     *
     * @return 用户输入的问题或指令
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置用户输入。
     *
     * @param content 用户输入的问题或指令
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取用户明确引用的文档 ID 列表。
     *
     * @return 文档 ID 列表
     */
    public List<Long> getDocumentIds() {
        return this.documentIds;
    }

    /**
     * 设置用户明确引用的文档 ID 列表。
     *
     * <p>缺省或显式 {@code null} 按空列表处理，但保留列表中的空元素供接口层校验。</p>
     *
     * @param documentIds 文档 ID 列表
     */
    public void setDocumentIds(List<Long> documentIds) {
        this.documentIds = documentIds == null ? new ArrayList<>() : new ArrayList<>(documentIds);
    }

    /**
     * 获取用户添加到聊天中的选区列表。
     *
     * @return 选区列表
     */
    public List<SelectionDTO> getSelections() {
        return this.selections;
    }

    /**
     * 设置用户添加到聊天中的选区列表。
     *
     * <p>缺省或显式 {@code null} 按空列表处理，但保留列表中的空元素供接口层校验。</p>
     *
     * @param selections 选区列表
     */
    public void setSelections(List<SelectionDTO> selections) {
        this.selections = selections == null ? new ArrayList<>() : new ArrayList<>(selections);
    }
}
