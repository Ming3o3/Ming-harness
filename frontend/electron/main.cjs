const { app, BrowserWindow, dialog, ipcMain, shell } = require('electron')
const http = require('node:http')
const fs = require('node:fs/promises')
const path = require('node:path')

const configuredFrontendUrl = process.env.HARNESS_FRONTEND_URL || ''
const apiBaseUrl = normalizeApiBaseUrl(process.env.HARNESS_API_BASE_URL || 'http://127.0.0.1:8080/api')
const bridgeToken = process.env.HARNESS_DESKTOP_BRIDGE_TOKEN || ''
let mainWindow
let frontendUrl = configuredFrontendUrl
let staticServer

function normalizeApiBaseUrl(value) {
  const parsed = new URL(value)
  // 本机目录只允许交给本机 Spring Boot Runtime，远端服务无法也不应读取用户磁盘。
  if (!['127.0.0.1', 'localhost', '::1'].includes(parsed.hostname)) {
    throw new Error('HARNESS_API_BASE_URL 必须指向本机 Runtime')
  }
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error('HARNESS_API_BASE_URL 必须使用 HTTP(S) 协议')
  }
  return value.replace(/\/+$/, '')
}

function isTrustedRenderer(contents) {
  const current = contents.getURL()
  if (current.startsWith('file://')) return true
  return Boolean(frontendUrl) && current.startsWith(frontendUrl)
}

/**
 * 发布构建通过本机 HTTP 服务提供静态资源，而不是 file://：
 * 这样 Spring Boot 可以继续用严格的 localhost CORS 白名单，且不会放宽网页文件协议权限。
 */
async function startStaticFrontendServer() {
  const distRoot = path.resolve(__dirname, '..', 'dist')
  await fs.access(path.join(distRoot, 'index.html'))
  const port = Number(process.env.HARNESS_DESKTOP_UI_PORT || 5173)
  const mimeTypes = {
    '.css': 'text/css; charset=utf-8',
    '.html': 'text/html; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.svg': 'image/svg+xml',
  }
  staticServer = http.createServer(async (request, response) => {
    try {
      const rawPath = decodeURIComponent(new URL(request.url, 'http://127.0.0.1').pathname)
      const relative = rawPath === '/' ? 'index.html' : rawPath.replace(/^\/+/, '')
      const candidate = path.resolve(distRoot, relative)
      if (candidate !== distRoot && !candidate.startsWith(`${distRoot}${path.sep}`)) {
        response.writeHead(403).end()
        return
      }
      let file = candidate
      try {
        const stat = await fs.stat(file)
        if (!stat.isFile()) throw new Error('not-file')
      } catch {
        // Vue 路由等无扩展名请求回退至入口；资源文件丢失则保留 404。
        if (path.extname(relative)) {
          response.writeHead(404).end()
          return
        }
        file = path.join(distRoot, 'index.html')
      }
      const content = await fs.readFile(file)
      response.writeHead(200, {
        'Content-Type': mimeTypes[path.extname(file)] || 'application/octet-stream',
        'Cache-Control': 'no-store',
      })
      response.end(content)
    } catch {
      response.writeHead(400).end()
    }
  })
  await new Promise((resolve, reject) => {
    staticServer.once('error', reject)
    staticServer.listen(port, '127.0.0.1', resolve)
  })
  return `http://127.0.0.1:${port}`
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 1024,
    minHeight: 700,
    title: 'Ming Harness',
    backgroundColor: '#080d1a',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      additionalArguments: [`--harness-api-base-url=${apiBaseUrl}`],
    },
  })

  // 桌面窗口只加载本地构建产物或本地 Vite 开发服务器，阻止导航到不受信任页面。
  mainWindow.webContents.on('will-navigate', (event, targetUrl) => {
    const trusted = targetUrl.startsWith('file://') || (frontendUrl && targetUrl.startsWith(frontendUrl))
    if (!trusted) event.preventDefault()
  })
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://')) shell.openExternal(url)
    return { action: 'deny' }
  })

  if (frontendUrl) {
    mainWindow.loadURL(frontendUrl)
  } else {
    mainWindow.loadFile(path.join(__dirname, '..', 'dist', 'index.html'))
  }
}

function text(value, maxLength = 255) {
  return typeof value === 'string' ? value.trim().slice(0, maxLength) : ''
}

