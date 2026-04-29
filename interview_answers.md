# 面试题答案整理

> 本文档基于简历技能定制的面试题及参考答案

---

## 一、Java基础与框架

---

### 1. 你提到熟悉多线程，能说说你在线程池方面是怎么用的吗？

**考察点：ThreadPoolExecutor 参数调优、线程池分类、实际项目中的应用**

**参考答案：**

```java
// 实际项目中，我用 ThreadPoolExecutor 做过异步任务处理

public class AsyncService {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        4,                      // corePoolSize：核心线程数
        8,                      // maximumPoolSize：最大线程数
        60L, TimeUnit.SECONDS,   // 空闲线程存活时间
        new LinkedBlockingQueue<>(100),  // 阻塞队列
        new ThreadFactoryBuilder().setNameFormat("async-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：调用者执行
    );

    public CompletableFuture<String> asyncProcess(String task) {
        return CompletableFuture.supplyAsync(() -> {
            // 业务逻辑
            return result;
        }, executor);
    }
}
```

**参数选择依据：**
- **CPU密集型任务**（计算、加密）：corePoolSize = CPU核数 + 1
- **IO密集型任务**（网络请求、文件读写）：corePoolSize = CPU核数 × 2 或更多

**追问回答（CallerRunsPolicy的影响）：**
- 当线程池满了且队列满了，新任务由调用线程执行
- 优点：不会丢失任务
- 缺点：可能拖慢主线程，适合对性能要求不高但要求任务不丢失的场景

---

### 2. Spring Boot 如何实现 Bean 的生命周期管理？

**考察点：@PostConstruct、InitializingBean、BeanPostProcessor**

**参考答案：**

Spring Bean 生命周期主要有以下几种方式：

```java
// 方式1：@PostConstruct 注解（最简单）
@Component
public class TdbManager {
    @PostConstruct
    public void init() {
        // 在构造函数之后、依赖注入完成后调用
        System.out.println("TDB 初始化连接");
    }
}

// 方式2：实现 InitializingBean 接口
@Component
public class VectorStoreManager implements InitializingBean {
    @Override
    public void afterPropertiesSet() throws Exception {
        // 同样的效果，比 @PostConstruct 更明确
    }
}

// 方式3：BeanPostProcessor（全局扩展）
@Component
public class MyBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // 所有 bean 初始化之前调用
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // 所有 bean 初始化之后调用
        return bean;
    }
}
```

**应用场景：**
- @PostConstruct：初始化 TDB 连接、预热缓存、加载配置
- InitializingBean：做强制检查（比如某个属性必须存在）
- BeanPostProcessor：实现通用逻辑，比如所有 bean 加上日志、事务增强

---

### 3. 你用过哪些设计模式？项目中哪里用到了？

**考察点：单例、工厂、代理、策略、模板方法**

**参考答案：**

**项目中实际用到的：**

```java
// 1. 单例模式 - TdbManager
@Component
public class TdbManager {
    private static TdbManager instance;

    @PostConstruct
    public void init() {
        instance = this;
    }

    public static TdbManager getInstance() {
        return instance;
    }
}

// 2. 代理模式 - @Transactional 原理
// Spring AOP 就是动态代理，在方法前后加事务/日志/缓存

// 3. 策略模式 - 意图检测路由
public interface IntentStrategy {
    Answer execute(String question);
}

@Service
public class FACT_QUERY_Strategy implements IntentStrategy {
    @Override
    public Answer execute(String question) {
        return sparqlQuery.query(question);  // 走 SPARQL
    }
}

@Service
public class OPEN_QUESTION_Strategy implements IntentStrategy {
    @Override
    public Answer execute(String question) {
        return ragPipeline.answer(question);  // 走 RAG
    }
}

// 4. 模板方法模式 - RAG Pipeline
public abstract class RAGPipeline {
    public final Answer answer(String question) {
        String embedded = embed(question);    // 模板：向量化
        List<Chunk> chunks = retrieve(embedded); // 模板：检索
        String prompt = buildPrompt(chunks);  // 模板：构建 prompt
        return generate(prompt);              // 钩子：生成答案
    }
}
```

---

## 二、数据库相关

---

### 4. MySQL 和 PostgreSQL 在索引实现上有什么区别？

**考察点：PostgreSQL 支持多种索引类型（B-tree、Hash、GIN、GiST）**

**参考答案：**

