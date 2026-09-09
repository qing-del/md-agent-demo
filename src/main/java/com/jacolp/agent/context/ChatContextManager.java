package com.jacolp.agent.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jacolp.mapper.ChatSessionMapper;
import com.jacolp.pojo.dto.ChatMessageDTO;
import com.jacolp.pojo.dto.SelectionDTO;
import com.jacolp.pojo.entity.ChatSession;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 基于内存的聊天记忆实现。
 *
 * <p>内部保留完整消息历史供持久化使用，同时只向 Spring AI 暴露固定大小的滑动窗口。</p>
 */
@Service
public class ChatContextManager implements ChatMemory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatContextManager.class);

    private static final int MAX_CONTEXT_MESSAGES = 10;

    private static final TypeReference<List<StoredMessage>> STORED_MESSAGES_TYPE = new TypeReference<>() {
    };

    private final ChatSessionMapper chatSessionMapper;

    private final ObjectMapper objectMapper;

    private final ConcurrentMap<String, ConversationState> conversations = new ConcurrentHashMap<>();

    /**
     * 创建聊天上下文管理器。
     *
     * @param chatSessionMapper 聊天会话数据库访问对象
     * @param objectMapper 用于读写消息 JSON 的对象映射器
     * @throws NullPointerException 依赖对象为空时抛出
     */
    public ChatContextManager(ChatSessionMapper chatSessionMapper, ObjectMapper objectMapper) {
        this.chatSessionMapper = Objects.requireNonNull(chatSessionMapper, "chatSessionMapper cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    /**
     * 将消息追加到指定会话的完整历史，并将会话标记为待刷新。
     *
     * @param conversationId UUID 会话标识
     * @param messages 待追加的消息列表
     * @throws IllegalArgumentException UUID 会话标识非法或消息列表包含空元素时抛出
     * @throws NullPointerException 消息列表为空时抛出
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        String canonicalConversationId = canonicalConversationId(conversationId);
        Objects.requireNonNull(messages, "messages cannot be null");
        // 提前拒绝空消息，避免把不完整的历史写入内存并在稍后刷新到数据库。
        if (messages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("messages cannot contain null elements");
        }

        ConversationState conversation = getOrLoad(canonicalConversationId);
        conversation.lock.lock();
        try {
            // 空列表不改变快照，因此无需产生一次无意义的刷新任务。
            if (!messages.isEmpty()) {
                conversation.messages.addAll(messages);
                conversation.dirty = true;
            }
        }
        finally {
            conversation.lock.unlock();
        }
    }

    /**
     * 获取指定会话的上下文窗口。
     *
     * @param conversationId UUID 会话标识
     * @return 最近 {@value #MAX_CONTEXT_MESSAGES} 条消息
     * @throws IllegalArgumentException UUID 会话标识非法时抛出
     */
    @Override
    public List<Message> get(String conversationId) {
        String canonicalConversationId = canonicalConversationId(conversationId);
        ConversationState conversation = getOrLoad(canonicalConversationId);
        conversation.lock.lock();
        try {
            // 完整历史用于持久化，模型上下文只取尾部窗口以限制单次请求的上下文规模。
            int firstMessage = Math.max(0, conversation.messages.size() - MAX_CONTEXT_MESSAGES);
            return List.copyOf(conversation.messages.subList(firstMessage, conversation.messages.size()));
        }
        finally {
            conversation.lock.unlock();
        }
    }

    /**
     * 清空指定会话的内存消息，并保留会话记录等待刷新为空数组。
     *
     * @param conversationId UUID 会话标识
     * @throws IllegalArgumentException UUID 会话标识非法时抛出
     */
    @Override
    public void clear(String conversationId) {
        String canonicalConversationId = canonicalConversationId(conversationId);
        ConversationState conversation = getOrLoad(canonicalConversationId);
        conversation.lock.lock();
        try {
            conversation.messages.clear();
            // 清空本身也需要持久化，否则数据库仍会保留清空前的消息历史。
            conversation.dirty = true;
        }
        finally {
            conversation.lock.unlock();
        }
    }

    /**
     * 将本轮聊天中的文档引用和选区追加到会话引用快照。
     *
     * <p>引用记录与消息历史使用同一个会话锁和 dirty 刷新机制，确保同一会话的并发请求不会互相覆盖。</p>
     *
     * @param conversationId UUID 会话标识
     * @param message 结构化聊天消息
     * @throws IllegalArgumentException UUID 会话标识非法时抛出
     * @throws NullPointerException 消息为空时抛出
     */
    public void appendReferenceMetadata(String conversationId, ChatMessageDTO message) {
        String canonicalConversationId = canonicalConversationId(conversationId);
        Objects.requireNonNull(message, "message cannot be null");

        List<Long> documentIds = message.getDocumentIds();
        List<SelectionDTO> selections = message.getSelections();
        if ((documentIds == null || documentIds.isEmpty())
                && (selections == null || selections.isEmpty())) {
            return;
        }

        ConversationState conversation = getOrLoad(canonicalConversationId);
        conversation.lock.lock();
        try {
            conversation.appendReferenceMetadata(message, this.objectMapper);
            conversation.dirty = true;
        }
        finally {
            conversation.lock.unlock();
        }
    }

    /**
     * 在持有会话锁的情况下执行一次聊天请求。
     *
     * <p>这样可以防止同一会话的多个请求交叉执行记忆顾问回调。</p>
     *
     * @param conversationId UUID 会话标识
     * @param action 需要在会话锁内执行的请求动作
     * @param <T> 请求结果类型
     * @return 请求动作的执行结果
     * @throws IllegalArgumentException UUID 会话标识非法时抛出
     * @throws NullPointerException 请求动作为空时抛出
     */
    public <T> T withConversationLock(String conversationId, Supplier<T> action) {
        String canonicalConversationId = canonicalConversationId(conversationId);
        Objects.requireNonNull(action, "action cannot be null");

        ConversationState conversation = getOrLoad(canonicalConversationId);
        conversation.lock.lock();
        try {
            return action.get();
        }
        finally {
            conversation.lock.unlock();
        }
    }

    /**
     * 刷新内存中已变更的会话快照。
     *
     * <p>定时任务使用非阻塞锁，正在执行 AI 请求的会话会被跳过；应用关闭时可以等待请求完成。</p>
     *
     * @param waitForInFlight 是否等待正在执行请求的会话锁
     * @return 成功刷新的会话数量
     */
    public int flushDirtySessions(boolean waitForInFlight) {
        int flushedCount = 0;
        for (ConversationState conversation : this.conversations.values()) {
            boolean locked = false;
            try {
                // 定时刷新不能阻塞正在调用模型的请求，关闭阶段则必须等待其生成最终快照。
                if (waitForInFlight) {
                    conversation.lock.lock();
                    locked = true;
                }
                else {
                    locked = conversation.lock.tryLock();
                    // 当前会话仍在请求中，留给下一次定时任务或关闭流程处理。
                    if (!locked) {
                        continue;
                    }
                }

                // 未发生变更的会话无需重复写数据库。
                if (!conversation.dirty) {
                    continue;
                }

                int updatedRows = this.chatSessionMapper.updateSnapshot(conversation.toSnapshot(this.objectMapper));
                if (updatedRows > 0) {
                    conversation.dirty = false;
                    flushedCount++;
                }
                else {
                    // 更新返回 0 视为未成功，保留 dirty 状态以便后续重试。
                    LOGGER.warn("Chat session snapshot was not updated: conversationId={}",
                            conversation.conversationId);
                }
            }
            catch (RuntimeException exception) {
                // 数据库或序列化异常不能丢弃内存快照，保留 dirty 状态等待下一轮刷新。
                LOGGER.warn("Unable to flush chat session snapshot: conversationId={}",
                        conversation.conversationId, exception);
            }
            finally {
                if (locked) {
                    conversation.lock.unlock();
                }
            }
        }
        return flushedCount;
    }

    /**
     * 从缓存获取会话；缓存未命中时从数据库加载或创建会话并建立内存状态。
     *
     * @param conversationId 已规范化的 UUID 会话标识
     * @return 会话内存状态
     */
    private ConversationState getOrLoad(String conversationId) {
        // computeIfAbsent 保证同一个规范化 ID 只建立一个缓存状态。
        return this.conversations.computeIfAbsent(conversationId, this::loadOrCreateConversation);
    }

    /**
     * 按 UUID 读取会话；不存在时创建空会话并返回其内存状态。
     *
     * @param sessionKey 已规范化的 UUID 会话标识
     * @return 已加载或新建的会话内存状态
     * @throws IllegalStateException 创建会话未成功写入数据库时抛出
     */
    private ConversationState loadOrCreateConversation(String sessionKey) {
        ChatSession session = this.chatSessionMapper.selectBySessionKey(sessionKey);
        if (session == null) {
            // 前端首次发送该 UUID 时创建空会话，使其后续历史可由同一标识稳定恢复。
            session = new ChatSession();
            session.setSessionKey(sessionKey);
            int insertedRows = this.chatSessionMapper.insert(session);
            if (insertedRows != 1) {
                throw new IllegalStateException("Unable to create chat session: " + sessionKey);
            }
        }
        return ConversationState.from(session, this.objectMapper, sessionKey);
    }

    /**
     * 校验并规范化 Spring AI 使用的 UUID 会话标识。
     *
     * @param conversationId 原始 UUID 会话标识
     * @return 小写、标准格式的 UUID 会话标识
     * @throws IllegalArgumentException 会话标识为空或格式非法时抛出
     */
    private static String canonicalConversationId(String conversationId) {
        // UUID 是前端与服务端会话状态的稳定关联，不能为空或使用非标准格式。
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId cannot be null or empty");
        }

        try {
            UUID uuid = UUID.fromString(conversationId);
            // UUID.fromString 可接受部分缩写格式，统一拒绝以避免同一会话出现多个缓存键。
            if (!uuid.toString().equalsIgnoreCase(conversationId)) {
                throw new IllegalArgumentException("conversationId must use the standard UUID format");
            }
            return uuid.toString();
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("conversationId must be a UUID", exception);
        }
    }

    /**
     * 数据库消息快照中使用的最小消息结构。
     *
     * @param role 消息角色
     * @param content 消息正文
     */
    private record StoredMessage(String role, String content) {
    }

    /**
     * 单个聊天会话的内存状态及其并发控制信息。
     */
    private static final class ConversationState {

        private final String conversationId;

        private final long sessionId;

        private final ReentrantLock lock = new ReentrantLock();

        private final List<Message> messages;

        private String title;

        private String referencedFileContents;

        private boolean dirty;

        /**
         * 创建一个会话内存状态。
         *
         * @param conversationId 规范化后的 UUID 会话标识
         * @param sessionId 数据库中的会话主键
         * @param title 会话标题
         * @param messages 完整消息历史
         * @param referencedFileContents 文件引用 JSON 快照
         */
        private ConversationState(
                String conversationId,
                long sessionId,
                String title,
                List<Message> messages,
                String referencedFileContents) {
            this.conversationId = conversationId;
            this.sessionId = sessionId;
            this.title = title;
            this.messages = new ArrayList<>(messages);
            this.referencedFileContents = referencedFileContents;
        }

        /**
         * 从数据库实体构建会话内存状态。
         *
         * @param session 数据库中的聊天会话
         * @param objectMapper 用于解析消息 JSON 的对象映射器
         * @param conversationId 当前缓存使用的 UUID 会话标识
         * @return 初始化完成的会话内存状态
         */
        private static ConversationState from(
                ChatSession session,
                ObjectMapper objectMapper,
                String conversationId) {
            // 文件引用 JSON 由数据库原样保留，当前阶段不解析或展开完整 Markdown 内容。
            return new ConversationState(
                    conversationId,
                    session.getId(),
                    session.getTitle(),
                    readMessages(session.getMessages(), objectMapper, conversationId),
                    session.getReferencedFileContents() == null
                            ? "[]"
                            : session.getReferencedFileContents());
        }

        /**
         * 将当前内存状态转换为可写入数据库的快照实体。
         *
         * @param objectMapper 用于序列化消息 JSON 的对象映射器
         * @return 当前会话的数据库快照
         */
        private ChatSession toSnapshot(ObjectMapper objectMapper) {
            ChatSession snapshot = new ChatSession();
            snapshot.setId(this.sessionId);
            snapshot.setTitle(this.title);
            snapshot.setMessages(writeMessages(this.messages, objectMapper));
            snapshot.setReferencedFileContents(this.referencedFileContents);
            return snapshot;
        }

        /**
         * 将一条引用记录追加到当前会话的 JSON 数组快照。
         *
         * @param message 结构化聊天消息
         * @param objectMapper 用于读写引用 JSON 的对象映射器
         */
        private void appendReferenceMetadata(ChatMessageDTO message, ObjectMapper objectMapper) {
            ArrayNode references = readReferences(this.referencedFileContents, objectMapper, this.conversationId);
            ObjectNode reference = objectMapper.createObjectNode();
            List<Long> documentIds = message.getDocumentIds() == null
                    ? List.of()
                    : message.getDocumentIds();
            List<SelectionDTO> selections = message.getSelections() == null
                    ? List.of()
                    : message.getSelections();
            reference.set("documentIds", objectMapper.valueToTree(documentIds));
            reference.set("selections", objectMapper.valueToTree(selections));
            references.add(reference);

            try {
                this.referencedFileContents = objectMapper.writeValueAsString(references);
            }
            catch (JsonProcessingException exception) {
                throw new IllegalStateException(
                        "Unable to serialize chat session references: " + this.conversationId, exception);
            }
        }

        /**
         * 解析数据库中的消息 JSON。
         *
         * @param messagesJson 消息 JSON 文本
         * @param objectMapper 用于反序列化的对象映射器
         * @param conversationId 出错时用于日志和异常信息的 UUID 会话标识
         * @return Spring AI 消息列表
         */
        private static List<Message> readMessages(
                String messagesJson,
                ObjectMapper objectMapper,
                String conversationId) {
            // 历史字段为空时按空数组处理，兼容旧记录或数据库默认值。
            String json = messagesJson == null ? "[]" : messagesJson;
            try {
                List<StoredMessage> storedMessages = objectMapper.readValue(json, STORED_MESSAGES_TYPE);
                // JSON 内容为 null 时也按没有历史消息处理。
                if (storedMessages == null) {
                    return List.of();
                }
                return storedMessages.stream().map(ConversationState::toMessage).toList();
            }
            catch (JsonProcessingException | IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Unable to read messages for chat session: " + conversationId, exception);
            }
        }

        /**
         * 将完整消息历史序列化为只包含角色和正文的 JSON。
         *
         * @param messages 完整消息历史
         * @param objectMapper 用于序列化的对象映射器
         * @return 消息 JSON 文本
         */
        private static String writeMessages(List<Message> messages, ObjectMapper objectMapper) {
            // 持久化只保留核心字段，避免把模型元数据写入聊天历史。
            List<StoredMessage> storedMessages = messages.stream()
                    .map(ConversationState::toStoredMessage)
                    .toList();
            try {
                return objectMapper.writeValueAsString(storedMessages);
            }
            catch (JsonProcessingException exception) {
                throw new IllegalStateException("Unable to serialize chat session messages", exception);
            }
        }

        /**
         * 解析数据库中的引用 JSON 数组。
         *
         * @param referencesJson 引用 JSON 文本
         * @param objectMapper 用于反序列化的对象映射器
         * @param conversationId 出错时用于日志和异常信息的 UUID 会话标识
         * @return 可追加的引用数组
         */
        private static ArrayNode readReferences(
                String referencesJson,
                ObjectMapper objectMapper,
                String conversationId) {
            String json = referencesJson == null ? "[]" : referencesJson;
            try {
                JsonNode references = objectMapper.readTree(json);
                if (references == null || !references.isArray()) {
                    throw new IllegalArgumentException("stored references must be a JSON array");
                }
                return (ArrayNode) references;
            }
            catch (JsonProcessingException | IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Unable to read references for chat session: " + conversationId, exception);
            }
        }

        /**
         * 将 Spring AI 消息转换为数据库使用的最小结构。
         *
         * @param message Spring AI 消息
         * @return 仅包含角色和正文的存储消息
         * @throws IllegalArgumentException 消息类型不受支持时抛出
         */
        private static StoredMessage toStoredMessage(Message message) {
            MessageType messageType = message.getMessageType();
            // v1 只保存对话文本，工具调用等复杂消息类型暂不纳入 JSON 快照。
            if (messageType != MessageType.SYSTEM
                    && messageType != MessageType.USER
                    && messageType != MessageType.ASSISTANT) {
                throw new IllegalArgumentException(
                        "Only system, user, and assistant messages are supported: " + messageType);
            }
            return new StoredMessage(
                    messageType.getValue(),
                    Objects.requireNonNull(message.getText(), "message content cannot be null"));
        }

        /**
         * 将数据库中的最小消息结构还原为 Spring AI 消息。
         *
         * @param storedMessage 数据库消息结构
         * @return Spring AI 消息
         * @throws IllegalArgumentException 消息字段缺失或角色不受支持时抛出
         */
        private static Message toMessage(StoredMessage storedMessage) {
            // 先校验核心字段，避免在角色分支中产生难以定位的空指针异常。
            if (storedMessage == null || storedMessage.role() == null || storedMessage.content() == null) {
                throw new IllegalArgumentException("stored message role and content cannot be null");
            }
            // 根据持久化角色恢复对应的 Spring AI 消息实现。
            return switch (storedMessage.role()) {
                case "system" -> new SystemMessage(storedMessage.content());
                case "user" -> new UserMessage(storedMessage.content());
                case "assistant" -> new AssistantMessage(storedMessage.content());
                default -> throw new IllegalArgumentException(
                        "Unsupported stored chat message role: " + storedMessage.role());
            };
        }
    }
}
