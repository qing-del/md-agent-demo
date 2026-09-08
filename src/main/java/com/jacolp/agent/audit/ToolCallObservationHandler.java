package com.jacolp.agent.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;

/**
 * Logs the arguments and result of each Spring AI tool execution.
 */
public final class ToolCallObservationHandler implements ObservationHandler<ToolCallingObservationContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger("com.jacolp.agent.audit.tool");

    private final ObjectMapper objectMapper;

    private final ConcurrentMap<ToolCallingObservationContext, Long> startTimes = new ConcurrentHashMap<>();

    public ToolCallObservationHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onStart(ToolCallingObservationContext context) {
        this.startTimes.put(context, System.nanoTime());
    }

    @Override
    public void onStop(ToolCallingObservationContext context) {
        if (!LOGGER.isInfoEnabled()) {
            this.startTimes.remove(context);
            return;
        }

        try {
            LOGGER.info("event=agent.tool payload={}",
                    this.objectMapper.writeValueAsString(toPayload(context, durationMillis(context))));
        }
        catch (JsonProcessingException | RuntimeException exception) {
            // Audit logging must never change the tool result or model flow.
            LOGGER.warn("Unable to serialize tool audit event", exception);
        }
        finally {
            this.startTimes.remove(context);
        }
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ToolCallingObservationContext;
    }

    private Map<String, Object> toPayload(ToolCallingObservationContext context, long durationMillis) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "agent.tool");
        payload.put("interactionId", AuditContext.interactionId());
        payload.put("conversationId", AuditContext.conversationId());
        payload.put("toolName", context.getToolDefinition().name());
        payload.put("arguments", context.getToolCallArguments());
        payload.put("result", context.getToolCallResult());
        payload.put("status", context.getError() == null ? "SUCCESS" : "FAILED");
        payload.put("durationMs", durationMillis);
        if (context.getError() != null) {
            payload.put("errorType", context.getError().getClass().getName());
            payload.put("errorMessage", context.getError().getMessage());
        }
        return payload;
    }

    private long durationMillis(ToolCallingObservationContext context) {
        Long startedAt = this.startTimes.remove(context);
        if (startedAt == null) {
            return 0L;
        }
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

}
