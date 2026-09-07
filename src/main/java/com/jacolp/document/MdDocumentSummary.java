package com.jacolp.document;

import java.time.LocalDateTime;

public record MdDocumentSummary(
        long id,
        String fileName,
        long fileSizeBytes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
