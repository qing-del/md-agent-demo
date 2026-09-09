package com.jacolp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import com.jacolp.pojo.dto.MdDocumentDraftDTO;
import com.jacolp.pojo.vo.MdDocumentSyncVO;
import com.jacolp.service.MdDocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MdDocumentControllerTest {

    @Mock
    private MdDocumentService service;

    @Test
    void syncDraftReturnsTheServiceSyncResult() {
        String content = "# Java\n\nJDK";
        MdDocumentSyncVO expected = new MdDocumentSyncVO(
                7L, "SYNCED", LocalDateTime.of(2026, 9, 9, 12, 0));
        when(this.service.syncContent(7L, content)).thenReturn(expected);

        ResponseEntity<MdDocumentSyncVO> actual = new MdDocumentController(this.service)
                .syncDraft(7L, new MdDocumentDraftDTO(content));

        assertEquals(HttpStatus.OK, actual.getStatusCode());
        assertEquals(expected, actual.getBody());
        verify(this.service).syncContent(7L, content);
    }

    @Test
    void syncDraftRejectsAMissingRequestBody() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> new MdDocumentController(this.service).syncDraft(7L, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}
