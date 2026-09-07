package com.jacolp.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.jacolp.document.MdDocument;
import com.jacolp.document.MdDocumentSummary;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
    int upsert(
            @Param("fileName") String fileName,
            @Param("content") String content,
            @Param("fileSizeBytes") long fileSizeBytes);

    @Select("""
            SELECT id, file_name, content, file_size_bytes, created_at, updated_at
            FROM md_documents
            WHERE file_name = #{fileName}
            """)
    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class, id = true),
        @Arg(column = "file_name", javaType = String.class),
        @Arg(column = "content", javaType = String.class),
        @Arg(column = "file_size_bytes", javaType = long.class),
        @Arg(column = "created_at", javaType = LocalDateTime.class),
        @Arg(column = "updated_at", javaType = LocalDateTime.class)
    })
    MdDocument selectByFileName(@Param("fileName") String fileName);

    @Select("""
            SELECT id, file_name, file_size_bytes, created_at, updated_at
            FROM md_documents
            ORDER BY updated_at DESC, id DESC
            """)
    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class, id = true),
        @Arg(column = "file_name", javaType = String.class),
        @Arg(column = "file_size_bytes", javaType = long.class),
        @Arg(column = "created_at", javaType = LocalDateTime.class),
        @Arg(column = "updated_at", javaType = LocalDateTime.class)
    })
    List<MdDocumentSummary> selectAll();

    @Select("""
            SELECT id, file_name, content, file_size_bytes, created_at, updated_at
            FROM md_documents
            WHERE id = #{id}
            """)
    @ConstructorArgs({
        @Arg(column = "id", javaType = long.class, id = true),
        @Arg(column = "file_name", javaType = String.class),
        @Arg(column = "content", javaType = String.class),
        @Arg(column = "file_size_bytes", javaType = long.class),
        @Arg(column = "created_at", javaType = LocalDateTime.class),
        @Arg(column = "updated_at", javaType = LocalDateTime.class)
    })
    MdDocument selectById(@Param("id") long id);

    @Delete("DELETE FROM md_documents WHERE id = #{id}")
    int deleteById(@Param("id") long id);
}
