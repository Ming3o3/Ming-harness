const fs = require('node:fs/promises')
const path = require('node:path')
const { spawn } = require('node:child_process')
const net = require('node:net')

class ManagedSpringBootRuntime {
  constructor({ resourcesRoot, logRoot, logger = console }) {
    this.resourcesRoot = resourcesRoot
    this.logRoot = logRoot
    this.logger = logger
    this.child = null
    this.port = null
    this.apiBaseUrl = null
  }

  async start(environment = {}) {
    const java = path.join(this.resourcesRoot, 'runtime', 'jre', 'bin', 'java.exe')
    const jar = path.join(this.resourcesRoot, 'runtime', 'ming-harness.jar')
    await assertFile(java, '内置 Java Runtime')
    await assertFile(jar, 'Ming Harness Spring Boot JAR')
    await fs.mkdir(this.logRoot, { recursive: true })

    this.port = await findFreePort()
    this.apiBaseUrl = `http://127.0.0.1:${this.port}/api`
    const logPath = path.join(this.logRoot, 'spring-boot.log')
    const child = spawn(java, ['-jar', jar], {
      cwd: this.resourcesRoot,
      env: {
        ...process.env,
        ...environment,
        SERVER_PORT: String(this.port),
      },
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    this.child = child
    const logHandle = await fs.open(logPath, 'a')
    child.stdout?.on('data', (chunk) => logHandle.write(chunk))
    child.stderr?.on('data', (chunk) => logHandle.write(chunk))
    child.once('close', () => logHandle.close().catch(() => {}))
    child.once('error', (error) => {
      child.startError = error
      this.logger.error?.(`Spring Boot 启动失败：${error.message}`)
    })

    await waitForHealth(`http://127.0.0.1:${this.port}/actuator/health`, this.child)
    return {
      apiBaseUrl: this.apiBaseUrl,
      port: this.port,
    }
  }

  async stop() {
    if (!this.child || this.child.exitCode !== null) return
    const child = this.child
    this.child = null
    await killProcessTree(child.pid)
  }
}

async function assertFile(file, label) {
  try {
    await fs.access(file)
  } catch {
    throw new Error(`${label}不存在：${file}`)
  }
}

async function waitForHealth(url, child, timeoutMs = 120000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (child.startError) {
      throw new Error(`Spring Boot 启动失败：${child.startError.message}`)
    }
    if (child.exitCode !== null) {
      throw new Error(`Spring Boot 提前退出（退出码 ${child.exitCode ?? '未知'}）`)
    }
    try {
      const response = await fetch(url)
      if (response.ok) {
        const body = await response.json().catch(() => ({}))
        if (!body.status || body.status === 'UP') return
      }
    } catch {
      // 启动期间端口尚未监听，继续轮询。
    }
    await delay(300)
  }
  throw new Error(`Spring Boot 未在 ${timeoutMs}ms 内通过健康检查：${url}`)
}

function findFreePort(host = '127.0.0.1') {
  return new Promise((resolve, reject) => {
    const server = net.createServer()
    server.once('error', reject)
    server.listen(0, host, () => {
      const address = server.address()
      const port = typeof address === 'object' && address ? address.port : 0
      server.close(() => resolve(port))
    })
  })
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function killProcessTree(pid) {
  if (!pid) return Promise.resolve()
  return new Promise((resolve) => {
    const killer = spawn(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', 'taskkill', '/PID', String(pid), '/T', '/F'], {
      windowsHide: true,
      stdio: 'ignore',
    })
    killer.once('close', () => resolve())
    killer.once('error', () => resolve())
  })
}

module.exports = {
  ManagedSpringBootRuntime,
  findFreePort,
}
