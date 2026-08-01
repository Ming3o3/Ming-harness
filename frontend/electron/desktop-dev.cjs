const { randomBytes } = require('node:crypto')
const { spawn } = require('node:child_process')
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

async function waitFor(url, label) {
  const deadline = Date.now() + 90_000
  while (Date.now() < deadline) {
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
  start(path.join(projectRoot, process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw'),
    ['spring-boot:run'], projectRoot, sharedEnv)
  start(command('npm'), ['run', 'dev', '--', '--host', '127.0.0.1'], frontendRoot, sharedEnv)
  await Promise.all([
    waitFor('http://127.0.0.1:8080/actuator/health', 'Spring Boot Runtime'),
    waitFor(frontendUrl, 'Vite 前端'),
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
