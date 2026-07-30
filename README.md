# Ming Harness

Ming Harness 是一个面向企业 Agent 的最小可运行 Harness：后端使用 Spring Boot 4，前端使用 Vue 3 + Vite。当前实现覆盖单 Agent Runtime 的第一条闭环，并提供租户隔离、人工审批、审计追踪和失败重试基线。

## 已实现模块

- Run / Step 持久化状态机：`QUEUED -> RUNNING -> WAITING_APPROVAL -> SUCCEEDED / FAILED / CANCELLED`
- 可插拔模型网关：默认演示模型，也支持 OpenAI 兼容的 `/chat/completions` 接口
- 工具注册表：工具声明输入 Schema、风险等级、只读属性和是否需要人工审批
- 审计事件：记录 Run、模型、工具和审批的完整执行链
- 租户隔离：读写 Run、Step、审计事件都需要 `X-Tenant-Id`
- 失败重试：失败步骤会保留 attempt 次数；有副作用的工具重试前会重新走审批
- Vue 3 控制台：Run 创建、执行、取消、审批、重试、工具注册和审计时间线

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

## 常用配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 后端端口 |
| `DB_URL` | `jdbc:h2:file:./data/ming-harness` | 数据库连接，生产环境建议替换为 PostgreSQL/MySQL |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | 前端来源白名单 |
| `MODEL_ENABLED` | `false` | 是否启用 OpenAI 兼容模型网关 |
| `MODEL_BASE_URL` | `https://api.openai.com/v1` | 模型服务地址 |
| `MODEL_API_KEY` | 空 | 模型服务密钥，仅通过环境变量注入 |
| `MODEL_NAME` | `gpt-4o-mini` | 模型名称 |

默认演示网关不会访问外部模型服务，适合本地开发和联调。

## API 示例

创建 Run：

```bash
curl -X POST http://localhost:8080/api/runs \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-demo' \
  -d '{"tenantId":"tenant-demo","userId":"operator","title":"订单分析","input":"分析订单状态","toolName":"demo.echo","budget":1}'
```

创建后调用 `POST /api/runs/{runId}/start` 启动；高风险工具会进入等待审批状态，再调用 `POST /api/runs/{runId}/approve` 或 `POST /api/runs/{runId}/reject`。失败任务可调用 `POST /api/runs/{runId}/retry`。

## 设计约束

模型只能提出行动，工具注册表和策略代码才可以授权执行。所有执行结果、错误、审批和版本信息都会持久化，便于恢复、重放和审计。当前 H2 配置用于快速启动，生产部署时应切换到正式数据库、密钥托管、认证授权和消息队列。