| 特性 | MySQL | PostgreSQL |
|------|-------|------------|
| 主键索引 | BTREE | BTREE（默认） |
| 唯一索引 | BTREE | BTREE |
| 全文索引 | InnoDB FULLTEXT | GIN（tsvector） |
| 空间索引 | RTREE | GiST |
| 向量索引 | 不支持（需插件） | pgvector 支持 IVFFlat、HNSW |

**pgvector 的索引类型：**
```sql
-- 1. IVFFlat（倒排文件索引）
-- 适合大数据量，召回率 vs 速度的平衡
CREATE INDEX ON embeddings USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- 2. HNSW（层次可导航小世界图）
-- 更高召回率，但占用更多内存
CREATE INDEX ON embeddings USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 200);
```

**为什么选择 pgvector：**
- 集成在 PostgreSQL 中，运维简单
- 与主数据库一体化，方便 JOIN
- 支持混合查询（向量 + 结构化条件）

---

### 5. Redis 缓存击穿、穿透、雪崩分别是什么？如何解决？

**考察点：缓存经典问题**

**参考答案：**

```
┌─────────────────────────────────────────────────────────────┐
│  缓存击穿：某个热点 key 突然过期，大量请求打到 DB              │
│  缓存穿透：查询不存在的数据，缓存没有，DB 也没有               │
│  缓存雪崩：大量 key 同时过期，导致大量请求同时打 DB             │
└─────────────────────────────────────────────────────────────┘
```

**解决方案：**

```java
// 1. 缓存击穿：互斥锁 / 永不过期 + 异步更新
public String getCache(String key) {
    String value = redis.get(key);
    if (value == null) {
        // 互斥锁，只允许一个线程查 DB
        if (redis.setnx(key + "_lock", "1", 30)) {
            value = db.query(key);
            redis.setex(key, 3600, value);
            redis.del(key + "_lock");
        } else {
            // 等待后重试
            Thread.sleep(50);
            return getCache(key);
        }
    }
    return value;
}

// 2. 缓存穿透：布隆过滤器 / 空值缓存
public String getCache(String key) {
    if (!bloomFilter.mightContain(key)) {
        return null; // 一定不存在
    }
    // ... 正常查询
}

// 3. 缓存雪崩：过期时间随机化 + 多级缓存
redis.setex(key, 3600 + random.nextInt(300), value);
```

**追问回答（我的项目中的过期策略）：**
- 首页热门商品：过期时间 5 分钟 + 主动刷新
- 用户 Session：过期时间 30 分钟
- 配置类数据：过期时间 24 小时

---

### 6. SQL 优化你一般怎么做？EXPLAIN 关注哪些字段？

**考察点：慢查询优化、索引失效分析**

**参考答案：**

**EXPLAIN 关键字段：**
```sql
EXPLAIN SELECT * FROM knowledge_chunk WHERE disease_id = 1;

-- 关键字段：
type:     ALL(全表扫描) < index < range < ref < eq_ref < const
key:      实际使用的索引
rows:     扫描行数，越少越好
extra:    Using filesort / Using temporary / Using index
```

**优化步骤：**
```sql
-- 1. 开启慢查询日志
SHOW VARIABLES LIKE 'slow_query_log%';

-- 2. 定位慢查询
SELECT * FROM performance_schema.events_statements_summary
WHERE SUMMARY_TIMER_WAIT > 1000000000 ORDER BY SUM_TIMER_WAIT DESC;

-- 3. 分析执行计划
EXPLAIN ANALYZE SELECT ...  -- MySQL 8.0+

-- 4. 常见优化
-- 4.1 避免 SELECT *，只查需要的字段
-- 4.2 避免 LIKE '%xxx%'（无法用索引）
-- 4.3 用 LIMIT 限制返回行数
-- 4.4 避免隐式类型转换
```

**联合索引 (a,b,c) vs (a,c,b) 的区别：**
```sql
-- (a,b,c) 索引可以命中：
WHERE a = 1           -- ✓ 走索引
WHERE a = 1 AND b = 2 -- ✓ 走索引
WHERE a = 1 AND c = 3 -- ✗ a 能用，c 用不到

-- 选择性：区分度大的列放前面
```

---

## 三、AI应用开发（核心！）

---

### 7. LangChain4j 相比直接调用 API，有什么优势？

**考察点：对 LangChain4j 框架的理解**

**参考答案：**

| 维度 | 直接调用 API | LangChain4j |
|------|------------|-------------|
| **代码量** | 手动处理 HTTP、JSON、错误重试 | 一行代码搞定 |
| **模型切换** | 每个模型写一套代码 | 换配置即可 |
| **工具调用** | 手动解析 function call | @Tool 注解 |
| **RAG 支持** | 从零实现 | 内置 Retriever、Embedding |
| **调试** | 黑盒 | 有中间件可观察 |

