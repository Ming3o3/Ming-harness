# 本地基础设施运行说明

本项目提供两种运行方式：

- `local`：H2 + 进程内同步执行，适合快速演示和单元测试。
- `local-infra`：PostgreSQL 17 + Redis + RabbitMQ，使用 Flyway、Outbox 和异步 Worker。

应用不会自动启动 Homebrew 服务，也不会自动迁移现有 H2 演示数据。

## 工作区工具

代码 Agent 的文件访问必须通过工作区工具，不直接暴露应用进程的任意文件系统。生产或共享环境请显式指定专用项目目录：

```bash
export WORKSPACE_ENABLED=true
export HARNESS_WORKSPACE_ROOT=/Users/ming/Projects/example
# 命令执行默认关闭；只把确实需要的可执行文件加入白名单。
export WORKSPACE_EXEC_ENABLED=true
export WORKSPACE_ALLOWED_COMMANDS=./mvnw,npm,node
export WORKSPACE_MAX_COMMAND_TIMEOUT_MS=120000
export WORKSPACE_MAX_COMMAND_OUTPUT_BYTES=200000
```

`workspace.list`、`workspace.read`、`workspace.search`、`workspace.git.status` 和 `workspace.git.diff` 需要 `workspace.read` 权限；路径、目录类型、二进制/过大文件等只读业务错误会以脱敏的 `recoverable=true` 结构化结果返回，Agent 可根据 `suggestion` 重新浏览或缩小读取范围。Git 工具只查看工作区范围内的状态和差异，并禁用外部 Diff、TextConv 与 fsmonitor，不需要人工审批。Git 工具要求 `HARNESS_WORKSPACE_ROOT` 指向包含普通 `.git` 目录的仓库根目录，不接受通过 `.git` 文件指向外部目录的独立 worktree。非 Git 工作区会以 `available=false`、`recoverable=true` 的结构化结果返回原因和 `workspace.read` 建议，Agent 可以继续检查文件；该结果不会计入修改后的有效 Git 核验。`workspace.write` 和 `workspace.edit` 是高风险副作用工具，需要 `workspace.write` 权限和人工审批。覆盖已有文件时必须携带读取结果中的 `sha256`，从而避免 Agent 把其他人的并发修改静默覆盖。`workspace.edit` 只允许精确文本片段替换，单个片段默认必须唯一匹配，适合代码 Agent 增量修改而不必回传整文件。默认拒绝隐藏文件、符号链接和工作区外路径，单次读取/写入默认限制为 1 MB。

`workspace.exec` 是高风险命令沙箱，需要 `workspace.exec` 权限、人工审批和 `WORKSPACE_ALLOWED_COMMANDS` 白名单。它使用参数数组直接启动进程，不执行 `sh -c` 或其他 Shell 拼接；工作目录必须在工作区根目录内，子进程不会继承数据库密码、模型 API Key 等宿主环境变量。命令超时或输出超过上限会终止进程树，并在结果和 `WORKSPACE_COMMAND_EXECUTED` 审计事件中标记 `timedOut`/`outputTruncated`。建议只允许项目测试、构建所需的固定可执行文件，不要把 `sh`、`bash`、`sudo`、`rm` 等通用系统命令加入白名单。

## 1. 启动本地服务

```bash
brew services start postgresql@17
brew services start redis
brew services start rabbitmq
brew services list
```

确认 PostgreSQL 已安装 pgvector：

```bash
psql --version
psql postgres -c "SELECT name, default_version FROM pg_available_extensions WHERE name = 'vector';"
```

## 2. 创建数据库

下面的用户名和密码只是本地示例，请按自己的环境替换，并通过环境变量传给应用：

```bash
psql postgres -c "CREATE USER ming_harness WITH PASSWORD 'ming_harness';"
psql postgres -c "CREATE DATABASE ming_harness OWNER ming_harness;"
```

如果用户或数据库已经存在，忽略对应的 already exists 提示即可。

RabbitMQ 本地默认使用 `guest/guest`，Redis 默认无密码。

