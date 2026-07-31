# 本地基础设施运行说明

本项目提供两种运行方式：

- `local`：H2 + 进程内同步执行，适合快速演示和单元测试。
- `local-infra`：PostgreSQL 17 + Redis + RabbitMQ，使用 Flyway、Outbox 和异步 Worker。

应用不会自动启动 Homebrew 服务，也不会自动迁移现有 H2 演示数据。

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

`local-infra` 启动时会执行 Flyway 迁移，创建 Run、Step、审计、上下文、评测、Outbox 和租户资源策略表，并声明 RabbitMQ 主队列和死信队列。
Outbox Relay 会先在 PostgreSQL 中抢占短期发布租约，再在租约外等待 RabbitMQ 发布确认；多实例不会同时发送同一条待处理事件。进程在确认前中断时，租约到期后允许重新投递，Run 执行锁负责去重。

Rabbit Worker 的模型和工具调用在数据库事务之外执行；领取租约、步骤开始/完成、心跳、审计和终态写回分别是短事务。每个步骤前后都会续租 Redis 锁并刷新 PostgreSQL Worker 租约，旧 Worker 丢失所有权后不能覆盖新 Worker 或取消操作的结果。Worker 执行锁会自动使用不小于 `RECOVERY_TIMEOUT_MS` 的租期，避免数据库恢复器在一个受控长步骤期间过早回收 Run。

评测接口在 Rabbit 模式下会轮询每个 Run 的详情，直到成功、失败、取消、超时或等待审批；等待审批不会被评测逻辑自动批准。单个 Run 超过等待边界后，报告会记录 `TIMEOUT` 和当时的 `QUEUED/RUNNING` 状态，然后继续下一个用例，不会让整批评测持有长数据库事务。默认等待 120 秒、每 250 毫秒轮询一次，可按模型响应时间和 API 请求超时覆盖：

```bash
export EVALUATION_WAIT_TIMEOUT_MS=120000
export EVALUATION_POLL_INTERVAL_MS=250
```

Worker 默认每个实例启动 1 个消费者，最多扩展到 4 个消费者，每个消费者预取 1 条消息。Outbox Relay 发布前会读取执行队列深度：达到 `RABBITMQ_MAX_QUEUE_DEPTH`（默认 1000）时暂停抢占，RabbitMQ 队列状态读取失败时也会安全暂停，待下一轮恢复后继续。可以根据模型供应商并发额度和数据库容量覆盖：

```bash
export RABBITMQ_CONSUMER_CONCURRENCY=1
export RABBITMQ_MAX_CONSUMER_CONCURRENCY=4
export RABBITMQ_PREFETCH=1
export RABBITMQ_MAX_QUEUE_DEPTH=1000
export RABBITMQ_QUEUE_METRICS_POLL_MS=5000
```

恢复器每 30 秒扫描一次过期 Worker 租约；候选 Run 使用 PostgreSQL/H2 悲观行锁读取，等待正在提交的 Worker 后重新判断状态，避免多实例恢复器把已经成功的结果覆盖为 `TIMED_OUT`。恢复操作会追加带 HMAC 的 `RUN_RECOVERED_AS_TIMED_OUT` 审计事件；`RECOVERY_TIMEOUT_MS` 默认 120 秒，生产环境应结合最长模型/工具调用和告警延迟设置。

取消 RUNNING 任务时，应用会先写入带租户范围、自动过期的 Redis 协作信号；Worker 会在每个步骤边界检查该信号，再检查 PostgreSQL 中的最终 Run 状态。这样即使 Worker 长事务暂时占用 Run 行，取消请求也能先让 Worker 停止后续步骤并释放锁。已开始的外部工具调用不能被安全地强制中断，因此仍应为工具配置超时、幂等键和可取消协议；Redis 不可用时取消接口会明确返回基础设施错误，不会静默降级。

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
export HARNESS_API_KEYS='demo-key|tenant-demo|operator|run.read,run.create,run.execute,run.approve,run.cancel,audit.read,context.read,context.write,evaluation.read,evaluation.run,tool.read,ops.read,tenant.policy.read,tenant.policy.write'
```

调用时使用 `Authorization: Bearer demo-key`。API Key 绑定的租户和用户会覆盖请求头，Run 创建请求中的 `tenantId/userId` 必须与认证身份一致。默认 `local` 模式仍兼容 `X-Tenant-Id`、`X-User-Id` 和 `X-Permissions`，仅适合本地演示。

### 租户级资源治理

平台环境变量定义所有租户都不能突破的硬上限。拥有 `tenant.policy.read`/`tenant.policy.write` 权限的身份可以通过管理接口为自己的租户设置更严格的活动 Run 数、步骤数、输入长度、单次预算和创建速率；跨租户运维还需要额外的 `tenant.policy.cross-tenant` 权限。未配置覆盖策略的租户自动使用平台默认值。

```bash
curl -X PUT http://localhost:8080/api/admin/tenants/tenant-demo/policy \
  -H 'Authorization: Bearer demo-key' \
  -H 'Content-Type: application/json' \
  -d '{"maxActiveRuns":5,"maxStepsPerRun":10,"maxInputLength":5000,"maxBudget":50,"maxCreatesPerMinute":20}'
```

`GET /api/admin/tenants/{tenantId}/policy` 查看当前生效策略，`DELETE` 恢复平台默认值，`GET .../policy/audits` 查看最近策略变更。策略变更与前后数值会单独留痕；策略只能收紧平台硬上限，不会因为租户配置错误而突破系统容量边界。

企业 OIDC/JWT：

```bash
export SPRING_PROFILES_ACTIVE=local-infra,oidc
export OIDC_ISSUER_URI=https://login.example.com/realms/harness
export OIDC_AUDIENCE=ming-harness-api
export AUDIT_INTEGRITY_KEY='由密钥系统注入的长随机字符串'
./mvnw spring-boot:run
```

JWT 必须包含 `sub`、`tenant_id`（或 `tenant`）以及配置的 `aud`（`OIDC_AUDIENCE`，多个值逗号分隔），权限可放在 `permissions`、`scope` 或 `scp` 声明中。Spring Security Resource Server 负责 JWT 验签及 issuer/audience 校验，Harness 负责租户绑定和接口 RBAC。未配置 audience 时 OIDC 模式会快速失败，不允许无受众保护地上线。

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
export EVALUATION_RETENTION_DAYS=90
export OUTBOX_RETENTION_DAYS=14
export RETENTION_BATCH_SIZE=100
```

清理任务只处理已结束 Run，并同时删除其 Step、审计事件和对应 Outbox；仍处于 `QUEUED`、`RUNNING` 或 `WAITING_APPROVAL` 的任务不会被删除。`PENDING` 或 `PUBLISHING` Outbox 代表尚未确认投递的执行命令，永远不会被保留任务直接删除。到期长期记忆会清理，用户主动删除的文档和记忆则在各自保留期结束后物理删除。

Run 和审计保留期取两者较大值，这是为了保持审计 HMAC 链完整。清理是不可逆操作，生产环境应先在备份和合规策略确认后启用；本地演示可以设置 `DATA_RETENTION_ENABLED=false`。Run、Step、审计、模型输入输出和工具错误中的常见凭证会在持久化或外发前替换为 `[REDACTED]`，长期记忆发现疑似凭证时会拒绝写入。
