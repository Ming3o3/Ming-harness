# Ming Harness

Ming Harness 是一个面向课程约束与学习者状态的教育知识库 Agent。后端使用 Spring Boot 4，前端使用 Vue 3 + Vite，并以 Codex 桌面端式的聊天工作台呈现“课程资料 → 学习者状态 → 教学决策 → 形成性证据”的学习闭环。通用 Run、工具治理与工作区能力保留在运行控制台中，用于开发和运维，而学习对话始终走教育 Agent 路径。

平台围绕一条可追溯的教育业务闭环运行：课程资料与权限 → 学习者画像/课程实例 → 受约束检索 → 分层讲解、练习或诊断 → 带证据的测评 → 掌握度、目标、复习任务和课程作业回写 → 教师审核与反馈。底层仍复用统一的审批、安全、审计、幂等和成本边界，确保每一次教学 Run 都可复现、可解释、可追踪。

## 技术栈

项目采用前后端分层架构，默认提供零依赖的本地演示模式，也可以切换到 PostgreSQL、Redis 和 RabbitMQ 组成的异步运行模式。

| 层次 | 技术 | 用途 |
| --- | --- | --- |
| 后端语言与构建 | Java 17、Maven Wrapper | 编写服务端业务代码，并统一本地构建与启动方式 |
| Web/API | Spring Boot 4.1.0、Spring MVC、Spring Validation | 提供 REST API、参数校验、全局异常处理和 Run 实时 SSE 事件流 |
| 模型访问 | Spring `RestClient`、OpenAI 兼容 Chat Completions API | 对接演示模型或外部模型供应商，支持重试、熔断、备用供应商和成本统计 |
| 持久化 | Spring Data JPA、Hibernate、H2、PostgreSQL 17 | 保存 Run、Step、会话、上下文、审计和 Outbox 等业务数据；H2 用于本地演示，PostgreSQL 用于基础设施模式 |
| 数据库迁移 | Flyway | 以 `src/main/resources/db/migration` 中的版本脚本管理表结构演进，并可启用 PostgreSQL `pgvector` 扩展 |
| 缓存与分布式治理 | Redis、Spring Data Redis | 跨实例限流、组织活动 Run 配额、执行锁和取消协作信号；不保存业务正文 |
| 异步消息 | RabbitMQ、Spring AMQP、Outbox、死信队列 | 解耦 Run 投递与 Worker 执行，支持发布租约、重试、背压和失败恢复 |
| 安全 | Spring Security、OAuth2 Resource Server、OIDC/JWT、API Key、RBAC | 支持本地演示身份、API Key 和企业 OIDC，执行组织隔离、接口权限和工作区审批控制 |
| 可观测性 | Spring Boot Actuator、Micrometer、Prometheus、`X-Request-Id` / `X-Trace-Id` | 提供健康探针、运行指标、请求关联追踪以及 Run/Step 级耗时、Token 和成本观测 |
| 前端 | Vue 3.5、JavaScript ES Modules、Vite 8.1 | 构建聊天工作台、Run 控制台、审批审计和上下文管理页面；开发服务器代理 `/api` 到后端 |
| 前端增强 | Monaco Editor、Markdown-it、highlight.js、Lucide Vue | 提供代码/JSON 编辑预览、Markdown 渲染、代码高亮和界面图标 |
| 桌面端 | Electron 38.8、Node.js 20.19+ | 提供本地项目选择、受信任桌面桥接令牌和受控工作区能力；绝对路径只保留在主进程 |
| 测试 | Spring Boot Test | 覆盖 Runtime、策略、工具、消息、认证、数据保留和 API 等服务端测试 |

### 运行模式

- `local`：H2 + 进程内执行，适合快速启动、功能演示和单元测试。
- `local-infra`：PostgreSQL 17 + Redis + RabbitMQ，使用 Flyway、Outbox 和异步 Worker，适合验证多实例治理和生产接近的执行链路。
- 模型层默认不访问外部服务；显式设置 `MODEL_ENABLED=true` 并提供模型地址与密钥后，才会调用 OpenAI 兼容接口。

### 教育 Agent 业务闭环

1. 教师或资料所有者为知识文档绑定学科、年级、课程版本、知识点、前置知识与难度元数据；只有当前学习者可见且匹配这些约束的资料才能启动教育 Run。
2. 学习者画像冻结课程三元组；课程实例会额外校验活跃选课关系。客户端不能覆盖画像或课程实例的课程范围。
3. 每个教育 Run 固化课程、学习目标、作业/复习计划、教学策略和掌握度快照，并只检索匹配课程资料；检索来源写入 Run Step 与测评记录，供审计和教师复核。
4. Agent 根据前置知识缺口、目标掌握度和资料难度选择讲解、苏格拉底追问、练习或诊断。学习对话不会退回为通用问答；缺少资料或画像时会明确阻断并引导配置。
5. 只有附带学生作答或推理依据的形成性测评才能更新掌握度。达标后自动进入保持度复习，到期生成学习任务；作业、提交物、教师量规、返工和干预形成可追踪的教学闭环。

教育检索默认以向量与关键词融合召回候选证据，再在课程硬约束内结合目标知识点、前置知识缺口、知识依赖图覆盖、学习者掌握度和难度适配进行证据集合选择。`FULL`、`VECTOR_ONLY`、`KEYWORD_ONLY`、`NO_LEARNER_STATE`、`NO_DEPENDENCY_GRAPH` 和 `STATIC_WEIGHT` 用于可复现基线与消融；`CALIBRATED` 会读取当前租户已有的教师证据标注，将目标 grounding、前置补强、难度适配和总体效用收缩校准为下一轮排序权重。校准权重在 Run 创建时冻结，历史 Run 不会因后续标注变化而漂移。实验聚合器还会在同一学习者-学习目标内将各策略与 `FULL` 配对，计算掌握度增益、目标达成率、达标轮次和前置缺口覆盖的差值，避免把学习者个体差异误当成检索收益。

教师可以在成功教育 Run 的真实检索证据上提交 1--5 分量规评价，系统只接受该 Run 的证据快照引用，并通过 `GET /api/education/retrieval-calibration` 查看当前租户的校准版本、样本量、四项均值和生效权重。该闭环用于实验校准与审计回放，不把主观评价伪装成学习者掌握度事实。

## 已实现模块