## 3. 启动后端

```bash
DB_USERNAME=ming_harness \
DB_PASSWORD=ming_harness \
SPRING_PROFILES_ACTIVE=local-infra \
./mvnw spring-boot:run
```

`local-infra` 启动时会执行 Flyway 迁移，创建 Run、Step、审计、上下文、Outbox 和组织资源策略表，并声明 RabbitMQ 主队列和死信队列。
Outbox Relay 会先在 PostgreSQL 中抢占短期发布租约，再在租约外等待 RabbitMQ 发布确认；多实例不会同时发送同一条待处理事件。进程在确认前中断时，租约到期后允许重新投递，Run 执行锁负责去重。当同一事件达到 `RABBITMQ_MAX_ATTEMPTS` 仍无法获得发布确认时，Outbox 会进入 `FAILED`，对应 Run 会在带行锁的短事务中立即落为 `FAILED`，并追加 `RUN_DISPATCH_FAILED` 审计事件，不再等待 Worker 租约超时后才让用户看到失败。

### 启用上下文向量检索

`local-infra` 使用 PostgreSQL + pgvector 保存上下文子块向量。Flyway 会自动执行向量列和 HNSW 索引迁移；应用只有在 PostgreSQL、embedding 网关和 `EMBEDDING_ENABLED=true` 同时满足时才会执行向量召回，否则继续使用关键词召回。

先配置一个 OpenAI 兼容的 `/embeddings` 服务：

```bash
export EMBEDDING_ENABLED=true
export EMBEDDING_BASE_URL=https://api.example.com/v1
export EMBEDDING_API_KEY='由密钥系统注入'
export EMBEDDING_MODEL=text-embedding-3-small
export EMBEDDING_MODEL_VERSION=v1
export EMBEDDING_DIMENSION=1536
export EMBEDDING_BATCH_SIZE=10
export EMBEDDING_MAX_INPUT_TOKENS=8192
```

也可以在桌面端的“向量设置”弹窗中保存组织级配置。页面保存的配置优先于同名环境变量；切换供应商、模型或版本后，旧 chunk 向量会自动清空，必须执行一次“重建索引”。当前迁移的 pgvector 列固定为 1536 维，其他维度需要先扩展数据库迁移，不支持直接在页面中混用。

#### 导入 PDF/DOCX 知识文档

运行控制台的“上下文治理”面板可以选择或拖入一个 PDF/DOCX。上传后接口立即创建 `PROCESSING` 文档并返回，原始文件暂存在 `CONTEXT_DOCUMENT_IMPORT_DIRECTORY`，后台 Worker 再按 PDF 页、DOCX 段落/表格段落流式解析，逐步分块、分批写入 chunk 和父窗口，完成后更新为 `READY` 并触发 embedding；失败则更新为 `FAILED`，页面显示脱敏错误并允许重新上传。服务端按文件扩展名和文件头双重校验，解析完成后原始二进制自动删除；解析结果沿用现有文档权限、chunk、父窗口和 embedding 索引流程。默认原始文件上限为 100 MB，解析正文上限为 1,000,000 字符，可通过 `CONTEXT_DOCUMENT_MAX_UPLOAD_BYTES` 和 `CONTEXT_DOCUMENT_MAX_CONTENT_CHARS` 调整。超过正文上限时返回/记录 `DOCUMENT_TEXT_TOO_LARGE`，不会静默截断、保存不完整正文或创建半成品索引。只有包含文本层的 PDF 可以直接提取，扫描型 PDF 需要先 OCR；加密或损坏文件会返回结构化解析错误。应用重启会重新接管仍有暂存文件的 `PROCESSING` 文档。

也可以直接调用上传接口（调用方需要 `context.write` 权限）：

```bash
curl -X POST http://localhost:8080/api/context/documents/upload \
  -H 'Authorization: Bearer demo-key' \
  -F 'file=@./docs/release-rules.pdf' \
  -F 'title=发布规则' \
  -F 'sensitivity=INTERNAL' \
  -F 'allowedUsers=operator'
```

