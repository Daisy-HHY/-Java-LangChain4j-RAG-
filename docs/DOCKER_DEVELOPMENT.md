# Docker 运行与本地断点调试

项目使用同一分支和一个 `compose.yaml`，支持完整 Docker 运行，以及本地前后端连接 Docker 数据库。这里的完整 Docker 模式面向本机开发，前端使用 Vite 开发服务器。

| 模式 | 前端 | 后端 | PostgreSQL |
|---|---|---|---|
| 完整 Docker | 容器，5173 端口 | 容器，8080 端口 | 容器，默认映射 5432 |
| 本地调试 | 本机 Vite | IDEA / 本机 Maven | 同一个容器、同一个数据卷 |

三个端口默认只绑定本机。浏览器统一访问 `http://localhost:5173`，前端通过 `/api` 代理后端，保留 Cookie 鉴权与 SSE 流式输出。

## 1. 环境要求

- Docker Engine / Docker Desktop 和 Docker Compose v2；Windows 使用 Linux 容器。
- 本地调试额外需要 JDK 17、Maven 3.8+、Node.js 22.12+。镜像使用 Java 17、Maven 3.9.11、Node.js 22.22.0。
- SiliconFlow API Key，容器与本机后端都需要能够访问 `https://api.siliconflow.cn`。
- 当前固定数据库镜像为 `pgvector/pgvector:0.8.2-pg18-trixie`。PostgreSQL 18 数据卷挂载到 `/var/lib/postgresql`，数据保存在其中的 `18/docker` 子目录。

已有 PostgreSQL 16 等其他版本的数据需要逻辑备份恢复，不能直接复用其物理数据目录。Compose 创建独立的 `kgqa_postgres_data` 卷，不自动导入之前 `docker run` 启动的数据库。

## 2. 首次准备

以下命令均从项目根目录执行。先停止正在访问源图谱的后端或 Jena 进程，再准备完整的图谱副本。

Linux / macOS / Git Bash：

```bash
cp .env.example .env
mkdir -p .runtime uploads
# 仅首次复制；已有副本时不要覆盖或重复复制。
test ! -e .runtime/tdb_drug_new && cp -a 'apache jena/tdb_drug_new' .runtime/tdb_drug_new
```

Windows PowerShell：

```powershell
Copy-Item .env.example .env
New-Item -ItemType Directory -Force .runtime, uploads | Out-Null
if (-not (Test-Path .runtime/tdb_drug_new)) {
    Copy-Item -LiteralPath 'apache jena/tdb_drug_new' -Destination '.runtime/tdb_drug_new' -Recurse
}
```

已有 `.env` 时保留原文件，按 `.env.example` 补齐变量。编辑 `.env`，至少填写 `SILICONFLOW_API_KEY`，并设置数据库密码及固定的 `KGQA_AUTH_TOKEN_SECRET`。确认聊天模型在当前账户可用；本次验证中原默认 `MiniMaxAI/MiniMax-M2.5` 返回 `Model disabled`，使用 `SILICONFLOW_CHAT_MODEL=Qwen/Qwen2.5-7B-Instruct` 完成了真实 SSE 验证。`.env.example` 已选用这个经过验证的聊天模型；也可改成当前有权限的其他模型，并同步至 IDEA。示例密码和密钥仅供本机开发；真实密钥不提交到 Git。

默认宿主机图谱目录为 `./.runtime/tdb_drug_new`，上传目录为 `./uploads`，两种模式共用这些目录。如需自定义，在 `.env` 设置：

```dotenv
KGQA_TDB_HOST_PATH="E:/My Project/.runtime/tdb_drug_new"
KGQA_UPLOAD_HOST_PATH="E:/My Project/uploads"
```

Compose 的相对路径基于项目根目录。容器图谱路径始终为 `/data/tdb_drug_new`，上传路径为 `/data/uploads`；本机 IDEA 使用对应宿主机目录的绝对路径。

当前图谱使用 `org.apache.jena.tdb.TDBFactory`，属于 **TDB1**。不要把 TDB2 目录直接替换进来。保留副本中的完整数据文件，无需额外启动 Fuseki。Compose 不会自动创建缺失的图谱挂载目录，避免因路径拼错而启动空图谱。

## 3. 完整 Docker 运行

```bash
docker compose --profile app config --quiet
docker compose --profile app up -d --build
docker compose --profile app ps
docker compose --profile app logs -f backend
```