- Run / Step 持久化状态机：`QUEUED -> RUNNING -> WAITING_APPROVAL -> SUCCEEDED / REJECTED / FAILED / TIMED_OUT / CANCELLED`；Agent 的 `REJECTED` 工具步骤会携带人工意见进入下一轮模型
- 幂等与资源边界：支持 `Idempotency-Key`、组织活动 Run 配额、创建速率、输入/预算/步骤数限制
- 可插拔模型网关：默认演示模型，也支持 OpenAI 兼容的 `/chat/completions` 接口
- 工具注册表与确定性策略：权限、风险、审批、网络策略、超时和输入校验
- 受控代码工作区工具：目录浏览、UTF-8 文件读取、文本搜索、带哈希并发保护的原子写入和白名单命令沙箱
- 审计与观测：Run `traceId`、Step `spanId`、Token、耗时、成本和组织/操作者快照，审计事件支持 HMAC 完整性校验
- 组织隔离：读写 Run、Step、审计事件都需要 `X-Tenant-Id`
- 可插拔认证与 RBAC：`local` 兼容演示请求头，`api-key` 和 `oidc` 支持组织、用户和接口权限快照
- 失败重试：只读工具可用 `RetryableToolException` 触发有限自动重试；有副作用的工具禁止自动重试，人工重试前会重新走审批
- 协作式取消：取消请求先写入组织绑定的短期协调信号，Worker 会在每个步骤和最终完成前检查，避免长步骤后的后续副作用继续执行
- 上下文与记忆：授权文档检索、引用来源、过期记忆、删除和敏感凭证拦截
- 上下文父文档子块索引：文档和长期记忆写入时按结构和长度生成有序子块，为后续 embedding/pgvector 检索保留稳定的父子关系
- embedding 索引写入：启用外部 embedding API 且使用 PostgreSQL 时，文档和长期记忆子块会批量写入 pgvector；供应商暂时不可用时保留关键词召回并等待后续重建
- embedding 缓存：按租户、内容哈希、模型、模型版本和维度持久化复用结果，减少重建索引的重复 API 调用，并按保留策略清理
- 敏感数据治理：Run、Step、审计、模型、工具和上下文边界统一凭证脱敏，长期记忆拒绝写入疑似凭证
- 数据保留策略：终态 Run 与审计链原子清理，过期记忆/文档和已完成 Outbox 定时删除，待投递消息不自动删除
- 业务闭环沉淀：每次 Run 持久化实际上下文证据，助手消息支持有用/需改进反馈
- 教育检索实验闭环：每个教育 Run 冻结检索策略、知识依赖图和（如使用 `CALIBRATED`）教师权重快照；实验摘要支持按策略比较证据覆盖、前置缺口覆盖、冗余、目标达成和掌握度变化，教师评价可追溯到真实 citation
- 证据学习收益归因：形成性测评冻结本轮 citation，实验服务按引用数量分摊掌握度变化与正确性，并回放排序拆解和快照匹配率；归因明确标记为描述性统计，不伪装成单文档因果结论
- 教育业务闭环：教师/组织可把课程约束和知识目标布置给指定学习者，学习者接受后自动生成画像与结构化学习目标；作业截止时间由调度器收敛为逾期状态，逾期作业不能再接受但仍可在已有学习目标达标后完成；目标达标后自动建立保持度计划，到期计划由调度器幂等物化为学习任务，任务可开始、延期并在复习测评后回写完成结果；初始作业 Run 成功但缺少形成性测评证据时，作业会进入待补证据并可继续启动，证据写入后恢复执行；作业 Run 失败、超时或取消时会回流为 `RETRY_REQUIRED`，保留原作业上下文并从作业入口重新执行；成功但缺少测评证据的复习任务会进入待补证据；到期、待补证据和失败重试状态会生成可幂等追踪的站内通知，支持未读、已读和触达时间记录
- 本地基础设施 Profile：PostgreSQL + Flyway、Redis 共享治理、RabbitMQ Outbox Worker
- 健康检查与运行指标：公开存活探针、受 `ops.read` 保护的 `/api/health` 和 Actuator 指标
- 请求关联追踪：自动生成并回传 `X-Request-Id`、`X-Trace-Id`，错误响应包含 `traceId`
- Vue 3 聊天工作台：持久化会话、消息气泡、逐轮输入、Run 实时 SSE 事件流与轮询兜底；原 Run 运维控制台仍可切换进入
- Electron 桌面工作区桥接：原生选择本地项目、会话/Run 固定绑定、路径加密存储和受信任桌面令牌校验

## 启动方式

三类用户的登录方式、课程资料配置路径和完整业务流程见
[docs/roles-and-user-flows.md](docs/roles-and-user-flows.md)。

登录入口说明：

- 本地演示启动后选择“管理员演示账号”“老师演示账号”或“学生演示账号”；右上角退出或切换身份后，工作台会重新加载。
- 正式 API Key 登录时，在未登录页面粘贴管理员发放的 API Key。服务端会根据 Key 绑定的组织、用户和权限自动进入管理员治理中心、教师课程工作台或学生学习工作台；Key 只保存在当前浏览器会话的 `sessionStorage` 中，退出后清除。
- 企业接入 OIDC/JWT 时，从学校或企业统一登录入口进入，不在前端手工选择角色。管理员应先为教师和学生发放最小权限的凭证，再由用户使用各自凭证登录。

后端要求 Java 17：

```bash
./mvnw spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`。Vite 会把 `/api` 代理到 `http://localhost:8080`。

桌面端（推荐的本地基础设施模式）会启动当前源码的 Spring Boot Runtime、Vite 和 Electron：

```bash
cd frontend

SPRING_PROFILES_ACTIVE=local-infra \
MODEL_ENABLED=true \
npm run desktop:dev
```

若默认端口已被其他开发实例占用，桌面启动器会为当前源码自动选择可用端口，避免前端误连到旧 Runtime。

默认启动使用 H2 和进程内同步执行；接入本机 PostgreSQL、Redis、RabbitMQ 的方式见
[docs/local-infra.md](docs/local-infra.md)。

