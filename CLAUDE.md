# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A RAG (Retrieval Augmented Generation) knowledge base QA system with knowledge graph capabilities built with:
- **Backend**: Java Spring Boot 3.5 + LangChain4j 1.12
- **Frontend**: Vue.js 3 (not yet created)
- **Database**: PostgreSQL 16 + pgvector 0.8.2
- **Embedding Store**: pgvector (PostgreSQL extension)
- **Knowledge Graph**: Apache Jena TDB2
- **AI Provider**: SiliconFlow (Qwen3-Embedding-8B for embedding, DeepSeek-V3.2 for chat)

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

Jena TDB2（知识图谱三元组，本地文件）
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
    path: E:/Github_project/LangChain4j-KGQA/tdb_medqa
  embedding:
    path: E:/Github_project/LangChain4j-KGQA/backend/resource/embeddings/medqa.json
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

4 tables in `kgqa` database:
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
- `TdbManager.java` - TDB2 connection management
- `MedicalOntology.java` - Ontology constants (Disease, Symptom, Drug, etc.)
- `LLMTripleExtractor.java` - LLM-based triple extraction with confidence scoring
- `KnowledgeGraphBuilder.java` - Writes triples to TDB

TDB path: `E:/Github_project/LangChain4j-KGQA/tdb_medqa`

## Docker Services

PostgreSQL + pgvector runs in Docker:
```bash
docker run --name kgqa-postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=kgqa \
  -d pgvector/pgvector:0.8.2-pg18-trixie
```

## Development Notes

- **pgvector**: Vector extension for PostgreSQL, automatically managed by LangChain4j
- **TDB locking**: Only one JVM can access TDB at a time; ensure no other process locks it
- **Embedding persistence**: MedQA vectors saved to `resource/embeddings/medqa.json`
- Apache Tika handles document parsing (PDF, Word, TXT)
- Documents are chunked with 500 char size and 50 char overlap
- Chat history is stored in PostgreSQL for multi-turn conversation support
