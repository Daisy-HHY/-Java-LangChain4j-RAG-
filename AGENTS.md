# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

A RAG (Retrieval Augmented Generation) knowledge base QA system with knowledge graph capabilities built with:
- **Backend**: Java Spring Boot 3.5 + LangChain4j 1.12
- **Frontend**: Vue.js 3 + Vite 8 (Node.js 22.12+)
- **Database**: PostgreSQL 18 + pgvector 0.8.2
- **Embedding Store**: pgvector (PostgreSQL extension)
- **Knowledge Graph**: Apache Jena TDB1
- **AI Provider**: SiliconFlow (Qwen3-Embedding-8B for embedding, MiniMax-M2.5 for chat)

## Build Commands

```bash
cd backend

# Compile and run
mvn clean compile
mvn spring-boot:run                          # Run dev server (port 8080)

# Build
mvn package -DskipTests                       # Build JAR
```

## Architecture

### Storage Design
```
PostgreSQL + pgvector
├── 元数据表（knowledge_base, knowledge_chunk, chat_session, chat_message）
└── 向量表（embeddings）- pgvector 自动管理

Jena TDB1（知识图谱三元组，本地文件）
```

### Backend Structure
```
com.kgqa/
├── config/                    # Spring configurations
├── controller/                # REST API endpoints
├── service/                   # Business logic
│   ├── qa/                    # QA services (HybridQAService, IntentDetection)
│   ├── rag/                   # RAG pipeline (embedding, vector store, memory)
│   └── sparql/                # SPARQL query execution
├── data/                      # MedQA data processing (parser, ingestor)
├── kg/                        # Knowledge graph (TDB, ontology, triple extraction)
├── init/                      # Startup initialization (DataInitializer)
├── repository/                # MyBatis-Plus data access
└── model/                     # Entities and DTOs
```

### RAG Flow
```
Question → Embed (SiliconFlow) → Vector Search (pgvector)
       → Top-K Chunks → Build Prompt → LLM (DeepSeek) → Answer + Sources
```

### Knowledge Graph Flow
```
Question → Intent Detection → SPARQL Generator → TDB Query → Structured Results
```

### Key Configuration (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/kgqa
    username: postgres
    password: 123456

kgqa:
  tdb:
    path: ../.runtime/tdb_drug_new
  embedding:
    table: embeddings
```
- Environment variable `SILICONFLOW_API_KEY` - Required for AI features

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/qa/chat` | Send question, returns answer + sources |
| POST | `/api/qa/hybrid` | Hybrid QA with RAG + SPARQL |
| GET | `/api/qa/sessions` | List chat sessions |
| DELETE | `/api/qa/sessions/{id}` | Delete session |
| POST | `/api/knowledge/upload` | Upload document (PDF/Word/TXT) |
| GET | `/api/knowledge/list` | List knowledge base |
| DELETE | `/api/knowledge/{id}` | Delete knowledge |
| GET | `/api/knowledge/{id}/status` | Check processing status |
| GET | `/api/chat/history/{sessionId}` | Get chat history |

## Database Schema (PostgreSQL)

5 business tables in `kgqa` database:
- `app_user` - 用户表
- `knowledge_base` - 知识库文档表
- `knowledge_chunk` - 知识块表（分块后的文本）
- `chat_session` - 对话会话表
- `chat_message` - 对话消息表

pgvector table `embeddings` is automatically created by LangChain4j.

## MedQA Data Processing

Located in `data/` package:
- `MedQaRecord.java` - Data model for MedQA records
- `MedQaParser.java` - JSONL parser with batch processing
- `EmbeddingIngestor.java` - Batch vectorization to InMemoryEmbeddingStore

Data path: `E:/Github_project/LangChain4j-KGQA/data/data_clean/questions/Mainland/chinese_qbank.jsonl`

## Knowledge Graph (kg/)

Located in `kg/` package:
- `TdbManager.java` - TDB1 connection management
- `MedicalOntology.java` - Ontology constants (Disease, Symptom, Drug, etc.)
- `LLMTripleExtractor.java` - LLM-based triple extraction with confidence scoring
- `KnowledgeGraphBuilder.java` - Writes triples to TDB

TDB path: `../.runtime/tdb_drug_new`

## Docker Services

Use the same branch and `compose.yaml` for both modes. See `docs/DOCKER_DEVELOPMENT.md`.

```bash
# Full local Docker mode: database + backend + Vite frontend
docker compose --profile app up -d --build

# Switch to local IDEA / Vite debugging; keep the database running
docker compose --profile app stop frontend backend
docker compose up -d postgres
```

- Copy `.env.example` to `.env` and prepare `.runtime/tdb_drug_new` before full startup.
- Compose uses PostgreSQL 18 with its data volume mounted at `/var/lib/postgresql`.
- IDEA does not automatically read Compose's `.env`; explicitly set the local database URL, graph/upload host paths, API key, and the same token secret.
- Always stop the previous backend before switching; do not share a graph directory between running JVMs.
- The actual graph API is `org.apache.jena.tdb.TDBFactory` (TDB1), not TDB2. Do not migrate it as part of container setup.
- Run backend commands from `backend` and frontend commands from `frontend`.

## Development Notes

- **pgvector**: Vector extension for PostgreSQL, automatically managed by LangChain4j
- **TDB locking**: Only one JVM can access TDB at a time; stop the previous backend before switching modes; lock-file existence alone does not prove a running owner
- **Embedding persistence**: MedQA vectors saved to `resource/embeddings/medqa.json`
- Apache Tika handles document parsing (PDF, Word, TXT)
- Documents are chunked with 500 char size and 50 char overlap
- Chat history is stored in PostgreSQL for multi-turn conversation support
