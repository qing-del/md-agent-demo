package com.jacolp.agent.markdown;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import com.jacolp.agent.markdown.exception.MarkdownCursorException;
import com.jacolp.agent.markdown.exception.SectionNotFoundException;
import com.jacolp.agent.markdown.model.DocumentSearchPage;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.agent.markdown.model.SectionNode;

/** Searches one immutable source snapshot without rendering or normalizing Markdown. */
final class MarkdownSearch {
    private MarkdownSearch() { }

    static DocumentSearchPage search(MarkdownContext snapshot, String query, Integer nodeNumber, String cursor) {
        if (query == null || query.isBlank() || query.codePointCount(0, query.length()) > 256) {
            throw new IllegalArgumentException("query must contain 1 to 256 Unicode code points and not be blank");
        }
        String source = snapshot.getSource();
        int start = 0;
        int end = source.length();
        if (nodeNumber != null) {
            SectionNode node = snapshot.getNodes().get(nodeNumber);
            if (node == null) {
                throw new SectionNotFoundException(snapshot.getDocumentId(), nodeNumber);
            }
            start = node.getHeadingStart();
            end = node.getSectionEnd();
        }
        String binding = snapshot.getDocumentId().value() + ":" + snapshot.getRevision() + ":"
                + Objects.toString(nodeNumber, "all") + ":" + encode(query) + ":";
        int scan = start;
        if (cursor != null) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                if (!decoded.startsWith(binding)) {
                    throw new IllegalArgumentException("cursor parameters or revision changed");
                }
                scan = Integer.parseInt(decoded.substring(binding.length()));
                if (scan < start || scan > end || (scan < source.length() && scan > 0
                        && Character.isLowSurrogate(source.charAt(scan))
                        && Character.isHighSurrogate(source.charAt(scan - 1)))) {
                    throw new IllegalArgumentException("invalid search offset");
                }
            }
            catch (IllegalArgumentException ex) {
                throw new MarkdownCursorException("search cursor is invalid or stale", ex);
            }
        }
        List<DocumentSearchPage.Match> matches = new ArrayList<>();
        int hit;
        while (matches.size() < 10 && (hit = source.indexOf(query, scan)) >= 0 && hit + query.length() <= end) {
            int hitEnd = hit + query.length();
            int left = source.offsetByCodePoints(hit, -Math.min(80, source.codePointCount(start, hit)));
            int right = source.offsetByCodePoints(hitEnd, Math.min(80, source.codePointCount(hitEnd, end)));
            List<SectionNode> path = new ArrayList<>();
            SectionNode owner = null;
            for (SectionNode candidate : snapshot.getNodes().values()) {
                if (candidate.getHeadingStart() > hit) {
                    break;
                }
                while (!path.isEmpty() && path.get(path.size() - 1).getLevel() >= candidate.getLevel()) {
                    path.remove(path.size() - 1);
                }
                path.add(candidate);
                owner = candidate;
            }
            String kind = owner == null ? "preamble" : hit < owner.getBodyStart() ? "heading" : "body";
            matches.add(new DocumentSearchPage.Match(owner == null ? null : owner.getNumber(),
                    path.stream().map(SectionNode::getTitle).toList(), kind, source.substring(hit, hitEnd),
                    source.substring(left, right), left > start || right < end));
            // Include overlapping matches, but never begin inside a supplementary character.
            scan = source.offsetByCodePoints(hit, 1);
        }
        int next = source.indexOf(query, scan);
        String nextCursor = next >= 0 && next + query.length() <= end ? encode(binding + scan) : null;
        return new DocumentSearchPage(snapshot.getDocumentId().value(), snapshot.getRevision(), matches, nextCursor);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
