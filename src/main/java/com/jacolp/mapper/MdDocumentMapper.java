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

@Mapper
public interface MdDocumentMapper {

    @Insert("""
            INSERT INTO md_documents (file_name, content, file_size_bytes)
            VALUES (#{fileName}, #{content}, #{fileSizeBytes})
            ON DUPLICATE KEY UPDATE
                content = VALUES(content),
                file_size_bytes = VALUES(file_size_bytes)
            """)
    int upsert(MdDocument document);

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

    @Select("""
            SELECT id, file_name, content, file_size_bytes, created_at, updated_at
            FROM md_documents
            WHERE id = #{id}
            """)
    @ResultMap("mdDocumentResultMap")
    MdDocument selectById(@Param("id") long id);

    @Delete("DELETE FROM md_documents WHERE id = #{id}")
    int deleteById(@Param("id") long id);
}
