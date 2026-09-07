package com.jacolp.controller;

import java.time.LocalDateTime;

import com.jacolp.document.MdDocument;
import com.jacolp.document.MdDocumentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class MdDocumentController {

    private final MdDocumentService service;

    public MdDocumentController(MdDocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(DocumentResponse.from(service.upload(file)));
    }

    public record DocumentResponse(
            long id,
            String fileName,
            String content,
            long fileSizeBytes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        private static DocumentResponse from(MdDocument document) {
            return new DocumentResponse(
                    document.id(),
                    document.fileName(),
                    document.content(),
                    document.fileSizeBytes(),
                    document.createdAt(),
                    document.updatedAt());
        }
    }
}