## 常用配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 后端端口 |
| `DB_URL` | `jdbc:h2:file:./data/ming-harness;DB_CLOSE_ON_EXIT=FALSE` | 数据库连接，生产环境建议替换为 PostgreSQL/MySQL |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | 前端来源白名单 |
| `HARNESS_AUTH_MODE` | `local` | `local`、`api-key` 或 `oidc`（OIDC 推荐使用 `oidc` Profile） |
| `HARNESS_API_KEYS` | 空 | 静态引导 Key：`key|tenant|user|permission1,permission2;...`；生产环境优先使用数据库生命周期 API，并通过密钥系统注入引导 Key |
| `OIDC_ISSUER_URI` | 空 | `oidc` Profile 使用的 OIDC Issuer 地址 |
| `OIDC_AUDIENCE` | 空 | OIDC Token 必须包含的受众，多个值使用逗号分隔；生产 OIDC 必填 |
| `AUDIT_INTEGRITY_KEY` | 本地演示默认值 | 审计 HMAC 密钥，生产环境必须从密钥系统注入 |
| `MODEL_ENABLED` | `false` | 是否启用 OpenAI 兼容模型网关 |
| `MODEL_BASE_URL` | `https://api.openai.com/v1` | 模型服务地址 |
| `MODEL_API_KEY` | 空 | 模型服务密钥，仅通过环境变量注入 |
| `MODEL_NAME` | `gpt-4o-mini` | 模型名称 |
| `MODEL_FALLBACK_BASE_URL` | 空 | 临时故障时使用的备用 OpenAI 兼容服务地址 |
| `MODEL_FALLBACK_API_KEY` | 空 | 备用模型服务密钥，仅通过环境变量注入 |
| `MODEL_FALLBACK_NAME` | 主模型名称 | 备用服务默认模型名称 |
| `MODEL_MAX_ATTEMPTS` | `3` | 单个供应商最大尝试次数，包含首次调用 |
| `MODEL_RETRY_BACKOFF_MS` | `200` | 供应商重试初始退避毫秒数，按指数退避并限制上限 |
| `MODEL_CIRCUIT_FAILURE_THRESHOLD` | `3` | 连续临时故障达到后打开应用内熔断 |
| `MODEL_CIRCUIT_OPEN_MS` | `30000` | 熔断打开时间 |
| `MODEL_INPUT_COST_PER_1K_TOKENS` | `0` | 输入每 1000 token 成本，按实际 usage 计算 |
| `MODEL_OUTPUT_COST_PER_1K_TOKENS` | `0` | 输出每 1000 token 成本，按实际 usage 计算 |
| `MODEL_MAX_RESPONSE_CHARS` | `100000` | 单次模型响应正文上限 |
| `MODEL_TIMEOUT_MS` | `30000` | 模型调用超时；前端连接测试会使用不超过 10 秒的快速边界 |
| `MAX_ACTIVE_RUNS_PER_TENANT` | `20` | 平台单组织活动 Run 硬上限；可通过组织策略进一步收紧 |
| `MAX_STEPS_PER_RUN` | `1000` | 平台单次 Run 的动态步骤硬上限；可通过组织策略进一步收紧 |
| `MAX_CREATES_PER_MINUTE` | `60` | 平台单组织每分钟创建 Run 硬上限；可通过组织策略进一步收紧 |
| `MAX_INPUT_LENGTH` | `10000` | 平台单次输入字符硬上限；可通过组织策略进一步收紧 |
| `MAX_RUN_BUDGET` | `1000` | 平台单次 Run 预算硬上限；可通过组织策略进一步收紧 |
| `MAX_CONTEXT_CHARS` | `64000` | 注入模型的上下文最大字符数 |
| `CONTEXT_CHUNK_MAX_CHARS` | `1600` | 上下文父文档子块的最大字符数 |
| `CONTEXT_CHUNK_OVERLAP_CHARS` | `160` | 相邻上下文子块的尾部重叠字符数 |
| `CONTEXT_PARENT_WINDOW_MAX_CHARS` | `4800` | 连续子块组成的父窗口最大字符数；只用于推理上下文，不参与向量召回 |
| `CONTEXT_DOCUMENT_MAX_UPLOAD_BYTES` | `26214400` | PDF/DOCX 知识文档原始文件最大大小（25 MB） |
| `CONTEXT_DOCUMENT_MAX_CONTENT_CHARS` | `100000` | PDF/DOCX 解析后写入知识库的正文最大字符数 |
| `CONTEXT_SEMANTIC_ENABLED` | `false` | 是否调用 embedding API 按语义边界分块 |
| `CONTEXT_SEMANTIC_BREAKPOINT` | `0.35` | 相邻原子单元余弦相似度低于该值时允许切分 |
| `CONTEXT_SEMANTIC_MIN_UNITS` | `3` | 语义切分前至少累计的原子单元数 |
| `CONTEXT_INDEX_ASYNC_ENABLED` | `true` | 是否在正文事务提交后异步执行 embedding 索引 |
| `CONTEXT_INDEX_CONCURRENCY` | `2` | 上下文 embedding 后台线程数 |
| `CONTEXT_INDEX_QUEUE_CAPACITY` | `100` | 上下文 embedding 有界队列容量；队列满时由重建接口补偿 |
| `EMBEDDING_ENABLED` | `false` | 是否启用外部 embedding API；关闭时保持关键词召回 |
| `EMBEDDING_BASE_URL` / `EMBEDDING_API_KEY` | OpenAI 地址 / 空 | OpenAI 兼容 embedding 服务地址和密钥 |
| `EMBEDDING_MODEL` | `text-embedding-3-small` | embedding 模型名称 |
| `EMBEDDING_MODEL_VERSION` | `v1` | embedding 模型或供应商配置版本；变更后自动隔离旧缓存 |
| `EMBEDDING_DIMENSION` | `1536` | embedding 维度，必须与 pgvector 迁移保持一致 |
| `EMBEDDING_BATCH_SIZE` | `32` | 单批 embedding 文本块数量 |
| `EMBEDDING_MAX_INPUT_TOKENS` | `8192` | 单条 embedding 输入的保守 token 上限；与字符上限同时生效 |
| `EMBEDDING_CACHE_RETENTION_DAYS` | `30` | 持久化 embedding 缓存的保留天数 |
| `CONTEXT_RETRIEVAL_CANDIDATE_LIMIT` | `20` | 向量召回候选子块数量 |
| `CONTEXT_RETRIEVAL_MAX_PARENTS` | `5` | 最终展开的父文档数量 |
| `CONTEXT_RETRIEVAL_NEIGHBOR_RADIUS` | `1` | 命中子块两侧补回的相邻子块数量 |
| `CONTEXT_RETRIEVAL_MIN_SIMILARITY` | `0.7` | 向量余弦相似度最低阈值 |
| `RECOVERY_TIMEOUT_MS` | `120000` | Worker 中断后将 RUNNING 任务转为超时的阈值 |
| `MAX_TOOL_ATTEMPTS` | `3` | 单个只读工具的自动重试次数上限，副作用工具固定为 1 |
| `RUN_EVENT_STREAM_POLL_MS` | `750` | 已订阅 Run 的持久化快照检查间隔；可跨 Worker 实例推送状态变化 |
| `RUN_EVENT_STREAM_HEARTBEAT_MS` | `15000` | SSE 空闲连接的保活注释间隔 |
| `RUN_EVENT_STREAM_MAX_SUBSCRIBERS` | `200` | 单个 Runtime 实例允许的并发 Run 实时订阅上限 |
| `WORKSPACE_ENABLED` | `false`（`local` 为 `true`） | 是否启用 Agent 工作区工具；生产环境必须显式评估后开启 |
| `HARNESS_WORKSPACE_ROOT` | `./workspace` | 工作区根目录，所有文件工具都不能访问该目录之外的路径 |
| `WORKSPACE_MAX_READ_BYTES` / `WORKSPACE_MAX_WRITE_BYTES` | `1000000` / `1000000` | 单次读取/写入的 UTF-8 字节上限 |
| `WORKSPACE_MAX_LIST_ENTRIES` | `200` | 单次目录浏览最多返回的条目数 |
| `WORKSPACE_MAX_SEARCH_FILES` / `WORKSPACE_MAX_SEARCH_RESULTS` | `2000` / `200` | 搜索扫描文件数和返回匹配数上限 |
| `WORKSPACE_MAX_READ_LINES` | `2000` | 单次文件读取允许请求的最大行数 |
| `WORKSPACE_ALLOW_HIDDEN_FILES` | `false` | 是否允许访问 `.git`、`.env` 等隐藏路径，生产环境建议保持关闭 |
| `WORKSPACE_EXEC_ENABLED` | `false` | 是否允许 `workspace.exec` 启动子进程；默认关闭，开启后仍需白名单和人工审批 |
| `WORKSPACE_ALLOWED_COMMANDS` | 空 | 允许的可执行文件逗号列表，例如 `./mvnw,npm,node`；未命中白名单直接拒绝 |
| `WORKSPACE_MAX_COMMAND_TIMEOUT_MS` | `120000` | 单条命令最大运行时间，超时会终止进程树 |
| `WORKSPACE_MAX_COMMAND_OUTPUT_BYTES` | `200000` | 单条命令最大合并输出，超过后终止进程并标记截断 |
| `WORKSPACE_MAX_COMMAND_ARGS` | `32` | 单条命令最多参数数量 |
| `WORKSPACE_PATH_ENCRYPTION_KEY` | 本地演示默认值 | 本机工作区绝对路径的 AES-GCM 加密密钥；正式环境必须单独配置并妥善保管 |
| `WORKSPACE_LOCAL_REGISTRATION_ENABLED` | `false`（`local` 为 `true`） | 是否允许桌面端登记用户主动选择的本地项目；仍必须提供桌面桥接令牌 |
| `HARNESS_DESKTOP_BRIDGE_TOKEN` | 空 | Electron/Tauri 主进程与本机 Runtime 的一次性桥接令牌；不可写入前端环境变量、数据库或聊天记录 |
| `DATA_RETENTION_ENABLED` | `false`（`local-infra` 为 `true`） | 是否启用定时数据保留清理 |
| `RUN_RETENTION_DAYS` | `90` | 终态 Run 最短保留天数；实际会与审计保留期取较大值 |
| `AUDIT_RETENTION_DAYS` | `365` | 审计链保留天数，避免清理部分事件破坏完整性 |
| `MEMORY_RETENTION_DAYS` | `30` | 已删除长期记忆的保留天数；已到期记忆会立即清理 |
| `DOCUMENT_RETENTION_DAYS` | `30` | 已删除知识文档的保留天数 |
| `OUTBOX_RETENTION_DAYS` | `14` | 已发布/最终失败 Outbox 保留天数，`PENDING` 永不自动清理 |
| `TENANT_POLICY_AUDIT_RETENTION_DAYS` | `365` | 组织资源策略变更审计保留天数 |
| `API_KEY_AUDIT_RETENTION_DAYS` | `365` | 数据库 API Key 生命周期审计保留天数 |
| `RETENTION_BATCH_SIZE` | `100` | 每轮最多清理的终态 Run 数量 |
| `SPRING_PROFILES_ACTIVE` | `local` | `local`、`local-infra`，可组合 `oidc` |
| `HARNESS_EXECUTION_MODE` | `sync` | `sync` 或 `rabbit` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis 连接参数 |
| `REDIS_LOCK_TTL_MS` | `30000` | Redis 执行锁和组织配额锁基础租约；Worker 执行锁会自动取不小于 `RECOVERY_TIMEOUT_MS` 的时长，不能低于 1000 毫秒 |
| `REDIS_QUOTA_LOCK_WAIT_MS` | `1000` | 活动 Run 配额锁等待时长；Redis 不可用时快速失败 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ 连接参数 |
| `OUTBOX_CLAIM_LEASE_MS` | `30000` | Outbox Relay 发布租约时长；实例中断后过期租约可被其他实例接管 |
| `RABBITMQ_CONSUMER_CONCURRENCY` | `1` | 每个应用实例初始 Worker 消费者数量 |
| `RABBITMQ_MAX_CONSUMER_CONCURRENCY` | `4` | 每个应用实例 Worker 消费者数量上限 |
| `RABBITMQ_PREFETCH` | `1` | 每个消费者预取消息数，避免未执行消息脱离队列监控 |
| `RABBITMQ_MAX_QUEUE_DEPTH` | `1000` | 执行队列允许的最大待消费消息数，达到上限时 Outbox Relay 暂停抢占 |
| `RABBITMQ_QUEUE_METRICS_POLL_MS` | `5000` | 队列深度指标刷新间隔 |

