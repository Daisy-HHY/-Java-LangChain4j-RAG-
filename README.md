# 医疗知识问答系统

基于 `Spring Boot + LangChain4j + Vue 3` 的医疗问答系统，当前实现的是一套并行的 `RAG + 知识图谱` 混合检索方案。

系统的核心目标不是单纯做“聊天”，而是把两类能力接到同一个问答入口：

- `RAG`：面向上传文档和向量知识库的语义检索
- `知识图谱`：面向 `kgdrug` 数据集的结构化事实查询

项目当前已经具备完整前后端、登录注册、会话管理、流式输出、多轮对话记忆和知识库文档管理能力。

![系统截图](image-2.png)

## 当前功能

- 混合问答：优先尝试知识图谱查询，失败后回退到 RAG
- 知识图谱查询：基于 `kgdrug` 的 TDB1 本地数据集，支持模板匹配和 LLM 生成 SPARQL
- RAG 检索：文档上传、切分、向量化、pgvector 相似度检索
- 流式回答：前端通过 SSE 接收流式输出，聊天区逐步渲染
- 多轮对话记忆：当当前问题缺少实体时，会根据最近用户消息补全上下文
- 用户体系：登录、注册、注销、用户隔离的知识库和聊天会话
- 会话管理：按最新活跃时间倒序排列，支持查看历史和删除会话
- 知识库管理：支持上传 `PDF / Word / TXT`，查看处理状态和删除文档

## 技术栈

| 类别 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.5, MyBatis-Plus |
| AI 框架 | LangChain4j 1.12 |
| 前端 | Vue 3, Vite, Pinia, Vue Router, Element Plus |
| 向量存储 | PostgreSQL 18 + pgvector 0.8.2 |
| 知识图谱 | Apache Jena TDB1 |
| 模型提供方 | SiliconFlow |

当前默认模型配置来自 [application.yml](backend/src/main/resources/application.yml)：

- Embedding: `Qwen/Qwen3-Embedding-8B`
- Chat: `MiniMaxAI/MiniMax-M2.5`

这些都可以通过环境变量覆盖。

## 架构概览

```text
                ┌────────────────────────────┐
                │        Vue 3 Frontend      │
                │ Chat / Knowledge / History │
                └──────────────┬─────────────┘
                               │
                               ▼
                ┌────────────────────────────┐
                │   Spring Boot Backend      │
                │ QA / Auth / Knowledge API  │
                └──────────────┬─────────────┘
                               │
          ┌────────────────────┴────────────────────┐
          ▼                                         ▼
┌──────────────────────┐                 ┌──────────────────────┐
│      RAG Pipeline    │                 │    KG Query Path     │
│ Embed -> pgvector    │                 │ Entity -> SPARQL     │
│ -> TopK -> LLM       │                 │ -> TDB1 -> LLM       │
└──────────────────────┘                 └──────────────────────┘
          │                                         │
          └────────────────────┬────────────────────┘
                               ▼
                     ┌────────────────────┐
                     │   Unified Answer   │
                     │ Answer + Sources   │
                     └────────────────────┘
```

问答链路的关键点：

1. 当前问题先做意图识别和实体抽取
2. 如果命中 `kgdrug` 实体，优先走知识图谱查询
3. 图谱无结果时回退到 RAG
4. 当前问题缺少实体但属于承接型追问时，会从最近历史中提取实体并重写问题
5. 回答通过流式接口返回到前端

## 目录结构

```text
LangChain4j-KGQA/
├── backend/
│   ├── src/main/java/com/kgqa/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── service/
│   │   │   ├── qa/
│   │   │   ├── rag/
│   │   │   └── sparql/
│   │   ├── kg/
│   │   ├── model/
│   │   └── repository/
│   └── src/main/resources/
├── frontend/
│   └── src/
│       ├── api/
│       ├── components/
│       ├── router/
│       ├── stores/
│       └── views/
├── apache jena/
│   └── tdb_drug_new/
└── uploads/
```

## 数据与存储

### 1. PostgreSQL + pgvector

用于存储：

- 用户表 `app_user`
- 知识库元数据 `knowledge_base`
- 文档切块 `knowledge_chunk`
- 聊天会话 `chat_session`
- 聊天消息 `chat_message`
- LangChain4j 维护的向量表 `embeddings`

初始化脚本见 [schema.sql](backend/src/main/resources/schema.sql)。

### 2. Apache Jena TDB1

当前仅保留 `kgdrug` 数据集，不再使用 `wikidata`。

- 原始图谱：`apache jena/tdb_drug_new`
- 开发副本：`.runtime/tdb_drug_new`；Docker 挂载到 `/data/tdb_drug_new`
- 本机后端从 `backend` 目录运行时默认读取 `../.runtime/tdb_drug_new`，也可以用 `KGQA_TDB_PATH` 指定绝对路径
- 本体基地址：`http://www.kgdrug.com`

注意：

- TDB1 同时只允许一个 JVM 可靠写入/访问同一数据目录
- `tdb.lock` 文件存在不等于有进程占用；遇到锁错误先关闭占用进程，不直接删除锁文件

## 快速开始

同一套代码支持完整 Docker 运行和本地前后端连接 Docker 数据库。完整步骤、Windows 命令、IDEA 环境变量和备份恢复见 [Docker 运行与本地断点调试](docs/DOCKER_DEVELOPMENT.md)。

