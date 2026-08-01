const { contextBridge, ipcRenderer } = require('electron')

// 后端地址不是敏感信息；它以启动参数传入，避免桌面版 file:// 页面把请求误发到文件协议。
const apiBaseUrl = (process.argv.find((item) => item.startsWith('--harness-api-base-url='))
  || '--harness-api-base-url=http://127.0.0.1:8080/api')
  .slice('--harness-api-base-url='.length)

/**
 * 只暴露最小化的桌面能力：渲染层不能读取路径，不能获得 Node.js 或任意 IPC 通道。
 * 目录选择、绝对路径和桥接令牌始终仅保留在 Electron 主进程。
 */
contextBridge.exposeInMainWorld('harnessDesktop', Object.freeze({
  isDesktop: true,
  apiBaseUrl,
  pickWorkspace: (identity) => ipcRenderer.invoke('harness:pick-workspace', identity || {}),
}))
