# 混合 RAG + Text2SPARQL 医疗问答系统 - 包结构重构计划

## 重构目标

1. **采用接口-实现类模式**：所有核心服务使用 `*Service` 接口 + `*ServiceImpl` 实现
2. **按功能分离包**：`service.rag`、`service.sparql`、`service.qa` 三个包

---

## 新包结构设计

### com.kgqa.service.rag（RAG 相关服务）

| 接口 | 实现类 | 职责 |
|------|--------|------|
| `RAGPipeline` | `RAGPipelineImpl` | RAG 检索 + LLM 生成答案 |
| `VectorStoreManager` | `VectorStoreManagerImpl` | 向量存储管理 |
| `EmbeddingService` | `EmbeddingServiceImpl` | 嵌入模型调用 |
| `ChatMemoryService` | `ChatMemoryServiceImpl` | 对话历史管理 |
| - | `TextSplitter` | 文本分块（无接口，保持原样）|

### com.kgqa.service.sparql（Text2SPARQL 相关服务）

| 接口 | 实现类 | 职责 |
|------|--------|------|
| `QueryExecutor` | `QueryExecutorImpl` | SPARQL 查询执行 |
| - | `TripleExtractor` | 三元组抽取（无接口）|
| `SPARQLTemplateMatcher` | `SPARQLTemplateMatcherImpl` | SPARQL 模板匹配 |
| `SPARQLGenerator` | `SPARQLGeneratorImpl` | LLM 生成 SPARQL |

### com.kgqa.service.qa（问答编排服务）

| 接口 | 实现类 | 职责 |
|------|--------|------|
| `IntentDetectionService` | `IntentDetectionServiceImpl` | 意图识别 |
| `MedicalEntityExtractor` | `MedicalEntityExtractorImpl` | 医学实体抽取 |
| `HybridQAService` | `HybridQAServiceImpl` | 混合问答编排 |

### com.kgqa.service（保持不变的原有服务）

| 类 | 职责 |
|----|------|
| `KnowledgeService` | 文档上传处理 |
| `DataImportService` | TDB/CSV 导入 |
| `MedQAImportService` | MedQA 数据导入 |
| `TextBookImportService` | 教科书导入 |

---

## 重构步骤

### Step 1: 创建新包
- 创建 `com.kgqa.service.rag` 包
- 创建 `com.kgqa.service.sparql` 包
- 创建 `com.kgqa.service.qa` 包

### Step 2: 移动并重构 RAG 相关服务
- 将 `VectorStoreManager` → `VectorStoreManager` (接口) + `VectorStoreManagerImpl`
- 将 `RAGPipeline` → `RAGPipeline` (接口) + `RAGPipelineImpl`
- 将 `EmbeddingService` → `EmbeddingService` (接口) + `EmbeddingServiceImpl`
- 将 `ChatMemoryService` → `ChatMemoryService` (接口) + `ChatMemoryServiceImpl`
- 移动 TextSplitter 到 `service.rag`

### Step 3: 移动并重构 SPARQL 相关服务
- 将 `QueryExecutor` → `QueryExecutor` (接口) + `QueryExecutorImpl`
- 移动 `TripleExtractor` 到 `service.sparql`
- 将 `SPARQLTemplateMatcher` → `SPARQLTemplateMatcher` (接口) + `SPARQLTemplateMatcherImpl`
- 将 `SPARQLGenerator` → `SPARQLGenerator` (接口) + `SPARQLGeneratorImpl`

### Step 4: 移动并重构 QA 相关服务
- 将 `IntentDetectionService` → `IntentDetectionService` (接口) + `IntentDetectionServiceImpl`
- 将 `MedicalEntityExtractor` → `MedicalEntityExtractor` (接口) + `MedicalEntityExtractorImpl`
- 将 `HybridQAService` → `HybridQAService` (接口) + `HybridQAServiceImpl`

### Step 5: 更新 Controller 依赖注入
- 更新 `QAController` 注入 `HybridQAService`（接口）
- 更新其他使用这些服务的 Controller

### Step 6: 删除旧文件
- 删除旧的 `com.kgqa.rag` 包
- 删除旧的服务文件

---

## 需要创建的新文件

### service.rag 包
- `VectorStoreManager.java` (接口)
- `VectorStoreManagerImpl.java`
- `RAGPipeline.java` (接口)
- `RAGPipelineImpl.java`
- `EmbeddingService.java` (接口)
- `EmbeddingServiceImpl.java`
- `ChatMemoryService.java` (接口)
- `ChatMemoryServiceImpl.java`
- `TextSplitter.java` (移动)

### service.sparql 包
- `QueryExecutor.java` (接口)
- `QueryExecutorImpl.java`
- `TripleExtractor.java` (移动)
- `SPARQLTemplateMatcher.java` (接口)
- `SPARQLTemplateMatcherImpl.java`
- `SPARQLGenerator.java` (接口)
- `SPARQLGeneratorImpl.java`

### service.qa 包
- `IntentDetectionService.java` (接口)
- `IntentDetectionServiceImpl.java`
- `MedicalEntityExtractor.java` (接口)
- `MedicalEntityExtractorImpl.java`
- `HybridQAService.java` (接口)
- `HybridQAServiceImpl.java`

---

## 需要删除的旧文件
- `service/HybridQAService.java`
- `service/IntentDetectionService.java`
- `service/MedicalEntityExtractor.java`
- `service/SPARQLTemplateMatcher.java`
- `service/SPARQLGenerator.java`
- `service/QueryExecutor.java`
- `service/TripleExtractor.java`
- `service/EmbeddingService.java`
- `service/ChatMemoryService.java`
- `service/RAGPipeline.java`
- `rag/VectorStoreManager.java`
- `rag/TextSplitter.java`
- `service/QAService.java` (可选)

---

## 需要更新的文件
- `controller/QAController.java` - 改用 `HybridQAService`（接口）
- `config/VectorStoreInitializer.java` - 改用 `VectorStoreManager`（接口）

---

## 验证方案

1. 编译检查：`mvn clean compile`
2. 启动应用：`mvn spring-boot:run`
3. 测试问答：`curl -X POST http://localhost:8080/api/qa/chat -d '{"question":"糖尿病的诊断标准"}'`
4. 确认包结构：`find src/main/java -name "*.java" | grep service`