**实际使用感受：**
```java
// 直接调用 DeepSeek API（繁琐）
OkHttpClient client = new OkHttpClient();
Request request = new Request.Builder()
    .url("https://api.siliconflow.cn/v1/chat/completions")
    .header("Authorization", "Bearer " + apiKey)
    .post(RequestBody.create(json, MediaType.parse("application/json")))
    .build();

// LangChain4j 调用（简洁）
ChatLanguageModel model = new SiliconFlowApiModel(apiKey);
AiServices services = AiServices.builder(MedicalAssistant.class)
    .chatLanguageModel(model)
    .retriever(vectorStore.retriever())
    .build();
```

**LangChain4j 的核心概念：**
- **ChatLanguageModel**：统一接口对接各种 LLM
- **Retriever**：从向量库检索文档
- **PromptTemplate**：模板化 prompt
- **AiServices**：自动绑定 Tool 和方法

---

### 8. RAG 架构中，向量检索和知识图谱 SPARQL 查询各适合什么场景？

**考察点：RAG + KG 混合架构的理解**

**参考答案：**

| 场景 | 向量检索 | SPARQL 查询 |
|------|---------|------------|
| **数据形式** | 非结构化文本 | 结构化三元组 |
| **查询方式** | 语义相似度 | 精确关系匹配 |
| **适合问题** | "感冒了怎么办？" | "阿司匹林能治什么病？" |
| **优势** | 理解自然语言 | 精确、多跳推理 |
| **劣势** | 可能胡编 | 依赖数据完整性 |

**项目中的混合策略：**
```java
public Answer hybridAnswer(String question) {
    // 1. 意图检测
    Intent intent = intentDetection.detect(question);

    // 2. 实体抽取
    List<Entity> entities = entityExtractor.extract(question);

    // 3. 路由决策
    if (intent == FACT_QUERY && hasEntities(entities)) {
        // 精确事实类 → SPARQL
        try {
            result = sparqlQuery.query(question, entities);
            if (result.isNotEmpty()) {
                return buildAnswer(result);
            }
        } catch (Exception e) {
            log.warn("SPARQL failed, fallback to RAG");
        }
    }

    // 4. 降级到 RAG
    return ragPipeline.answer(question);
}
```

**什么时候用向量检索？**
- 开放性医学问题："高血糖饮食要注意什么？"
- 解释性问题："为什么会感冒？"
- 没有明确实体的问题

**什么时候用 SPARQL？**
- 事实类查询："阿司匹林的副作用是什么？"
- 需要精确匹配的问题
- 多跳关系查询："这个药能治什么病？这个病有什么症状？"

---

### 9. 你的意图检测是怎么实现的？用了什么方法？

**考察点：LLM 分类 vs 关键词匹配**

**参考答案：**

**两种方案结合：**

```java
@Service
public class IntentDetectionService {

    // 方案1：关键词快速匹配（备用）
    private static final Map<String, Intent> KEYWORD_MAP = Map.of(
        "症状", Intent.FACT_QUERY,
        "治疗", Intent.FACT_QUERY,
        "什么原因", Intent.EXPLANATION,
        "为什么", Intent.EXPLANATION,
        "区别", Intent.COMPARISON
    );

    // 方案2：LLM 智能分类（主方案）
    @Autowired
    private ChatLanguageModel chatModel;

    public Intent detect(String question) {
        // 优先关键词匹配
        for (Map.Entry<String, Intent> entry : KEYWORD_MAP.entrySet()) {
            if (question.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 关键词没匹配上，再用 LLM
        String prompt = String.format("""
            判断用户问题的意图类型，只能返回以下一种：
            - FACT_QUERY：事实查询（问疾病症状、药物适应症等）
            - EXPLANATION：解释类问题（问原因、原理）
            - COMPARISON：比较类问题（问两个事物的区别）
            - OPEN_QUESTION：开放性问题

            用户问题：%s
            意图类型：
            """, question);

        String response = chatModel.generate(prompt);
        return parseIntent(response);
    }
}
```

**意图类型定义：**
```java
public enum Intent {
    FACT_QUERY,      // "高血压有什么症状？"
    EXPLANATION,     // "为什么感冒会发烧？"
    COMPARISON,      // "阿司匹林和布洛芬的区别？"
    AGGREGATION,     // "心血管疾病有哪些？"
    OPEN_QUESTION    // "感冒了怎么办？"（直接 RAG）
}
```

