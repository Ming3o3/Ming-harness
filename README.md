# Ming Harness

Ming Harness 是一个面向企业 Agent 的可运行 Harness：后端使用 Spring Boot 4，前端使用 Vue 3 + Vite。当前实现覆盖单 Agent Runtime、安全策略、上下文治理、离线评测和运行观测基线。

## 已实现模块

- Run / Step 持久化状态机：`QUEUED -> RUNNING -> WAITING_APPROVAL -> SUCCEEDED / FAILED / TIMED_OUT / CANCELLED`
- 幂等与资源边界：支持 `Idempotency-Key`、租户活动 Run 配额、创建速率、输入/预算/步骤数限制
- 可插拔模型网关：默认演示模型，也支持 OpenAI 兼容的 `/chat/completions` 接口
- 工具注册表与确定性策略：权限、风险、审批、网络策略、超时和输入校验
- 受控代码工作区工具：目录浏览、UTF-8 文件读取、文本搜索、带哈希并发保护的原子写入和白名单命令沙箱
- 审计与观测：Run `traceId`、Step `spanId`、Token、耗时、成本和租户/操作者快照，审计事件支持 HMAC 完整性校验
- 租户隔离：读写 Run、Step、审计事件都需要 `X-Tenant-Id`
- 可插拔认证与 RBAC：`local` 兼容演示请求头，`api-key` 和 `oidc` 支持租户、用户和接口权限快照
- 失败重试：只读工具可用 `RetryableToolException` 触发有限自动重试；有副作用的工具禁止自动重试，人工重试前会重新走审批
- 协作式取消：取消请求先写入租户绑定的短期协调信号，Worker 会在每个步骤和最终完成前检查，避免长步骤后的后续副作用继续执行
- 上下文与记忆：授权文档检索、引用来源、过期记忆、删除和敏感凭证拦截
- 敏感数据治理：Run、Step、审计、模型、工具和上下文边界统一凭证脱敏，长期记忆拒绝写入疑似凭证
- 数据保留策略：终态 Run 与审计链原子清理，过期记忆/文档/评测和已完成 Outbox 定时删除，待投递消息不自动删除
- 离线评测：固定用例回放并保存模型/Prompt/策略版本报告
- 本地基础设施 Profile：PostgreSQL + Flyway、Redis 共享治理、RabbitMQ Outbox Worker
- 健康检查与运行指标：公开存活探针、受 `ops.read` 保护的 `/api/health` 和 Actuator 指标
- 请求关联追踪：自动生成并回传 `X-Request-Id`、`X-Trace-Id`，错误响应包含 `traceId`
- Vue 3 聊天工作台：持久化会话、消息气泡、逐轮输入、异步状态轮询和本轮 Run 执行链；原 Run 运维控制台仍可切换进入

