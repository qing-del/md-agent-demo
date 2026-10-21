package com.jacolp.agent.markdown;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.jacolp.agent.markdown.exception.MarkdownReplacementException;
import com.jacolp.agent.markdown.exception.MarkdownRevisionException;
import com.jacolp.agent.markdown.exception.SectionNotFoundException;
import com.jacolp.agent.markdown.model.DocumentEdit;
import com.jacolp.agent.markdown.model.EditDiff;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.agent.markdown.model.SectionNode;

/** Resolves every edit against the same immutable source, then constructs one candidate. */
final class MarkdownEdits {
    private MarkdownEdits() { }

    static Plan plan(MarkdownContext snapshot, String baseRevision, List<DocumentEdit> edits) {
        if (baseRevision == null || !baseRevision.equals(snapshot.getRevision())) {
            throw new MarkdownRevisionException();
        }
        if (edits == null || edits.isEmpty()) {
            throw invalid("edits must not be empty");
        }
        List<Resolved> resolved = new ArrayList<>();
        String source = snapshot.getSource();
        for (DocumentEdit edit : edits) {
            if (edit == null || edit.newText() == null) {
                throw invalid("edit and newText must not be null");
            }
            SectionNode node = snapshot.getNodes().get(edit.nodeNumber());
            if (node == null) {
                throw new SectionNotFoundException(snapshot.getDocumentId(), edit.nodeNumber());
            }
            String body = source.substring(node.getBodyStart(), node.getDirectEnd());
            int start;
            int end;
            if ("replace".equals(edit.type())) {
                if (edit.position() != null || edit.anchorText() != null) {
                    throw invalid("replace does not accept position or anchorText");
                }
                start = node.getBodyStart() + uniqueMatch(body, edit.originalText());
                end = start + edit.originalText().length();
                // Preserve the existing single-replacement deletion behavior.
                if (edit.newText().isEmpty()) {
                    end = MarkdownManager.endOfDeletedLine(source, start, end);
                }
            }
            else if ("insert".equals(edit.type())) {
                if (edit.newText().isEmpty() || edit.originalText() != null) {
                    throw invalid("insert requires nonempty newText and does not accept originalText");
                }
                String position = edit.position();
                if (position == null) {
                    throw invalid("insert position is required");
                }
                start = switch (position) {
                    case "body_start" -> node.getBodyStart();
                    case "body_end" -> node.getDirectEnd();
                    case "before" -> node.getBodyStart() + uniqueMatch(body, edit.anchorText());
                    case "after" -> node.getBodyStart() + uniqueMatch(body, edit.anchorText()) + edit.anchorText().length();
                    default -> throw invalid("unknown insert position");
                };
                if ((position.equals("body_start") || position.equals("body_end")) && edit.anchorText() != null) {
                    throw invalid("body_start/body_end do not accept anchorText");
                }
                end = start;
            }
            else {
                throw invalid("unknown edit type");
            }
            resolved.add(new Resolved(start, end, edit.newText(), new EditDiff(edit.type(), edit.nodeNumber(),
                    sectionPath(snapshot, node), edit.position(), edit.anchorText(), source.substring(start, end), edit.newText())));
        }
        // Original order is retained in the preview; execution order only uses source coordinates.
        List<EditDiff> diffs = resolved.stream().map(Resolved::diff).toList();
        resolved.sort(Comparator.comparingInt(Resolved::start));
        for (int i = 1; i < resolved.size(); i++) {
            Resolved left = resolved.get(i - 1);
            Resolved right = resolved.get(i);
            boolean insertion = left.start == left.end || right.start == right.end;
            if (right.start < left.end || (insertion && right.start <= left.end)) {
                throw invalid("edits overlap or share an insertion boundary");
            }
        }
        StringBuilder updated = new StringBuilder(source);
        for (int i = resolved.size() - 1; i >= 0; i--) {
            Resolved edit = resolved.get(i);
            updated.replace(edit.start, edit.end, edit.newText);
        }
        return new Plan(updated.toString(), diffs);
    }

    private static int uniqueMatch(String body, String text) {
        if (text == null || text.isEmpty()) {
            throw invalid("originalText/anchorText must not be empty");
        }
        int start = body.indexOf(text);
        if (start < 0 || body.indexOf(text, start + 1) >= 0) {
            throw invalid("originalText/anchorText must match exactly once in the direct body");
        }
        return start;
    }

    static String sectionPath(MarkdownContext snapshot, SectionNode target) {
        List<SectionNode> path = new ArrayList<>();
        for (SectionNode node : snapshot.getNodes().values()) {
            while (!path.isEmpty() && path.get(path.size() - 1).getLevel() >= node.getLevel()) {
                path.remove(path.size() - 1);
            }
            path.add(node);
            if (node.getNumber() == target.getNumber()) {
                return path.stream().map(n -> snapshot.getSource().substring(n.getHeadingStart(), n.getBodyStart())
                        .replaceFirst("[\\r\\n]+$", "")).reduce((a, b) -> a + " | " + b).orElseThrow();
            }
        }
        throw new SectionNotFoundException(snapshot.getDocumentId(), target.getNumber());
    }

    private static MarkdownReplacementException invalid(String message) {
        return new MarkdownReplacementException(message);
    }

    record Plan(String content, List<EditDiff> diffs) { }
    private record Resolved(int start, int end, String newText, EditDiff diff) { }
}
