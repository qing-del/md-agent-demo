package com.jacolp.agent.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Logs the complete prompt received by each Spring AI chat model observation.
 */
public final class LlmPromptObservationHandler implements ObservationHandler<ChatModelObservationContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger("com.jacolp.agent.audit.llm");

    private final ObjectMapper objectMapper;

    public LlmPromptObservationHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onStart(ChatModelObservationContext context) {
        if (!LOGGER.isInfoEnabled()) {
            return;
        }

        try {
            LOGGER.info("event=agent.llm.prompt payload={}",
                    this.objectMapper.writeValueAsString(toPayload(context)));
        }
        catch (JsonProcessingException | RuntimeException exception) {
            // Audit logging must never prevent the model request from being sent.
            LOGGER.warn("Unable to serialize LLM prompt audit event", exception);
        }
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ChatModelObservationContext;
    }

    private static Map<String, Object> toPayload(ChatModelObservationContext context) {
        Prompt prompt = context.getRequest();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "agent.llm.prompt");
        payload.put("interactionId", AuditContext.interactionId());
        payload.put("conversationId", AuditContext.conversationId());
        payload.put("provider", context.getOperationMetadata().provider());
        payload.put("model", model(prompt.getOptions()));
        payload.put("options", options(prompt.getOptions()));
        payload.put("messages", messages(prompt.getInstructions()));
        payload.put("toolDefinitions", toolDefinitions(prompt.getOptions()));
        return payload;
    }

    private static String model(ChatOptions options) {
        return options == null ? null : options.getModel();
    }

    private static Map<String, Object> options(ChatOptions options) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (options == null) {
            return values;
        }
        putIfNotNull(values, "frequencyPenalty", options.getFrequencyPenalty());
        putIfNotNull(values, "maxTokens", options.getMaxTokens());
        putIfNotNull(values, "presencePenalty", options.getPresencePenalty());
        putIfNotNull(values, "stopSequences", options.getStopSequences());
        putIfNotNull(values, "temperature", options.getTemperature());
        putIfNotNull(values, "topK", options.getTopK());
        putIfNotNull(values, "topP", options.getTopP());
        if (options instanceof ToolCallingChatOptions toolOptions) {
            putIfNotNull(values, "internalToolExecutionEnabled", toolOptions.getInternalToolExecutionEnabled());
            values.put("toolNames", new ArrayList<>(new TreeSet<>(toolOptions.getToolNames())));
        }
        return values;
    }

    private static List<Map<String, Object>> messages(List<Message> messages) {
        List<Map<String, Object>> result = new ArrayList<>(messages.size());
        for (int index = 0; index < messages.size(); index++) {
            Message message = messages.get(index);
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("index", index);
            value.put("role", message.getMessageType().getValue());
            value.put("content", message.getText());
            if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
                value.put("toolCalls", assistantMessage.getToolCalls());
            }
            if (message instanceof ToolResponseMessage toolResponseMessage) {
                value.put("toolResponses", toolResponseMessage.getResponses());
            }
            result.add(value);
        }
        return result;
    }

    private static List<Map<String, Object>> toolDefinitions(ChatOptions options) {
        if (!(options instanceof ToolCallingChatOptions toolOptions)) {
            return List.of();
        }
        return toolOptions.getToolCallbacks()
                .stream()
                .map(ToolCallback::getToolDefinition)
                .map(LlmPromptObservationHandler::toolDefinition)
                .toList();
    }

    private static Map<String, Object> toolDefinition(ToolDefinition definition) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", definition.name());
        value.put("description", definition.description());
        value.put("inputSchema", definition.inputSchema());
        return value;
    }

    private static void putIfNotNull(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }

}
