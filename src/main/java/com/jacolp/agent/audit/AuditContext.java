package com.jacolp.agent.audit;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;

/**
 * Maintains identifiers used to correlate one chat request's audit records.
 */
public final class AuditContext {

    public static final String INTERACTION_ID = "interactionId";

    public static final String CONVERSATION_ID = "conversationId";

    private AuditContext() {
    }

    /**
     * Opens an audit scope and restores the previous MDC values when it closes.
     *
     * @param conversationId canonical conversation identifier
     * @return scope that restores the previous MDC state
     */
    public static Scope open(String conversationId) {
        Objects.requireNonNull(conversationId, "conversationId cannot be null");
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        MDC.put(INTERACTION_ID, UUID.randomUUID().toString());
        MDC.put(CONVERSATION_ID, conversationId);
        return () -> {
            if (previousContext == null) {
                MDC.clear();
            }
            else {
                MDC.setContextMap(previousContext);
            }
        };
    }

    public static String interactionId() {
        return MDC.get(INTERACTION_ID);
    }

    public static String conversationId() {
        return MDC.get(CONVERSATION_ID);
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        @Override
        void close();

    }

}
