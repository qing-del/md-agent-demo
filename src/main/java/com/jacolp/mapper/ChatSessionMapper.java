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

/**
 * 提供聊天会话及其 JSON 快照的数据库访问方法。
 */
@Mapper
public interface ChatSessionMapper {

    /**
     * 新增一个聊天会话，并回填数据库生成的主键。
     *
     * @param session 待保存的聊天会话
     * @return 受影响的行数
     */
    @Insert("""
            INSERT INTO chat_sessions (title, messages, referenced_file_contents)
            VALUES (#{title}, #{messages}, #{referencedFileContents})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ChatSession session);

    /**
     * 根据主键加载聊天会话及其历史快照。
     *
     * @param id 聊天会话 ID
     * @return 找到的会话；不存在时返回 {@code null}
     */
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

    /**
     * 按最近更新时间倒序查询全部聊天会话。
     *
     * @return 聊天会话列表
     */
    @Select("""
            SELECT id, title, messages, referenced_file_contents, created_at, updated_at
            FROM chat_sessions
            ORDER BY updated_at DESC, id DESC
            """)
    @ResultMap("chatSessionResultMap")
    List<ChatSession> selectAll();

    /**
     * 一次性更新聊天会话的标题、消息和文件引用快照。
     *
     * @param session 包含最新快照的聊天会话
     * @return 受影响的行数
     */
    @Update("""
            UPDATE chat_sessions
            SET title = #{title},
                messages = #{messages},
                referenced_file_contents = #{referencedFileContents}
            WHERE id = #{id}
            """)
    int updateSnapshot(ChatSession session);

    /**
     * 根据主键删除聊天会话。
     *
     * @param id 要删除的聊天会话 ID
     * @return 受影响的行数
     */
    @Delete("DELETE FROM chat_sessions WHERE id = #{id}")
    int deleteById(@Param("id") long id);
}
