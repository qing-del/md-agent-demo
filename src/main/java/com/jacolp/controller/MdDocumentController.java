package com.jacolp.controller;

import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.document.MdDocument;
import com.jacolp.document.MdDocumentService;
import com.jacolp.document.MdDocumentSummary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping
    public List<DocumentSummaryResponse> list() {
        return service.list().stream().map(DocumentSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse getById(@PathVariable("id") long id) {
        return DocumentResponse.from(service.getById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
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

    public record DocumentSummaryResponse(
            long id,
            String fileName,
            long fileSizeBytes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        private static DocumentSummaryResponse from(MdDocumentSummary document) {
            return new DocumentSummaryResponse(
                    document.id(),
                    document.fileName(),
                    document.fileSizeBytes(),
                    document.createdAt(),
                    document.updatedAt());
        }
    }
}
