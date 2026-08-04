const { randomBytes } = require('node:crypto')
const { spawn } = require('node:child_process')
const net = require('node:net')
const path = require('node:path')

const frontendRoot = path.resolve(__dirname, '..')
const projectRoot = path.resolve(frontendRoot, '..')
const bridgeToken = randomBytes(32).toString('base64url')
const runtimeProfile = process.env.SPRING_PROFILES_ACTIVE || 'local'
const frontendUrl = process.env.HARNESS_FRONTEND_URL || 'http://127.0.0.1:5173'
const apiBaseUrl = process.env.HARNESS_API_BASE_URL || 'http://127.0.0.1:8080/api'
let children = []
let stopping = false

function command(name) {
  return process.platform === 'win32' ? `${name}.cmd` : name
}

function start(commandName, args, cwd, env) {
  const child = spawn(commandName, args, { cwd, env, stdio: 'inherit' })
  children.push(child)
  child.once('exit', (code) => {
    if (!stopping && code !== 0) {
      console.error(`本地开发进程异常退出（${commandName}，退出码 ${code ?? '未知'}）`)
      stop(code || 1)
    }
  })
  return child
}

function stop(exitCode = 0) {
  if (stopping) return
  stopping = true
  for (const child of children) {
    if (!child.killed) child.kill('SIGTERM')
  }
  process.exit(exitCode)
}

function assertPortAvailable(url, label) {
  const parsed = new URL(url)
  const port = Number(parsed.port || (parsed.protocol === 'https:' ? 443 : 80))
  const host = parsed.hostname
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host, port })
    socket.once('connect', () => {
      socket.destroy()
      reject(new Error(`${label} 端口 ${port} 已被占用，请先关闭已有 Runtime 或修改端口配置。`))
    })
    socket.once('error', (error) => {
      socket.destroy()
      if (error.code === 'ECONNREFUSED' || error.code === 'ENOTFOUND') {
        resolve()
        return
      }
      reject(new Error(`${label} 端口 ${port} 无法检查：${error.message}`))
    })
  })
}

async function waitFor(url, label, child) {
  const deadline = Date.now() + 90_000
  while (Date.now() < deadline) {
    if (child?.exitCode !== null && child?.exitCode !== undefined) {
      throw new Error(`${label} 进程已提前退出（退出码 ${child.exitCode}）。`)
    }
    try {
      const response = await fetch(url)
      if (response.status < 500) return
    } catch {
      // 启动期连接被拒绝是预期状态，继续短暂轮询。
    }
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`${label} 未在 90 秒内启动`)
}

async function main() {
  const sharedEnv = {
    ...process.env,
    SPRING_PROFILES_ACTIVE: runtimeProfile,
    WORKSPACE_ENABLED: 'true',
    WORKSPACE_LOCAL_REGISTRATION_ENABLED: 'true',
    HARNESS_DESKTOP_BRIDGE_TOKEN: bridgeToken,
    HARNESS_API_BASE_URL: apiBaseUrl,
    HARNESS_FRONTEND_URL: frontendUrl,
  }
  console.log(`启动本地桌面 Runtime（Profile: ${runtimeProfile}）`)
  await Promise.all([
    assertPortAvailable('http://127.0.0.1:8080', 'Spring Boot Runtime'),
    assertPortAvailable(frontendUrl, 'Vite 前端'),
  ])
  const runtimeProcess = start(path.join(projectRoot, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw'),
    ['spring-boot:run'], projectRoot, sharedEnv)
  const frontendProcess = start(command('npm'), ['run', 'dev', '--', '--host', '127.0.0.1', '--strictPort'], frontendRoot, sharedEnv)
  await Promise.all([
    waitFor('http://127.0.0.1:8080/actuator/health', 'Spring Boot Runtime', runtimeProcess),
    waitFor(frontendUrl, 'Vite 前端', frontendProcess),
  ])
  console.log('打开 Ming Harness 桌面窗口；关闭窗口将停止本地开发进程。')
  start(path.join(frontendRoot, 'node_modules', '.bin', process.platform === 'win32' ? 'electron.cmd' : 'electron'),
    ['.'], frontendRoot, sharedEnv)
}

process.on('SIGINT', () => stop(0))
process.on('SIGTERM', () => stop(0))
main().catch((error) => {
  console.error(error.message)
  stop(1)
})
