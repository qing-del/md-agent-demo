package com.jacolp.document;

import java.time.LocalDateTime;

public record MdDocument(
        long id,
        String fileName,
        String content,
        long fileSizeBytes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
