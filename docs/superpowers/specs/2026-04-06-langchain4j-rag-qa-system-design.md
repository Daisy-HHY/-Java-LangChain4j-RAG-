# 基于 Java + LangChain4j 的 RAG 知识库问答系统设计方案

## 1. 系统架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              用户层                                         │
│  ┌──────────────┐                      ┌──────────────┐                   │
│  │   浏览器用户   │                      │   API 客户端  │                   │
│  └──────┬───────┘                      └──────┬───────┘                   │
└─────────┼─────────────────────────────────────┼─────────────────────────────┘
          │          HTTP / WebSocket            │
┌─────────▼─────────────────────────────────────▼─────────────────────────────┐
│                              前端层 (Vue.js)                                │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  知识库管理界面  │  问答界面  │  对话历史  │  知识库配置                │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │ HTTP REST API
┌────────────────────────────────────▼───────────────────────────────────────┐
│                              后端层 (Spring Boot)                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │  Controller  │  │   Service   │  │   Repository │  │  LangChain4j │       │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘       │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                     LangChain4j RAG Pipeline                        │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                     │
│  │  Milvus Client │  │ SiliconFlow  │  │   Chat Memory │                     │
│  └──────────────┘  └──────────────┘  └──────────────┘                     │
└─────────────────────────────────────────────────────────────────────────────┘
          │                         │                         │
┌─────────▼─────────┐   ┌──────────▼──────────┐   ┌──────────▼──────────┐
│  Milvus 向量数据库   │   │ SiliconFlow API    │   │   知识库文件存储      │
│  localhost:19530   │   │ (Embedding + LLM)  │   │   (Local File System)│
└────────────────────┘   └─────────────────────┘   └─────────────────────┘
```

---

## 2. 技术栈清单

| 层级 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **后端框架** | Spring Boot | 3.2.x | Web 框架 |
| **AI 集成** | LangChain4j | 1.x | RAG、LLM、Embedding 集成 |
| **向量数据库** | Milvus | 2.4.x | 向量存储与检索 |
| **Embedding** | SiliconFlow | text-embedding-v1 | 文本向量化 |
| **LLM** | SiliconFlow | Qwen/Qwen2 系列 | 对话生成 |
| **数据库** | MySQL | 8.x | 知识库元数据、对话历史存储 |
| **ORM** | MyBatis-Plus | 3.5.x | 数据访问 |
| **前端框架** | Vue.js | 3.x | 前端界面 |
| **前端 UI** | Element Plus | 2.x | UI 组件库 |
| **文档解析** | Apache Tika | 2.x | PDF/Word 等文档解析 |
| **构建工具** | Maven | 3.9.x | 项目构建 |

---

## 3. 核心模块设计

### 3.1 后端模块结构

```
src/main/java/com/kgqa/
├── config/                    # 配置模块
│   ├── LangChain4jConfig.java  # LangChain4j 全局配置
│   ├── MilvusConfig.java      # Milvus 连接配置
│   ├── SiliconFlowConfig.java # SiliconFlow API 配置
│   └── WebConfig.java         # Web 相关配置
├── controller/                 # 控制层
│   ├── QAController.java      # 问答 API
│   ├── KnowledgeController.java # 知识库管理 API
│   └── ChatHistoryController.java # 对话历史 API
├── service/                    # 服务层
│   ├── QAService.java         # 问答服务
│   ├── KnowledgeService.java  # 知识库服务
│   ├── EmbeddingService.java  # Embedding 服务
│   └── ChatMemoryService.java # 对话记忆服务
├── repository/                 # 数据访问层
│   ├── KnowledgeRepository.java
│   ├── ChatHistoryRepository.java
│   └── MessageRepository.java
├── model/                     # 数据模型
│   ├── entity/                # 实体类
│   ├── dto/                   # 数据传输对象
│   └── vo/                    # 视图对象
├── rag/                       # RAG 核心
│   ├── DocumentLoader.java    # 文档加载器
│   ├── TextSplitter.java      # 文本分割
│   ├── VectorStoreManager.java # 向量存储管理
│   └── RAGPipeline.java       # RAG 流水线
└── util/                      # 工具类
```

### 3.2 前端模块结构

```
src/
├── views/                     # 页面
│   ├── QAView.vue            # 问答页面
│   ├── KnowledgeManage.vue   # 知识库管理
│   └── ChatHistory.vue       # 对话历史
├── components/                # 组件
│   ├── ChatWindow.vue        # 聊天窗口
│   ├── MessageList.vue       # 消息列表
│   └── KnowledgeUpload.vue   # 知识上传
├── api/                       # API 调用
│   └── index.js             # API 接口封装
└── stores/                   # 状态管理 (Pinia)
    ├── chat.js              # 对话状态
    └── knowledge.js          # 知识库状态
