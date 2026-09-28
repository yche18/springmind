# SpringMind 思泉｜企业知识证据检索与问答平台

[English](README_EN.md) | **简体中文**

一个面向团队知识库的全栈 RAG 项目。用户可以创建或加入知识组，上传企业文档，并基于组内资料进行有来源、可追溯、证据不足时会拒答的知识问答。

SpringMind 围绕“文档入库—混合检索—证据分级—可追溯回答”构建完整业务闭环。后端采用 `Controller → Service → Mapper` 分层，核心模块按认证、知识组、文档、入库、检索和问答等业务职责组织，便于维护与扩展。

## 核心亮点

- 组织级数据隔离：检索请求必须携带真实用户与知识组上下文，文档、切片和检索结果按 `groupId` 隔离。
- 完整文档生命周期：支持普通上传与分片续传、MinIO 对象存储、异步入库、失败重试和卡死任务恢复。
- 结构感知解析：支持 PDF、DOCX、Markdown、TXT，经过文本清洗和带重叠窗口的结构化切片。
- 混合检索：pgvector 语义检索与 Elasticsearch 关键词检索并行召回，使用 RRF 融合排名。
- 查询规划：模型根据问题选择直接检索、改写或拆解；规划失败时自动回退原问题，不阻断问答。
- 证据约束回答：对召回结果划分 `NONE / WEAK / PARTIAL / SUFFICIENT`，限制模型只能基于证据作答。
- 可追溯引用：答案返回文档名、文档 ID、切片位置和检索分数等引用信息。
- 可执行质量门禁：认证、组权限、文档生命周期和问答检索均有 Harness 测试约束核心业务规则。

## 业务流程

```text
注册 / 登录
  → 创建或加入知识组
  → 上传 PDF / DOCX / MD / TXT
  → MinIO 保存原文件
  → 异步解析、清洗、切片
  → pgvector + Elasticsearch 建立双路索引
  → 查询规划与混合召回
  → RRF 融合、相邻切片扩展、证据分级
  → 大模型生成受证据约束的答案
  → 返回答案、引用或拒答原因
```

## 技术栈

- 后端：Java 21、Spring Boot 3.5、Spring AI 1.1、MyBatis-Plus、Flyway
- 检索：PostgreSQL + pgvector、Elasticsearch、Ollama Embedding
- 存储：MinIO
- 前端：Vue 3、TypeScript、Pinia、Vue Router、Vite
- 工程：Docker Compose、JUnit 5、Mockito

聊天模型使用 OpenAI-compatible 接口，通过环境变量配置；向量模型默认使用本地 Ollama 的 `qllama/bge-small-zh-v1.5`。

## 本地启动

### 环境要求

- Docker Desktop（建议为 Docker 分配至少 6 GB 内存）
- 可用的 OpenAI-compatible 聊天模型 API Key

### 配置模型

在项目根目录创建 `.env`：

```dotenv
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=your-api-key
AI_CHAT_MODEL=gpt-4o-mini
```

也可以将 `AI_BASE_URL` 和 `AI_CHAT_MODEL` 替换为其他兼容服务的地址与模型名。不要提交包含真实密钥的 `.env`。

### 启动服务

```bash
docker compose up -d
```

首次启动需要下载镜像和 Embedding 模型，耗时取决于网络。查看状态：

```bash
docker compose ps
docker compose logs -f backend
```

访问地址：

- Web：<http://localhost:5173>
- 后端健康检查：<http://localhost:18080/actuator/health>
- API 文档：<http://localhost:18080/doc.html>
- MinIO 控制台：<http://localhost:9001>

开发环境管理员：

- 用户名：`admin`
- 密码：`Admin@123456`

管理员用于用户管理；普通用户注册后进入知识组、文档和问答功能。

### 数据与环境说明

项目使用 Flyway 管理数据库结构，并通过 Compose 项目名 `springmind` 隔离容器和数据卷。若需要重置本地演示数据：

```bash
docker compose down -v
```

该命令会删除当前 SpringMind 环境中的 PostgreSQL、Elasticsearch、MinIO 和 Ollama 数据，请只在确认无需保留数据时执行。

## 本地开发

后端：

```bash
mvn test
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

本地运行后端时，PostgreSQL、Elasticsearch、MinIO 和 Ollama 仍可由 Docker 单独启动：

```bash
docker compose up -d postgres elasticsearch minio ollama ollama-model-init
```

## 项目结构

```text
src/main/java/com/yche/springmind/
├─ ai                 # OpenAI-compatible 聊天模型配置
├─ auth               # 登录、JWT、刷新令牌
├─ user               # 账户安全与管理员用户管理
├─ groupmembership    # 知识组、成员、邀请和加入申请
├─ document           # 上传、分片续传、预览与文档状态
├─ ingestion          # 解析、清洗、切片、异步入库
├─ retrieval          # pgvector 与 Elasticsearch 适配
├─ qa                 # 查询规划、混合检索、证据分级、引用和回答
└─ storage            # MinIO 对象存储

frontend/src/
├─ pages              # 登录、知识组、文档、问答、管理后台
├─ api                # 后端接口封装
├─ stores             # 登录态和当前知识组
└─ components         # 通用布局与内容组件
```

## 核心代码入口

可以按以下顺序跟随文档入库与问答主链路：

1. `DocumentController` → `DocumentService` / `DocumentUploadService`
2. `DocumentIngestionAsyncListener` → `EtlDocumentIngestionProcessor`
3. `StructureAwareChunkTransformer` → `VectorIngestionService` / `ElasticsearchChunkIndexService`
4. `QaController` → `QaService` → `QueryPlanningService`
5. `HybridEvidenceRetriever` → `QaChatService` → `CitationAssembler`

## 测试

运行不依赖真实外部模型的核心门禁：

```bash
mvn "-Dtest=IdentityAccessHarnessTest,GroupPermissionHarnessTest,DocumentLifecycleHarnessTest,QaRetrievalHarnessTest" test
```

Flyway 集成测试需要本机 `5433` 端口上的 PostgreSQL：

```bash
docker compose up -d postgres
mvn -Dtest=FlywayMigrationTest test
```

详细门禁规则见 [Harness 说明](harness/README.md)。

## 设计边界

SpringMind 以知识组作为数据与权限边界，以团队文档作为回答依据。问答链路只处理可检索、可验证的知识请求；证据不足时返回明确的拒答原因，所有引用均由真实召回结果生成。新增能力需要同时满足权限隔离、结果可评测、成本可观测和故障可恢复四项工程约束。
