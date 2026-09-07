package com.jacolp.service;

import java.util.List;

import com.jacolp.document.MdDocument;
import com.jacolp.document.MdDocumentSummary;
import org.springframework.web.multipart.MultipartFile;

public interface MdDocumentService {

    MdDocument upload(MultipartFile file);

    List<MdDocumentSummary> list();

    MdDocument getById(long id);

    void deleteById(long id);
}