```

---

## 4. 数据流设计

### 4.1 问答流程 (RAG Pipeline)

```
用户问题 → 问题向量化(SiliconFlow) → 向量检索(Milvus) → 召回Top-K知识块
    → 构建Prompt → LLM生成答案(SiliconFlow Qwen) → 返回答案+保存历史
```

### 4.2 知识库管理流程

```
文档上传 → 文档解析(Apache Tika) → 文本分割 → 向量化(SiliconFlow)
    → 存入Milvus → 保存元数据到MySQL → 更新索引状态
```

---

## 5. API 接口设计

### 5.1 问答 API

| 方法 | 路径 | 描述 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/api/qa/chat` | 发送问题 | `{sessionId, question}` | `{answer, sources, sessionId}` |
| GET | `/api/qa/sessions` | 获取会话列表 | - | `List<Session>` |
| DELETE | `/api/qa/sessions/{id}` | 删除会话 | - | `{success}` |

### 5.2 知识库管理 API

| 方法 | 路径 | 描述 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/api/knowledge/upload` | 上传文档 | `multipartFile, title, tags` | `{knowledgeId, status}` |
| GET | `/api/knowledge/list` | 知识库列表 | `page, size` | `Page<Knowledge>` |
| DELETE | `/api/knowledge/{id}` | 删除知识 | - | `{success}` |
| GET | `/api/knowledge/{id}/status` | 查看索引状态 | - | `{status, progress}` |

### 5.3 对话历史 API

| 方法 | 路径 | 描述 | 请求体 | 响应 |
|------|------|------|--------|------|
| GET | `/api/chat/history/{sessionId}` | 获取会话历史 | - | `List<Message>` |
| DELETE | `/api/chat/history/{sessionId}` | 清空会话 | - | `{success}` |

---

## 6. 数据库设计

### 6.1 MySQL 表结构

```sql
-- 知识库元数据表
CREATE TABLE knowledge_base (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    file_name VARCHAR(255),
    file_type VARCHAR(50),
    tags VARCHAR(500),
    chunk_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 知识块表
CREATE TABLE knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    chunk_index INT,
    embedding_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (knowledge_id) REFERENCES knowledge_base(id)
);

-- 对话会话表
CREATE TABLE chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) UNIQUE NOT NULL,
    title VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 消息表
CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sources TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES chat_session(id)
);
```

---

## 7. 知识库管理功能

| 功能 | 描述 |
|------|------|
| 文档上传 | 支持 PDF、Word、TXT、Markdown 等格式 |
| 文档解析 | 自动提取文本内容 |
| 智能分块 | 按语义/长度自动分割文档 |
| 向量化 | 自动调用 SiliconFlow Embedding |
| 知识检索 | 实时搜索已索引的知识 |
| 知识删除 | 级联删除向量和元数据 |
| 状态监控 | 查看文档处理进度 |

---

## 8. 对话历史功能

| 功能 | 描述 |
|------|------|
| 多轮对话 | 支持上下文连贯的对话 |
| 会话列表 | 查看所有历史会话 |
| 会话续聊 | 从中断处继续对话 |
| 会话删除 | 删除不需要的对话 |
| 引用来源 | 显示答案引用的知识块 |
