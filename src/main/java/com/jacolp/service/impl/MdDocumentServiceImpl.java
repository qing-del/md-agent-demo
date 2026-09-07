package com.jacolp.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.jacolp.mapper.MdDocumentMapper;
import com.jacolp.pojo.dto.MdDocumentDTO;
import com.jacolp.pojo.entity.MdDocument;
import com.jacolp.pojo.vo.MdDocumentSummaryVO;
import com.jacolp.pojo.vo.MdDocumentVO;
import com.jacolp.service.MdDocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MdDocumentServiceImpl implements MdDocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 0xFFFF_FFFFL;

    private final MdDocumentMapper mapper;

    public MdDocumentServiceImpl(MdDocumentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public MdDocumentVO upload(MultipartFile file) {
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
            MdDocumentDTO documentDTO = new MdDocumentDTO(fileName, content, fileSizeBytes);
            MdDocument document = toEntity(documentDTO);
            mapper.upsert(document);
            return MdDocumentVO.from(Optional.ofNullable(mapper.selectByFileName(fileName))
                    .orElseThrow(() -> new IllegalStateException(
                            "document was not saved: " + fileName)));
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "unable to read uploaded file", exception);
        }
    }

    @Override
    public List<MdDocumentSummaryVO> list() {
        return mapper.selectAll().stream().map(MdDocumentSummaryVO::from).toList();
    }

    @Override
    public MdDocumentVO getById(long id) {
        return MdDocumentVO.from(Optional.ofNullable(mapper.selectById(id))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "document not found: " + id)));
    }

    @Override
    public void deleteById(long id) {
        if (mapper.deleteById(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found: " + id);
        }
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

    private static MdDocument toEntity(MdDocumentDTO documentDTO) {
        MdDocument document = new MdDocument();
        document.setFileName(documentDTO.getFileName());
        document.setContent(documentDTO.getContent());
        document.setFileSizeBytes(documentDTO.getFileSizeBytes());
        return document;
    }
}
