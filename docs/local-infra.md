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

`local-infra` 启动时会执行 Flyway 迁移，创建 Run、Step、审计、上下文、评测和 Outbox 表，并声明 RabbitMQ 主队列和死信队列。

共享环境可以启用 API Key 认证和接口权限校验：

```bash
export HARNESS_AUTH_MODE=api-key
export HARNESS_API_KEYS='demo-key|tenant-demo|operator|run.read,run.create,run.execute,run.approve,run.cancel,audit.read,context.read,context.write,evaluation.read,evaluation.run,tool.read'
```

调用时使用 `Authorization: Bearer demo-key`。API Key 绑定的租户和用户会覆盖请求头，Run 创建请求中的 `tenantId/userId` 必须与认证身份一致。默认 `local` 模式仍兼容 `X-Tenant-Id`、`X-User-Id` 和 `X-Permissions`，仅适合本地演示。

健康检查：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics/harness.worker.duration
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
