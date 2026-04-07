# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A RAG (Retrieval Augmented Generation) knowledge base QA system built with:
- **Backend**: Java Spring Boot 3.2 + LangChain4j 1.0
- **Frontend**: Vue.js 3 (not yet created)
- **Database**: MySQL 8 + InMemory Embedding Store (dev) / Milvus (prod)
- **AI Provider**: SiliconFlow (BAAI/bge-large-zh-v1.5 for embedding, Qwen for chat)

## Build Commands

```bash
# Backend
cd backend
mvn clean compile                    # Compile
mvn spring-boot:run                 # Run dev server (port 8080)
mvn package -DskipTests             # Build JAR

# Initialize database (run in MySQL)
mysql -u root -p < backend/src/main/resources/schema.sql
```

## Architecture

### Backend Structure
- `config/` - Spring configurations (SiliconFlow API, EmbeddingStore, CORS)
- `controller/` - REST API endpoints (QA, Knowledge, ChatHistory)
- `service/` - Business logic (QAService, KnowledgeService, ChatMemoryService)
- `repository/` - MyBatis-Plus data access layer
- `model/entity/` - JPA entities (KnowledgeBase, KnowledgeChunk, ChatSession, ChatMessage)
- `model/dto/` - API request/response DTOs
- `rag/` - RAG pipeline components (DocumentLoader, TextSplitter, VectorStoreManager, RAGPipeline)

### RAG Flow
```
Question → Embedding (SiliconFlow) → Vector Search (InMemory/Milvus) 
        → Retrieve Top-K Chunks → Build Prompt → LLM (SiliconFlow Qwen) 
        → Answer + Sources
```

### Key Configuration
- `application.yml` - All configs (DB, AI provider, upload paths)
- Environment variable `SILICONFLOW_API_KEY` - Required for AI features

## Database Schema

4 tables: `knowledge_base`, `knowledge_chunk`, `chat_session`, `chat_message`

See `backend/src/main/resources/schema.sql` for full DDL.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/qa/chat` | Send question, returns answer + sources |
| GET | `/api/qa/sessions` | List chat sessions |
| DELETE | `/api/qa/sessions/{id}` | Delete session |
| POST | `/api/knowledge/upload` | Upload document (multipart) |
| GET | `/api/knowledge/list` | List knowledge base |
| DELETE | `/api/knowledge/{id}` | Delete knowledge |
| GET | `/api/knowledge/{id}/status` | Check processing status |
| GET | `/api/chat/history/{sessionId}` | Get chat history |

## Development Notes

- Currently using `InMemoryEmbeddingStore` for local development (no Milvus needed)
- To switch to Milvus for production: update `EmbeddingStoreConfig.java`
- Apache Tika handles document parsing (PDF, Word, TXT)
- Documents are chunked with 500 char size and 50 char overlap
- Chat history is stored in MySQL for multi-turn conversation support