默认演示网关不会访问外部模型服务，适合本地开发和联调。

### Windows 绿色版

前端桌面版支持 Windows x64 的 Managed `local-infra` 发布模式：Electron 主进程会启动随包的
PostgreSQL 17 + pgvector、Garnet、RabbitMQ、Erlang、.NET Runtime 和 Java Runtime，再以
`SPRING_PROFILES_ACTIVE=local-infra,desktop` 启动 Spring Boot。绿色版数据写在软件目录下的
`data/infra`、`data/workspace` 和 `data/logs`，不使用系统服务，也不需要用户预装 Java、Node、
.NET、PostgreSQL、Redis、RabbitMQ 或 Docker。

PostgreSQL 会拒绝由 Windows 管理员令牌启动的服务器进程。因此不要使用“以管理员身份运行”启动
`Ming Harness.exe`，也不要从已提升的 PowerShell/CMD 启动；应取消 EXE 或快捷方式“属性 > 兼容性”中的
“以管理员身份运行此程序”。绿色版请解压至当前用户有写入权限的目录，例如
`%LOCALAPPDATA%\Ming-Harness`，而不是通过管理员权限写入 `Program Files`。

运行时二进制因授权和平台差异不提交到仓库，准备方式与目录要求见
[runtime/README.md](runtime/README.md)。Windows x64 构建在 `frontend` 目录执行：

```bash
npm ci
npm run dist:win:green
```

构建完成后脚本会自动检查最终目录包含 Garnet 和 pgvector 来源材料，且不含 Memurai。

### 控制台模型设置

大语言模型和向量模型配置从运行控制台顶部直接进入；知识源、索引、组织策略和凭证仍收纳在“高级治理设置”中。用户可以输入 OpenAI 兼容 API 地址、模型名称和 API Key；保存后只影响当前组织/用户创建的新 Run，用户覆盖配置会在 Run 创建时固化供应商快照，因此正在排队、审批或执行的 Run 不会被中途切换。后端通过 `GET/PUT/DELETE /api/model-config` 管理设置，API Key 使用 AES-GCM 加密保存，读取接口只返回掩码，不写入浏览器 localStorage。使用 api-key/OIDC 认证时，当前身份需要 `model.configure` 权限。

Embedding 配置按组织保存（知识库向量是组织共享索引），从运行控制台顶部的“向量模型”入口维护。它支持 OpenAI 兼容的 `/embeddings` 地址、模型、模型版本、API Key 和当前固定的 1536 维向量。保存后会清空该组织旧 chunk 向量，必须在“高级治理设置”的“向量索引”中重新建立索引；API Key 使用独立 AES-GCM 密钥标签加密，读取接口只返回掩码。后端通过 `GET/PUT/DELETE /api/context/embedding-config` 和 `POST /api/context/embedding-config/test` 管理配置；使用 api-key/OIDC 认证时需要 `context.configure` 权限。

治理面板的“添加授权知识文档”支持直接拖入或选择 PDF/DOCX。Runtime 只保留解析后的纯文本，不保存原始二进制；解析完成后会复用知识文档的权限过滤、确定性/语义分块、父窗口物化和异步 embedding 索引流程。当前只提取有文本层的 PDF，扫描图片 PDF 需要先做 OCR；加密、损坏、格式签名不匹配或正文为空的文件会被拒绝。上传接口需要 `context.write` 权限，默认单文件上限为 25 MB、解析正文上限为 100000 字符。

聊天和运行控制台都支持 `⌘/Ctrl + K` 命令面板，可搜索并执行新建对话、聚焦输入框、打开项目文件、查看当前 Run、模型设置、工作台切换和主题切换等操作；面板会根据当前会话和权限自动隐藏不可用命令。

前端共享环境可通过 `VITE_HARNESS_API_KEY` 使用 API Key；同时设置 `VITE_HARNESS_TENANT_ID` 和 `VITE_HARNESS_USER_ID`，让创建 Run 表单与 API Key 绑定的身份保持一致。

### API Key / OIDC 认证

生产或共享环境建议设置 `HARNESS_AUTH_MODE=api-key`。调用方使用 `Authorization: Bearer <key>` 或 `X-Api-Key`，服务端根据配置或数据库凭证将请求绑定到固定组织和用户，并按接口校验权限，例如 `run.read`、`run.create`、`run.execute`、`run.approve`、`context.read`、`context.write`、`context.configure`、`audit.read`、`tool.read`、`model.configure`、`education.assign`、`education.evaluate` 和 `ops.read`。工作区读取工具还需要 `workspace.read`，写入工具需要 `workspace.write` 并进入人工审批；`education.assign` 只允许授权的教师/组织操作者创建课程作业，`education.evaluate` 只允许第二评分者提交独立评价，学习者接受作业仍使用 `education.write`；`model.configure` 允许当前用户在控制台保存自己的模型 URL、模型名和加密 API Key；`context.configure` 允许组织内授权操作者保存共享知识库的 Embedding URL、模型和加密 API Key；`ops.read` 用于读取 `/api/health`、Actuator 指标、Prometheus 和应用信息。

通过具有 `auth.key.manage` 权限的引导 Key 或 OIDC 服务账号，可调用 `POST /api/admin/api-keys` 创建数据库 API Key；明文 `secret` 仅在创建响应中出现一次，数据库只保存 SHA-256 摘要。`GET /api/admin/api-keys` 只返回前缀和元数据，`POST /api/admin/api-keys/{keyId}/rotate` 会在同一事务中创建同权限新 Key 并立即撤销旧 Key，`DELETE /api/admin/api-keys/{keyId}` 可即时撤销，`GET /api/admin/api-keys/audits` 可查看生命周期审计。读取接口需要 `auth.key.read`，跨组织管理还需 `auth.key.cross-tenant`。环境变量 `HARNESS_API_KEYS` 保留为紧急引导兼容方案，变更或撤销需要重启；正式环境应逐步迁移至数据库生命周期 Key。

企业环境接入 OIDC/JWT 时使用 `SPRING_PROFILES_ACTIVE=local-infra,oidc`，并设置 `OIDC_ISSUER_URI` 与 `OIDC_AUDIENCE`。Spring Security Resource Server 负责验签和明确校验 issuer/audience，Harness 从 JWT 的 `sub`、`tenant_id`（兼容 `tenant`）以及 `permissions`/`scope`/`scp` 声明映射用户、组织和 RBAC 权限。

### 审计完整性

每条新审计事件都会使用 `AUDIT_INTEGRITY_KEY` 计算 HMAC，并与同一 Run 的前序哈希、序号组成链；Run 同时保存链头签名。运维可以调用 `GET /api/runs/{runId}/audit-events/verify` 主动校验，检测事件内容修改、删除、乱序或数据库中的链头篡改。迁移前的旧事件会标记为未签名历史记录，不能被校验结果当作完整可信链。

### 工具重试与 Run 预算