## 启动方式

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
| `MAX_ACTIVE_RUNS_PER_TENANT` | `20` | 平台单租户活动 Run 硬上限；可通过租户策略进一步收紧 |
| `MAX_CREATES_PER_MINUTE` | `60` | 平台单租户每分钟创建 Run 硬上限；可通过租户策略进一步收紧 |
| `MAX_INPUT_LENGTH` | `10000` | 平台单次输入字符硬上限；可通过租户策略进一步收紧 |
| `MAX_RUN_BUDGET` | `1000` | 平台单次 Run 预算硬上限；可通过租户策略进一步收紧 |
| `MODEL_TIMEOUT_MS` | `30000` | 模型调用超时 |
| `MAX_CONTEXT_CHARS` | `4000` | 注入模型的上下文最大字符数 |
| `RECOVERY_TIMEOUT_MS` | `120000` | Worker 中断后将 RUNNING 任务转为超时的阈值 |
| `MAX_TOOL_ATTEMPTS` | `3` | 单个只读工具的自动重试次数上限，副作用工具固定为 1 |
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
| `DATA_RETENTION_ENABLED` | `false`（`local-infra` 为 `true`） | 是否启用定时数据保留清理 |
| `RUN_RETENTION_DAYS` | `90` | 终态 Run 最短保留天数；实际会与审计保留期取较大值 |
| `AUDIT_RETENTION_DAYS` | `365` | 审计链保留天数，避免清理部分事件破坏完整性 |
| `MEMORY_RETENTION_DAYS` | `30` | 已删除长期记忆的保留天数；已到期记忆会立即清理 |
| `DOCUMENT_RETENTION_DAYS` | `30` | 已删除知识文档的保留天数 |
| `EVALUATION_RETENTION_DAYS` | `90` | 评测报告保留天数 |
| `OUTBOX_RETENTION_DAYS` | `14` | 已发布/最终失败 Outbox 保留天数，`PENDING` 永不自动清理 |
| `TENANT_POLICY_AUDIT_RETENTION_DAYS` | `365` | 租户资源策略变更审计保留天数 |
| `API_KEY_AUDIT_RETENTION_DAYS` | `365` | 数据库 API Key 生命周期审计保留天数 |
| `RETENTION_BATCH_SIZE` | `100` | 每轮最多清理的终态 Run 数量 |
| `SPRING_PROFILES_ACTIVE` | `local` | `local`、`local-infra`，可组合 `oidc` |
| `HARNESS_EXECUTION_MODE` | `sync` | `sync` 或 `rabbit` |
| `EVALUATION_WAIT_TIMEOUT_MS` | `120000` | Rabbit 异步评测等待单个 Run 到终态的最长时间；超时记录当前状态并继续后续用例 |
| `EVALUATION_POLL_INTERVAL_MS` | `250` | Rabbit 异步评测查询 Run 状态的间隔，不能小于 1 毫秒 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis 连接参数 |
| `REDIS_LOCK_TTL_MS` | `30000` | Redis 执行锁和租户配额锁基础租约；Worker 执行锁会自动取不小于 `RECOVERY_TIMEOUT_MS` 的时长，不能低于 1000 毫秒 |
| `REDIS_QUOTA_LOCK_WAIT_MS` | `1000` | 活动 Run 配额锁等待时长；Redis 不可用时快速失败 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ 连接参数 |
| `OUTBOX_CLAIM_LEASE_MS` | `30000` | Outbox Relay 发布租约时长；实例中断后过期租约可被其他实例接管 |
| `RABBITMQ_CONSUMER_CONCURRENCY` | `1` | 每个应用实例初始 Worker 消费者数量 |
| `RABBITMQ_MAX_CONSUMER_CONCURRENCY` | `4` | 每个应用实例 Worker 消费者数量上限 |
| `RABBITMQ_PREFETCH` | `1` | 每个消费者预取消息数，避免未执行消息脱离队列监控 |
| `RABBITMQ_MAX_QUEUE_DEPTH` | `1000` | 执行队列允许的最大待消费消息数，达到上限时 Outbox Relay 暂停抢占 |
| `RABBITMQ_QUEUE_METRICS_POLL_MS` | `5000` | 队列深度指标刷新间隔 |

默认演示网关不会访问外部模型服务，适合本地开发和联调。

前端共享环境可通过 `VITE_HARNESS_API_KEY` 使用 API Key；同时设置 `VITE_HARNESS_TENANT_ID` 和 `VITE_HARNESS_USER_ID`，让创建 Run 表单与 API Key 绑定的身份保持一致。

### API Key / OIDC 认证

生产或共享环境建议设置 `HARNESS_AUTH_MODE=api-key`。调用方使用 `Authorization: Bearer <key>` 或 `X-Api-Key`，服务端根据配置或数据库凭证将请求绑定到固定租户和用户，并按接口校验权限，例如 `run.read`、`run.create`、`run.execute`、`run.approve`、`context.read`、`context.write`、`audit.read`、`evaluation.run`、`tool.read` 和 `ops.read`。工作区读取工具还需要 `workspace.read`，写入工具需要 `workspace.write` 并进入人工审批；`ops.read` 用于读取 `/api/health`、Actuator 指标、Prometheus 和应用信息。

通过具有 `auth.key.manage` 权限的引导 Key 或 OIDC 服务账号，可调用 `POST /api/admin/api-keys` 创建数据库 API Key；明文 `secret` 仅在创建响应中出现一次，数据库只保存 SHA-256 摘要。`GET /api/admin/api-keys` 只返回前缀和元数据，`POST /api/admin/api-keys/{keyId}/rotate` 会在同一事务中创建同权限新 Key 并立即撤销旧 Key，`DELETE /api/admin/api-keys/{keyId}` 可即时撤销，`GET /api/admin/api-keys/audits` 可查看生命周期审计。读取接口需要 `auth.key.read`，跨租户管理还需 `auth.key.cross-tenant`。环境变量 `HARNESS_API_KEYS` 保留为紧急引导兼容方案，变更或撤销需要重启；正式环境应逐步迁移至数据库生命周期 Key。

企业环境接入 OIDC/JWT 时使用 `SPRING_PROFILES_ACTIVE=local-infra,oidc`，并设置 `OIDC_ISSUER_URI` 与 `OIDC_AUDIENCE`。Spring Security Resource Server 负责验签和明确校验 issuer/audience，Harness 从 JWT 的 `sub`、`tenant_id`（兼容 `tenant`）以及 `permissions`/`scope`/`scp` 声明映射用户、租户和 RBAC 权限。

