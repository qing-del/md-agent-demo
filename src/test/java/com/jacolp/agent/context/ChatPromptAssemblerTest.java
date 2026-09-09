package com.jacolp.agent.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import com.jacolp.pojo.dto.ChatMessageDTO;
import com.jacolp.pojo.dto.SelectionDTO;
import org.junit.jupiter.api.Test;

class ChatPromptAssemblerTest {

    @Test
    void keepsPlainContentUnchangedWhenThereIsNoAdditionalContext() {
        ChatMessageDTO message = new ChatMessageDTO("hello", List.of(), List.of());

        assertEquals("hello", new ChatPromptAssembler().assemble(message));
    }

    @Test
    void formatsDocumentIdsAndMultipleSelectionsAsXml() {
        ChatMessageDTO message = new ChatMessageDTO(
                "inspect",
                List.of(1L, 2L),
                List.of(
                        new SelectionDTO(1L, "a & <b>", "#Java | ##安装"),
                        new SelectionDTO(2L, "quote \" and apostrophe '", "#Spring Boot | ##快速开始")));

        assertEquals("""
                inspect

                <documentIds>
                  <documentId>1</documentId>
                  <documentId>2</documentId>
                </documentIds>

                <selections>
                  <selection>
                    <documentId>1</documentId>
                    <sectionText>#Java | ##安装</sectionText>
                    <originalText>a &amp; &lt;b&gt;</originalText>
                  </selection>
                  <selection>
                    <documentId>2</documentId>
                    <sectionText>#Spring Boot | ##快速开始</sectionText>
                    <originalText>quote &quot; and apostrophe &apos;</originalText>
                  </selection>
                </selections>""", new ChatPromptAssembler().assemble(message));
    }
}
