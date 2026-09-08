package com.jacolp.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.jacolp.mapper.MdDocumentMapper;
import com.jacolp.agent.markdown.MarkdownContextProvider;
import com.jacolp.pojo.dto.MdDocumentDTO;
import com.jacolp.pojo.entity.MdDocument;
import com.jacolp.pojo.vo.MdDocumentSummaryVO;
import com.jacolp.pojo.vo.MdDocumentVO;
import com.jacolp.service.MdDocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Markdown 文档业务服务的默认实现。
 */
@Service
public class MdDocumentServiceImpl implements MdDocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 0xFFFF_FFFFL;

    private final MdDocumentMapper mapper;

    private final MarkdownContextProvider contextProvider;

    /**
     * 创建 Markdown 文档服务。
     *
     * @param mapper Markdown 文档数据库访问对象
     */
    public MdDocumentServiceImpl(MdDocumentMapper mapper) {
        this(mapper, null);
    }

    /**
     * 创建 Markdown 文档服务并绑定上下文刷新器。
     *
     * @param mapper Markdown 文档数据库访问对象
     * @param contextProvider Markdown 上下文提供器
     */
    @Autowired
    public MdDocumentServiceImpl(MdDocumentMapper mapper, MarkdownContextProvider contextProvider) {
        this.mapper = mapper;
        this.contextProvider = contextProvider;
    }

    /**
     * 校验并保存上传的 Markdown 文件，然后返回数据库中的最新记录。
     *
     * @param file 待上传的 Markdown 文件
     * @return 保存后的文档详情
     * @throws ResponseStatusException 文件参数、文件名或文件大小不符合要求时抛出
     */
    @Override
    public MdDocumentVO upload(MultipartFile file) {
        // 先校验文件对象，避免在读取文件属性时触发空指针。
        if (file == null) {
            throw badRequest("file must be provided");
        }

        String fileName = normalizeFileName(file.getOriginalFilename());
        // 业务只接收 Markdown 文件，扩展名校验使用不区分大小写的比较。
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw badRequest("only .md files are supported");
        }

        long fileSizeBytes = file.getSize();
        // 数据库字段使用无符号 32 位范围，这里在读取正文前拒绝超大文件。
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw badRequest("file is too large");
        }

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            MdDocumentDTO documentDTO = new MdDocumentDTO(fileName, content, fileSizeBytes);
            MdDocument document = toEntity(documentDTO);
            mapper.upsert(document);
            // 写入后重新查询，确保返回值包含数据库生成的 ID 和时间字段。
            MdDocument saved = Optional.ofNullable(mapper.selectByFileName(fileName))
                    .orElseThrow(() -> new IllegalStateException(
                            "document was not saved: " + fileName));
            if (this.contextProvider != null) {
                this.contextProvider.refresh(saved);
            }
            return MdDocumentVO.from(saved);
        } catch (IOException exception) {
            // 文件读取失败属于请求输入无法处理，转换为客户端可识别的 400 响应。
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "unable to read uploaded file", exception);
        }
    }

    /**
     * 查询所有文档并转换为摘要视图。
     *
     * @return 文档摘要列表
     */
    @Override
    public List<MdDocumentSummaryVO> list() {
        return mapper.selectAll().stream().map(MdDocumentSummaryVO::from).toList();
    }

    /**
     * 查询指定文档并转换为详情视图。
     *
     * @param id 文档 ID
     * @return 文档详情
     * @throws ResponseStatusException 文档不存在时抛出 404 异常
     */
    @Override
    public MdDocumentVO getById(long id) {
        // 查询不到记录时返回 404，而不是将空对象继续向接口层传递。
        return MdDocumentVO.from(Optional.ofNullable(mapper.selectById(id))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "document not found: " + id)));
    }

    /**
     * 删除指定文档，并确认目标记录确实存在。
     *
     * @param id 要删除的文档 ID
     * @throws ResponseStatusException 未删除任何记录时抛出 404 异常
     */
    @Override
    public void deleteById(long id) {
        // 受影响行数为 0 表示目标文档不存在，避免把删除请求误报为成功。
        if (mapper.deleteById(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found: " + id);
        }
        if (this.contextProvider != null) {
            this.contextProvider.remove(id);
        }
    }

    /**
     * 清理上传文件名中的路径部分并执行文件名安全校验。
     *
     * @param originalFileName Multipart 请求提供的原始文件名
     * @return 可用于存储和查询的文件名
     * @throws ResponseStatusException 文件名为空或不合法时抛出 400 异常
     */
    private static String normalizeFileName(String originalFileName) {
        // 文件名不能缺失，否则无法建立文档的稳定唯一标识。
        if (originalFileName == null || originalFileName.isBlank()) {
            throw badRequest("file name must not be blank");
        }

        String fileName = originalFileName.replace('\\', '/');
        int lastSlash = fileName.lastIndexOf('/');
        // 只保留文件名本身，避免把客户端传入的目录路径写入存储层。
        if (lastSlash >= 0) {
            fileName = fileName.substring(lastSlash + 1);
        }

        // 拒绝空名称、NUL 字符和超长名称，保证文件名可以安全存储和传输。
        if (fileName.isBlank() || fileName.indexOf('\0') >= 0) {
            throw badRequest("file name is invalid");
        }
        if (fileName.codePointCount(0, fileName.length()) > 255) {
            throw badRequest("file name must be at most 255 characters");
        }
        return fileName;
    }

    /**
     * 创建表示客户端输入错误的 400 异常。
     *
     * @param message 错误信息
     * @return HTTP 400 状态异常
     */
    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 将文档请求对象转换为数据库实体。
     *
     * @param documentDTO 文档请求对象
     * @return 待持久化的文档实体
     */
    private static MdDocument toEntity(MdDocumentDTO documentDTO) {
        MdDocument document = new MdDocument();
        document.setFileName(documentDTO.getFileName());
        document.setContent(documentDTO.getContent());
        document.setFileSizeBytes(documentDTO.getFileSizeBytes());
        return document;
    }
}
