package com.jacolp.agent.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class LlmPromptObservationHandlerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("com.jacolp.agent.audit.llm");

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        this.logger.setLevel(Level.INFO);
        this.appender.start();
        this.logger.addAppender(this.appender);
    }

    @AfterEach
    void detachAppender() {
        this.logger.detachAppender(this.appender);
        this.appender.stop();
        MDC.clear();
    }

    @Test
    void logsOrderedMessagesToolCallsAndToolResponses() throws Exception {
        ToolCallback callback = mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("get_section_preview")
                .description("preview")
                .inputSchema("{\"type\":\"object\"}")
                .build());

        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .model("qwen-plus")
                .toolCallbacks(List.of(callback))
                .build();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("system"),
                new UserMessage("question"),
                AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-1", "function", "get_section_preview", "{\"documentId\":1}")))
                        .build(),
                ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                "call-1", "get_section_preview", "preview-result")))
                        .build()), options);
        ChatModelObservationContext context = ChatModelObservationContext.builder()
                .prompt(prompt)
                .provider("qwen")
                .build();

        try (AuditContext.Scope ignored = AuditContext.open("conversation-1")) {
            new LlmPromptObservationHandler(new ObjectMapper()).onStart(context);
        }

        assertEquals(1, this.appender.list.size());
        String message = this.appender.list.get(0).getFormattedMessage();
        assertTrue(message.startsWith("event=agent.llm.prompt payload="));
        JsonNode payload = new ObjectMapper().readTree(message.substring(message.indexOf("payload=") + 8));
        assertEquals("conversation-1", payload.get("conversationId").asText());
        assertEquals("system", payload.get("messages").get(0).get("role").asText());
        assertEquals("user", payload.get("messages").get(1).get("role").asText());
        assertEquals("get_section_preview", payload.get("messages").get(2)
                .get("toolCalls").get(0).get("name").asText());
        assertEquals("preview-result", payload.get("messages").get(3)
                .get("toolResponses").get(0).get("responseData").asText());
        assertEquals("get_section_preview", payload.get("toolDefinitions").get(0).get("name").asText());
    }

}
