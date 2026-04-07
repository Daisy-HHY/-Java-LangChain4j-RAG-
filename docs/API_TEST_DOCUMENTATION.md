# API 接口测试文档

## 基本信息

| 项目 | 值 |
|------|-----|
| 基础 URL | `http://localhost:8080` |
| 编码 | UTF-8 |
| Content-Type | `application/json` |

---

## 1. 问答 API

### 1.1 发送问题

**接口**: `POST /api/qa/chat`

**Body (raw, JSON)**:
```json
{
  "question": "你好",
  "sessionId": ""
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| question | String | ✅ | 用户问题 |
| sessionId | String | ❌ | 会话ID，空值表示新会话 |

**响应 Body (raw, JSON)**:
```json
{
  "answer": "您好！我是您的医药知识问答助手，很乐意为您解答与医药相关的问题...",
  "sources": [],
  "sessionId": "ed2bbe4e697a4436ad2db4c60abb1d4e"
}
```

---

### 1.2 获取会话列表

**接口**: `GET /api/qa/sessions`

**Body**: 无

**响应 Body (raw, JSON)**:
```json
[
  {
    "id": 1,
    "sessionId": "ed2bbe4e697a4436ad2db4c60abb1d4e",
    "title": "新会话",
    "createdAt": "2026-04-07T11:22:36",
    "updatedAt": "2026-04-07T11:22:36"
  }
]
```

---

### 1.3 删除会话

**接口**: `DELETE /api/qa/sessions/{sessionId}`

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 要删除的会话ID |

**Body**: 无

**响应 Body (raw, JSON)**:
```json
true
```

---

## 2. 知识库管理 API

### 2.1 上传文档

**接口**: `POST /api/knowledge/upload`

**Body (form-data)**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | ✅ | 上传的文件（PDF/Word/TXT） |
| title | String | ✅ | 文档标题 |
| tags | String | ❌ | 标签，逗号分隔 |

**响应 Body (raw, JSON)**:
```json
{
  "knowledgeId": 1,
  "status": "PROCESSING"
}
```

---

### 2.2 获取知识库列表

**接口**: `GET /api/knowledge/list`

**Params**:

| 参数 | 值 | 类型 | 默认值 | 说明 |
|------|-----|------|--------|------|
| page | 1 | Integer | 1 | 页码 |
| size | 10 | Integer | 10 | 每页数量 |

**Body**: 无

**响应 Body (raw, JSON)**:
```json
[
  {
    "id": 1,
    "title": "药品说明书",
    "fileName": "document.pdf",
    "fileType": "application/pdf",
    "tags": "药品,说明书",
    "chunkCount": 5,
    "status": "READY",
    "createdAt": "2026-04-07 11:30"
  }
]
```

**status 值说明**:

| 值 | 说明 |
|-----|------|
| PENDING | 等待处理 |
| PROCESSING | 处理中 |
| READY | 就绪可用 |
| FAILED | 处理失败 |

---

### 2.3 删除知识

**接口**: `DELETE /api/knowledge/{id}`

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 知识库文档ID |

**Body**: 无

**响应 Body (raw, JSON)**:
```json
true
```

---

### 2.4 查看索引状态

**接口**: `GET /api/knowledge/{id}/status`

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 知识库文档ID |

**Body**: 无

**响应 Body (raw, JSON)**:
```json
{
  "status": "READY"
}
```

---

## 3. 对话历史 API

### 3.1 获取会话历史

**接口**: `GET /api/chat/history/{sessionId}`

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话ID |

**Body**: 无

**响应 Body (raw, JSON)**:
```json
[
  {
    "role": "USER",
    "content": "你好",
    "sources": null,
    "createdAt": "2026-04-07 11:22:36"
  },
  {
    "role": "ASSISTANT",
    "content": "您好！我是您的医药知识问答助手...",
    "sources": "[]",
    "createdAt": "2026-04-07 11:22:37"
  }
]
```

---

### 3.2 清空会话

**接口**: `DELETE /api/chat/history/{sessionId}`

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话ID |

**Body**: 无

**响应 Body (raw, JSON)**:
```json
true
```

---

## 错误响应格式

**响应 Body (raw, JSON)**:
```json
{
  "timestamp": "2026-04-07T11:01:20.003+08:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/qa/chat"
}
```

**常见 HTTP 状态码**:

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 405 | Method Not Allowed（接口应为POST却用GET） |
| 500 | 服务器内部错误 |

---

## Postman 测试配置

1. **新建 Collection**: `KGQA-RAG-System`
2. **新建 Environment**:
   ```
   变量名: baseUrl
   初始值: http://localhost:8080
   当前值: http://localhost:8080
   ```
3. **接口配置示例**:
   - **POST 问答接口**:
     - Method: `POST`
     - URL: `{{baseUrl}}/api/qa/chat`
     - Body → raw → JSON: `{"question": "你好", "sessionId": ""}`

---

## 注意事项

1. **CORS**: 后端已配置允许跨域访问
2. **SessionId**: 首次调用 `POST /api/qa/chat` 时传入 `sessionId: ""` 或 `sessionId: null`，会返回新的 sessionId
3. **多轮对话**: 后续请求使用返回的 sessionId 保持对话连贯
4. **知识库上传**: 文件上传后会异步处理，状态从 `PROCESSING` 变为 `READY`