工具只有抛出 `RetryableToolException` 才会进入自动重试；Runtime 仅对 `readOnly=true` 的工具使用 `maxAttempts`，并受 `MAX_TOOL_ATTEMPTS` 全局上限约束。副作用工具即使声明更高次数也只执行一次，失败后通过 Run 重试接口重新经过策略和审批。模型步骤完成后会校验实际成本，超过 Run 预算的任务会以 `RUN_BUDGET_EXCEEDED` 失败并写入审计事件。

工具的 `inputSchema` 和 `outputSchema` 现在按结构化 JSON Schema 校验，不再通过字符串包含字段名来判断数据是否合格。当前支持对象、数组、字符串、数字、整数、布尔值和 null 类型，以及 `required`、`properties`、`additionalProperties`、`items`、`enum`、`const`、`allOf`、`anyOf`、`oneOf`、`not`、长度/数量/数值边界、`pattern`、`uniqueItems` 和常用 `format`（`email`、`uuid`、`date-time`、`uri`）。校验器会拒绝重复 JSON 字段和尾随的第二个 JSON 文档，避免解析差异造成输入绕过。校验失败会以 `TOOL_INPUT_INVALID` 或 `TOOL_OUTPUT_INVALID` 终止当前 Run，并保留脱敏后的错误原因。

### Agent 工作区工具

工作区工具是代码 Agent 的受控文件边界。启动前将 `HARNESS_WORKSPACE_ROOT` 指向一个专用项目目录；工具不会跟随符号链接访问根目录之外的文件，默认拒绝隐藏路径和非 UTF-8 文件。

当该目录就是你要开发的项目根目录时，Agent 读取、编辑和通过审批的命令会直接作用于原项目，无需先把整个项目上传到聊天框。聊天页会通过 `GET /api/workspace` 显示已连接的工作区名称、Git 状态和命令开关；该接口需要 `workspace.read` 权限，并且**永远不会**返回本机绝对路径。模型同样只使用相对于工作区根目录的路径（例如 `src/App.vue`）。

- `workspace.list`：浏览目录结构，需要 `workspace.read`；路径暂时失效时返回 `recoverable=true`，Agent 会重新从根目录定位
- `workspace.read`：读取文件，可按 `startLine`/`endLine` 截取，并返回当前文件 SHA-256；文件路径或内容不可读时返回脱敏的可恢复结果
- `workspace.search`：在工作区文本文件中搜索路径、行号和脱敏后的内容，需要 `workspace.read`；搜索目录失效时可恢复
- `workspace.write`：原子写入 UTF-8 文件，需要 `workspace.write` 和人工审批；覆盖已有文件必须携带上一次读取返回的 `sha256`，文件被其他人修改时会返回 `WORKSPACE_FILE_CHANGED`
- `workspace.edit`：按多个精确 `oldText`/`newText` 片段增量编辑文件，需要 `workspace.write` 和人工审批；默认要求每个片段只匹配一处，并且必须携带读取时的 `expectedSha256`
- `workspace.git.status`：查看当前工作区范围内的分支和文件变更，需要 `workspace.read`，只执行固定的只读 Git 命令；非 Git 工作区会返回 `available=false` 的可恢复结果，Agent 可继续使用文件工具
- `workspace.git.diff`：查看当前工作区范围内的未暂存或已暂存差异，需要 `workspace.read`，支持按文件和上下文行数限制输出；Git 不可用时不会冒充有效修改核验

本地配置示例：

```bash
export WORKSPACE_ENABLED=true
export HARNESS_WORKSPACE_ROOT=/Users/ming/Projects/example
./mvnw spring-boot:run
```

不要将默认工作区配置为用户主目录、桌面或 Documents 等宽泛目录。桌面版可以在用户明确授权后登记多个项目；每个会话和其创建的 Run 都会固定绑定一个 `workspaceId`，之后切换到其他项目不会影响旧任务。

### 桌面代码工作区（Electron）

浏览器无法安全地读取任意本机目录，因此“选择本地项目”仅在 Electron 桌面模式显示。开发时可直接从前端目录启动：

```bash
cd frontend
npm install
npm run desktop:dev
```

该命令会生成一次性桌面桥接令牌，并启动本机 H2 Runtime、Vite 和 Electron 窗口。点击聊天页的“选择本地项目”后，系统原生目录选择器会请求用户授权；选择成功会自动创建一条绑定该项目的新会话。Agent 后续的 `workspace.*` 读取、编辑、Git 查看与受审批命令只作用在该会话的项目根目录内。

桌面桥接遵循以下边界：

- Electron 主进程持有绝对路径和桥接令牌；Vue 渲染层、模型、API 返回值和聊天记录均不会获得真实路径。
- 后端只保存 AES-GCM 密文路径；`GET /api/workspaces` 仅返回项目名称、可访问状态与 Git 状态。
- `POST /api/workspaces` 除了 `workspace.manage` 权限外，还需要 `X-Harness-Desktop-Bridge` 令牌。普通浏览器请求会被拒绝。
- 新建会话时携带 `workspaceId`，Run 在创建时复制该 ID；Worker 每次工具调用都会重新验证项目仍存在且归属当前组织/用户。

在 Electron 桌面版中，也可以把**一个项目文件夹直接拖到聊天输入框**。preload 会从原生 `File` 受控取得路径并交给主进程，主进程确认它是一个真实目录后登记工作区；Vue 只收到工作区摘要，并自动创建绑定该项目的新会话。一次只接受一个项目目录，多目录拖入会明确拒绝，普通文件仍按附件导入。绝对路径不会进入页面、模型、聊天记录或 API 响应。

浏览器版仍保留“附件 / 文件夹”导入：这是复制 UTF-8 文本到受控工作区的降级能力，并不等于授权 Agent 操作原项目。Electron 当前提供开发态桌面壳；发行安装包、自动更新和代码签名可在后续发布模块接入。

### 会话项目浏览器

聊天工作台右上角的“项目文件”会打开当前会话绑定工作区的只读浏览器。它可逐层查看目录、预览 UTF-8 文本文件，并显示 Git 分支和未提交变更数量；这让用户能够在让 Agent 修改前核验当前项目与代码内容。

浏览器使用 `GET /api/workspace/files` 和 `GET /api/workspace/files/content`，均需要 `workspace.read` 权限，并通过 `workspaceId` 重新校验组织和用户归属。它只接受工作区内的相对路径，复用工具层的隐藏文件、符号链接、大小和行数限制；预览中的疑似凭证会显示为 `[REDACTED]`，不会返回本机绝对路径。文件浏览器仅提供查看能力，代码修改仍必须由 Agent 调用 `workspace.edit` / `workspace.write` 并经过既有审批策略。

### Git 变更审阅

已绑定 Git 项目的“项目文件”面板可展开“审阅变更”，查看当前工作区的变更文件，并按“工作区”或“暂存”查看单文件 Diff。对应接口是 `GET /api/workspace/git/status` 和 `GET /api/workspace/git/diff`，只运行固定的只读 Git 命令，不接受自由命令行参数；输出沿用工作区路径、大小和凭证脱敏保护。未跟踪文件没有 Git Diff 时可使用既有的受限文件预览检查内容。

工具输入使用 JSON，例如读取文件：

```json
{"path":"src/main/java/App.java","startLine":1,"endLine":120}
```

代码 Agent 修改已读取的文件时，应使用返回的 `sha256` 做乐观并发校验，并提交精确替换：

```json
{"path":"src/main/java/App.java","expectedSha256":"<read-result-sha256>","edits":[{"oldText":"return oldValue;","newText":"return newValue;"}]}
```

编辑后可以先查看工作区状态，再查看指定文件差异：

```json
{"path":"src/main/java/App.java"}
```

上例可作为 `workspace.git.diff` 的输入；`workspace.git.status` 使用 `{}` 输入即可。两者都是只读工具，适合在运行测试前确认 Agent 实际修改内容。

工作区写入只负责可靠地落盘；`workspace.exec` 使用 `ProcessBuilder` 参数列表直接启动白名单命令，不经过 Shell 拼接。命令执行默认关闭，开启后仍需要 `workspace.exec` 权限和人工审批，并会将命令、工作目录、退出码、超时/截断状态和输出摘要写入 Step 与 HMAC 审计链。

