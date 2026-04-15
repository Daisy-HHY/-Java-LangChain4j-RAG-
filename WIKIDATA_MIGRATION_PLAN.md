# Wikidata 数据迁移与 RAG + SPARQL 重构计划

## 一、项目概述

### 1.1 目标
基于 Wikidata 抽取的医疗数据集，重构现有 RAG + SPARQL 混合问答系统。

### 1.2 数据源
**Wikidata TTL 文件位置**: `E:/Github_project/LangChain4j-KGQA/wikidata/`

| 文件 | 内容 | 行数 |
|------|------|------|
| disease.ttl | 疾病实体 | 5,103 |
| medication.ttl | 药物实体 | 2,330 |
| disease_drug.ttl | 疾病→药物关系 (P2176) | 1,724 |
| disease_cause.ttl | 疾病→病因关系 (P828) | 1,465 |
| disease_symptom.ttl | 疾病→症状关系 (P780) | 1,282 |
| infectiousdisease.ttl | 传染病数据 | 1,208 |
| disease_exam.ttl | 疾病→检查关系 (P923) | 376 |
| disease_complication.ttl | 疾病→并发症关系 (P1542) | 255 |

### 1.3 体系架构

```
┌─────────────────────────────────────────────────────────────┐
│                      用户问题                                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   HybridQAService                           │
│  ┌─────────────────────┐    ┌─────────────────────────┐   │
│  │   SPARQL 查询路径   │    │     RAG 语义搜索路径    │   │
│  └─────────────────────┘    └─────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
         │                              │
         ▼                              ▼
┌─────────────────┐            ┌─────────────────────────┐
│     TDB2        │            │      pgvector            │
│  (Jena 知识图谱)  │            │    (向量数据库)          │
│                 │            │                         │
│ 疾病/症状/药物    │            │  中文文本向量             │
│  三元组关系       │            │  (label 描述)           │
└─────────────────┘            └─────────────────────────┘
```

---

## 二、数据存储设计

### 2.1 TDB2 存储设计

**位置**: `E:/Github_project/LangChain4j-KGQA/tdb_wikidata`

**存储内容**:
- 疾病实体 (disease.ttl)
- 药物实体 (medication.ttl)
- 关系三元组 (disease_drug, disease_symptom, disease_cause, etc.)

**本体 URI 设计** (沿用现有 medical_ontology):
```
http://kgqa.com/medical#Disease      # 疾病类
http://kgqa.com/medical#Symptom     # 症状类
http://kgqa.com/medical#Drug        # 药物类
http://kgqa.com/medical#hasSymptom  # 关系属性
http://kgqa.com/medical#treatedBy    # 关系属性
```

### 2.2 pgvector 存储设计

**表名**: `embeddings`

**存储内容**: 从 TTL 提取的中文文本向量

**RAG 文本来源**:
```ttl
# 从 disease_symptom.ttl 提取
"疾病 失眠 的症状包括 寡尿"
"疾病 眼部受傷 的症状包括 眼部受傷"

# 从 disease_drug.ttl 提取
"疾病 外滲 可以用药物治疗 玻璃酸酶"
```

---

## 三、数据导入流程

### 3.1 步骤 1: 加载 TTL 到 TDB2

**工具**: Apache Jena tdb2.tdbloader

**命令**:
```bash
# 设置 JENA_HOME
export JENA_HOME=/path/to/apache-jena-4.9.0

# 创建 TDB2 数据集
tdb2.tdbloader --loc=E:/Github_project/LangChain4j-KGQA/tdb_wikidata \
  E:/Github_project/LangChain4j-KGQA/wikidata/disease.ttl \
  E:/Github_project/LangChain4j-KGQA/wikidata/medication.ttl \
  E:/Github_project/LangChain4j-KGQA/wikidata/disease_symptom.ttl \
  E:/Github_project/LangChain4j-KGQA/wikidata/disease_drug.ttl \
  E:/Github_project/LangChain4j-KGQA/wikidata/disease_cause.ttl \
  E:/Github_project/LangChain4j-KGQA/wikidata/disease_exam.ttl \
  E:/Github_project/LangChain4j-KGQA/wikidata/disease_complication.ttl \
  E:/Github_project/LangChain4j-KGQA/wikidata/infectiousdisease.ttl
```