**准确率评估：**
- 关键词匹配：简单场景 ~85% 准确率
- LLM 分类：复杂场景 ~92% 准确率
- 两者结合：整体 ~90%+

---

### 10. pgvector 的 Top-K 检索原理是什么？

**考察点：向量索引、相似度计算（余弦相似度/内积）**

**参考答案：**

**向量检索原理：**
```sql
-- 用户问题向量化
SELECT embedding FROM questions WHERE id = 1;
-- 得到：[0.12, -0.34, 0.56, ...]  (1024维)

-- Top-K 相似度检索
SELECT content, 1 - (embedding <=> query_embedding) AS similarity
FROM knowledge_chunk
ORDER BY similarity DESC
LIMIT 5;
-- <=> 是余弦距离运算符
```

**相似度计算方式：**
```sql
-- 1. 余弦相似度（cosine similarity）- 最常用
-- 值范围 [-1, 1]，越接近 1 越相似
SELECT 1 - (vec1 <=> vec2) AS cosine_similarity;

-- 2. 内积（inner product）- 适合归一化向量
-- 值范围 [-维度, 维度]
SELECT vec1 <#> vec2 AS inner_product;

-- 3. 欧几里得距离（Euclidean）
-- 值范围 [0, +∞)，越接近 0 越相似
SELECT vec1 <-> vec2 AS euclidean_distance;
```

**pgvector 索引类型：**
```sql
-- 1. IVFFlat（Inverted File Index）
-- 把向量空间分成 N 个簇，搜索时只搜索最近的几个簇
-- 适合：亿级向量，召回率 vs 速度可调
CREATE INDEX ON items USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);  -- lists 越大越准但越慢

-- 2. HNSW（Hierarchical Navigable Small World）
-- 构建多层图，高层快速定位，低层精确搜索
-- 适合：千万级向量，高召回、低延迟
CREATE INDEX ON items USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 200);
```

**我的项目配置：**
```sql
-- 使用 IVFFlat，lists 根据数据量设置
CREATE INDEX ON embeddings USING ivfflat
(embedding vector_cosine_ops) WITH (lists = 100);

-- 检索时设置 ef 参数控制召回率
SET ivfflat.probes = 10;  -- 搜索的簇数量
```

---

### 11. 知识图谱的本体是如何设计的？drug/disease/symptom 之间是什么关系？

**考察点：RDF/OWL 理解、本体建模**

**参考答案：**

**KGDrug 本体结构（OWL 描述）：**

```turtle
# 类定义
:drug rdf:type owl:Class .
:disease rdf:type owl:Class .
:symptom rdf:type owl:Class .

# 对象属性（关系）
:cute rdf:type owl:ObjectProperty ;    # 治愈（药物→疾病）
    rdfs:domain :drug ;
    rdfs:range :disease .

:haszhengzhuang rdf:type owl:ObjectProperty ;  # 有症状（疾病→症状）
    rdfs:domain :disease ;
    rdfs:range :symptom ;
    owl:inverseOf :relatedisease .   # 与 relatedisease 互为逆关系

:needcure rdf:type owl:ObjectProperty ;  # 需要治愈（疾病→药物）
    owl:inverseOf :cure .            # 与 cure 互为逆关系

:relatedisease rdf:type owl:ObjectProperty ;  # 相关疾病（症状→疾病）
    rdfs:domain :symptom ;
    rdfs:range :disease .

# 数据属性
:proname rdf:type owl:DatatypeProperty ;   # 药品名称
    rdfs:domain :drug .
:jibingname rdf:type owl:DatatypeProperty ; # 疾病名称
    rdfs:domain :disease .
:zzname rdf:type owl:DatatypeProperty ;    # 症状名称
    rdfs:domain :symptom .
```

**实体关系图：**
```
         ┌─────────────────────────────────────────┐
         │                                         │
         ▼                                         │
    ┌────────┐   cure / needcure    ┌───────────┐  │
    │  drug  │◄────────────────────►│  disease  │  │
    └────────┘                      └─────┬─────┘  │
                                          │        │
                              haszhengzhuang /      │
                                 relatedisease       │
                                          │        │
                                          ▼        │
                                    ┌───────────┐  │
                                    │  symptom   │  │
                                    └───────────┘  │
                                          ▲        │
                                          │        │
                                 relatedisease     │
                                          └────────┘
```

**示例三元组：**
```turtle
<阿司匹林> :cure <感冒> .
<感冒> :haszhengzhuang <发烧> .
<感冒> :haszhengzhuang <头痛> .
<阿司匹林> :proname "阿司匹林" .
```

