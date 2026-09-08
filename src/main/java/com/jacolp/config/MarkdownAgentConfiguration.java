package com.jacolp.config;

import com.jacolp.agent.markdown.MarkdownManager;
import com.jacolp.agent.markdown.MarkdownStore;
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
}
