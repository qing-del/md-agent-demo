package com.jacolp.agent.markdown.operation;

import java.util.List;
import java.util.Objects;

import com.jacolp.agent.markdown.model.DocumentEdit;
import com.jacolp.agent.markdown.model.EditDiff;

/** Immutable base-version metadata shared by all proposal kinds. */
public record OperationDetails(String kind, String baseRevision, String sectionText,
        List<DocumentEdit> edits, List<EditDiff> diffs) {
    public OperationDetails {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(baseRevision);
        Objects.requireNonNull(sectionText);
        edits = List.copyOf(edits);
        diffs = List.copyOf(diffs);
    }
}
