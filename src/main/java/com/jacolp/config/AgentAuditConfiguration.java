package com.jacolp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.agent.audit.LlmPromptObservationHandler;
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
    ObservationRegistry observationRegistry(LlmPromptObservationHandler llmPromptObservationHandler) {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(llmPromptObservationHandler);
        return registry;
    }

}
