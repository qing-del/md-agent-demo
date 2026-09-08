package com.jacolp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.agent.audit.LlmPromptObservationHandler;
import com.jacolp.agent.audit.ToolCallObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enables Spring AI observations used by Agent audit logging.
 */
@Configuration(proxyBeanMethods = false)
public class AgentAuditConfiguration {

    @Bean
    LlmPromptObservationHandler llmPromptObservationHandler(ObjectMapper objectMapper) {
        return new LlmPromptObservationHandler(objectMapper);
    }

    @Bean
    ToolCallObservationHandler toolCallObservationHandler(ObjectMapper objectMapper) {
        return new ToolCallObservationHandler(objectMapper);
    }

    @Bean
    ObservationRegistry observationRegistry(
            LlmPromptObservationHandler llmPromptObservationHandler,
            ToolCallObservationHandler toolCallObservationHandler) {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(llmPromptObservationHandler)
                .observationHandler(toolCallObservationHandler);
        return registry;
    }

}