### 审计完整性

每条新审计事件都会使用 `AUDIT_INTEGRITY_KEY` 计算 HMAC，并与同一 Run 的前序哈希、序号组成链；Run 同时保存链头签名。运维可以调用 `GET /api/runs/{runId}/audit-events/verify` 主动校验，检测事件内容修改、删除、乱序或数据库中的链头篡改。迁移前的旧事件会标记为未签名历史记录，不能被校验结果当作完整可信链。

### 工具重试与 Run 预算

工具只有抛出 `RetryableToolException` 才会进入自动重试；Runtime 仅对 `readOnly=true` 的工具使用 `maxAttempts`，并受 `MAX_TOOL_ATTEMPTS` 全局上限约束。副作用工具即使声明更高次数也只执行一次，失败后通过 Run 重试接口重新经过策略和审批。模型步骤完成后会校验实际成本，超过 Run 预算的任务会以 `RUN_BUDGET_EXCEEDED` 失败并写入审计事件。

工具的 `inputSchema` 和 `outputSchema` 现在按结构化 JSON Schema 校验，不再通过字符串包含字段名来判断数据是否合格。当前支持对象、数组、字符串、数字、整数、布尔值和 null 类型，以及 `required`、`properties`、`additionalProperties`、`items`、`enum`、`const`、`allOf`、`anyOf`、`oneOf`、`not`、长度/数量/数值边界、`pattern`、`uniqueItems` 和常用 `format`（`email`、`uuid`、`date-time`、`uri`）。校验器会拒绝重复 JSON 字段和尾随的第二个 JSON 文档，避免解析差异造成输入绕过。校验失败会以 `TOOL_INPUT_INVALID` 或 `TOOL_OUTPUT_INVALID` 终止当前 Run，并保留脱敏后的错误原因。

### Agent 工作区工具

工作区工具是代码 Agent 的受控文件边界。启动前将 `HARNESS_WORKSPACE_ROOT` 指向一个专用项目目录；工具不会跟随符号链接访问根目录之外的文件，默认拒绝隐藏路径和非 UTF-8 文件。

- `workspace.list`：浏览目录结构，需要 `workspace.read`
- `workspace.read`：读取文件，可按 `startLine`/`endLine` 截取，并返回当前文件 SHA-256
- `workspace.search`：在工作区文本文件中搜索路径、行号和脱敏后的内容，需要 `workspace.read`
- `workspace.write`：原子写入 UTF-8 文件，需要 `workspace.write` 和人工审批；覆盖已有文件必须携带上一次读取返回的 `sha256`，文件被其他人修改时会返回 `WORKSPACE_FILE_CHANGED`
- `workspace.edit`：按多个精确 `oldText`/`newText` 片段增量编辑文件，需要 `workspace.write` 和人工审批；默认要求每个片段只匹配一处，并且必须携带读取时的 `expectedSha256`
- `workspace.git.status`：查看当前工作区范围内的分支和文件变更，需要 `workspace.read`，只执行固定的只读 Git 命令
- `workspace.git.diff`：查看当前工作区范围内的未暂存或已暂存差异，需要 `workspace.read`，支持按文件和上下文行数限制输出

本地配置示例：

```bash
export WORKSPACE_ENABLED=true
export HARNESS_WORKSPACE_ROOT=/Users/ming/Projects/example
./mvnw spring-boot:run
```

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

### 代码 Agent 多轮模式

### 持久化聊天会话

聊天工作台使用会话接口将每轮用户消息和助手结果持久化到数据库。每条用户消息都会创建一个关联 Run，下一轮会把同一会话中已完成的消息拼入模型输入；Rabbit 异步模式下助手气泡先显示“Agent 执行中”，Worker 完成后自动回写最终内容。

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
```

相关接口：`GET /api/conversations`、`GET /api/conversations/{id}`、`POST /api/conversations/{id}/messages`。会话按租户和用户隔离，消息中的 `runId` 可以继续调用原有 Run 详情、审批、取消和重试接口。

创建 Run 时将 `agentMode` 设置为 `true`，Harness 会把模型返回的 Tool Call 持久化为新的工具步骤；每个工具完成后自动追加下一轮模型步骤。模型结果、工具参数、审计事件和当前轮次都保存在数据库中，Rabbit Worker 重启后可以从最后一个已提交步骤恢复，而不会依赖进程内上下文。

```bash
curl -X POST http://localhost:8080/api/runs \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-demo' \
  -d '{"tenantId":"tenant-demo","userId":"operator","title":"分析项目结构","input":"请读取项目并总结入口模块","agentMode":true,"maxTurns":8,"budget":10,"permissions":"workspace.read"}'