接口返回的文档状态初始为 `PROCESSING`，异步完成后列表中的 `importStatus` 变为 `READY`；异步导入文档的 `content` 字段保持为空，正文以受权限控制的 chunk/父窗口形式保存，关键词或向量检索会按问题召回相关片段。模型单次上下文仍受 `MAX_CONTEXT_CHARS`（默认 64,000 字符）约束，不会因为资料总长度增加而把全文发送给模型。embedding 网关不可用时，确定性 chunk 仍会保存，待处理 chunk 可通过“向量索引”或 `POST /api/context/reindex` 补齐。

`EMBEDDING_DIMENSION` 必须与数据库中的 `vector(1536)` 一致；更换模型、维度或语义分块版本后，应执行一次有界重建。语义分块默认关闭，开启后会对段落/句子原子单元批量向量化，按相邻单元余弦相似度寻找边界，同时保留最大长度、最小单元数和 overlap 约束：

```bash
export CONTEXT_SEMANTIC_ENABLED=true
export CONTEXT_SEMANTIC_BREAKPOINT=0.35
export CONTEXT_SEMANTIC_MIN_UNITS=3
```

代码块等结构单元仍优先于语义边界；embedding 服务暂时不可用时，写入和重建会回退到确定性分块，并留下待索引数量等待下次重建。生产环境建议把重建放在低峰期，并观察 `harness.context.embedding.*`、`harness.context.vector.*` 和 `harness.context.index.*` 指标。

索引器会把成功的 chunk embedding 写入 PostgreSQL 缓存，缓存键包含租户、内容哈希、请求模型、模型版本和维度。命中缓存时不会再次调用供应商，但仍会把向量写入当前 chunk；缓存只作为加速层，缓存数据库读写失败会自动退化为正常 API 索引。更换模型权重、供应商部署或预处理方式时递增 `EMBEDDING_MODEL_VERSION`，旧缓存不会被误用。缓存命中和未命中可分别通过 `harness.context.embedding.cache.hits`、`harness.context.embedding.cache.misses` 观察，并由 `EMBEDDING_CACHE_RETENTION_DAYS` 控制清理。

检索阶段使用“小块召回，大块推理”。每个子块单独写入 pgvector，向量命中后按连续子块聚合为有界父窗口，再将父窗口作为模型上下文返回。父窗口默认上限为 4800 字符，可通过 `CONTEXT_PARENT_WINDOW_MAX_CHARS` 调整；父窗口不单独参与向量召回，也不会绕过文档权限、租户隔离或记忆用户过滤。

```bash
export CONTEXT_PARENT_WINDOW_MAX_CHARS=4800
```

修改子块大小、语义分块阈值或父窗口上限后，应使用 `rechunk=true` 重新物化子块和父窗口，再补齐 embedding：

正文写入默认只在事务中保存父对象和 chunk，事务提交后再由有界后台队列执行 embedding，避免供应商网络延迟占住数据库连接。可通过以下参数调整并发；队列满或进程在任务完成前退出时，数据库中仍保留 `embedded_at` 为空的 chunk，下一次重建会继续处理：

```bash
export CONTEXT_INDEX_ASYNC_ENABLED=true
export CONTEXT_INDEX_CONCURRENCY=2
export CONTEXT_INDEX_QUEUE_CAPACITY=100
```

重建接口按租户、父对象和 chunk 数量设上限，不会一次性把整个租户发送给外部服务。调用方需要 `context.reindex` 权限：

```bash
curl -X POST http://localhost:8080/api/context/reindex \
  -H 'Authorization: Bearer demo-key' \
  -H 'Content-Type: application/json' \
  -d '{"scope":"DOCUMENT","parentLimit":100,"chunkLimit":1000,"rechunk":true}'
```

`scope` 可取 `ALL`、`DOCUMENT` 或 `MEMORY`；`rechunk=true` 才会按当前分块配置替换已有子块，省略时只补齐缺失的 chunk 并为未向量化的 chunk 建索引。响应中的 `chunksFailed` 和 `pendingChunks` 可用于判断是否需要重试。

