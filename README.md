# Ming Harness

Ming Harness 是一个面向企业 Agent 的可运行 Harness：后端使用 Spring Boot 4，前端使用 Vue 3 + Vite。当前实现覆盖单 Agent Runtime、安全策略、上下文治理、离线评测和运行观测基线。

## 已实现模块

- Run / Step 持久化状态机：`QUEUED -> RUNNING -> WAITING_APPROVAL -> SUCCEEDED / FAILED / TIMED_OUT / CANCELLED`
- 幂等与资源边界：支持 `Idempotency-Key`、租户活动 Run 配额、创建速率、输入/预算/步骤数限制
- 可插拔模型网关：默认演示模型，也支持 OpenAI 兼容的 `/chat/completions` 接口
- 工具注册表与确定性策略：权限、风险、审批、网络策略、超时和输入校验
- 审计与观测：Run `traceId`、Step `spanId`、Token、耗时、成本和租户/操作者快照，审计事件支持 HMAC 完整性校验
- 租户隔离：读写 Run、Step、审计事件都需要 `X-Tenant-Id`
- 可插拔认证与 RBAC：`local` 兼容演示请求头，`api-key` 和 `oidc` 支持租户、用户和接口权限快照
- 失败重试：只读工具可用 `RetryableToolException` 触发有限自动重试；有副作用的工具禁止自动重试，人工重试前会重新走审批
- 上下文与记忆：授权文档检索、引用来源、过期记忆、删除和敏感凭证拦截
- 敏感数据治理：Run、Step、审计、模型、工具和上下文边界统一凭证脱敏，长期记忆拒绝写入疑似凭证
- 数据保留策略：终态 Run 与审计链原子清理，过期记忆/文档/评测和已完成 Outbox 定时删除，待投递消息不自动删除
- 离线评测：固定用例回放并保存模型/Prompt/策略版本报告
- 本地基础设施 Profile：PostgreSQL + Flyway、Redis 共享治理、RabbitMQ Outbox Worker
- 健康检查与运行指标：`/actuator/health`、`/actuator/metrics`
- 请求关联追踪：自动生成并回传 `X-Request-Id`、`X-Trace-Id`，错误响应包含 `traceId`
- Vue 3 控制台：Run 创建、执行、取消、审批、重试、工具注册、上下文和快速评测

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
| `HARNESS_API_KEYS` | 空 | `key|tenant|user|permission1,permission2;...`，生产环境通过密钥系统注入 |
| `OIDC_ISSUER_URI` | 空 | `oidc` Profile 使用的 OIDC Issuer 地址 |
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
| `MAX_ACTIVE_RUNS_PER_TENANT` | `20` | 单租户活动 Run 上限 |
| `MAX_CREATES_PER_MINUTE` | `60` | 单租户每分钟创建 Run 上限 |
| `MAX_INPUT_LENGTH` | `10000` | 单次输入最大字符数 |
| `MAX_RUN_BUDGET` | `1000` | 单次 Run 预算上限 |
| `MODEL_TIMEOUT_MS` | `30000` | 模型调用超时 |
| `MAX_CONTEXT_CHARS` | `4000` | 注入模型的上下文最大字符数 |
| `RECOVERY_TIMEOUT_MS` | `120000` | Worker 中断后将 RUNNING 任务转为超时的阈值 |
| `MAX_TOOL_ATTEMPTS` | `3` | 单个只读工具的自动重试次数上限，副作用工具固定为 1 |
| `DATA_RETENTION_ENABLED` | `false`（`local-infra` 为 `true`） | 是否启用定时数据保留清理 |
| `RUN_RETENTION_DAYS` | `90` | 终态 Run 最短保留天数；实际会与审计保留期取较大值 |
| `AUDIT_RETENTION_DAYS` | `365` | 审计链保留天数，避免清理部分事件破坏完整性 |
| `MEMORY_RETENTION_DAYS` | `30` | 已删除长期记忆的保留天数；已到期记忆会立即清理 |
| `DOCUMENT_RETENTION_DAYS` | `30` | 已删除知识文档的保留天数 |
| `EVALUATION_RETENTION_DAYS` | `90` | 评测报告保留天数 |
| `OUTBOX_RETENTION_DAYS` | `14` | 已发布/最终失败 Outbox 保留天数，`PENDING` 永不自动清理 |
| `RETENTION_BATCH_SIZE` | `100` | 每轮最多清理的终态 Run 数量 |
| `SPRING_PROFILES_ACTIVE` | `local` | `local`、`local-infra`，可组合 `oidc` |
| `HARNESS_EXECUTION_MODE` | `sync` | `sync` 或 `rabbit` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis 连接参数 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ 连接参数 |

