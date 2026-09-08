package com.jacolp.config;

import com.jacolp.agent.context.ChatContextManager;
import com.jacolp.agent.tool.MarkdownAgentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置应用使用的聊天客户端及其默认顾问。
 */
@Configuration
public class ChatClientConfiguration {

    /**
     * 创建带有聊天记忆顾问的 {@link ChatClient}。
     *
     * @param chatClientBuilder Spring AI 提供的聊天客户端构建器
     * @param chatContextManager 应用的聊天上下文管理器
     * @return 配置完成的聊天客户端
     */
    @Bean
    ChatClient chatClient(
            ChatClient.Builder chatClientBuilder,
            ChatContextManager chatContextManager,
            MarkdownAgentTools markdownAgentTools) {
        return chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatContextManager).build())
                .defaultTools(markdownAgentTools)
                .build();
    }
}