Rabbit Worker 的模型和工具调用在数据库事务之外执行；领取租约、步骤开始/完成、心跳、审计和终态写回分别是短事务。每个步骤前后都会续租 Redis 锁并刷新 PostgreSQL Worker 租约，旧 Worker 丢失所有权后不能覆盖新 Worker 或取消操作的结果。Worker 执行锁会自动使用不小于 `RECOVERY_TIMEOUT_MS` 的租期，避免数据库恢复器在一个受控长步骤期间过早回收 Run。

Worker 默认每个实例启动 1 个消费者，最多扩展到 4 个消费者，每个消费者预取 1 条消息。Outbox Relay 发布前会读取执行队列深度：达到 `RABBITMQ_MAX_QUEUE_DEPTH`（默认 1000）时暂停抢占，RabbitMQ 队列状态读取失败时也会安全暂停，待下一轮恢复后继续。可以根据模型供应商并发额度和数据库容量覆盖：

```bash
export RABBITMQ_CONSUMER_CONCURRENCY=1
export RABBITMQ_MAX_CONSUMER_CONCURRENCY=4
export RABBITMQ_PREFETCH=1
export RABBITMQ_MAX_QUEUE_DEPTH=1000
export RABBITMQ_QUEUE_METRICS_POLL_MS=5000
```

恢复器每 30 秒扫描一次过期 Worker 租约；候选 Run 使用 PostgreSQL/H2 悲观行锁读取，等待正在提交的 Worker 后重新判断状态，避免多实例恢复器把已经成功的结果覆盖为 `TIMED_OUT`。恢复操作会追加带 HMAC 的 `RUN_RECOVERED_AS_TIMED_OUT` 审计事件；`RECOVERY_TIMEOUT_MS` 默认 120 秒，生产环境应结合最长模型/工具调用和告警延迟设置。

取消 RUNNING 任务时，应用会先写入带组织范围、自动过期的 Redis 协作信号；Worker 会在每个步骤边界检查该信号，再检查 PostgreSQL 中的最终 Run 状态。这样即使 Worker 长事务暂时占用 Run 行，取消请求也能先让 Worker 停止后续步骤并释放锁。已开始的外部工具调用不能被安全地强制中断，因此仍应为工具配置超时、幂等键和可取消协议；Redis 不可用时取消接口会明确返回基础设施错误，不会静默降级。

如果使用真实模型服务，建议同时设置供应商可靠性和成本参数：

```bash
export MODEL_ENABLED=true
export MODEL_BASE_URL=https://api.example.com/v1
export MODEL_API_KEY='由密钥系统注入'
export MODEL_NAME=primary-model
export MODEL_MAX_ATTEMPTS=3
export MODEL_CIRCUIT_FAILURE_THRESHOLD=3
export MODEL_INPUT_COST_PER_1K_TOKENS=0.0005
export MODEL_OUTPUT_COST_PER_1K_TOKENS=0.0015
# 可选：主供应商临时故障时切换到另一家 OpenAI 兼容服务
export MODEL_FALLBACK_BASE_URL=https://backup.example.com/v1
export MODEL_FALLBACK_API_KEY='由密钥系统注入'
export MODEL_FALLBACK_NAME=backup-model
```

网关只重试临时 HTTP/网络故障，并对连续故障执行应用内熔断；4xx 参数错误和响应契约错误会直接让当前 Run 失败。供应商未返回 usage 时不会猜测 token 成本，Run 预算按已知实际成本校验。

共享环境可以启用 API Key 认证和接口权限校验：

```bash
export HARNESS_AUTH_MODE=api-key
export HARNESS_API_KEYS='demo-key|tenant-demo|operator|run.read,run.create,run.execute,run.approve,run.cancel,audit.read,context.read,context.write,context.configure,tool.read,ops.read,tenant.policy.read,tenant.policy.write,auth.key.read,auth.key.manage'
```

