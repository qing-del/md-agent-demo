package com.jacolp.mapper;

import java.util.List;

import com.jacolp.pojo.entity.MdDocument;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 提供 Markdown 文档的数据库访问方法。
 */
@Mapper
public interface MdDocumentMapper {

    /**
     * 按文件名新增或更新 Markdown 文档内容。
     *
     * @param document 待保存的文档
     * @return 受影响的行数
     */
    @Insert("""
            INSERT INTO md_documents (file_name, content, file_size_bytes)
            VALUES (#{fileName}, #{content}, #{fileSizeBytes})
            ON DUPLICATE KEY UPDATE
                content = VALUES(content),
                file_size_bytes = VALUES(file_size_bytes)
            """)
    int upsert(MdDocument document);

    /**
     * 根据文件名查询文档详情。
     *
     * @param fileName 文档文件名
     * @return 找到的文档；不存在时返回 {@code null}
     */
    @Select("""
            SELECT id, file_name, content, file_size_bytes, created_at, updated_at
            FROM md_documents
            WHERE file_name = #{fileName}
            """)
    @Results(id = "mdDocumentResultMap", value = {
        @Result(property = "id", column = "id", id = true),
        @Result(property = "fileName", column = "file_name"),
        @Result(property = "content", column = "content"),
        @Result(property = "fileSizeBytes", column = "file_size_bytes"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    MdDocument selectByFileName(@Param("fileName") String fileName);

    @Select("""
            SELECT id, file_name, file_size_bytes, created_at, updated_at
            FROM md_documents
            ORDER BY updated_at DESC, id DESC
            """)
    @ResultMap("mdDocumentResultMap")
    List<MdDocument> selectAll();

    /**
     * 根据主键查询文档详情。
     *
     * @param id 文档 ID
     * @return 找到的文档；不存在时返回 {@code null}
     */
    @Select("""
            SELECT id, file_name, content, file_size_bytes, created_at, updated_at
            FROM md_documents
            WHERE id = #{id}
            """)
    @ResultMap("mdDocumentResultMap")
    MdDocument selectById(@Param("id") long id);

    /**
     * 根据主键更新 Markdown 正文及其 UTF-8 字节数。
     *
     * @param id 文档主键
     * @param content 新的 Markdown 正文
     * @param fileSizeBytes 新正文的 UTF-8 字节数
     * @return 受影响的行数
     */
    @Update("""
            UPDATE md_documents
            SET content = #{content},
                file_size_bytes = #{fileSizeBytes},
                updated_at = CURRENT_TIMESTAMP(6)
            WHERE id = #{id}
            """)
    int updateContentById(
            @Param("id") long id,
            @Param("content") String content,
            @Param("fileSizeBytes") long fileSizeBytes);

    /**
     * 根据主键删除文档。
     *
     * @param id 要删除的文档 ID
     * @return 受影响的行数
     */
    @Delete("DELETE FROM md_documents WHERE id = #{id}")
    int deleteById(@Param("id") long id);
}