### Run 实时事件流

聊天工作台和 Run 详情会订阅 `GET /api/runs/{runId}/events`。连接建立后先收到 `snapshot`，之后只有 Run、Step、结果或错误发生持久化变化时才收到 `run` 事件；任务进入 `SUCCEEDED`、`FAILED`、`CANCELLED` 或 `TIMED_OUT` 后服务端自动关闭连接。每条事件正文与 `GET /api/runs/{runId}` 的既有 `RunDetail` 结构一致，不额外推送审计正文或本机工作区路径。

该接口需要 `run.read` 权限，并沿用 API Key、OIDC 或本地身份请求头。前端通过 `fetch` 而不是浏览器原生 `EventSource` 建立连接，因而能够携带 `Authorization` 等认证头；临时断线会自动重连，原有 HTTP 轮询仍是兜底。跨组织、无权限或其他连接失败会返回既有 JSON 错误结构，即使请求的 `Accept` 为 `text/event-stream` 也不会错误地变为 500。

```bash
curl -N http://localhost:8080/api/runs/<RUN_ID>/events \
  -H 'Authorization: Bearer demo-key' \
  -H 'Accept: text/event-stream'
```

为了让 Rabbit Worker 位于其他 Runtime 实例时也能更新浏览器，本实现按配置读取数据库快照，而不是依赖单 JVM 内存事件；生产部署无需粘性会话。应结合连接容量设置 `RUN_EVENT_STREAM_MAX_SUBSCRIBERS`，并通过负载均衡把 SSE 长连接合理分散到各实例。

### 代码 Agent 多轮模式

### 持久化聊天会话

聊天工作台使用会话接口将每轮用户消息和助手结果持久化到数据库。每条用户消息都会创建一个关联 Run；上下文未接近组织输入上限时保留最近的已完成消息，超过预算后把较早消息压缩到会话上下文快照中，同时保留完整原始消息。这样长会话不会因为历史累积直接超过 `maxInputLength`，Rabbit 异步模式下助手气泡先显示“Agent 执行中”，Worker 完成后自动回写最终内容。新会话在首条消息发送后会从用户输入生成简洁标题；已自定义的标题不会被覆盖。当前会话可在标题栏或 `⌘/Ctrl + K` 命令面板中重命名，修改只影响当前组织/用户可见的会话标题，不改变工作区绑定或既有 Run。

```bash
# 创建会话
curl -X POST http://localhost:8080/api/conversations \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-demo' \
  -H 'X-User-Id: operator' \
  -d '{"title":"代码工作台"}'

# 发送一轮消息，conversationId 替换为上一步返回值
curl -X POST http://localhost:8080/api/conversations/{conversationId}/messages \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-demo' \
  -H 'X-User-Id: operator' \
  -H 'Idempotency-Key: chat-round-1' \
  -d '{"content":"请读取项目入口并总结模块","maxTurns":8}'

# 先将本地 UTF-8 文本文件导入受控工作区，再把返回的附件 ID 传给消息接口。
# 浏览器和 Agent 都不会获得原始本机绝对路径。
curl -X POST http://localhost:8080/api/conversations/{conversationId}/attachments \
  -H 'X-Tenant-Id: tenant-demo' \
  -H 'X-User-Id: operator' \
  -F 'files=@src/main/java/App.java;type=text/plain'

# 目录上传时 files 和 paths 的顺序必须一致；前端会自动完成该组装。
curl -X POST http://localhost:8080/api/conversations/{conversationId}/attachments \
  -H 'X-Tenant-Id: tenant-demo' \
  -H 'X-User-Id: operator' \
  -F 'files=@demo-project/src/App.java;type=text/plain' \
  -F 'paths=demo-project/src/App.java' \
  -F 'files=@demo-project/README.md;type=text/markdown' \
  -F 'paths=demo-project/README.md'

curl -X POST http://localhost:8080/api/conversations/{conversationId}/messages \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-demo' \
  -H 'X-User-Id: operator' \
  -H 'Idempotency-Key: chat-round-with-file' \
  -d '{"content":"请阅读并修改已附加的文件","maxTurns":8,"attachmentIds":["<attachment-id>"]}'
```

相关接口：`GET /api/conversations`、`GET /api/conversations/{id}`、`POST /api/conversations/{id}/attachments`、`DELETE /api/conversations/{id}/attachments/{attachmentId}`、`POST /api/conversations/{id}/messages`。聊天框可拖入文件或文件夹，也可点击“文件夹”选择本地目录；文件夹会保留相对层级，以一个目录附件写入 `HARNESS_WORKSPACE_ROOT/attachments/<conversation-id>/`，Agent 会先通过 `workspace.list` 获取目录信息，再按需 `workspace.read` 或编写代码。附件仅接受 UTF-8 文本，单次最多 200 个文件、20 MB，单条消息最多 8 个文件或文件夹。浏览器不会暴露本机绝对路径，返回的 `workspacePath` 是 Agent 唯一可见、并受工作区安全边界保护的路径；文件会在消息发送成功时绑定到该轮记录，发送失败时前端会尽力回收未绑定文件。会话按组织和用户隔离，消息中的 `runId` 可以继续调用原有 Run 详情、审批、取消和重试接口。

聊天输入框旁的“Agent”设置可以调整本轮模型轮数上限，预设为 8、24、100 或平台上限 1000；该值会随消息发送到 `maxTurns`，并保存在浏览器中用于下次继续使用。较低的轮数适合快速问答，较高的轮数适合需要多次读取、修改和核验的代码任务。

`local` 与 `local-infra` Profile 默认启用受控工作区；若在其他环境启用聊天附件，请显式配置 `WORKSPACE_ENABLED=true` 与专用的 `HARNESS_WORKSPACE_ROOT`。不要把工作区配置成用户主目录或其他宽泛目录。

创建 Run 时将 `agentMode` 设置为 `true`，Harness 会把模型返回的 Tool Call 持久化为新的工具步骤；每个工具完成后自动追加下一轮模型步骤。模型结果、工具参数、审计事件和当前轮次都保存在数据库中，Rabbit Worker 重启后可以从最后一个已提交步骤恢复，而不会依赖进程内上下文。

```bash
curl -X POST http://localhost:8080/api/runs \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-demo' \
  -d '{"tenantId":"tenant-demo","userId":"operator","title":"分析项目结构","input":"请读取项目并总结入口模块","agentMode":true,"maxTurns":8,"budget":10,"permissions":"workspace.read"}'
```

Agent 模式下 `toolName` 不参与选择，模型只会收到当前组织工具白名单、工具可用性和当前执行身份权限都满足的工具契约；工具注册表、JSON Schema、组织策略、权限和审批仍是最终授权边界。默认演示模型在工作区工具可用时会先浏览根目录，再深入典型源码目录并读取一个代表性文本文件，把受限内容片段和 `sha256` 带入最终结论；没有工作区工具时退回安全的回显工具，便于本地验证真实的“模型 → 工具 → 模型”链路。接入外部模型后由模型自行决定工具调用。`maxTurns` 范围为 1 到 1000，超过后 Run 以 `FAILED` 结束并记录 `AGENT_MAX_TURNS_EXCEEDED`；重试时也会要求最后一轮模型明确结束，避免把未完成工具调用误判为成功。控制台创建表单可以直接开启 Agent 模式，详情页会展示模型轮次、Tool Call、工具输出和审批状态。

当前工作区工具支持浏览、读取、搜索、精确增量编辑、原子写入、Git 状态/差异查看和受控命令执行。写入和编辑工具需要 `workspace.write` 权限、人工审批以及读取时返回的 `sha256` 并发校验；编辑工具只接受精确文本替换，匹配不唯一时会拒绝执行，避免误改代码。Git 工具只查看工作区范围内的变更，不执行 Hook、外部 Diff 或 TextConv，不需要人工审批；为防止 Git 配置越界，工作区根目录必须是包含普通 `.git` 目录的仓库根目录。若目录不是 Git 仓库，工具会把原因和替代建议交给 Agent，默认演示模型会自动降级到 `workspace.read`，但该结果不会满足“修改后必须核验”的策略。命令工具需要 `workspace.exec` 权限、白名单和人工审批。Agent Run 的人工拒绝会作为带原因的工具结果反馈给下一轮模型，Agent 可调整计划后继续；普通 Run 仍会在拒绝后结束。所有工作目录仍受工作区根目录、隐藏路径和符号链接边界保护，Agent 不会获得任意 Shell 拼接能力。