### 3.2 步骤 2: 清空 pgvector 向量

**SQL**:
```sql
TRUNCATE TABLE embeddings;
```

### 3.3 步骤 3: 构建 RAG 文本并向量化

**文本提取策略**:
1. 从 `disease_symptom.ttl` 提取 "疾病 X 的症状是 Y"
2. 从 `disease_drug.ttl` 提取 "疾病 X 可以用药物治疗 Y"
3. 从 `disease_cause.ttl` 提取 "疾病 X 的病因是 Y"
4. 从 `disease_exam.ttl` 提取 "疾病 X 需要做的检查是 Y"

---

## 四、代码改造计划

### 4.1 配置文件修改

**文件**: `backend/src/main/resources/application.yml`

```yaml
kgqa:
  tdb:
    path: E:/Github_project/LangChain4j-KGQA/tdb_wikidata  # 改为 wikidata TDB
  wikidata:
    path: E:/Github_project/LangChain4j-KGQA/wikidata      # 新增
```

### 4.2 新增服务

#### 4.2.1 WikidataDataImporter

**职责**:
- 从 TTL 文件提取中文 label
- 构建 RAG 文本
- 批量写入 pgvector

**文件位置**: `com.kgqa.data.WikidataDataImporter`

```java
public class WikidataDataImporter {
    // 1. 解析 TTL 文件
    // 2. 提取 rdfs:label @zh 的文本
    // 3. 构建问答对文本
    // 4. 调用 EmbeddingModel 向量化
    // 5. 写入 pgvector
}
```

#### 4.2.2 WikidataTdbLoader

**职责**:
- 将 TTL 文件加载到 TDB2
- 提供增量加载能力

**文件位置**: `com.kgqa.kg.WikidataTdbLoader`

### 4.3 修改现有文件

#### 4.3.1 MedicalOntology.java

**修改**: 添加 Wikidata 属性 URI 常量

```java
// Wikidata 属性映射
wdt:P780  -> hasSymptom
wdt:P2176 -> treatedBy
wdt:P828  -> causedBy
wdt:P1542 -> hasComplication
wdt:P923  -> requiresExam
```

#### 4.3.2 SPARQLTemplateMatcherImpl.java

**修改**: 适配 Wikidata URI 格式

当前模板:
```
SELECT ?disease WHERE { ?disease <http://kgqa.com/medical#hasSymptom> ?symptom }
```

需改为支持 Wikidata 属性查询。

#### 4.3.3 DataInitializer.java

**修改**:
- 移除 MedQA 初始化逻辑
- 改为 Wikidata 数据初始化
- 调用 WikidataDataImporter

---

## 五、问答流程

### 5.1 混合问答流程

```
用户问题: "冠心病的症状有哪些？"
    │
    ├─► SPARQL 路径
    │      │
    │      ▼
    │   意图识别 → 查询疾病症状
    │      │
    │      ▼
    │   SPARQL: SELECT ?symptom WHERE {
    │     ?disease a medical:Disease ;
    │              medical:label "冠心病"@zh ;
    │              medical:hasSymptom ?symptom .
    │   }
    │      │
    │      ▼
    │   返回: [胸痛, 心悸, 呼吸困难]
    │
    └─► RAG 路径
           │
           ▼
        向量搜索: "冠心病的症状"
           │
           ▼
        Top-K 文本片段
           │
           ▼
        返回: "冠心病是一种心脏疾病，症状包括胸痛、心悸等"
```

### 5.2 结果合并

