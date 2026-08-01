# Ming Harness 控制台

这是基于 Vue 3 和 Vite 的 Harness 聊天工作台，默认提供持久化会话、消息气泡、逐轮输入和 Agent 执行状态轮询；运行控制台仍覆盖 Run 操作、审批审计、上下文文档和快速回归评测。

创建 Run 时可以在“运行模式”中开启代码 Agent 多轮执行并设置最大轮数。Agent 运行会动态产生模型和工具步骤，详情页会展示 Tool Call、工具输出、审批和最终状态；非终态 Run 会自动轮询。工具白名单、权限和写入审批由后端统一控制，前端仅提交模式和轮数配置。

聊天工作台中，每次发送都会形成一条 USER 消息和一条关联 Run 的 ASSISTANT 消息。异步 Worker 执行期间助手气泡显示排队/执行状态，完成后自动更新；“查看运行”可以打开当前消息的步骤、审批、取消和重试操作。对于 `workspace.edit`、`workspace.write`，在人工审批前会展示目标文件和拟修改内容，避免盲目批准代码变更。

## 本地运行

先启动后端。零依赖演示模式：

```bash
../mvnw spring-boot:run
```

如果要使用 PostgreSQL、Redis、RabbitMQ 异步 Worker，请先按仓库根目录的
[`docs/local-infra.md`](../docs/local-infra.md) 启动本地服务，再使用 `SPRING_PROFILES_ACTIVE=local-infra` 启动后端。

再启动前端：

```bash
npm install
npm run dev
```

开发服务器默认运行在 `http://localhost:5173`，`/api` 请求会代理到 Spring Boot 的 `8080` 端口。
