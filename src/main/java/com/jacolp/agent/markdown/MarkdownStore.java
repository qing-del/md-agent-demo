package com.jacolp.agent.markdown;

import com.jacolp.agent.markdown.model.MarkdownContext;

/**
 * Persistence boundary used by the framework-neutral Markdown SDK.
 */
@FunctionalInterface
public interface MarkdownStore {

    /**
     * Persists a newly created Markdown snapshot.
     *
     * <p>The application adapter is responsible for resolving the context UUID to its
     * existing document record.</p>
     *
     * @param context snapshot to persist
     */
    void save(MarkdownContext context);
}