```java
public class HybridQAService {
    public ChatResponse chat(ChatRequest request) {
        // 1. SPARQL 查询结构化结果
        List<String> kgResults = sparqlQuery(query);

        // 2. RAG 语义搜索
        List<SourceItem> ragResults = vectorSearch(query, topK);

        // 3. 合并结果
        String combinedContext = mergeContexts(kgResults, ragResults);

        // 4. LLM 生成答案
        String answer = llm.generate(combinedContext, query);

        return new ChatResponse(answer, sources);
    }
}
```

---

## 六、执行步骤

### Phase 1: 环境准备
- [ ] 确认 Apache Jena CLI 工具可用 (tdb2.tdbloader)
- [ ] 确认 PostgreSQL + pgvector 运行正常
- [ ] 确认 SiliconFlow API Key 配置正确

### Phase 2: TDB2 数据加载
- [ ] 创建 `tdb_wikidata` 目录
- [ ] 执行 tdb2.tdbloader 导入所有 TTL
- [ ] 验证导入: SPARQL 查询疾病症状

### Phase 3: 向量数据导入
- [ ] 编写 WikidataDataImporter
- [ ] 从 TTL 提取中文 RAG 文本
- [ ] 向量化并写入 pgvector
- [ ] 验证向量搜索

### Phase 4: 代码重构
- [ ] 更新 application.yml 配置
- [ ] 修改 MedicalOntology.java
- [ ] 更新 SPARQLTemplateMatcherImpl
- [ ] 修改 DataInitializer
- [ ] 更新 HybridQAServiceImpl

### Phase 5: 测试验证
- [ ] 测试 SPARQL 查询
- [ ] 测试 RAG 向量搜索
- [ ] 测试混合问答
- [ ] 端到端验证

---

## 七、关键文件清单

### 7.1 配置类
- `application.yml` - 更新 TDB 路径

### 7.2 实体类
- `MedicalOntology.java` - Wikidata 属性常量

### 7.3 服务类
- `WikidataDataImporter.java` - 新增: TTL 解析与向量化
- `WikidataTdbLoader.java` - 新增: TDB2 数据加载
- `DataInitializer.java` - 修改: 移除 MedQA，改用 Wikidata
- `HybridQAServiceImpl.java` - 修改: 适配新数据源

### 7.4 SPARQL 类
- `SPARQLTemplateMatcherImpl.java` - 修改: 适配 Wikidata URIs
- `QueryExecutorImpl.java` - 已修复: 使用 TdbManager 避免文件锁

---

## 八、SPARQL 查询示例

### 8.1 查询疾病症状
```sparql
PREFIX medical: <http://kgqa.com/medical#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT ?symptomLabel WHERE {
  ?disease a medical:Disease ;
           rdfs:label "失眠"@zh ;
           medical:hasSymptom ?symptom .
  ?symptom rdfs:label ?symptomLabel .
}
```

### 8.2 查询疾病用药
```sparql
PREFIX medical: <http://kgqa.com/medical#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT ?drugLabel WHERE {
  ?disease a medical:Disease ;
           rdfs:label "热性痉挛"@zh ;
           medical:treatedBy ?drug .
  ?drug rdfs:label ?drugLabel .
}
```

### 8.3 查询疾病病因
```sparql
PREFIX medical: <http://kgqa.com/medical#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT ?causeLabel WHERE {
  ?disease a medical:Disease ;
           rdfs:label "锆肉芽肿"@zh ;
           medical:causedBy ?cause .
  ?cause rdfs:label ?causeLabel .
}
```

---

## 九、注意事项

1. **TDB 文件锁**: 确保只有一个进程访问 TDB，使用 TdbManager.readTransaction()
2. **Wikidata URI 映射**: 需要将 wdt:P780 等映射到 medical:hasSymptom
3. **中文语言标签**: Wikidata TTL 使用 `@zh` 语言标签，查询时需注意
4. **向量去重**: 已有 MedQA 向量需清空或重新导入 Wikidata 数据
5. **API Key**: 确保 SILICONFLOW_API_KEY 环境变量已配置
