const { contextBridge, ipcRenderer, webUtils } = require('electron')

// 后端地址不是敏感信息；它以启动参数传入，避免桌面版 file:// 页面把请求误发到文件协议。
const apiBaseUrl = (process.argv.find((item) => item.startsWith('--harness-api-base-url='))
  || '--harness-api-base-url=http://127.0.0.1:8080/api')
  .slice('--harness-api-base-url='.length)

let workspaceDropIdentity = {}
let workspaceDropListener = null

/**
 * 只暴露最小化的桌面能力：渲染层不能读取路径，不能获得 Node.js 或任意 IPC 通道。
 * 目录选择、原生拖拽路径和桥接令牌始终仅保留在 Electron 主进程或 preload 隔离世界。
 */
contextBridge.exposeInMainWorld('harnessDesktop', Object.freeze({
  isDesktop: true,
  apiBaseUrl,
  pickWorkspace: (identity) => ipcRenderer.invoke('harness:pick-workspace', identity || {}),
  configureWorkspaceDrop: (identity) => {
    workspaceDropIdentity = identity || {}
  },
  onWorkspaceDropped: (listener) => {
    workspaceDropListener = typeof listener === 'function' ? listener : null
  },
  clearWorkspaceDropListener: () => {
    workspaceDropListener = null
  },
}))

/**
 * 只在隔离世界读取 Electron File 的路径；webUtils 对 JS 伪造的 File 返回空路径。
 * 普通文件会被主进程忽略，仍由 Vue 原有的附件导入流程处理。
 */
window.addEventListener('drop', async (event) => {
  if (!workspaceDropListener) return
  // 仅把聊天输入框内的拖入解释为项目授权，避免用户向窗口其他区域拖文件时意外扩大 Agent 范围。
  if (!event.target?.closest?.('.chat-composer')) return
  const droppedPaths = Array.from(event.dataTransfer?.files || [])
    .map((file) => {
      try {
        return webUtils.getPathForFile(file)
      } catch {
        return ''
      }
    })
    .filter(Boolean)
  if (!droppedPaths.length) return
  try {
    const result = await ipcRenderer.invoke('harness:register-dropped-workspace', {
      droppedPaths,
      identity: workspaceDropIdentity,
    })
    if (!result?.ignored) workspaceDropListener(result)
  } catch (error) {
    workspaceDropListener({ error: error instanceof Error ? error.message : '登记拖入项目失败' })
  }
}, true)
