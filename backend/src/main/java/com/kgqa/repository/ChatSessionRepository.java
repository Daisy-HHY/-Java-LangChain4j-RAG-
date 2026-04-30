package com.kgqa.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kgqa.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatSessionRepository extends BaseMapper<ChatSession> {
    @Select("""
            SELECT s.*
            FROM chat_session s
            LEFT JOIN (
                SELECT session_id, MAX(created_at) AS latest_message_at
                FROM chat_message
                GROUP BY session_id
            ) m ON m.session_id = s.id
            WHERE s.user_id = #{userId}
            ORDER BY
                (COALESCE(m.latest_message_at, s.updated_at, s.created_at) IS NULL) ASC,
                COALESCE(m.latest_message_at, s.updated_at, s.created_at) DESC,
                s.id DESC
            """)
    List<ChatSession> selectSessionsOrderByLatestActivity(@Param("userId") Long userId);
}
