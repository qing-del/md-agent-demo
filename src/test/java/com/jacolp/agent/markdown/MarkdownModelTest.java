package com.jacolp.agent.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jacolp.agent.markdown.model.DocumentId;
import com.jacolp.agent.markdown.model.MarkdownContext;
import com.jacolp.agent.markdown.model.SectionNode;
import com.jacolp.agent.markdown.model.SectionPage;
import org.junit.jupiter.api.Test;

class MarkdownModelTest {

    @Test
    void documentIdRequiresPositiveValueAndUsesValueEquality() {
        assertEquals(new DocumentId(7L), new DocumentId(7L));
        assertThrows(IllegalArgumentException.class, () -> new DocumentId(0L));
        assertThrows(IllegalArgumentException.class, () -> new DocumentId(-1L));
    }

    @Test
    void contextDefensivelyCopiesAndExposesImmutableCollections() {
        SectionNode node = new SectionNode(1, 1, "Java", List.of(), 0, 8, 8, 8);
        List<Integer> roots = new ArrayList<>(List.of(1));
        Map<Integer, SectionNode> nodes = new HashMap<>(Map.of(1, node));
        MarkdownContext context = new MarkdownContext(
                new DocumentId(1L), "revision", "# Java\n", roots, nodes);

        roots.clear();
        nodes.clear();

        assertEquals(List.of(1), context.getRootNodeIds());
        assertEquals(Map.of(1, node), context.getNodes());
        assertEquals(new DocumentId(1L), context.getDocumentId());
        assertThrows(UnsupportedOperationException.class, () -> context.getRootNodeIds().add(2));
        assertThrows(UnsupportedOperationException.class, () -> context.getNodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> node.getChildren().add(2));
    }

    @Test
    void sectionPageRequiresCursorOnlyWhenMoreContentExists() {
        assertEquals("body", new SectionPage("body", false, null).getContent());
        assertThrows(IllegalArgumentException.class, () -> new SectionPage("body", false, "cursor"));
        assertThrows(IllegalArgumentException.class, () -> new SectionPage("body", true, null));
    }
}