```

Agent 模式下 `toolName` 不参与选择，模型只会收到当前租户工具白名单内的工具契约；工具注册表、JSON Schema、租户策略、权限和审批仍是最终授权边界。`maxTurns` 范围为 1 到 20，超过后 Run 以 `FAILED` 结束并记录 `AGENT_MAX_TURNS_EXCEEDED`。控制台创建表单可以直接开启 Agent 模式，详情页会展示模型轮次、Tool Call、工具输出和审批状态。

当前工作区工具支持浏览、读取、搜索、精确增量编辑、原子写入、Git 状态/差异查看和受控命令执行。写入和编辑工具需要 `workspace.write` 权限、人工审批以及读取时返回的 `sha256` 并发校验；编辑工具只接受精确文本替换，匹配不唯一时会拒绝执行，避免误改代码。Git 工具只查看工作区范围内的变更，不执行 Hook、外部 Diff 或 TextConv，不需要人工审批；为防止 Git 配置越界，工作区根目录必须是包含普通 `.git` 目录的仓库根目录。命令工具需要 `workspace.exec` 权限、白名单和人工审批。所有工作目录仍受工作区根目录、隐藏路径和符号链接边界保护，Agent 不会获得任意 Shell 拼接能力。

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

在 Rabbit/Redis 模式下，取消接口会先写入租户绑定、自动过期的 Redis 取消信号，再等待数据库行锁释放并将 Run 状态写为 `CANCELLED`。Worker 在每个步骤和最终成功落库前读取该信号及数据库状态；发现取消时不会执行后续步骤，也不会用长事务中的旧状态覆盖取消结果。已经开始的外部工具调用不能被安全地强制中断，因此工具本身仍应实现超时、幂等和可取消协议。Redis 取消协调不可用时，`local-infra` 会返回基础设施不可用，而不会静默继续执行。

Rabbit Worker 仅在抛出临时基础设施异常时由队列重试；业务、策略和工具错误会持久化为 Run 的 `FAILED` 状态并确认消息。Worker 的模型/工具网络调用在数据库事务之外执行，领取、步骤状态、心跳、结果和审计分别使用短事务，避免长调用占用连接和行锁；每个步骤前后都会续租并再次校验 Worker 所有权。每个实例的消费者并发和预取量都有上限；Outbox Relay 发布前读取队列深度，达到 `RABBITMQ_MAX_QUEUE_DEPTH` 或无法读取队列状态时会暂停抢占，等待下一轮重试。Outbox 耗尽发布重试次数后会立即将尚未执行的 Run 标记为 `FAILED` 并记录 `RUN_DISPATCH_FAILED` 审计事件，避免任务无 Worker 执行却长时间显示为 `RUNNING`。可通过 `harness.rabbit.queue.depth`、`harness.rabbit.queue.capacity`、`harness.worker.active`、`harness.worker.concurrency`、`harness.rabbit.backpressure`、`harness.rabbit.queue.poll_failures` 以及原有的 `harness.rabbit.retries`、`harness.rabbit.dead_letters` 指标观察背压和 Worker 状态。

### 敏感数据与保留策略

Harness 会在写入 Run/Step、审计、上下文、评测和 Outbox 错误前，统一替换常见的 API Key、Bearer Token、JWT、连接串密码、PEM 私钥和厂商 Token 为 `[REDACTED]`。模型调用前也会再次执行脱敏；长期记忆发现疑似凭证时直接拒绝写入。该规则是安全基线，不替代生产环境的密钥托管、DLP 和权限控制。

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

上下文与评测接口：

- `GET /api/runs/page?page=0&size=20&status=RUNNING`：按租户分页查询 Run，`status` 可选，单页最多 100 条；原 `GET /api/runs` 继续返回最近 50 条数组
- `POST/GET/DELETE /api/context/documents`：管理租户隔离的知识文档
- `POST/GET/DELETE /api/context/memories`：管理用户范围的长期记忆
- `GET /api/context/preview?query=...`：预览授权来源和引用
- `POST/GET /api/evaluations`：运行固定回归用例并查询评测报告；`rabbit` 模式下接口会等待每个 Run 到终态，等待审批的用例不会自动审批，单个用例超时会记录当前状态并继续后续用例

## 设计约束

模型只能提出行动，工具注册表和策略代码才可以授权执行。所有执行结果、错误、审批和版本信息都会持久化，便于恢复、重放和审计。`local-infra` 已提供 PostgreSQL、Redis 共享限流/执行锁、带发布租约的 RabbitMQ 异步 Worker、带数据库行锁的多实例超时恢复、API Key 和 OIDC/JWT RBAC 基线；正式环境仍需接入密钥托管、告警和密钥轮换。