调用时使用 `Authorization: Bearer demo-key`。API Key 绑定的组织和用户会覆盖请求头，Run 创建请求中的 `tenantId/userId` 必须与认证身份一致。默认 `local` 模式仍兼容 `X-Tenant-Id`、`X-User-Id` 和 `X-Permissions`，仅适合本地演示。

`HARNESS_API_KEYS` 适合作为初始运维密钥；它们来自启动配置，撤销需要替换密钥系统配置并重启。共享环境应使用该初始密钥（或具备 `auth.key.manage` 的 OIDC 服务账号）创建可即时撤销的数据库 Key，明文仅在响应中显示一次：

```bash
curl -X POST http://localhost:8080/api/admin/api-keys \
  -H 'Authorization: Bearer demo-key' \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"tenant-demo","userId":"analyst","permissions":["run.read","run.create","run.execute"],"expiresAt":"2027-01-01T00:00:00Z"}'
```

保存响应中的 `secret` 后，可用 `GET /api/admin/api-keys` 查看前缀、状态和过期时间；使用 `POST /api/admin/api-keys/{keyId}/rotate` 会原子创建同权限新 Key 并立即使旧 Key 失效，`DELETE /api/admin/api-keys/{keyId}` 也可以直接撤销。创建或轮换响应之外，API、数据库、日志和审计均不会返回完整 Key 或摘要。查询需要 `auth.key.read`，创建/轮换/撤销需要 `auth.key.manage`，跨组织操作另需 `auth.key.cross-tenant`。

### 组织级资源治理

平台环境变量定义所有组织都不能突破的硬上限。拥有 `tenant.policy.read`/`tenant.policy.write` 权限的身份可以通过管理接口为自己的组织设置更严格的活动 Run 数、步骤数、输入长度、单次预算、创建速率和工具白名单；跨组织运维还需要额外的 `tenant.policy.cross-tenant` 权限。未配置覆盖策略的组织自动使用平台默认值。工具白名单为空表示允许所有已注册工具，非空时 Run 创建阶段会在任何执行前拒绝未列出的工具。

```bash
curl -X PUT http://localhost:8080/api/admin/tenants/tenant-demo/policy \
  -H 'Authorization: Bearer demo-key' \
  -H 'Content-Type: application/json' \
  -d '{"maxActiveRuns":5,"maxStepsPerRun":10,"maxInputLength":5000,"maxBudget":50,"maxCreatesPerMinute":20,"allowedTools":["demo.echo"]}'
```

`GET /api/admin/tenants/{tenantId}/policy` 查看当前生效策略，`DELETE` 恢复平台默认值，`GET .../policy/audits` 查看最近策略变更。策略变更与前后数值会单独留痕；策略只能收紧平台硬上限，不会因为组织配置错误而突破系统容量边界。

企业 OIDC/JWT：

```bash
export SPRING_PROFILES_ACTIVE=local-infra,oidc
export OIDC_ISSUER_URI=https://login.example.com/realms/harness
export OIDC_AUDIENCE=ming-harness-api
export AUDIT_INTEGRITY_KEY='由密钥系统注入的长随机字符串'
./mvnw spring-boot:run
```

JWT 必须包含 `sub`、`tenant_id`（或 `tenant`）以及配置的 `aud`（`OIDC_AUDIENCE`，多个值逗号分隔），权限可放在 `permissions`、`scope` 或 `scp` 声明中。Spring Security Resource Server 负责 JWT 验签及 issuer/audience 校验，Harness 负责组织绑定和接口 RBAC。未配置 audience 时 OIDC 模式会快速失败，不允许无受众保护地上线。

审计完整性密钥通过 `AUDIT_INTEGRITY_KEY` 注入。审计查询接口之外，还可以校验指定 Run：

```bash
curl http://localhost:8080/api/runs/<RUN_ID>/audit-events/verify \
  -H 'X-Tenant-Id: tenant-demo'
```

返回 `valid=false` 时优先查看 `failureCode`；`LEGACY_UNSIGNED_EVENTS` 表示迁移前的历史审计记录尚未封签。