**为什么这样设计：**
- **owl:inverseOf**：定义逆关系，查询"什么药能治感冒"和"感冒需要什么药"都能用
- **rdfs:domain/range**：定义主语和宾语的类型，保证数据一致性
- **对象属性 vs 数据属性**：关系用 ObjectProperty，文本描述用 DatatypeProperty

---

## 四、项目深挖

---

### 12. 你在医疗问答系统中，遇到最难的技术问题是什么？怎么解决的？

**考察点：问题解决能力**

**参考答案：**

**最难的问题：SPARQL 生成质量不可控**

**问题描述：**
- 最开始尝试让 LLM 直接生成 SPARQL 查询"阿司匹林能治什么病"
- 但 LLM 生成的 SPARQL 语法错误率约 30%
- 错误类型：属性名写错、语法结构错误、中文实体没绑定命名空间

**解决方案（4层防护）：**

```java
public Answer hybridAnswer(String question) {
    // 第1层：预定义模板优先
    SPARQLTemplate matched = templateMatcher.match(question);
    if (matched != null) {
        return executeTemplate(matched);
    }

    // 第2层：LLM 生成 + 语法验证
    String generated = llm.generateSPARQL(question);
    if (sparqlValidator.isValid(generated)) {
        try {
            return executeQuery(generated);
        } catch (Exception e) {
            log.warn("Query execution failed");
        }
    }

    // 第3层：查询失败自动降级 RAG
    return ragPipeline.answer(question);
}
```

**效果：**
- 简单问题（能匹配模板）：~95% 准确率
- 复杂问题（需 LLM 生成）：~70% 准确率
- 整体查询成功率：从 60% 提升到 95%

---

### 13. 2.8万向量的规模，你是怎么评估的？向量维度是多少？

**考察点：对向量数据库的理解**

**参考答案：**

**数据规模：**
```
向量总数：~28,000 条
向量维度：1024 维（Qwen3-Embedding-8B 输出）

存储估算：
- 每条向量：1024 × 4 bytes (float32) = 4KB
- 2.8万 × 4KB = ~112MB 向量数据
- 加上索引：~200-300MB

性能测试：
- 单次检索延迟：< 50ms（IVFFlat 索引）
- 支持并发：50-100 用户同时在线
```

**为什么选 2.8万？**
- KGDrug 公开数据集：~5000 条药物/疾病/症状实体
- 每个实体生成 3-5 个知识块（不同角度描述）
- 每个知识块对应 1 个向量
- 总计约 2.8 万向量

**追问回答（向量太多会影响速度吗？）：**
```
- 100 条向量：暴力搜索也很快（<10ms）
- 1 万条：需要索引，IVFFlat 可控制在 30ms 内
- 100 万条：需要分区或分布式
- 我的 2.8 万属于中小规模，IVFFlat 足够
```

---

### 14. SPARQL 查询失败时，你的降级策略是什么？

**考察点：系统稳定性设计**

**参考答案：**

**完整降级链：**
```java
public Answer hybridAnswer(String question) {
    // Step 1: 意图检测 + SPARQL 查询
    if (canUseSPARQL(question)) {
        try {
            Result result = sparqlQuery.execute(question);
            if (result.isNotEmpty()) {
                return buildAnswer(result, AnswerSource.SPARQL);
            }
            // 结果为空，尝试 RAG
            log.info("SPARQL returned empty, fallback to RAG");
        } catch (SPARQLException e) {
            log.warn("SPARQL failed: {}, fallback to RAG", e.getMessage());
        } catch (TDBLockException e) {
            log.error("TDB locked, fallback to RAG");
        }
    }

    // Step 2: RAG 向量检索兜底
    try {
        return ragPipeline.answer(question);
    } catch (Exception e) {
        log.error("RAG also failed", e);
    }

    // Step 3: 最后兜底 - LLM 自由回答（带免责声明）
    return llmFreeAnswer.withDisclaimer(question);
}
```

**降级触发条件：**
| 触发条件 | 处理 |
|---------|------|
| SPARQL 语法错误 | 记录错误，降级 RAG |
| SPARQL 结果为空 | 尝试 RAG 扩大召回 |
| TDB 连接失败 | 降级 RAG + 告警 |
| RAG 检索为空 | LLM 自由回答 + 免责声明 |

**降级时如何保证质量：**
```
1. 记录降级原因和频率，持续优化
2. 定期分析降级case，完善 SPARQL 模板
3. 维护"无法回答"的标准回复模板
```

---

## 五、系统设计与架构

---

### 15. 如果用户问"某种药能治什么病、有什么副作用"，系统怎么处理多跳查询？

