# 基于 Java + LangChain4j 的医疗知识库问答系统

一个结合 RAG（检索增强生成）和知识图谱的智能医疗问答系统，支持混合问答、意图识别、实体抽取和多轮对话。
![alt text](image.png)

## 技术栈

| 类别 | 技术 |
|------|------|
| 后端框架 | Java 17 + Spring Boot 3.5 |
| AI 框架 | LangChain4j 1.12 |
| 前端框架 | Vue 3 + Composition API + Element Plus |
| 向量数据库 | PostgreSQL 16 + pgvector 0.8.2 |
| 知识图谱 | Apache Jena TDB2 |
| AI 提供商 | SiliconFlow (Qwen3-Embedding-8B + DeepSeek-V3.2) |

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (Vue 3)                      │
│    ChatView │ KnowledgeView │ History                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ QAController │  │ Knowledge    │  │ ChatHistory       │  │
│  │              │  │ Controller   │  │ Controller        │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │              HybridQAService (混合问答引擎)               │ │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────────────┐  │ │
│  │  │ Intent     │  │ Medical    │  │ SPARQL Generator  │  │ │
│  │  │ Detection  │  │ Entity     │  │ + Template Matcher │  │ │
│  │  └────────────┘  │ Extractor  │  └────────────────────┘  │ │
│  │                 └────────────┘                            │ │
│  └──────────────────────────────────────────────────────────┘ │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐ │
│  │ RAGPipeline  │  │ SPARQL       │  │ EmbeddingService     │ │
│  │              │  │ QueryExecutor│  │                      │ │
│  └──────────────┘  └──────────────┘  └──────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌────────────────┐    ┌────────────────┐    ┌────────────────────┐
│   pgvector     │    │   Jena TDB2    │    │   SiliconFlow API  │
│ (向量存储)      │    │ (知识图谱)      │    │ (Embedding + LLM)  │
└────────────────┘    └────────────────┘    └────────────────────┘
```

## 核心功能

### 1. 混合问答系统
- **意图识别**：自动判断用户问题是事实查询还是开放域问答
- **实体抽取**：从问题中提取疾病、症状、药品等医疗实体
- **路由决策**：根据意图和实体选择最优问答路径

### 2. RAG 向量检索
- 基于 Qwen3-Embedding-8B (1024维) 生成向量
- pgvector 向量相似度搜索
- 支持多轮对话上下文记忆

### 3. 知识图谱查询
- SPARQL 模板匹配 + LLM 自动生成
- 支持疾病、症状、药品等实体关系查询
- TDB2 高性能三元组存储

### 4. 会话管理
- 多会话支持
- 自动生成会话标题（基于首个问题）
- 完整对话历史记录

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.8+
- Node.js 18+
- Docker (用于 PostgreSQL + pgvector)

### 1. 启动数据库

```bash
docker run --name kgqa-postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=kgqa \
  -d pgvector/pgvector:0.8.2-pg18-trixie
```

### 2. 配置环境变量

```bash
export SILICONFLOW_API_KEY=your_api_key_here
```

### 3. 启动后端

```bash
cd backend
mvn clean compile
mvn spring-boot:run
```

后端运行在 http://localhost:8080

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 http://localhost:5173

## API 接口

### 问答接口

| Method | Path | 描述 |
|--------|------|------|
| POST | `/api/qa/chat` | 发送问题，获取回答 |
| GET | `/api/qa/sessions` | 获取所有会话列表 |
| DELETE | `/api/qa/sessions/{id}` | 删除指定会话 |

### 知识库接口

| Method | Path | 描述 |
|--------|------|------|
| POST | `/api/knowledge/upload` | 上传文档 (PDF/Word/TXT) |
| GET | `/api/knowledge/list` | 获取知识库列表 |
| DELETE | `/api/knowledge/{id}` | 删除知识条目 |
| GET | `/api/knowledge/{id}/status` | 查看处理状态 |

### 聊天历史

| Method | Path | 描述 |
|--------|------|------|
| GET | `/api/chat/history/{sessionId}` | 获取会话历史 |

### 测试接口

| Method | Path | 描述 |
|--------|------|------|
| GET | `/api/qa/test/triples` | 查询 TDB 中所有三元组 |
| POST | `/api/qa/test/sparql` | 执行自定义 SPARQL 查询 |

## 数据统计

- **向量总数**：28,622 条
  - 疾病：7,775 条
  - 症状：7,770 条
  - 药品：13,077 条
- **向量维度**：1024
- **嵌入模型**：Qwen/Qwen3-Embedding-8B

## 项目结构

```
LangChain4j-KGQA/
├── backend/
│   └── src/main/java/com/kgqa/
│       ├── config/          # Spring 配置类
│       ├── controller/      # REST API 控制器
│       ├── service/
│       │   ├── qa/         # 问答服务 (HybridQA, IntentDetection)
│       │   ├── rag/         # RAG 管道 (Embedding, VectorStore)
│       │   └── sparql/      # SPARQL 查询服务
│       ├── kg/              # 知识图谱管理 (TDB, Ontology)
│       ├── data/             # 数据导入 (Wikidata, Kgdrug)
│       ├── model/            # 实体类和数据传输对象
│       └── repository/       # MyBatis-Plus 数据访问层
├── frontend/
│   └── src/
│       ├── components/      # Vue 组件
│       ├── views/           # 页面视图
│       ├── stores/          # Pinia 状态管理
│       └── api/             # API 调用封装
├── apache_configuration/
│   └── tdb_drug_new/        # TDB 知识图谱数据
└── tdb_wikidata/            # Wikidata TDB 数据
```

## 配置说明

主要配置在 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/kgqa
    username: postgres
    password: 123456

langchain4j:
  siliconflow:
    api-key: ${SILICONFLOW_API_KEY}
    embedding-model: Qwen/Qwen3-Embedding-8B
    chat-model: deepseek-ai/DeepSeek-V3.2

kgqa:
  tdb:
    path: apache_configuration/tdb_drug_new
  ontology:
    base: http://www.kgdrug.com
```

## License

MIT