工具重试和预算治理：只读工具抛出 `RetryableToolException` 时才会按 `maxAttempts` 有限重试，`MAX_TOOL_ATTEMPTS` 默认限制为 3 次；副作用工具不会自动重试。模型实际成本超过 Run 的 `budget` 时，任务会失败并记录 `RUN_BUDGET_EXCEEDED` 审计事件。

健康检查：

```bash
# 公开探针只返回整体 status，不返回组件详情。
curl http://localhost:8080/actuator/health
# 控制台和运维查看组件状态使用受 ops.read 保护的摘要接口。
curl http://localhost:8080/api/health -H 'Authorization: Bearer demo-key'
# 运行指标、Prometheus 和应用信息同样需要 ops.read。
curl http://localhost:8080/actuator/metrics/harness.worker.duration -H 'Authorization: Bearer demo-key'
curl http://localhost:8080/actuator/metrics/harness.rabbit.retries -H 'Authorization: Bearer demo-key'
curl http://localhost:8080/actuator/metrics/harness.rabbit.dead_letters -H 'Authorization: Bearer demo-key'
curl http://localhost:8080/actuator/metrics/harness.rabbit.queue.depth -H 'Authorization: Bearer demo-key'
curl http://localhost:8080/actuator/metrics/harness.rabbit.backpressure -H 'Authorization: Bearer demo-key'
```

## 4. 启动前端

另开终端执行：

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:5173`。控制台会自动轮询非终态 Run，并显示数据库、Redis、RabbitMQ 健康状态。

## 5. 验证异步执行链

创建 Run 后，API 会先将任务状态持久化并写入 Outbox；Relay 发布到 RabbitMQ，Worker 执行模型和工具步骤，前端通过详情接口轮询最终状态。

```bash
curl -X POST http://localhost:8080/api/runs \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-demo' \
  -H 'Idempotency-Key: local-infra-demo-1' \
  -d '{"tenantId":"tenant-demo","userId":"operator","title":"异步演示","input":"验证异步执行","toolName":"demo.echo","budget":1}'
```

拿到返回的 `id` 后启动：

```bash
curl -X POST http://localhost:8080/api/runs/<RUN_ID>/start \
  -H 'X-Tenant-Id: tenant-demo'
```

## 6. 停止服务

```bash
brew services stop rabbitmq
brew services stop redis
brew services stop postgresql@17
```

Redis 只保存限流计数和短期执行锁；业务数据、审计数据和幂等事实始终以 PostgreSQL 为准。
活动 Run 配额检查还会使用 `harness:tenant:{tenantId}:run-quota-lock` 短期互斥键，锁在数据库事务提交或回滚后释放；Redis 不可用时不会退回不安全的单实例配额判断。

## 7. 数据保留和敏感信息治理

`local-infra` 默认开启定时清理。可以在启动前按合规要求覆盖配置：

```bash
export DATA_RETENTION_ENABLED=true
export RUN_RETENTION_DAYS=90
export AUDIT_RETENTION_DAYS=365
export MEMORY_RETENTION_DAYS=30
export DOCUMENT_RETENTION_DAYS=30
export OUTBOX_RETENTION_DAYS=14
export RETENTION_BATCH_SIZE=100
```

清理任务只处理已结束 Run，并同时删除其 Step、审计事件和对应 Outbox；仍处于 `QUEUED`、`RUNNING` 或 `WAITING_APPROVAL` 的任务不会被删除。`PENDING` 或 `PUBLISHING` Outbox 代表尚未确认投递的执行命令，永远不会被保留任务直接删除。到期长期记忆会清理，用户主动删除的文档和记忆则在各自保留期结束后物理删除。

Run 和审计保留期取两者较大值，这是为了保持审计 HMAC 链完整。清理是不可逆操作，生产环境应先在备份和合规策略确认后启用；本地演示可以设置 `DATA_RETENTION_ENABLED=false`。Run、Step、审计、模型输入输出和工具错误中的常见凭证会在持久化或外发前替换为 `[REDACTED]`，长期记忆发现疑似凭证时会拒绝写入。
