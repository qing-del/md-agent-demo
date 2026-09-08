package com.jacolp.agent.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AuditContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void scopeSetsAndRestoresCorrelationValues() {
        MDC.put("existing", "value");

        try (AuditContext.Scope ignored = AuditContext.open("conversation-1")) {
            assertEquals("conversation-1", AuditContext.conversationId());
            assertEquals("conversation-1", MDC.get(AuditContext.CONVERSATION_ID));
            org.junit.jupiter.api.Assertions.assertNotNull(AuditContext.interactionId());
        }

        assertNull(AuditContext.conversationId());
        assertNull(AuditContext.interactionId());
        assertEquals("value", MDC.get("existing"));
    }

}