function identityHeaders(payload) {
  const headers = {
    'Content-Type': 'application/json',
    'X-Harness-Desktop-Bridge': bridgeToken,
  }
  const apiKey = text(payload?.apiKey, 4096)
  if (apiKey) {
    headers.Authorization = `Bearer ${apiKey}`
    return headers
  }
  headers['X-Tenant-Id'] = text(payload?.tenantId, 128) || 'tenant-demo'
  headers['X-User-Id'] = text(payload?.userId, 128) || 'operator'
  headers['X-Permissions'] = text(payload?.permissions, 2048)
    || 'workspace.read,workspace.manage,workspace.write,workspace.exec'
  return headers
}

async function registerWorkspace(rootPath, payload) {
  if (!bridgeToken) {
    throw new Error('桌面桥接令牌未配置，请使用 npm run desktop:dev 启动')
  }
  const response = await fetch(`${apiBaseUrl}/workspaces`, {
    method: 'POST',
    headers: identityHeaders(payload),
    body: JSON.stringify({
      displayName: text(payload?.displayName),
      // 绝对路径只在本机 Electron 主进程与本机 Runtime 之间传递。
      rootPath,
    }),
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(body.message || `登记本地项目失败（${response.status}）`)
  }
  return body
}

ipcMain.handle('harness:pick-workspace', async (event, payload) => {
  if (!isTrustedRenderer(event.sender)) {
    throw new Error('未受信任的页面不能请求本地目录')
  }
  const chosen = await dialog.showOpenDialog(mainWindow, {
    title: '选择要让 Ming Agent 操作的项目目录',
    buttonLabel: '授权此项目',
    properties: ['openDirectory', 'createDirectory'],
  })
  if (chosen.canceled || !chosen.filePaths[0]) {
    return { cancelled: true }
  }
  const workspace = await registerWorkspace(chosen.filePaths[0], payload)
  // 后端摘要不含 rootPath；主进程也不向渲染层返回用户选择的真实路径。
  return { cancelled: false, workspace }
})

/**
 * 原生拖拽只接受真实目录：路径来自 preload 对浏览器 File 的受控解析，
 * 渲染层既不能调用此 IPC，也不会从结果中得到绝对路径。
 */
ipcMain.handle('harness:register-dropped-workspace', async (event, payload) => {
  if (!isTrustedRenderer(event.sender)) {
    throw new Error('未受信任的页面不能登记拖入目录')
  }
  const rootPath = await resolveDroppedWorkspaceRoot(payload?.droppedPaths)
  if (!rootPath) return { ignored: true }
  const workspace = await registerWorkspace(rootPath, payload?.identity)
  return { ignored: false, workspace }
})

/** 仅保留一个项目根目录，避免一次拖入多项目时把 Agent 的可写边界扩大为不明确集合。 */
async function resolveDroppedWorkspaceRoot(droppedPaths) {
  if (!Array.isArray(droppedPaths) || droppedPaths.length === 0) return null
  if (droppedPaths.length > 8) {
    throw new Error('一次最多检查 8 个拖入项目，请一次只拖入一个目录')
  }
  const directories = []
  for (const value of droppedPaths) {
    if (typeof value !== 'string' || value.length === 0 || value.length > 4096) continue
    try {
      const realPath = await fs.realpath(path.resolve(value))
      const stat = await fs.stat(realPath)
      if (stat.isDirectory()) directories.push(realPath)
    } catch {
      // 被移动、无权限或不是目录的拖入项会被忽略，普通文件继续走浏览器附件导入。
    }
  }
  const uniqueDirectories = [...new Set(directories)]
  if (!uniqueDirectories.length) return null
  if (uniqueDirectories.length > 1) {
    throw new Error('一次只能授权一个本地项目目录，请拆分后重新拖入')
  }
  return uniqueDirectories[0]
}

app.whenReady().then(() => {
  return (frontendUrl ? Promise.resolve(frontendUrl) : startStaticFrontendServer())
    .then((resolvedUrl) => {
      frontendUrl = resolvedUrl
      createWindow()
    })
    .catch((error) => {
      dialog.showErrorBox('无法启动桌面工作台', error.message)
      app.quit()
    })
})

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0 && frontendUrl) createWindow()
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

app.on('before-quit', () => {
  staticServer?.close()
})
