package com.jacolp.service;

import java.util.List;

import com.jacolp.pojo.vo.MdDocumentSummaryVO;
import com.jacolp.pojo.vo.MdDocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface MdDocumentService {

    MdDocumentVO upload(MultipartFile file);

    List<MdDocumentSummaryVO> list();

    MdDocumentVO getById(long id);

    void deleteById(long id);
}
