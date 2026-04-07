-- 创建数据库
CREATE DATABASE IF NOT EXISTS kgqa DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE kgqa;

-- 知识库元数据表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL COMMENT '文档标题',
    file_name VARCHAR(255) COMMENT '原始文件名',
    file_type VARCHAR(50) COMMENT '文件类型',
    tags VARCHAR(500) COMMENT '标签，逗号分隔',
    chunk_count INT DEFAULT 0 COMMENT '知识块数量',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING, PROCESSING, READY, FAILED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

-- 知识块表
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL COMMENT '所属知识库ID',
    content TEXT NOT NULL COMMENT '文本内容',
    chunk_index INT COMMENT '块索引',
    embedding_id BIGINT COMMENT '向量ID（预留）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (knowledge_id) REFERENCES knowledge_base(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识块表';

-- 对话会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) UNIQUE NOT NULL COMMENT '会话ID',
    title VARCHAR(255) COMMENT '会话标题',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

-- 消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(20) NOT NULL COMMENT '角色: USER, ASSISTANT',
    content TEXT NOT NULL COMMENT '消息内容',
    sources TEXT COMMENT '引用来源（JSON格式）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息表';

-- 创建索引
CREATE INDEX idx_knowledge_status ON knowledge_base(status);
CREATE INDEX idx_knowledge_created ON knowledge_base(created_at);
CREATE INDEX idx_chunk_knowledge ON knowledge_chunk(knowledge_id);
CREATE INDEX idx_message_session ON chat_message(session_id);
CREATE INDEX idx_session_updated ON chat_session(updated_at);