### 环境要求

- Docker Engine / Docker Desktop 和 Docker Compose v2
- 本地调试额外需要 JDK 17、Maven 3.8+、Node.js 22.12+
- 可用的 `SILICONFLOW_API_KEY`，模型仍通过 SiliconFlow API 调用

### 首次准备

从项目根目录执行，复制图谱前停止访问原图谱的进程；已有 `.env` 或图谱副本时保留原文件：

```bash
cp .env.example .env
mkdir -p .runtime uploads
test ! -e .runtime/tdb_drug_new && cp -a 'apache jena/tdb_drug_new' .runtime/tdb_drug_new
```

编辑 `.env`，填写 API Key、数据库密码和固定的令牌密钥，并选择账户可用的聊天模型。原应用默认聊天模型返回禁用错误，`.env.example` 已选择经过验证的替代模型；详情见运行文档。

### 完整 Docker 运行

```bash
docker compose --profile app up -d --build
docker compose --profile app logs -f backend
```

后端启动完成后访问 `http://localhost:5173`。默认管理员为 `admin / admin123`，可以在首次启动前通过 `.env` 修改。此模式用于本机开发，前端运行 Vite 开发服务器。

### 本地断点调试

```bash
docker compose --profile app stop frontend backend
docker compose up -d postgres
```

在 IDEA 中设置 Spring `dev` profile 和文档列出的环境变量，数据库使用 `localhost:5432`，图谱与上传目录使用对应宿主机绝对路径，然后 Debug 启动 `com.kgqa.KgQaApplication`。Compose 根目录 `.env` 不会被 IDEA 自动加载。

```bash
cd frontend
npm ci
npm run dev
```

切回 Docker 时先停止本机前后端，再从项目根目录执行 `docker compose --profile app up -d --build`。数据库数据卷始终保留；同一图谱目录只允许一个后端进程访问。

## 认证说明

系统已经实现登录和注册，不再是匿名问答。

- 鉴权接口：`/api/auth/login`、`/api/auth/register`、`/api/auth/me`、`/api/auth/logout`
- 认证令牌由后端写入 `HttpOnly Cookie`
- 前端不会把 token 暴露在 `localStorage`
- 不同用户的知识库、会话和历史记录彼此隔离
- RAG 私有向量包含 `knowledge_id`、`user_id` 和 `visibility=PRIVATE`；向量查询在 pgvector 内按当前用户过滤，普通及流式问答的降阈值重试也保留此过滤
- 共享向量必须由显式的系统导入写入 `visibility=PUBLIC`；开发环境的 CSV/教材共享导入仅限管理员，缺失用户 ID 的上传不会自动公开
- 不完整的历史向量元数据默认不可检索；迁移、重建注意事项见 [RAG 用户隔离与迁移](docs/RAG_USER_ISOLATION.md)

## API

### 问答

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/qa/chat` | 非流式问答 |
| POST | `/api/qa/chat/stream` | SSE 流式问答 |
| GET | `/api/qa/sessions` | 获取当前用户会话列表 |
| DELETE | `/api/qa/sessions/{id}` | 删除指定会话 |

### 认证

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/register` | 注册 |
| GET | `/api/auth/me` | 获取当前用户 |
| POST | `/api/auth/logout` | 注销 |

### 聊天历史

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/chat/history/{sessionId}` | 获取会话历史 |
| DELETE | `/api/chat/history/{sessionId}` | 清空会话历史 |

### 知识库

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/knowledge/upload` | 上传文档 |
| GET | `/api/knowledge/list` | 获取知识库列表 |
| DELETE | `/api/knowledge/{id}` | 删除知识条目 |
| GET | `/api/knowledge/{id}/status` | 获取处理状态 |

### 导入与调试

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/import/tdb` | 导入 TDB 数据 |
| POST | `/api/import/csv` | 管理员导入共享 CSV 数据（仅 dev） |
| POST | `/api/import/textbook` | 管理员导入共享教材文本（仅 dev） |
| GET | `/api/qa/test/triples` | 查看三元组 |
| POST | `/api/qa/test/sparql` | 执行调试 SPARQL |

## 当前前端页面

- `/login`：登录 / 注册
- `/`：聊天问答页
- `/knowledge`：知识库管理页
- `/history`：历史会话页

## 当前实现上的约束

- 知识图谱当前只围绕 `kgdrug` 数据集，不包含 `wikidata`
- 流式输出依赖后端 SSE 和前端打字机式渲染
- 多轮记忆现在是“最近实体补全 + 追问改写”策略，不是完整会话摘要记忆
- RAG 检索质量强依赖上传文档质量和向量库内容

## 开发说明

常用命令：

```bash
cd backend
mvn clean compile

cd frontend
npm run build
```

如果你要调整系统行为，优先关注这些位置：

- [HybridQAServiceImpl.java](backend/src/main/java/com/kgqa/service/qa/impl/HybridQAServiceImpl.java)
- [RAGPipelineImpl.java](backend/src/main/java/com/kgqa/service/rag/impl/RAGPipelineImpl.java)
- [SPARQLTemplateMatcherImpl.java](backend/src/main/java/com/kgqa/service/sparql/impl/SPARQLTemplateMatcherImpl.java)
- [ChatView.vue](frontend/src/views/ChatView.vue)

## License

MIT
