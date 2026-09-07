package com.jacolp.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.mapper.ChatSessionMapper;
import com.jacolp.pojo.entity.ChatSession;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * In-memory chat memory that keeps the complete conversation for persistence while
 * exposing only a fixed-size sliding window to Spring AI.
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

    public ChatContextManager(ChatSessionMapper chatSessionMapper, ObjectMapper objectMapper) {
        this.chatSessionMapper = Objects.requireNonNull(chatSessionMapper, "chatSessionMapper cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String canonicalConversationId = canonicalConversationId(conversationId);
        Objects.requireNonNull(messages, "messages cannot be null");
        if (messages.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("messages cannot contain null elements");
        }

        ConversationState conversation = getOrLoad(canonicalConversationId);
        conversation.lock.lock();
        try {
            if (!messages.isEmpty()) {
                conversation.messages.addAll(messages);
                conversation.dirty = true;
            }
        }
        finally {
            conversation.lock.unlock();
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        String canonicalConversationId = canonicalConversationId(conversationId);
        ConversationState conversation = getOrLoad(canonicalConversationId);
        conversation.lock.lock();
        try {
            int firstMessage = Math.max(0, conversation.messages.size() - MAX_CONTEXT_MESSAGES);
            return List.copyOf(conversation.messages.subList(firstMessage, conversation.messages.size()));
        }
        finally {
            conversation.lock.unlock();
        }
    }

    @Override
    public void clear(String conversationId) {
        String canonicalConversationId = canonicalConversationId(conversationId);
        ConversationState conversation = getOrLoad(canonicalConversationId);
        conversation.lock.lock();
        try {
            conversation.messages.clear();
            conversation.dirty = true;
        }
        finally {
            conversation.lock.unlock();
        }
    }

    /**
     * Runs one chat request while holding the conversation lock. This prevents two
     * requests for the same conversation from interleaving their advisor callbacks.
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
     * Flushes dirty cached conversations. Scheduled calls use non-blocking locking so
     * an active AI request is skipped; shutdown calls can wait for active requests.
     *
     * @param waitForInFlight whether to wait for conversation locks
     * @return the number of successfully flushed conversations
     */
    public int flushDirtySessions(boolean waitForInFlight) {
        int flushedCount = 0;
        for (ConversationState conversation : this.conversations.values()) {
            boolean locked = false;
            try {
                if (waitForInFlight) {
                    conversation.lock.lock();
                    locked = true;
                }
                else {
                    locked = conversation.lock.tryLock();
                    if (!locked) {
                        continue;
                    }
                }

                if (!conversation.dirty) {
                    continue;
                }

                int updatedRows = this.chatSessionMapper.updateSnapshot(conversation.toSnapshot(this.objectMapper));
                if (updatedRows > 0) {
                    conversation.dirty = false;
                    flushedCount++;
                }
                else {
                    LOGGER.warn("Chat session snapshot was not updated: conversationId={}",
                            conversation.conversationId);
                }
            }
            catch (RuntimeException exception) {
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

    private ConversationState getOrLoad(String conversationId) {
        return this.conversations.computeIfAbsent(conversationId, key -> {
            long sessionId = Long.parseLong(key);
            ChatSession session = this.chatSessionMapper.selectById(sessionId);
            if (session == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "chat session not found: " + sessionId);
            }
            return ConversationState.from(session, this.objectMapper);
        });
    }

    private static String canonicalConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId cannot be null or empty");
        }

        try {
            long sessionId = Long.parseLong(conversationId);
            if (sessionId <= 0) {
                throw new IllegalArgumentException("conversationId must be a positive session id");
            }
            return Long.toString(sessionId);
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("conversationId must be a numeric session id", exception);
        }
    }

    private record StoredMessage(String role, String content) {
    }

    private static final class ConversationState {

        private final String conversationId;

        private final long sessionId;

        private final ReentrantLock lock = new ReentrantLock();

        private final List<Message> messages;

        private String title;

        private String referencedFileContents;

        private boolean dirty;

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

        private static ConversationState from(ChatSession session, ObjectMapper objectMapper) {
            String conversationId = Long.toString(session.getId());
            return new ConversationState(
                    conversationId,
                    session.getId(),
                    session.getTitle(),
                    readMessages(session.getMessages(), objectMapper, conversationId),
                    session.getReferencedFileContents() == null
                            ? "[]"
                            : session.getReferencedFileContents());
        }

        private ChatSession toSnapshot(ObjectMapper objectMapper) {
            ChatSession snapshot = new ChatSession();
            snapshot.setId(this.sessionId);
            snapshot.setTitle(this.title);
            snapshot.setMessages(writeMessages(this.messages, objectMapper));
            snapshot.setReferencedFileContents(this.referencedFileContents);
            return snapshot;
        }

        private static List<Message> readMessages(
                String messagesJson,
                ObjectMapper objectMapper,
                String conversationId) {
            String json = messagesJson == null ? "[]" : messagesJson;
            try {
                List<StoredMessage> storedMessages = objectMapper.readValue(json, STORED_MESSAGES_TYPE);
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

        private static String writeMessages(List<Message> messages, ObjectMapper objectMapper) {
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

        private static StoredMessage toStoredMessage(Message message) {
            MessageType messageType = message.getMessageType();
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

        private static Message toMessage(StoredMessage storedMessage) {
            if (storedMessage == null || storedMessage.role() == null || storedMessage.content() == null) {
                throw new IllegalArgumentException("stored message role and content cannot be null");
            }
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
