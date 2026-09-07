package com.jacolp.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MdDocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 0xFFFF_FFFFL;

    private final MdDocumentRepository repository;

    public MdDocumentService(MdDocumentRepository repository) {
        this.repository = repository;
    }

    public MdDocument upload(MultipartFile file) {
        if (file == null) {
            throw badRequest("file must be provided");
        }

        String fileName = normalizeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw badRequest("only .md files are supported");
        }

        long fileSizeBytes = file.getSize();
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw badRequest("file is too large");
        }

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            return repository.upsert(fileName, content, fileSizeBytes);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "unable to read uploaded file", exception);
        }
    }

    public List<MdDocumentSummary> list() {
        return repository.findAll();
    }

    private static String normalizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw badRequest("file name must not be blank");
        }

        String fileName = originalFileName.replace('\\', '/');
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }

        if (fileName.isBlank() || fileName.indexOf('\0') >= 0) {
            throw badRequest("file name is invalid");
        }
        if (fileName.codePointCount(0, fileName.length()) > 255) {
            throw badRequest("file name must be at most 255 characters");
        }
        return fileName;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