**考察点：多跳推理能力**

**参考答案：**

**问题分析：**
```
"阿司匹林能治什么病、有什么副作用？"

跳1：阿司匹林 → 治愈 → 疾病（cure 关系）
跳2：这些疾病 → 有症状 → 副作用（haszhengzhuang 关系）
```

**解决方案1：链式 SPARQL**
```sql
-- 一条 SPARQL 实现多跳
PREFIX drug: <http://www.kgdrug.com#>

SELECT ?disease ?symptom WHERE {
    drug:阿司匹林 drug:cures ?disease .
    ?disease drug:haszhengzhuang ?symptom .
}
```

**解决方案2：LLM 分解问题**
```java
// LLM 判断需要分几步查询
Prompt: """
    用户问题："阿司匹林能治什么病、有什么副作用？"

    分解为子问题：
    1. 阿司匹林能治什么病？→ SPARQL 查询
    2. 这些病有什么症状？→ SPARQL 查询
    3. 合并结果返回
    """
```

**解决方案3：先查适应症，再查症状（项目实际做法）**
```java
public Answer answer(String question) {
    // 识别出药物实体：阿司匹林
    // 识别出意图：问适应症 + 问副作用

    // 第一跳：查适应症
    List<Disease> diseases = sparql.query(
        "SELECT ?d WHERE { drug:阿司匹林 :cure ?d }"
    );

    // 第二跳：查这些疾病的症状
    List<Symptom> symptoms = sparql.query(
        "SELECT ?s WHERE { :感冒 :haszhengzhuang ?s }"
    );

    // 合并结果
    return buildAnswer(diseases, symptoms);
}
```

**效果：**
- 单跳查询：~92% 准确率
- 双跳查询：~78% 准确率
- 三跳及以上：建议拆分为多个问题

---

### 16. 你的系统能支持多少并发用户？响应时间多少？

**考察点：性能评估**

**参考答案：**

**压测数据：**
```
测试环境：
- CPU: 8 核
- 内存: 16GB
- DB: PostgreSQL + pgvector (本地)

测试工具：JMeter
并发用户：1 → 50 → 100

结果：
- 10 并发：平均响应时间 200ms，99% < 500ms
- 50 并发：平均响应时间 350ms，99% < 800ms
- 100 并发：平均响应时间 600ms，99% < 1.5s

瓶颈分析：
1. LLM API 调用延迟（DeepSeek）：平均 1-2 秒（不可控）
2. pgvector 检索：< 50ms
3. SPARQL 查询：< 200ms
4. DB 操作：< 100ms
```

**瓶颈在哪里：**
```
用户请求延迟 = LLM延迟 + 检索延迟 + 网络开销

主要瓶颈：LLM API 响应时间（1-3秒），这是外部依赖，不可控

优化方向：
1. 本地缓存高频问题的 LLM 回答
2. 异步处理 + 前端轮询
3. 流式输出（减少等待感知时间）
```

**扩容方案：**
```
短期：增加 RAG 结果缓存，同一个问题 5 分钟内直接返回缓存
中期：LLM 服务多实例 + 负载均衡
长期：引入消息队列，异步处理大批量请求
```

---

### 17. 怎么防止 RAG 回答"一本正经地胡说八道"？

**考察点：AI 应用的风险控制**

**参考答案：**

**医疗场景的风险控制（4层防护）：**

```java
public Answer answer(String question, double confidenceThreshold) {
    // 第1层：来源可信度
    enum SourceCredibility {
        OFFICIAL_DOC(1.0),   // 官方文档
        PEER_REVIEWED(0.9), // 同行评审
        USER_UPLOAD(0.5)    // 用户上传
    }

    // 第2层：置信度计算
    double confidence = calculateConfidence(vectorSimilarity, sourceWeight);

    // 第3层：不确定时的回复
    if (confidence < confidenceThreshold) {
        return Answer.builder()
            .content("根据现有资料...可能有效，但建议咨询专业医生")
            .confidence(confidence)
            .needsProfessionalConfirmation(true)
            .build();
    }

    // 第4层：危险信号检测
    if (containsRedFlags(question)) {
        return crisisIntervention.handle(question);
    }
}
```

**医疗场景特殊处理：**
```java
// 危险信号关键词检测
private static final Set<String> RED_FLAGS = Set.of(
    "自杀", "自残", "不想活了", "怎么死"
);

private boolean containsRedFlags(String question) {
    return RED_FLAGS.stream().anyMatch(question::contains);
}

private Answer handleCrisis(String question) {
    return Answer.builder()
        .content("如果您有自杀倾向，请拨打心理援助热线：400-821-1212")
        .source(Source.CRISIS_HOTLINE)
        .build();
}
```

