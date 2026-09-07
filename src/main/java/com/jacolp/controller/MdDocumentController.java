package com.jacolp.controller;

import java.util.List;

import com.jacolp.pojo.vo.MdDocumentSummaryVO;
import com.jacolp.pojo.vo.MdDocumentVO;
import com.jacolp.service.MdDocumentService;
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
    public ResponseEntity<MdDocumentVO> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.upload(file));
    }

    @GetMapping
    public List<MdDocumentSummaryVO> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public MdDocumentVO getById(@PathVariable("id") long id) {
        return service.getById(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
