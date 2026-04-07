# LangChain4j RAG 知识库问答系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个基于 Java Spring Boot + LangChain4j 的 RAG 知识库问答系统，支持向量检索、多轮对话、知识库管理

**Architecture:** 单体 Spring Boot 后端 + Vue.js 前端，通过 REST API 通信。后端使用 LangChain4j 集成 SiliconFlow Embedding/LLM 和 Milvus 向量数据库

**Tech Stack:** Spring Boot 3.2.x, LangChain4j 1.x, Milvus 2.4.x, SiliconFlow, MySQL 8.x, MyBatis-Plus, Vue.js 3, Element Plus

---

## 项目文件结构

```
LangChain4j-KGQA/
├── backend/                                    # Spring Boot 后端
│   ├── pom.xml
│   ├── src/main/java/com/kgqa/
│   │   ├── KgQaApplication.java
│   │   ├── config/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── model/entity/
│   │   ├── model/dto/
│   │   ├── rag/
│   │   └── util/
│   └── src/main/resources/
│       ├── application.yml
│       └── schema.sql
├── frontend/                                    # Vue.js 前端
│   ├── package.json
│   ├── vite.config.js
│   ├── src/
│   └── index.html
└── docs/superpowers/
```

---

## Phase 1: 项目初始化与配置

### Task 1: 初始化 Spring Boot 项目结构

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/kgqa/KgQaApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/schema.sql`

### Task 2: 配置类开发

**Files:**
- Create: `backend/src/main/java/com/kgqa/config/SiliconFlowConfig.java`
- Create: `backend/src/main/java/com/kgqa/config/LangChain4jConfig.java`
- Create: `backend/src/main/java/com/kgqa/config/MilvusConfig.java`
- Create: `backend/src/main/java/com/kgqa/config/WebConfig.java`

### Task 3: 实体类开发

**Files:**
- Create: `backend/src/main/java/com/kgqa/model/entity/KnowledgeBase.java`
- Create: `backend/src/main/java/com/kgqa/model/entity/KnowledgeChunk.java`
- Create: `backend/src/main/java/com/kgqa/model/entity/ChatSession.java`
- Create: `backend/src/main/java/com/kgqa/model/entity/ChatMessage.java`

### Task 4: Repository 层开发

**Files:**
- Create: `backend/src/main/java/com/kgqa/repository/KnowledgeRepository.java`
- Create: `backend/src/main/java/com/kgqa/repository/KnowledgeChunkRepository.java`
- Create: `backend/src/main/java/com/kgqa/repository/ChatSessionRepository.java`
- Create: `backend/src/main/java/com/kgqa/repository/ChatMessageRepository.java`

### Task 5: DTO 开发

**Files:**
- Create: `backend/src/main/java/com/kgqa/model/dto/ChatRequest.java`
- Create: `backend/src/main/java/com/kgqa/model/dto/ChatResponse.java`
- Create: `backend/src/main/java/com/kgqa/model/dto/UploadResponse.java`
- Create: `backend/src/main/java/com/kgqa/model/dto/KnowledgeDTO.java`

### Task 6: RAG 核心模块开发

**Files:**
- Create: `backend/src/main/java/com/kgqa/rag/DocumentLoader.java`
- Create: `backend/src/main/java/com/kgqa/rag/TextSplitter.java`
- Create: `backend/src/main/java/com/kgqa/rag/VectorStoreManager.java`
- Create: `backend/src/main/java/com/kgqa/rag/RAGPipeline.java`

### Task 7: Service 层开发

**Files:**
- Create: `backend/src/main/java/com/kgqa/service/EmbeddingService.java`
- Create: `backend/src/main/java/com/kgqa/service/ChatMemoryService.java`
- Create: `backend/src/main/java/com/kgqa/service/KnowledgeService.java`
- Create: `backend/src/main/java/com/kgqa/service/QAService.java`

### Task 8: Controller 层开发

**Files:**
- Create: `backend/src/main/java/com/kgqa/controller/QAController.java`
- Create: `backend/src/main/java/com/kgqa/controller/KnowledgeController.java`
- Create: `backend/src/main/java/com/kgqa/controller/ChatHistoryController.java`

### Task 9: 工具类

**Files:**
- Create: `backend/src/main/java/com/kgqa/util/UUIDUtil.java`

### Task 10: 前端 Vue.js 项目

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.js`
- Create: `frontend/index.html`
- Create: `frontend/src/main.js`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/router/index.js`
- Create: `frontend/src/api/index.js`
- Create: `frontend/src/stores/chat.js`
- Create: `frontend/src/stores/knowledge.js`
- Create: `frontend/src/components/ChatWindow.vue`
- Create: `frontend/src/components/MessageList.vue`
- Create: `frontend/src/components/KnowledgeUpload.vue`
- Create: `frontend/src/views/QAView.vue`
- Create: `frontend/src/views/KnowledgeManage.vue`
- Create: `frontend/src/views/ChatHistory.vue`

### Task 11: 集成测试

- [ ] 验证后端编译: `cd backend && mvn compile -q`
- [ ] 验证前端依赖安装: `cd frontend && npm install`
- [ ] 验证前端编译: `cd frontend && npm run build`

---

## 执行选项

**1. Subagent-Driven（推荐）** - 每个任务派遣独立子 agent，支持快速迭代

**2. Inline Execution** - 在当前会话中顺序执行任务，带检查点

请选择执行方式？
