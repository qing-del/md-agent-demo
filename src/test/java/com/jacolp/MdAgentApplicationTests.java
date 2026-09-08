package com.jacolp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "QWEN_API_KEY=test-key")
class MdAgentApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1",
                environment.getProperty("spring.ai.openai.base-url"));
        assertEquals("test-key", environment.getProperty("spring.ai.openai.api-key"));
        assertEquals("qwen3.8-flash",
                environment.getProperty("spring.ai.openai.chat.options.model"));
    }

}
