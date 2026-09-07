package com.jacolp.mapper;

import java.util.List;

import com.jacolp.pojo.entity.ChatSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper {

    @Insert("""
            INSERT INTO chat_sessions (title, messages, referenced_file_contents)
            VALUES (#{title}, #{messages}, #{referencedFileContents})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ChatSession session);

    @Select("""
            SELECT id, title, messages, referenced_file_contents, created_at, updated_at
            FROM chat_sessions
            WHERE id = #{id}
            """)
    @Results(id = "chatSessionResultMap", value = {
        @Result(property = "id", column = "id", id = true),
        @Result(property = "title", column = "title"),
        @Result(property = "messages", column = "messages"),
        @Result(property = "referencedFileContents", column = "referenced_file_contents"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    ChatSession selectById(@Param("id") long id);

    @Select("""
            SELECT id, title, messages, referenced_file_contents, created_at, updated_at
            FROM chat_sessions
            ORDER BY updated_at DESC, id DESC
            """)
    @ResultMap("chatSessionResultMap")
    List<ChatSession> selectAll();

    @Update("""
            UPDATE chat_sessions
            SET title = #{title},
                messages = #{messages},
                referenced_file_contents = #{referencedFileContents}
            WHERE id = #{id}
            """)
    int updateSnapshot(ChatSession session);

    @Delete("DELETE FROM chat_sessions WHERE id = #{id}")
    int deleteById(@Param("id") long id);
}
