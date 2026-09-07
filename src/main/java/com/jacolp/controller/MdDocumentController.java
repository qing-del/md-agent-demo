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

/**
 * 提供 Markdown 文档的上传、查询和删除接口。
 */
@RestController
@RequestMapping("/api/documents")
public class MdDocumentController {

    private final MdDocumentService service;

    /**
     * 创建 Markdown 文档控制器。
     *
     * @param service Markdown 文档业务服务
     */
    public MdDocumentController(MdDocumentService service) {
        this.service = service;
    }

    /**
     * 上传并保存一个 Markdown 文档。
     *
     * @param file 待上传的 Markdown 文件
     * @return 保存后的文档详情
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MdDocumentVO> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.upload(file));
    }

    /**
     * 查询所有已保存文档的摘要信息。
     *
     * @return 文档摘要列表
     */
    @GetMapping
    public List<MdDocumentSummaryVO> list() {
        return service.list();
    }

    /**
     * 根据文档 ID 查询文档详情。
     *
     * @param id 文档 ID
     * @return 文档详情
     */
    @GetMapping("/{id}")
    public MdDocumentVO getById(@PathVariable("id") long id) {
        return service.getById(id);
    }

    /**
     * 删除指定文档。
     *
     * @param id 要删除的文档 ID
     * @return 无内容的 HTTP 响应
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