**免责声明（必须）：**
```
⚠️ 本系统仅供参考，不能替代专业医生的诊断和治疗。
如有身体不适，请及时就医。
```

---

## 六、学习态度

---

### 18. 你平时是怎么用 Cursor/Trae 提升开发效率的？能举个具体例子吗？

**考察点：AI 辅助编程的实际应用**

**参考答案：**

**实际使用场景：**

```java
// 场景1：快速生成样板代码
// 我：写一个分页查询的 Service
// Cursor：自动生成完整的分页逻辑

// 场景2：调试复杂 bug
// 我：向 Cursor 描述异常信息
// Cursor：分析可能原因，提供排查步骤

// 场景3：学习新框架
// 我：让 Cursor 解释 @Transactional 的原理
// Cursor：给出原理说明 + 示例代码
```

**具体例子：**
```
问题：RAG 返回的答案和原文不一致，不知道哪里出了问题

我和 Cursor 的对话：
Me: "向量检索结果 top-1 的内容和答案不一致"
Cursor: "可能是 prompt 构建的问题，让我看看你的 RAGPipeline 代码"
     → 发现 prompt 中用了 chunks.get(i) 导致下标越界
     → 修正后问题解决

效率提升：原本 2 小时的问题，15 分钟解决
```

**正确使用方式：**
```
1. AI 生成代码 → 我审查 → 不直接复制
2. 复杂逻辑 → 让 AI 解释原理 → 我判断是否正确
3. 遇到错误 → 先描述问题 → AI 分析 → 我验证
```

**不依赖 AI 的原则：**
```
1. 核心业务逻辑必须自己写
2. 安全相关代码必须自己审查
3. AI 只能辅助，不能替代思考
```

---

### 19. 最近你学了什么新技术？怎么学的？

**考察点：学习能力**

**参考答案：**

**最近学习的技术：LangChain4j + 向量数据库**

**学习路径：**

```
第1步：官方文档（2-3天）
- 阅读 LangChain4j 官方文档和示例
- 跑通最简单的 RAG demo

第2步：小项目验证（1周）
- 用 Spring Boot 集成 LangChain4j
- 接入 SiliconFlow API
- 实现最简单的问答功能

第3步：解决实际问题（持续）
- 遇到 pgvector 集成问题 → 查 Stack Overflow
- 遇到 SPARQL 语法问题 → 查 Apache Jena 文档
- 遇到效果不佳 → 看论文找思路

第4步：输出沉淀（持续）
- 写技术笔记
- 和同学讨论
- Code Review 时分享
```

**学习资源推荐：**
```
- 官方文档：https://docs.langchain4j.dev/
- B站视频：某些原理讲得很清楚
- GitHub 示例：star 多的项目
- 英文搜索：Stack Overflow / Reddit
```

---

### 20. 你觉得 AI 编程工具的出现，对你未来的职业发展有什么影响？

**考察点：对 AI 的态度（拥抱 vs 抵触）**

**参考答案：**

**我的观点：AI 是杠杆，放大个人能力**

```
短期影响（1-3年）：
- 重复性编码工作减少
- 学习新技术的门槛降低
- 但核心原理、设计能力更重要

长期影响（5年+）：
- 会用 AI 的 vs 不会用 AI 的，效率差 3-5 倍
- 初级岗位需求减少，中高级岗位需求稳定
- 需要更多关注：架构设计、业务理解、技术决策
```

**我的应对策略：**
```
1. 拥抱变化：把 AI 工具用熟，成为团队中的"AI专家"
2. 夯实基础：算法、系统设计、数据库原理
3. 差异化竞争：业务领域知识（医疗）+ 技术能力
4. 持续学习：保持对新技术的好奇心
```

**不要担心的理由：**
```
AI 能写代码，但不理解业务
AI 能搜索，但不会判断
AI 能生成，但需要人来决策

技术会变，但解决问题的能力不会过时
```

---

## 七、开放讨论

---

### 21. 为什么选择医疗领域做问答系统？

**考察点：动机、兴趣**

**参考答案：**

```
选择医疗领域的原因：

1. 行业价值高
   - 医疗信息需求大，但普通人难以获取准确信息
   - 如果能帮助人们更好地理解健康问题，社会价值大

2. 技术挑战大
   - 医学术语专业性强（同义词、缩写多）
   - 需要知识图谱辅助理解实体关系
   - 容错率低，推动我深入研究技术

3. 个人兴趣
   - 对 AI + 医疗交叉领域感兴趣
   - 家人有慢性病，想通过项目学习相关知识

4. 数据可得性
   - KGDrug 是公开数据集，无需自己采集
   - 医疗问答数据集丰富（MedQA）
```