开启命令沙箱的本地示例：

```bash
export WORKSPACE_ENABLED=true
export WORKSPACE_EXEC_ENABLED=true
export HARNESS_WORKSPACE_ROOT=/Users/ming/Projects/example
export WORKSPACE_ALLOWED_COMMANDS=./mvnw,npm,node
```

模型可以提交如下工具参数来运行测试；`args` 是独立参数数组，不是 Shell 字符串：

```json
{"command":"./mvnw","args":["test","-q"],"workdir":".","timeoutMs":120000}
```

旧版纯文本工具必须在 schema 中显式声明 `"x-harness-legacy-text": true`，普通文本会先转换成 JSON 字符串节点再执行其余约束；结构化对象工具不会静默降级为纯文本。新工具建议始终传入 JSON，例如：

```json
{
  "type": "object",
  "required": ["query"],
  "additionalProperties": false,
  "properties": {
    "query": { "type": "string", "minLength": 1 }
  }
}
```

模型网关只对网络错误、408、425、429 和 5xx 等临时故障重试；结构化响应错误和其他 4xx 不会盲目重试。主供应商达到熔断阈值后，配置了 `MODEL_FALLBACK_BASE_URL` 才会切换备用供应商。供应商返回的 `usage.prompt_tokens`/`completion_tokens`（也兼容 `input_tokens`/`output_tokens`）会写入 Step，并按每千 Token 单价计算实际成本；未返回 usage 时成本为 0，不会伪造计费数据。

### 协作式取消

在 Rabbit/Redis 模式下，取消接口会先写入组织绑定、自动过期的 Redis 取消信号，再等待数据库行锁释放并将 Run 状态写为 `CANCELLED`。Worker 在每个步骤和最终成功落库前读取该信号及数据库状态；发现取消时不会执行后续步骤，也不会用长事务中的旧状态覆盖取消结果。已经开始的外部工具调用不能被安全地强制中断，因此工具本身仍应实现超时、幂等和可取消协议。Redis 取消协调不可用时，`local-infra` 会返回基础设施不可用，而不会静默继续执行。

Rabbit Worker 仅在抛出临时基础设施异常时由队列重试；业务、策略和工具错误会持久化为 Run 的 `FAILED` 状态并确认消息。Worker 的模型/工具网络调用在数据库事务之外执行，领取、步骤状态、心跳、结果和审计分别使用短事务，避免长调用占用连接和行锁；每个步骤前后都会续租并再次校验 Worker 所有权。每个实例的消费者并发和预取量都有上限；Outbox Relay 发布前读取队列深度，达到 `RABBITMQ_MAX_QUEUE_DEPTH` 或无法读取队列状态时会暂停抢占，等待下一轮重试。Outbox 耗尽发布重试次数后会立即将尚未执行的 Run 标记为 `FAILED` 并记录 `RUN_DISPATCH_FAILED` 审计事件，避免任务无 Worker 执行却长时间显示为 `RUNNING`。可通过 `harness.rabbit.queue.depth`、`harness.rabbit.queue.capacity`、`harness.worker.active`、`harness.worker.concurrency`、`harness.rabbit.backpressure`、`harness.rabbit.queue.poll_failures` 以及原有的 `harness.rabbit.retries`、`harness.rabbit.dead_letters` 指标观察背压和 Worker 状态。

### 敏感数据与保留策略

Harness 会在写入 Run/Step、审计、上下文和 Outbox 错误前，统一替换常见的 API Key、Bearer Token、JWT、连接串密码、PEM 私钥和厂商 Token 为 `[REDACTED]`。模型调用前也会再次执行脱敏；长期记忆发现疑似凭证时直接拒绝写入。该规则是安全基线，不替代生产环境的密钥托管、DLP 和权限控制。

`local` 默认关闭自动清理，避免演示数据被删除；`local-infra` 默认开启。Run 与其 Step、审计链会作为一个完整单元清理，`RUN_RETENTION_DAYS` 会自动提升到不小于 `AUDIT_RETENTION_DAYS`，从而不会留下可查询但无法校验的半截审计链。正式环境应按合规要求设置保留天数，并在发布前评估删除不可逆性。

### 管理端点安全

`/actuator/health`、`/actuator/health/liveness` 和 `/actuator/health/readiness` 仅公开整体存活状态，不返回数据库地址、Redis 配置或异常详情。控制台通过带 `ops.read` 权限的 `GET /api/health` 获取数据库、Redis、RabbitMQ 和磁盘的状态摘要；该接口只返回状态码，不返回组件 details。`/api/health` 同时返回 Worker 活跃数/并发上限、Rabbit 队列深度/剩余容量、Outbox 待投递量以及重试、死信和超时计数；同步执行模式下无法监控的队列指标会省略对应字段，不会把内部哨兵值暴露给前端。`/actuator/metrics/**`、`/actuator/prometheus` 和 `/actuator/info` 均需要 `ops.read`，生产环境不要将这些端点直接暴露到公网。

## API 示例

所有 API 请求都可以通过 `X-Request-Id` 和 `X-Trace-Id` 传入调用链标识；如果未传入或格式不安全，服务会生成新的 UUID，并在响应头中返回。错误响应统一包含 `code`、`message`、`details`、`timestamp` 和 `traceId`，排查问题时请优先使用 `traceId` 关联日志和审计记录。

创建 Run：

```bash
curl -X POST http://localhost:8080/api/runs \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-demo' \
  -d '{"tenantId":"tenant-demo","userId":"operator","title":"订单分析","input":"分析订单状态","toolName":"demo.echo","budget":1}'
```

创建后调用 `POST /api/runs/{runId}/start` 启动；高风险工具会进入等待审批状态，再调用 `POST /api/runs/{runId}/approve` 或 `POST /api/runs/{runId}/reject`。失败任务可调用 `POST /api/runs/{runId}/retry`。

上下文接口：

