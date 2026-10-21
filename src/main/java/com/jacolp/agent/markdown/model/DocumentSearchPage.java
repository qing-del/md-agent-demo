package com.jacolp.agent.markdown.model;

import java.util.List;

/** Exact source matches; context is a possibly truncated reading aid, not an edit anchor. */
public record DocumentSearchPage(long documentId, String revision, List<Match> matches, String nextCursor) {
    public DocumentSearchPage {
        matches = List.copyOf(matches);
    }

    public record Match(Integer nodeNumber, List<String> sectionPath, String locationKind,
            String matchedText, String context, boolean contextTruncated) {
        public Match {
            sectionPath = List.copyOf(sectionPath);
        }
    }
}
