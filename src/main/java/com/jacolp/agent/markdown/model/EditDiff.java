package com.jacolp.agent.markdown.model;

/** A source-faithful per-edit preview; the section path is captured at proposal creation. */
public record EditDiff(String type, int nodeNumber, String sectionText, String position,
        String anchorText, String originalText, String newText) { }