- `GET /api/runs/page?page=0&size=20&status=RUNNING`：按组织分页查询 Run，`status` 可选，单页最多 100 条；原 `GET /api/runs` 继续返回最近 50 条数组
- `POST/GET/DELETE /api/context/documents`：管理组织隔离的知识文档
- `POST /api/context/documents/upload`：以 multipart 上传一个 PDF/DOCX，字段为 `file`（必填）、`title`、`sensitivity`、`allowedUsers`（可选）；需要 `context.write` 权限，成功后立即创建 chunk 并异步补齐 embedding
- `POST/GET/DELETE /api/context/memories`：管理用户范围的长期记忆
- `POST/GET /api/education/goals`：创建和查询绑定学习者画像的结构化学习目标；目标测评、推荐和保持度计划接口位于 `/api/education/goals/{goalId}/...`，目标不能通过状态接口直接改为 `COMPLETED`
- `POST /api/education/profiles/{profileId}/mastery`：仅用于没有进行中学习目标时的画像基线/校准写入；形成性或保持度观察必须通过绑定 Run 的测评接口提交，不能直接伪造目标完成
- `GET /api/education/tasks`：查询当前用户的教育学习任务；到期复习会在查询或后台调度时自动物化
- `POST /api/education/tasks/{taskId}/start`：启动任务并绑定学习会话与教育 Run；重复调用会恢复已绑定会话
- `POST /api/education/tasks/{taskId}/defer`：延期任务并同步顺延复习计划，不记录虚假的复习结果
- `GET /api/education/notifications?unreadOnly=false&limit=50`：查询当前用户的学习任务站内通知，并返回未读数量；查询会记录通知已被客户端触达
- `POST /api/education/notifications/{notificationId}/read`：将一条学习任务通知标记为已读
- `POST /api/education/notifications/read-all`：将当前用户的学习任务通知全部标记为已读
- `GET /api/education/metrics`：读取当前租户和用户可见的作业、提交物覆盖、待重试/待返工作业、任务、测评证据、教师确认、教师量规评价覆盖与三维平均分、反馈确认与执行、反馈确认时延、重试成功率、保持度正确率和平均掌握度提升；无事实时各比率返回 `0`
- `GET /api/education/experiments`：按 Run 创建时冻结的教育检索策略聚合实验指标，包括证据覆盖、前置缺口覆盖、证据冗余、目标知识点匹配、知识图覆盖、难度适配、测评准确率、平均掌握度变化和目标达成轮次
- `GET /api/education/experiments.csv`：导出上述策略级实验指标；样本状态会区分无数据、样本不足和达到基础分析门槛，不能把小样本结果误读为显著性结论
- `GET /api/education/experiments/paired.csv`：导出同一学习者-目标内以 `FULL` 为参考的配对策略结果；差值定义为“对比策略 − FULL”，达到目标轮次为负表示对比策略更快
- `GET /api/education/evidence-impact`：按“检索策略 + citation”聚合形成性测评的证据级学习收益，返回引用权重、掌握度增益、正确率、排序拆解和快照匹配率；只统计成功教育 Run
- `GET /api/education/evidence-impact.csv`：导出证据级学习收益归因，适合与教师证据标注或策略消融结果联表分析
- `GET /api/education/retrieval-calibration`：查看当前租户教师检索证据标注聚合出的版本化校准快照；只有新建并选择 `CALIBRATED` 策略的 Run 使用该快照
- `POST/GET /api/education/courses`：教师创建或查询课程实例；课程固定学科、年级和课程版本，课程状态为 `ACTIVE`、`COMPLETED` 或 `ARCHIVED`
- `POST/GET /api/education/courses/{courseId}/enrollments`：课程负责人加入或查询学习者名单；`POST /api/education/courses/{courseId}/enrollments/{learnerUserId}/remove` 可移除成员，已结课或已归档课程不能再变更名单
- `POST /api/education/courses/{courseId}/assignments`：向课程活跃名单批量布置统一目标，必须携带 `Idempotency-Key`；同一课程和幂等键会复用原批次，同一键提交不同内容会返回 `409 ASSIGNMENT_BATCH_KEY_REUSED_WITH_DIFFERENT_REQUEST`
- `GET /api/education/courses/{courseId}/progress?limit=500`：教师查看课程级作业状态、教师确认、开放干预、活跃名单作业覆盖率和名单中每个学习者的掌握度进度；超过上限时返回 `truncated=true`
- `POST /api/education/courses/{courseId}/complete`：教师在有活跃名单、每名活跃学习者至少有一份有效作业、所有未取消作业均达到 `COMPLETED + VERIFIED` 且每份作业都有学习者提交物后结课；结课说明、操作者和时间会作为课程事实保存，未满足条件时返回相应 `409` 状态码
- `GET /api/education/courses/{courseId}/result`：查询结课时固化的课程级和学习者级结果快照；课程教师可查看全班结果，学习者只能查看自己的结果，后续保持度复习不会改写该快照
- `GET /api/education/courses/{courseId}/result.csv`：仅课程教师可导出结课结果 CSV；报告包含课程汇总和每名学习者的冻结结果，适合归档、交付或导入后续教务流程
- `POST/GET /api/education/assignments`：教师/组织以当前身份布置或查询课程作业；作业携带学科、年级、课程版本和目标知识点，截止时间到达后会显示为 `OVERDUE`
- `GET /api/education/assignments/{assignmentId}`：查询当前用户作为布置者或学习者参与的课程作业
- `GET /api/education/assignments/{assignmentId}/progress`：查询当前参与者可见的掌握度、测评、Run 证据覆盖、掌握度提升、反馈确认和学习任务进度；教师可据此判断是否需要干预以及干预是否被确认
- `POST/GET /api/education/assignments/{assignmentId}/submissions`：学习者提交或查询绑定到成功教育 Run 的作业提交物；目标达标后作业虽进入 `COMPLETED`，在教师确认前仍允许补交；同一 Run 重复提交幂等返回原提交，教师可据此查看可审计的原始作答
- `POST/GET /api/education/assignments/{assignmentId}/feedback`：教师提交普通反馈、补证据、重新学习或重新安排截止时间的干预；学习者可确认反馈，补证据/重新学习干预在下一次教育 Run 成功创建后进入 `RESOLVED`，不再污染后续 Run
- `GET /api/education/assignments/{assignmentId}/evaluations`：查询该作业不可变的教师量规评价历史；教师和该作业学习者可见，评价记录包含内容正确性、证据质量、迁移准备度（1-5 分）、决定和量规版本
- `GET /api/education/evaluation-queue`：拥有 `education.evaluate` 权限的第二评分者查询待独立评价的已完成作业
- `POST /api/education/assignments/{assignmentId}/evaluations`：第二评分者提交独立量规评价；不会改变教师确认状态，同一评分者对同一作业幂等返回原记录
- `GET /api/education/assignments/{assignmentId}/evaluations/consensus`：查看教师评价与独立评价的共识状态；三个维度的分差均不超过 1 分时为 `AGREED`
- `POST /api/education/assignments/{assignmentId}/accept`：学习者接受作业，系统幂等创建对应学习者画像和结构化学习目标
- `POST /api/education/assignments/{assignmentId}/start`：学习者接受（如尚未接受）并直接启动第一步教育 Run；返回绑定的学习会话，作业要求和未完成的“补证据/重新学习”教师干预会冻结到 Run 上下文；失败、超时或取消的 Run 会从该入口进入下一轮重试
- `POST /api/education/assignments/{assignmentId}/review`：布置者用 `VERIFY` 确认已达标且已有学习者提交物的作业，或用带说明的 `RETURN` 退回返工；请求必须提供内容正确性、证据质量、迁移准备度三个 1-5 分量规，系统把学习者掌握度达标、教师交付确认、量规评价和返工周期分开记录，重复确认幂等返回
- `POST /api/education/assignments/{assignmentId}/cancel`：布置者取消尚未完成的课程作业，取消后学习者不能再接受该作业
- `GET /api/context/preview?query=...`：预览授权来源和引用
- `GET/PUT/DELETE /api/context/embedding-config`：读取、保存或恢复当前组织的 Embedding 连接配置；密钥只返回掩码
- `POST /api/context/embedding-config/test`：使用未保存配置测试一次 OpenAI 兼容 `/embeddings` 连接
- `POST /api/context/reindex`：按租户有界重建上下文 chunk 和 embedding，需要 `context.reindex` 权限；`rechunk=true` 时按当前语义分块配置重新切块
- `POST/GET /api/runs/{runId}/feedback`：对自己的 Run 记录 `POSITIVE`/`NEGATIVE` 反馈、原因和备注；重复提交会覆盖同一用户对该 Run 的反馈，并写入审计事件
- `GET /api/runs/{runId}` 的 Step 详情包含 `contextEvidence`：模型步骤实际注入的授权来源、标题、citation 和摘要，可从聊天消息追溯到 Run 详情
- `GET /api/education/runs/{runId}/retrieval-judgments`：查看该教育 Run 的证据级教师评价；教师可用 `POST` 到同一路径提交目标 grounding、前置补强、难度适配和总体效用四项 1--5 分评价。提交只允许引用该 Run 已实际检索到的证据，历史评价不可变，重复评价按同一评价者/Run/证据保留最新版本参与校准

## 设计约束

模型只能提出行动，工具注册表和策略代码才可以授权执行。所有执行结果、错误、审批和版本信息都会持久化，便于恢复、重放和审计。`local-infra` 已提供 PostgreSQL、Redis 共享限流/执行锁、带发布租约的 RabbitMQ 异步 Worker、带数据库行锁的多实例超时恢复、API Key 和 OIDC/JWT RBAC 基线；正式环境仍需接入密钥托管、告警和密钥轮换。
