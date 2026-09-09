package com.jacolp.service;

import java.util.List;

import com.jacolp.pojo.vo.MdDocumentSummaryVO;
import com.jacolp.pojo.vo.MdDocumentSyncVO;
import com.jacolp.pojo.vo.MdDocumentVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 定义 Markdown 文档的业务操作。
 */
public interface MdDocumentService {

    /**
     * 上传并保存 Markdown 文档。
     *
     * @param file 待上传的 Markdown 文件
     * @return 保存后的文档详情
     */
    MdDocumentVO upload(MultipartFile file);

    /**
     * 查询所有文档摘要。
     *
     * @return 文档摘要列表
     */
    List<MdDocumentSummaryVO> list();

    /**
     * 查询指定文档详情。
     *
     * @param id 文档 ID
     * @return 文档详情
     */
    MdDocumentVO getById(long id);

    /**
     * 保存指定文档的完整 Markdown 草稿。
     *
     * @param id 文档 ID
     * @param content 前端提交的完整 Markdown 正文
     * @return 同步结果
     */
    MdDocumentSyncVO syncContent(long id, String content);

    /**
     * 删除指定文档。
     *
     * @param id 文档 ID
     */
    void deleteById(long id);
}
