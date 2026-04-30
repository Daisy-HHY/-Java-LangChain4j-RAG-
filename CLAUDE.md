# CLAUDE.md

This file provides repository-specific guidance for Claude Code and similar coding agents working in this project.

## Project Summary

This repository is a medical QA system that combines:

- `RAG` over uploaded medical documents stored in PostgreSQL + pgvector
- `Knowledge graph QA` over a local Apache Jena TDB2 dataset based on `kgdrug`
- `Vue 3 frontend` with login, chat, history, and knowledge-base management

The system is no longer a backend-only prototype. It already includes:

- login / register / logout
- HttpOnly cookie authentication
- chat session management
- SSE streaming answers
- multi-turn follow-up question rewriting
- per-user knowledge base isolation

## Current Stack

- Backend: Java 17, Spring Boot 3.5, MyBatis-Plus, LangChain4j 1.12
- Frontend: Vue 3, Vite, Pinia, Vue Router, Element Plus
- Database: PostgreSQL 16 + pgvector
- Knowledge graph: Apache Jena TDB2
- AI provider: SiliconFlow

Current default model configuration comes from [application.yml](E:/Github_project/LangChain4j-KGQA/backend/src/main/resources/application.yml):

- embedding model: `Qwen/Qwen3-Embedding-8B`
- chat model: `MiniMaxAI/MiniMax-M2.5`

Do not assume older docs mentioning `DeepSeek-V3.2`, `tdb_medqa`, or `wikidata` are still valid.

## What Is Actually Implemented

### Frontend

Routes in [router/index.js](E:/Github_project/LangChain4j-KGQA/frontend/src/router/index.js):

- `/login`
- `/`
- `/knowledge`
- `/history`

Main frontend responsibilities:

- auth flow and session restoration
- chat streaming UI
- source rendering
- session sorting by latest activity
- knowledge upload and deletion

### Backend API

Implemented controllers:

- [AuthController.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/controller/AuthController.java)
- [QAController.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/controller/QAController.java)
- [ChatHistoryController.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/controller/ChatHistoryController.java)
- [KnowledgeController.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/controller/KnowledgeController.java)
- [DataImportController.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/controller/DataImportController.java)
- [QaDebugController.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/controller/QaDebugController.java)

Important API paths:

- `POST /api/auth/login`
- `POST /api/auth/register`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `POST /api/qa/chat`
- `POST /api/qa/chat/stream`
- `GET /api/qa/sessions`
- `DELETE /api/qa/sessions/{id}`
- `GET /api/chat/history/{sessionId}`
- `DELETE /api/chat/history/{sessionId}`
- `POST /api/knowledge/upload`
- `GET /api/knowledge/list`
- `DELETE /api/knowledge/{id}`
- `GET /api/knowledge/{id}/status`

There is no current `/api/qa/hybrid` controller endpoint, even though older docs may mention it.

## Retrieval Architecture

### RAG Path

Current behavior:

1. uploaded documents are parsed with Apache Tika
2. content is chunked
3. embeddings are stored in pgvector
4. top-k chunks are retrieved by semantic similarity
5. LangChain4j chat model generates the final answer

Primary code:

- [RAGPipelineImpl.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/service/rag/impl/RAGPipelineImpl.java)
- [VectorStoreManagerImpl.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/service/rag/impl/VectorStoreManagerImpl.java)

### Knowledge Graph Path

Current behavior:

1. extract medical entities from the question
2. try SPARQL template matching first
3. fall back to LLM-generated SPARQL if needed
4. query the local TDB2 dataset
5. format results into natural language

Primary code:

- [HybridQAServiceImpl.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/service/qa/impl/HybridQAServiceImpl.java)
- [MedicalEntityExtractorImpl.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/service/qa/impl/MedicalEntityExtractorImpl.java)
- [SPARQLTemplateMatcherImpl.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/service/sparql/impl/SPARQLTemplateMatcherImpl.java)
- [QueryExecutorImpl.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/service/sparql/impl/QueryExecutorImpl.java)

## Multi-turn Memory

Multi-turn support is already implemented in two layers:

1. RAG prompt history:
   recent messages are appended into the current question context.
2. follow-up entity resolution:
   if the current question lacks an explicit entity, backend logic rewrites it using the latest entity found in recent user messages.

Example:

- turn 1: `胃溃疡有什么症状？`
- turn 2: `有什么药可以治疗？`
- rewritten form: `胃溃疡用什么药`

This logic currently lives in [HybridQAServiceImpl.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/service/qa/impl/HybridQAServiceImpl.java).

## Authentication Model

Authentication is cookie-based now.

- backend issues an `HttpOnly` auth cookie
- frontend uses `withCredentials: true`
- frontend may cache non-sensitive user profile and expiry metadata locally
- token itself should not be reintroduced into `localStorage`

Relevant files:

- [AuthController.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/controller/AuthController.java)
- [TokenAuthInterceptor.java](E:/Github_project/LangChain4j-KGQA/backend/src/main/java/com/kgqa/config/TokenAuthInterceptor.java)
- [auth.js](E:/Github_project/LangChain4j-KGQA/frontend/src/stores/auth.js)

## Data and Paths

### PostgreSQL schema

Defined in [schema.sql](E:/Github_project/LangChain4j-KGQA/backend/src/main/resources/schema.sql).

Core tables:

- `app_user`
- `knowledge_base`
- `knowledge_chunk`
- `chat_session`
- `chat_message`

The `embeddings` table is managed through LangChain4j / pgvector integration.

### TDB dataset

Current default path:

- `E:/Github_project/LangChain4j-KGQA/apache jena/tdb_drug_new`

Important:

- only `kgdrug` is in scope now
- do not reintroduce `wikidata` assumptions unless the user explicitly asks for it
- TDB lock files may appear when another process is holding the dataset

## Build and Run

### Backend

```bash
cd backend
mvn clean compile
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

### Verification

```bash
cd backend
mvn clean compile

cd frontend
npm run build
```

## Agent Notes

- Prefer updating root `README.md` over `frontend/README.md` unless the frontend-specific setup truly changed.
- Treat `README.md` and this file as living docs; older statements about architecture are not authoritative.
- When changing auth, streaming, session behavior, or retrieval routing, update these docs because they are easy to drift.
- If you touch TDB-related code, check current config in [application.yml](E:/Github_project/LangChain4j-KGQA/backend/src/main/resources/application.yml) before making assumptions.
- If you touch frontend chat behavior, inspect both [ChatView.vue](E:/Github_project/LangChain4j-KGQA/frontend/src/views/ChatView.vue) and [chat.js](E:/Github_project/LangChain4j-KGQA/frontend/src/stores/chat.js).