等待后端日志出现启动完成信息，再访问 `http://localhost:5173`。默认管理员为 `admin / admin123`，可通过 `.env` 的 `KGQA_DEFAULT_ADMIN_USERNAME` 和 `KGQA_DEFAULT_ADMIN_PASSWORD` 设置。修改默认密码变量不会重置数据库中已经存在的管理员密码。

数据库健康检查通过后才会启动后端。Spring 执行 `schema.sql` 创建 pgvector 扩展和业务表，向量存储显式依赖此初始化，然后创建 `embeddings` 表。全新数据库没有旧文档、向量和聊天记录。默认关闭启动向量重建及图谱到向量库的兼容导入。

前端源码挂载到容器，修改 Vue 文件自动热更新；`node_modules` 使用容器独立卷，与本机依赖隔离。依赖文件或容器 Node 版本变化时，入口脚本自动重新执行 `npm ci`。修改 `docker-entrypoint.sh` 或 Dockerfile 后需要重建前端镜像。

修改 Java 代码后更新容器：

```bash
docker compose --profile app up -d --build backend
```

`app` 是 Compose profile，只决定启动哪些容器。容器后端使用 Spring `dev` profile，以启用本地导入和 SPARQL 调试接口；这些接口仍要求登录。无需额外的 `application-docker.yml`。

## 4. 切换到本地断点调试

先停止容器里的前后端，数据库继续运行：

```bash
docker compose --profile app stop frontend backend
docker compose up -d postgres
```

在 IDEA 中配置 `com.kgqa.KgQaApplication` 的 Spring Boot 运行配置（如类名发生变化，以项目启动类为准）：

1. 使用 JDK 17，工作目录设为项目的 `backend` 目录。
2. Active profiles 设置 `dev`，或设置环境变量 `SPRING_PROFILES_ACTIVE=dev`。
3. 按下表设置环境变量，再用 Debug 启动。绝对路径可使用正斜杠；IDEA 环境变量编辑器中填写路径值时不需要额外加引号。

| 环境变量 | 本地值 |
|---|---|
| `KGQA_DB_URL` | `jdbc:postgresql://localhost:5432/kgqa` |
| `KGQA_DB_USERNAME` | 与根目录 `.env` 一致 |
| `KGQA_DB_PASSWORD` | 与根目录 `.env` 一致 |
| `KGQA_TDB_PATH` | 项目 `/.runtime/tdb_drug_new` 的绝对路径 |
| `UPLOAD_PATH` | 项目 `/uploads` 的绝对路径 |
| `SILICONFLOW_API_KEY` | 与根目录 `.env` 一致 |
| `KGQA_AUTH_TOKEN_SECRET` | 与根目录 `.env` 完全一致 |
| `KGQA_DEFAULT_ADMIN_USERNAME` / `KGQA_DEFAULT_ADMIN_PASSWORD` | 与根目录 `.env` 一致 |

如修改了 `.env` 的 `KGQA_DB_PORT`，同步修改本地 JDBC URL 的端口。Docker 后端仍使用 `postgres:5432`。如自定义模型或 API 地址，IDEA 也设置对应的 `SILICONFLOW_BASE_URL`、`SILICONFLOW_EMBEDDING_MODEL`、`SILICONFLOW_CHAT_MODEL`。

**根目录 `.env` 只由 Compose 用于变量替换，不会自动被 IDEA 或 Spring Boot 加载。** `KGQA_TDB_HOST_PATH` 也不会自动转换成 IDEA 的 `KGQA_TDB_PATH`。

前端在本机启动：

```bash
cd frontend
npm ci
npm run dev
```

默认代理为 `http://localhost:8080`。后端地址变化时，在 `frontend/.env.local` 设置 `API_PROXY_TARGET=http://localhost:新端口`，然后重启 Vite。Docker 的代理环境变量会覆盖此值。浏览器使用 `/api`，不要把 `http://backend:8080` 设置成浏览器的 API 地址。

也可以在配置好相同环境变量后，从 `backend` 目录执行 `mvn spring-boot:run -Dspring-boot.run.profiles=dev`。通过 IDEA Debug 启动即可命中本地断点，修改 Java 后重新编译、运行。

## 5. 切换回完整 Docker

1. 停止 IDEA / Maven 后端和本机 Vite。
2. 确认图谱目录已释放，`8080` 和 `5173` 端口可用。
3. 在项目根目录执行：

```bash
docker compose --profile app up -d --build
```