---

### 22. 如果让你重来一遍，这个项目你会怎么改进？

**考察点：复盘能力、技术判断力**

**参考答案：**

**做得好的地方（继续保持）：**
```
1. RAG + KG 混合架构设计合理
2. 降级策略保证了系统稳定性
3. 使用成熟的 LangChain4j 框架
```

**需要改进的地方：**

```java
// 改进1：数据层面
// 问题：KGDrug 数据量较小（约5000实体）
// 改进：接入更多医疗数据源
//   - MedQA 医学问答数据集
//   - 中医药知识图谱
//   - 药品说明书结构化

// 改进2：检索层面
// 问题：纯向量检索，无法做精确条件过滤
// 改进：混合检索
//   SELECT * FROM medical_kb
//   WHERE embedding <=> ? < 0.3    // 向量相似度
//   AND category = 'drug'          // 结构化条件
//   AND status = 'approved'         // 状态过滤

// 改进3：评估层面
// 问题：没有系统性的效果评估
// 改进：建立评估体系
//   - 准确率：答案与标准答案的匹配度
//   - 召回率：相关文档被召回的比例
//   - ROUGE/Blue 指标：自动化评估生成质量

// 改进4：用户体验层面
// 问题：纯文本回答，不知道来源哪里
// 改进：增加答案溯源
//   - 每个答案标注参考来源
//   - 支持点击查看原文
//   - 显示置信度评分
```

**总结：**
```
"这个项目让我理解了 RAG + KG 的核心原理，
如果要重来，我会在数据质量和评估体系上投入更多，
因为这两点是 AI 应用真正落地时的关键瓶颈。"
```

---

## 参考答案要点速查表

| 问题 | 核心考察点 | 一句话回答 |
|------|-----------|-----------|
| 1. 线程池参数 | ThreadPoolExecutor 调优 | CPU 密集型 N+1，IO 密集型 2N |
| 2. Bean 生命周期 | @PostConstruct, InitializingBean | @PostConstruct 最简单，BeanPostProcessor 最强大 |
| 3. 设计模式 | 项目中实际用到哪些 | 单例( TdbManager)、策略(意图路由)、模板方法(RAG) |
| 4. MySQL vs PG | PostgreSQL 多种索引类型 | pgvector 支持 IVFFlat/HNSW，MySQL 不支持 |
| 5. 缓存问题 | 击穿/穿透/雪崩 | 互斥锁/布隆过滤器/过期时间随机化 |
| 6. SQL 优化 | EXPLAIN 关键字段 | type/keys/rows/extra |
| 7. LangChain4j 优势 | 框架理解 | 统一接口、Tool 调用、内置 RAG 支持 |
| 8. 向量 vs SPARQL | 各自适用场景 | 开放问题用向量，精确事实用 SPARQL |
| 9. 意图检测 | 实现方式 | 关键词 + LLM 双层 |
| 10. pgvector 原理 | Top-K、索引类型 | IVFFlat 分簇搜索，HNSW 图搜索 |
| 11. 本体设计 | RDF/OWL | 类 + 对象属性 + 数据属性 |
| 12. 最难问题 | 解决问题的能力 | SPARQL 生成质量，4 层防护解决 |
| 13. 数据规模 | 评估能力 | 2.8万 × 1024维，约 300MB |
| 14. 降级策略 | 系统稳定性 | SPARQL → RAG → LLM自由回答 |
| 15. 多跳查询 | 图推理能力 | 链式 SPARQL 或 LLM 分解 |
| 16. 并发性能 | 性能评估 | LLM API 是主要瓶颈 |
| 17. 风险控制 | 医疗 AI 安全 | 置信度过滤 + 危险信号检测 + 免责声明 |
| 18. AI 编程 | 实际使用经验 | 辅助而非依赖，审查而非复制 |
| 19. 学习方法 | 学习能力 | 官方文档 → 小项目验证 → 解决实际问题 |
| 20. AI 影响 | 态度和认知 | AI 是杠杆，核心能力不能丢 |
| 21. 为什么医疗 | 动机 | 价值高、挑战大、兴趣驱动 |
| 22. 如何改进 | 复盘 | 数据质量、评估体系、用户体验 |

---

*整理时间：2026-04-19*
*基于简历技能定制，共 22 道核心问题*
