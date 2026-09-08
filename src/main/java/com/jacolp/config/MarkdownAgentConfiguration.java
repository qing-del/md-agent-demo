package com.jacolp.config;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.MarkdownStore;
import com.jacolp.agent.markdown.operation.OperationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置 Markdown SDK 及其待确认操作管理器。
 */
@Configuration
public class MarkdownAgentConfiguration {

    /**
     * 创建应用级 Markdown 上下文管理器。
     *
     * @param store Markdown 快照持久化适配器
     * @return Markdown 上下文管理器
     */
    @Bean
    MarkdownManager markdownManager(MarkdownStore store) {
        return new MarkdownManager(store);
    }

    /**
     * 创建应用级替换提案管理器。
     *
     * @param markdownManager Markdown 上下文管理器
     * @return 替换提案管理器
     */
    @Bean
    OperationManager markdownOperationManager(MarkdownManager markdownManager) {
        return new OperationManager(markdownManager);
    }
}
