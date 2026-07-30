# Ming Harness 控制台

这是基于 Vue 3 和 Vite 的 Harness 管理控制台。

## 本地运行

先启动后端：

```bash
../mvnw spring-boot:run
```

再启动前端：

```bash
npm install
npm run dev
```

开发服务器默认运行在 `http://localhost:5173`，`/api` 请求会代理到 Spring Boot 的 `8080` 端口。
