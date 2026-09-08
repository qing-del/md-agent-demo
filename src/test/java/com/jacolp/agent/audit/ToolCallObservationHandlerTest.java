package com.jacolp.agent.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;

class ToolCallObservationHandlerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger("com.jacolp.agent.audit.tool");

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
    void logsToolArgumentsAndResult() throws Exception {
        ToolCallingObservationContext context = context();
        context.setToolCallResult("{\"content\":\"preview\"}");

        try (AuditContext.Scope ignored = AuditContext.open("conversation-1")) {
            ToolCallObservationHandler handler = new ToolCallObservationHandler(new ObjectMapper());
            handler.onStart(context);
            handler.onStop(context);
        }

        assertEquals(1, this.appender.list.size());
        String message = this.appender.list.get(0).getFormattedMessage();
        assertTrue(message.startsWith("event=agent.tool payload="));
        JsonNode payload = new ObjectMapper().readTree(message.substring(message.indexOf("payload=") + 8));
        assertEquals("conversation-1", payload.get("conversationId").asText());
        assertEquals("get_section_preview", payload.get("toolName").asText());
        assertEquals("{\"documentId\":1}", payload.get("arguments").asText());
        assertEquals("{\"content\":\"preview\"}", payload.get("result").asText());
        assertEquals("SUCCESS", payload.get("status").asText());
        assertTrue(payload.get("durationMs").asLong() >= 0);
    }

    @Test
    void logsObservationErrorAsFailed() throws Exception {
        ToolCallingObservationContext context = context();
        context.setError(new IllegalStateException("section unavailable"));

        ToolCallObservationHandler handler = new ToolCallObservationHandler(new ObjectMapper());
        handler.onStart(context);
        handler.onStop(context);

        JsonNode payload = new ObjectMapper().readTree(this.appender.list.get(0)
                .getFormattedMessage().substring("event=agent.tool payload=".length()));
        assertEquals("FAILED", payload.get("status").asText());
        assertEquals(IllegalStateException.class.getName(), payload.get("errorType").asText());
        assertEquals("section unavailable", payload.get("errorMessage").asText());
    }

    private static ToolCallingObservationContext context() {
        ToolDefinition definition = ToolDefinition.builder()
                .name("get_section_preview")
                .description("preview")
                .inputSchema("{\"type\":\"object\"}")
                .build();
        return ToolCallingObservationContext.builder()
                .toolDefinition(definition)
                .toolCallArguments("{\"documentId\":1}")
                .build();
    }

}
