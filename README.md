# Ming Harness

Ming Harness 是一个面向企业 Agent 的可运行 Harness：后端使用 Spring Boot 4，前端使用 Vue 3 + Vite。当前实现覆盖单 Agent Runtime、安全策略、上下文治理、离线评测和运行观测基线。

## 已实现模块

- Run / Step 持久化状态机：`QUEUED -> RUNNING -> WAITING_APPROVAL -> SUCCEEDED / FAILED / TIMED_OUT / CANCELLED`
- 幂等与资源边界：支持 `Idempotency-Key`、租户活动 Run 配额、创建速率、输入/预算/步骤数限制
- 可插拔模型网关：默认演示模型，也支持 OpenAI 兼容的 `/chat/completions` 接口
- 工具注册表与确定性策略：权限、风险、审批、网络策略、超时和输入校验
- 审计与观测：Run `traceId`、Step `spanId`、Token、耗时、成本和租户/操作者快照
- 租户隔离：读写 Run、Step、审计事件都需要 `X-Tenant-Id`
- 失败重试：失败步骤会保留 attempt 次数；有副作用的工具重试前会重新走审批
- 上下文与记忆：授权文档检索、引用来源、过期记忆、删除和敏感凭证拦截
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
| `MODEL_ENABLED` | `false` | 是否启用 OpenAI 兼容模型网关 |
| `MODEL_BASE_URL` | `https://api.openai.com/v1` | 模型服务地址 |
| `MODEL_API_KEY` | 空 | 模型服务密钥，仅通过环境变量注入 |
| `MODEL_NAME` | `gpt-4o-mini` | 模型名称 |
| `MAX_ACTIVE_RUNS_PER_TENANT` | `20` | 单租户活动 Run 上限 |
| `MAX_CREATES_PER_MINUTE` | `60` | 单租户每分钟创建 Run 上限 |
| `MAX_INPUT_LENGTH` | `10000` | 单次输入最大字符数 |
| `MAX_RUN_BUDGET` | `1000` | 单次 Run 预算上限 |
| `MODEL_TIMEOUT_MS` | `30000` | 模型调用超时 |
| `MAX_CONTEXT_CHARS` | `4000` | 注入模型的上下文最大字符数 |
| `RECOVERY_TIMEOUT_MS` | `120000` | Worker 中断后将 RUNNING 任务转为超时的阈值 |
| `SPRING_PROFILES_ACTIVE` | `local` | `local` 或 `local-infra` |
| `HARNESS_EXECUTION_MODE` | `sync` | `sync` 或 `rabbit` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis 连接参数 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ 连接参数 |

默认演示网关不会访问外部模型服务，适合本地开发和联调。

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

模型只能提出行动，工具注册表和策略代码才可以授权执行。所有执行结果、错误、审批和版本信息都会持久化，便于恢复、重放和审计。`local-infra` 已提供 PostgreSQL、Redis 共享限流/执行锁和 RabbitMQ 异步 Worker 基线；正式环境仍需接入认证授权、密钥托管、告警和密钥轮换。
