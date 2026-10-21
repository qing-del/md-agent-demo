package com.jacolp.agent.markdown.model;

import java.util.List;

/** Read-only trial application, including the complete candidate source for the editor. */
public record DocumentEditPreview(long documentId, String baseRevision, String revision,
        String content, List<EditDiff> edits) {
    public DocumentEditPreview {
        edits = List.copyOf(edits);
    }
}
