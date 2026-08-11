import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// desktop:dev 会在默认端口被占用时为当前源码 Runtime 分配新的本机端口。
// Vite 代理必须复用它，不能悄悄回落到可能仍在运行的旧 8080 Runtime。
const apiBaseUrl = process.env.HARNESS_API_BASE_URL || 'http://localhost:8080/api'
const apiTarget = new URL(apiBaseUrl).origin

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
      '/actuator': {
        target: apiTarget,
        changeOrigin: true,
      },
    },
  },
})