数据库、文档切块、向量及聊天数据由同一个 PostgreSQL 数据卷提供。保持同一令牌密钥、同一数据库和 `localhost` 访问地址，可以继续使用已有登录 Cookie。

同一图谱目录只能由一个后端进程访问。遇到文件锁错误先停止占用进程，不直接删除锁文件；仅存在 `tdb.lock` 文件并不能证明仍有进程占用。

## 6. 持久化与备份恢复

| 内容 | 保存位置 |
|---|---|
| 用户、文档元数据、切块、向量、聊天 | `kgqa_postgres_data` 命名卷 |
| 图谱副本 | `.runtime/tdb_drug_new` 或自定义宿主机目录 |
| 上传静态目录 | `uploads` 或自定义宿主机目录 |
| Docker 前端依赖 | `kgqa_frontend_node_modules` 命名卷 |

当前上传接口从内存读取文档并保存元数据、切块和向量，**没有把原始上传文件写入 `uploads`**。目录挂载用于保留现有静态文件，不会增加原文件归档功能；上传文档的持久化以数据库内容为准。

停止服务使用 `docker compose --profile app stop`；移除容器但保留命名卷使用 `docker compose --profile app down`。日常切换不使用 `down -v`，该选项会删除数据库和依赖卷。不要随意改变 Compose 项目名，否则会连接到另一组数据卷。

数据库备份（以下用户名按默认 `postgres`，自定义时替换；命令兼容 PowerShell）：

```bash
docker compose exec -T postgres pg_dump -U postgres -d kgqa -f /tmp/kgqa-backup.sql
docker compose cp postgres:/tmp/kgqa-backup.sql .runtime/kgqa-backup.sql
```

恢复时先停止两种模式的后端，在已创建的空 `kgqa` 数据库执行，避免与已有表冲突：

```bash
docker compose cp .runtime/kgqa-backup.sql postgres:/tmp/kgqa-backup.sql
docker compose exec -T postgres psql -U postgres -d kgqa -v ON_ERROR_STOP=1 -f /tmp/kgqa-backup.sql
```

从旧数据库迁移时先在旧实例导出，再复制至新容器恢复；首次启动后端前完成恢复。图谱和上传目录需要单独备份，复制图谱前关闭后端。

## 7. 常见问题

- **5432 被占用**：修改 `.env` 的 `KGQA_DB_PORT`（例如 5433），重新创建数据库容器，并同步 IDEA 的 JDBC URL。
- **8080 / 5173 被占用**：确认上一种模式的前后端已停止。Vite 设置了 `strictPort`，不会静默切换到其他端口。
- **图谱为空或路径不存在**：确认完整副本和路径；启动日志应显示非零三元组数量。
- **`No node in NodeTable`**：当前仓库图谱在全量遍历时已有少量缺失节点，复制目录不会修复这类问题。已验证的哮喘症状查询可用；完整数据修复需要使用可靠 RDF 源重新导出或重建图谱，不在容器化过程中直接修改原始数据。
- **数据库认证失败**：已有数据卷的用户密码不会因 `POSTGRES_PASSWORD` 变化自动更新。使用原密码或显式修改数据库密码，再同步 `.env` 和 IDEA。
- **前端不能连接后端**：本机代理应为 `localhost:8080`，容器代理应为 `backend:8080`；检查后端启动日志，不仅检查容器是否启动。
- **Docker Desktop 热更新无效**：设置 `.env` 中 `VITE_USE_POLLING=true`，重新创建前端容器。
- **切换后登录失效**：核对固定令牌密钥、数据库和访问主机名，然后重新登录。
- **模型或向量化失败**：检查 API Key、模型权限、余额和网络。嵌入模型的维度用于向量表初始化，启动时也可能需要访问模型 API；更换嵌入模型需安排向量重建。
- **企业代理或云环境证书**：Dockerfile 支持可选 BuildKit `proxy_ca` 密钥，后端还支持 `maven_settings` 密钥。代理的 CA 和 Maven 配置仅在构建时挂载，不写入镜像。运行时的 Java / Node 代理和信任设置需由所在环境提供，不关闭 TLS 校验。

## 8. 验收

依次验证全新数据库启动、登录与注册、文档上传后达到 `READY`、图谱查询、SSE 逐步输出、本地断点与 Vue 热更新、双向切换后原数据仍可访问，以及重建应用容器后数据仍在。

所有验收以实际执行结果为准，进度记录见根目录的 `开发任务清单.md`。
