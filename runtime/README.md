# Windows local-infra runtime

Windows 绿色版不会依赖系统安装的 Java、PostgreSQL、Garnet、RabbitMQ、Erlang 或 .NET。
构建前请准备一个未提交到 Git 的目录：

```text
runtime/vendor/win-x64/
├── jre/bin/java.exe
├── postgres/bin/initdb.exe
├── postgres/bin/postgres.exe
├── postgres/bin/pg_ctl.exe
├── postgres/bin/createdb.exe
├── postgres/include/server/postgres.h   # 仅用于构建 pgvector
├── postgres/lib/vector.dll
├── postgres/share/extension/vector.control
├── postgres/share/extension/vector--*.sql
├── postgres/share/extension/pgvector-build.json
├── garnet/GarnetServer.exe
├── garnet/dotnet/dotnet.exe
├── rabbitmq/sbin/rabbitmq-server.bat
├── rabbitmq/sbin/rabbitmqctl.bat
└── erlang/bin/erl.exe
```

`postgres` 必须是 PostgreSQL 17 Windows ZIP 中完整的 `pgsql` 目录，除了运行时文件外还要
包含 `include/server` 和 `lib/*.lib`。如果当前目录没有头文件，可以重新解压 PostgreSQL ZIP，
或只补充 `pgsql/include`：

```powershell
$tmp = Join-Path $env:TEMP 'ming-harness-pg17'
Expand-Archive -Force runtime/vendor/win-x64/_downloads/postgresql-17.6-1-windows-x64-binaries.zip $tmp
Copy-Item -Recurse -Force "$tmp/pgsql/include" runtime/vendor/win-x64/postgres/include
```

这些头文件只用于编译 pgvector；`prepare-win-runtime.mjs` 会在打包前从最终运行时中移除它们。

也可以通过 `MING_HARNESS_WIN_RUNTIME_SOURCE` 指定上述目录的绝对路径。

本次已验证过的下载版本、来源和 SHA-256 记录在
[win-runtime.manifest.json](win-runtime.manifest.json)。Garnet 以 MIT License 发布，
并随包保留 Garnet 的 LICENSE/NOTICE 以及 Microsoft .NET Runtime 的许可证文件。

可复制 [win-runtime.manifest.example.json](win-runtime.manifest.example.json) 为运行时清单，
并把各组件的许可证文件放入 `runtime/licenses`（构建时会复制到发布包）。Windows Redis 兼容实现固定为
Microsoft Garnet。Garnet 必须以 `--lua --lua-transaction-mode` 启动，因为 Harness 的分布式锁和限流
使用需要原子执行的 Lua 脚本。

运行时来源需要由发布者自行确认版本、校验和和再分发许可。尤其是 pgvector：仓库不接受
来历不明的 Windows DLL。请在 Windows x64 的 Visual Studio Developer PowerShell (x64) 中，
从 pgvector 官方仓库检出固定 tag 后执行：

```powershell
git clone --branch v0.8.6 --depth 1 https://github.com/pgvector/pgvector.git runtime/vendor/pgvector-src
./runtime/scripts/build-pgvector.ps1 `
  -SourceDir runtime/vendor/pgvector-src `
  -PostgresRoot runtime/vendor/win-x64/postgres
```

脚本使用随包 PostgreSQL 17 的头文件和库进行 `nmake` 编译，安装 `vector.dll`、control/SQL
文件，并生成 `postgres/share/extension/pgvector-build.json`。`verify:win-runtime` 和公开构建
默认要求这个来源标记；只有本地技术验证可以临时设置
`MING_HARNESS_ALLOW_UNVERIFIED_PGVECTOR=true`，该模式禁止用于发布。

其余运行时来源：

- PostgreSQL 17 Windows binaries，必须包含从 pgvector 源码自行构建的扩展文件；
- Microsoft Garnet Windows x64 运行时和随附的 .NET Runtime 8；
- RabbitMQ Windows distribution；
- 与 RabbitMQ 兼容的 Erlang/OTP Runtime；
- Java 17 Runtime（当前清单使用 Eclipse Temurin Windows x64 JRE）。

Garnet 官方 Windows x64 运行时来源为
[`win-x64-based-readytorun.zip`](https://github.com/microsoft/garnet/releases/tag/v2.1.1)，
当前清单同时固定了 .NET Runtime 8.0.18 的 SHA-256。发布构建会通过
`DOTNET_ROOT` 使用包内 .NET Runtime，并将 `DOTNET_MULTILEVEL_LOOKUP` 设为 `0`，
因此不会偷偷依赖用户机器上的 .NET 安装。

准备好后，从 `frontend` 目录执行：

```bash
npm run verify:win-runtime
npm run dist:win:green
# dist:win:green 会在生成后检查最终目录不含 Memurai 且包含 Garnet/pgvector 来源材料
```

在受限网络的构建机上，可临时设置 `ELECTRON_MIRROR` 和
`ELECTRON_BUILDER_BINARIES_MIRROR`，让 electron-builder 从企业内部镜像取得 Electron/7zip；
这两个变量不会写入最终软件。

构建机需要安装 JDK 17、npm；如果重新构建 Garnet，还需要 .NET 8 SDK。这些只是构建依赖，
不会要求最终用户额外安装 Java、Node 或 .NET。

脚本会先构建 Spring Boot JAR，再把运行时复制到 `runtime/`，最后由 electron-builder 生成
`release/win-green/win-unpacked`。把该目录压缩成 ZIP，用户解压后运行 `Ming Harness.exe` 即可。

绿色版数据始终写在软件目录下：

```text
<Ming Harness>/data/infra/
<Ming Harness>/data/workspace/
<Ming Harness>/data/logs/
```

数据目录不可写时，程序会直接报错退出，不会回退到 `%LOCALAPPDATA%`。
