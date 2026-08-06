# Ming Harness 控制台

这是基于 Vue 3 和 Vite 的 Harness 聊天工作台，默认提供持久化会话、消息气泡、逐轮输入和 Agent 执行状态轮询；运行控制台仍覆盖 Run 操作、审批审计、上下文文档和快速回归评测。

## 前端技术栈

| 技术 | 版本/形态 | 用途 |
| --- | --- | --- |
| Vue | 3.5.x | 构建聊天工作台、Run 控制台和各类交互组件 |
| Vite | 8.1.x | 前端开发服务器、`/api` 代理和生产构建 |
| JavaScript | ES Modules | 前端业务代码；当前项目未引入 TypeScript |
| Monaco Editor | 0.52.x | 工作区文件、代码和 JSON 的编辑/预览 |
| Markdown-it + highlight.js | 14.3.x + 11.11.x | 渲染 Agent Markdown 回复并高亮代码块 |
| Lucide Vue | 1.28.x | 提供统一的界面图标 |
| Electron | 38.8.x | 桌面开发模式下的本地目录选择和安全桥接 |

后端接口仍由仓库根目录的 Spring Boot Runtime 提供；前端通过 `fetch` 调用 REST API，并使用 SSE 接收 Run 实时事件、以轮询作为断线兜底。依赖版本以本目录的 [`package.json`](package.json) 为准，具体后端与基础设施技术栈见根目录的 [`README.md`](../README.md#技术栈)。

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

## 本地代码 Agent 桌面模式

浏览器不能直接取得电脑上的任意绝对路径。桌面模式使用 Electron 主进程调用系统目录选择器，再把用户选择的目录安全登记给本机 Spring Boot Runtime：

```bash
npm install
npm run desktop:dev
```

启动脚本会一并启动 `local` Profile 的后端、Vite 和 Electron，并在当前进程内生成 `HARNESS_DESKTOP_BRIDGE_TOKEN`。默认端口被其他本地进程占用时，脚本会自动从下一个可用端口启动，并同步更新 Runtime、Vite、CORS 和 Electron 请求地址；如需严格禁止端口切换，可设置 `HARNESS_ALLOW_PORT_FALLBACK=false`。聊天页中的“选择本地项目”不会把绝对路径返回给 Vue；它会创建一条绑定该项目的新会话。退出桌面开发应用后，相应的本地开发进程会一起停止。

`electron/preload.cjs` 只暴露 `pickWorkspace()`，不启用 Node Integration；目录路径、桥接令牌及登记 HTTP 请求都保留在 `electron/main.cjs`。不要把 `HARNESS_DESKTOP_BRIDGE_TOKEN` 写入 `.env`、前端代码或日志。

## Windows 绿色版（Managed local-infra）

Windows 绿色版由 Electron 主进程管理随包的 PostgreSQL 17 + pgvector、Garnet、RabbitMQ、Erlang、
.NET Runtime 和 Java 17 Runtime。它启动 Spring Boot 时使用 `local-infra,desktop`，不会切换到 H2 或进程内执行。
基础设施数据直接写入绿色版目录下的 `data/infra`，目录不可写时直接启动失败，不回退到用户目录。

PostgreSQL 不允许服务器进程以 Windows 管理员令牌运行。启动 `Ming Harness.exe` 时不要选择“以管理员身份运行”，也不要从已提升的 PowerShell/CMD 启动；若快捷方式或 EXE 的“属性 > 兼容性”勾选了“以管理员身份运行此程序”，请取消该选项。绿色版应解压到当前用户可写的目录，例如 `%LOCALAPPDATA%\Ming-Harness`，而不是通过管理员权限写入 `Program Files`。

运行时文件不提交到 Git。请按根目录 [runtime/README.md](../runtime/README.md) 准备授权版本的
`runtime/vendor/win-x64`，然后在 Windows x64 环境执行：

```bash
npm ci
npm run verify:win-runtime
npm run dist:win:green
```

`dist:win:green` 会在生成后自动检查最终包内含 Garnet 和 pgvector 来源材料，且不含 Memurai。

构建机需要 JDK 17；JDK 只用于构建 Spring Boot JAR，最终用户不需要安装它。

生成的 `release/win-green/win-unpacked` 可直接压缩为 ZIP 发布。Garnet 是 Windows 绿色版的
Redis 兼容实现，启动时启用 Lua；发布前必须确认 Garnet/.NET、PostgreSQL/pgvector、RabbitMQ/Erlang
和 Java Runtime 的版本、校验和与再分发许可。公开构建还要求 pgvector DLL 由发布者从源码构建，
并随包保留 `postgres/share/extension/pgvector-build.json` 来源标记；不应发布来历不明的预编译 DLL。