默认演示网关不会访问外部模型服务，适合本地开发和联调。

前端共享环境可通过 `VITE_HARNESS_API_KEY` 使用 API Key；同时设置 `VITE_HARNESS_TENANT_ID` 和 `VITE_HARNESS_USER_ID`，让创建 Run 表单与 API Key 绑定的身份保持一致。

### API Key / OIDC 认证

生产或共享环境建议设置 `HARNESS_AUTH_MODE=api-key`。调用方使用 `Authorization: Bearer <key>` 或 `X-Api-Key`，服务端根据配置将请求绑定到固定租户和用户，并按接口校验权限，例如 `run.read`、`run.create`、`run.execute`、`run.approve`、`context.read`、`context.write`、`audit.read`、`evaluation.run` 和 `tool.read`。API Key 只在启动配置中出现，应用内部仅保存 SHA-256 摘要。

企业环境接入 OIDC/JWT 时使用 `SPRING_PROFILES_ACTIVE=local-infra,oidc`，并设置 `OIDC_ISSUER_URI`。Spring Security Resource Server 负责验签和校验 issuer/audience 基础身份，Harness 从 JWT 的 `sub`、`tenant_id`（兼容 `tenant`）以及 `permissions`/`scope`/`scp` 声明映射用户、租户和 RBAC 权限。

### 审计完整性

每条新审计事件都会使用 `AUDIT_INTEGRITY_KEY` 计算 HMAC，并与同一 Run 的前序哈希、序号组成链；Run 同时保存链头签名。运维可以调用 `GET /api/runs/{runId}/audit-events/verify` 主动校验，检测事件内容修改、删除、乱序或数据库中的链头篡改。迁移前的旧事件会标记为未签名历史记录，不能被校验结果当作完整可信链。

### 工具重试与 Run 预算

工具只有抛出 `RetryableToolException` 才会进入自动重试；Runtime 仅对 `readOnly=true` 的工具使用 `maxAttempts`，并受 `MAX_TOOL_ATTEMPTS` 全局上限约束。副作用工具即使声明更高次数也只执行一次，失败后通过 Run 重试接口重新经过策略和审批。模型步骤完成后会校验实际成本，超过 Run 预算的任务会以 `RUN_BUDGET_EXCEEDED` 失败并写入审计事件。

模型网关只对网络错误、408、425、429 和 5xx 等临时故障重试；结构化响应错误和其他 4xx 不会盲目重试。主供应商达到熔断阈值后，配置了 `MODEL_FALLBACK_BASE_URL` 才会切换备用供应商。供应商返回的 `usage.prompt_tokens`/`completion_tokens`（也兼容 `input_tokens`/`output_tokens`）会写入 Step，并按每千 Token 单价计算实际成本；未返回 usage 时成本为 0，不会伪造计费数据。

### 敏感数据与保留策略

Harness 会在写入 Run/Step、审计、上下文、评测和 Outbox 错误前，统一替换常见的 API Key、Bearer Token、JWT、连接串密码、PEM 私钥和厂商 Token 为 `[REDACTED]`。模型调用前也会再次执行脱敏；长期记忆发现疑似凭证时直接拒绝写入。该规则是安全基线，不替代生产环境的密钥托管、DLP 和权限控制。

`local` 默认关闭自动清理，避免演示数据被删除；`local-infra` 默认开启。Run 与其 Step、审计链会作为一个完整单元清理，`RUN_RETENTION_DAYS` 会自动提升到不小于 `AUDIT_RETENTION_DAYS`，从而不会留下可查询但无法校验的半截审计链。正式环境应按合规要求设置保留天数，并在发布前评估删除不可逆性。

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

- `POST/GET/DELETE /api/context/documents`：管理租户隔离的知识文档
- `POST/GET/DELETE /api/context/memories`：管理用户范围的长期记忆
- `GET /api/context/preview?query=...`：预览授权来源和引用
- `POST/GET /api/evaluations`：运行固定回归用例并查询评测报告

## 设计约束

模型只能提出行动，工具注册表和策略代码才可以授权执行。所有执行结果、错误、审批和版本信息都会持久化，便于恢复、重放和审计。`local-infra` 已提供 PostgreSQL、Redis 共享限流/执行锁、RabbitMQ 异步 Worker、API Key 和 OIDC/JWT RBAC 基线；正式环境仍需接入密钥托管、告警和密钥轮换。
