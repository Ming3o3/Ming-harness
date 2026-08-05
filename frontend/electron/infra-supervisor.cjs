const crypto = require('node:crypto')
const fs = require('node:fs/promises')
const path = require('node:path')
const net = require('node:net')
const { spawn } = require('node:child_process')

/**
 * Windows green-build infrastructure supervisor.
 *
 * The supervisor deliberately runs PostgreSQL, Garnet and RabbitMQ as
 * user-owned child processes instead of installing Windows services. This
 * keeps the zip distribution portable and avoids requiring administrator
 * privileges. Runtime binaries are supplied by the build pipeline under
 * runtime/infra/win-x64; they are not committed to the repository.
 */
class ManagedInfrastructure {
  constructor({ runtimeRoot, dataRoot, logRoot, logger = console }) {
    this.runtimeRoot = runtimeRoot
    this.dataRoot = dataRoot
    this.logRoot = logRoot
    this.logger = logger
    this.children = new Map()
    this.config = null
    this.started = false
  }

  async start() {
    if (process.platform !== 'win32') {
      throw new Error('Managed local-infra 目前只实现 Windows 运行时')
    }
    await this.ensureDirectories()
    await this.assertRuntimeLayout()
    this.config = await this.loadOrCreateConfig()

    try {
      await this.startPostgres()
      await this.startGarnet()
      await this.startRabbitMq()
      this.started = true
      return this.environment()
    } catch (error) {
      await this.stop()
      throw error
    }
  }

  async stop() {
    // Reverse dependency order: clients first, then brokers and storage.
    await this.stopRabbitMq()
    await this.stopGarnet()
    await this.stopPostgres()
    this.started = false
  }

  environment() {
    if (!this.config || !this.started) {
      throw new Error('Managed local-infra 尚未启动')
    }
    return {
      SPRING_PROFILES_ACTIVE: 'local-infra,desktop',
      HARNESS_EXECUTION_MODE: 'rabbit',
      LOCAL_ASYNC_EXECUTION: 'false',
      DB_URL: `jdbc:postgresql://127.0.0.1:${this.config.postgres.port}/ming_harness`,
      DB_USERNAME: this.config.postgres.username,
      DB_PASSWORD: this.config.postgres.password,
      REDIS_ENABLED: 'true',
      REDIS_HOST: '127.0.0.1',
      REDIS_PORT: String(this.config.garnet.port),
      REDIS_USERNAME: '',
      REDIS_PASSWORD: this.config.garnet.password,
      RABBITMQ_ENABLED: 'true',
      RABBITMQ_HOST: '127.0.0.1',
      RABBITMQ_PORT: String(this.config.rabbitmq.port),
      RABBITMQ_USERNAME: this.config.rabbitmq.username,
      RABBITMQ_PASSWORD: this.config.rabbitmq.password,
      ENABLE_VECTOR_EXTENSION: 'true',
      HARNESS_WORKSPACE_ROOT: path.join(this.dataRoot, 'workspace'),
      WORKSPACE_ENABLED: 'true',
      WORKSPACE_LOCAL_REGISTRATION_ENABLED: 'true',
    }
  }

  async ensureDirectories() {
    await fs.mkdir(this.dataRoot, { recursive: true })
    await fs.mkdir(this.logRoot, { recursive: true })
    await fs.mkdir(path.join(this.dataRoot, 'postgres', 'data'), { recursive: true })
    // Garnet stores only short-lived coordination keys for this application.
    // Keep a directory available for optional storage-tier data and logs, but
    // run the default green build in memory so stale locks cannot survive a
    // process restart.
    await fs.mkdir(path.join(this.dataRoot, 'garnet'), { recursive: true })
    await fs.mkdir(path.join(this.dataRoot, 'rabbitmq'), { recursive: true })
    await fs.mkdir(path.join(this.dataRoot, 'workspace'), { recursive: true })
    try {
      await fs.access(this.dataRoot, fs.constants.W_OK)
    } catch {
      throw new Error(`绿色版数据目录不可写：${this.dataRoot}`)
    }
  }

  async assertRuntimeLayout() {
    const required = [
      ['PostgreSQL', this.postgresExecutable('initdb.exe')],
      ['PostgreSQL', this.postgresExecutable('postgres.exe')],
      ['PostgreSQL', this.postgresExecutable('pg_ctl.exe')],
      ['PostgreSQL', this.postgresExecutable('createdb.exe')],
      ['pgvector', path.join(this.runtimeRoot, 'postgres', 'lib', 'vector.dll')],
      ['pgvector', path.join(this.runtimeRoot, 'postgres', 'share', 'extension', 'vector.control')],
      ['pgvector provenance', path.join(this.runtimeRoot, 'postgres', 'share', 'extension', 'pgvector-build.json')],
      ['Garnet', path.join(this.runtimeRoot, 'garnet', 'GarnetServer.exe')],
      ['Garnet .NET runtime', path.join(this.runtimeRoot, 'garnet', 'dotnet', 'dotnet.exe')],
      ['RabbitMQ', path.join(this.runtimeRoot, 'rabbitmq', 'sbin', 'rabbitmq-server.bat')],
      ['RabbitMQ', path.join(this.runtimeRoot, 'rabbitmq', 'sbin', 'rabbitmqctl.bat')],
      ['Erlang', path.join(this.runtimeRoot, 'erlang', 'bin', 'erl.exe')],
    ]
    const missing = []
    for (const [component, file] of required) {
      try {
        await fs.access(file, fs.constants.X_OK)
      } catch {
        missing.push(`${component}: ${file}`)
      }
    }
    if (missing.length) {
      throw new Error(`Windows local-infra 运行时不完整：\n${missing.join('\n')}`)
    }
  }

  async loadOrCreateConfig() {
    const configPath = path.join(this.dataRoot, 'runtime-config.json')
    try {
      const existing = JSON.parse(await fs.readFile(configPath, 'utf8'))
      if (existing?.schemaVersion === 2 && existing.postgres && existing.garnet && existing.rabbitmq) {
        return existing
      }

      // Upgrade a green directory created by an older Redis-runtime build. PostgreSQL
      // and RabbitMQ credentials remain valid; Garnet gets a fresh port and
      // password because it is a different Redis-compatible server.
      if (existing?.schemaVersion === 1 && existing.postgres && existing.rabbitmq) {
        const upgraded = {
          schemaVersion: 2,
          postgres: existing.postgres,
          garnet: {
            port: await findFreePort(),
            password: randomToken(32),
          },
          rabbitmq: existing.rabbitmq,
        }
        await atomicWriteJson(configPath, upgraded)
        return upgraded
      }
    } catch {
      // First launch or an interrupted write; create a fresh local config.
    }

    const config = {
      schemaVersion: 2,
      postgres: {
        port: await findFreePort(),
        username: `ming_${randomToken(8)}`,
        password: randomToken(32),
      },
      garnet: {
        port: await findFreePort(),
        password: randomToken(32),
      },
      rabbitmq: {
        port: await findFreePort(),
        distributionPort: await findFreePort(),
        username: `ming_${randomToken(8)}`,
        password: randomToken(32),
      },
    }
    await atomicWriteJson(configPath, config)
    return config
  }

  postgresExecutable(name) {
    return path.join(this.runtimeRoot, 'postgres', 'bin', name)
  }

  async startPostgres() {
    const dataDir = path.join(this.dataRoot, 'postgres', 'data')
    const initialized = await exists(path.join(dataDir, 'PG_VERSION'))
    if (!initialized) {
      const passwordFile = path.join(this.dataRoot, 'postgres', '.bootstrap-password')
      await fs.writeFile(passwordFile, `${this.config.postgres.password}\n`, { encoding: 'utf8', mode: 0o600 })
      try {
        await runCommand(this.postgresExecutable('initdb.exe'), [
          '--pgdata', dataDir,
          '--username', this.config.postgres.username,
          '--pwfile', passwordFile,
          '--auth-host', 'scram-sha-256',
          '--encoding', 'UTF8',
        ], { cwd: path.dirname(this.postgresExecutable('initdb.exe')), logger: this.logger })
      } finally {
        await fs.rm(passwordFile, { force: true })
      }
    }

    const child = this.spawnLongRunning('postgres', this.postgresExecutable('postgres.exe'), [
      '-D', dataDir,
      '-h', '127.0.0.1',
      '-p', String(this.config.postgres.port),
    ])
    await waitForTcp('127.0.0.1', this.config.postgres.port, child, 'PostgreSQL')

    if (!await exists(path.join(dataDir, '.ming-harness-db-created'))) {
      await runCommandWithRetry(this.postgresExecutable('createdb.exe'), [
        '--host', '127.0.0.1',
        '--port', String(this.config.postgres.port),
        '--username', this.config.postgres.username,
        'ming_harness',
      ], {
        cwd: path.dirname(this.postgresExecutable('createdb.exe')),
        env: { PGPASSWORD: this.config.postgres.password },
        logger: this.logger,
      })
      await fs.writeFile(path.join(dataDir, '.ming-harness-db-created'), '1\n')
    }
  }

  async stopPostgres() {
    const child = this.children.get('postgres')
    if (child && child.exitCode === null) {
      try {
        await runCommand(this.postgresExecutable('pg_ctl.exe'), [
          '--pgdata', path.join(this.dataRoot, 'postgres', 'data'),
          '--mode', 'fast',
          'stop',
        ], { cwd: path.dirname(this.postgresExecutable('pg_ctl.exe')), logger: this.logger })
      } catch (error) {
        this.logger.warn?.(`PostgreSQL 优雅停止失败，将终止进程：${error.message}`)
      }
    }
    await this.stopChild('postgres')
  }

  async startGarnet() {
    const dataDir = path.join(this.dataRoot, 'garnet')
    const garnetRoot = path.join(this.runtimeRoot, 'garnet')
    const dotnetRoot = path.join(garnetRoot, 'dotnet')
    const executable = path.join(garnetRoot, 'GarnetServer.exe')
    const child = this.spawnLongRunning('garnet', executable, [
      '--port', String(this.config.garnet.port),
      '--bind', '127.0.0.1',
      '--auth', 'Password',
      '--password', this.config.garnet.password,
      '--lua',
      '--lua-transaction-mode',
    ], {
      cwd: dataDir,
      env: {
        // The official Windows Garnet bundle is framework-dependent. These
        // variables force the apphost to use the bundled .NET runtime and
        // prevent fallback to a machine-wide installation.
        DOTNET_ROOT: dotnetRoot,
        DOTNET_ROOT_X64: dotnetRoot,
        DOTNET_MULTILEVEL_LOOKUP: '0',
      },
    })
    await waitForTcp('127.0.0.1', this.config.garnet.port, child, 'Garnet')
    await waitForGarnet('127.0.0.1', this.config.garnet.port, this.config.garnet.password, child)
  }

  async stopGarnet() {
    await this.stopChild('garnet')
  }

  async startRabbitMq() {
    const baseDir = path.join(this.dataRoot, 'rabbitmq')
    const sbinDir = path.join(this.runtimeRoot, 'rabbitmq', 'sbin')
    const configPath = path.join(baseDir, 'rabbitmq.conf')
    await fs.writeFile(configPath, [
      `listeners.tcp.default = 127.0.0.1:${this.config.rabbitmq.port}`,
      'loopback_users.guest = false',
      'cluster_partition_handling = pause_minority',
      '',
    ].join('\n'), 'utf8')
    const cookiePath = path.join(baseDir, '.erlang.cookie')
    if (!await exists(cookiePath)) {
      await fs.writeFile(cookiePath, randomToken(48), { encoding: 'utf8', mode: 0o600 })
    }
    const erlangCookie = (await fs.readFile(cookiePath, 'utf8')).trim()

    const env = {
      ERLANG_HOME: path.join(this.runtimeRoot, 'erlang'),
      RABBITMQ_BASE: baseDir,
      RABBITMQ_NODENAME: 'ming_harness@localhost',
      RABBITMQ_CONFIG_FILE: configPath,
      RABBITMQ_DIST_PORT: String(this.config.rabbitmq.distributionPort),
      RABBITMQ_ERLANG_COOKIE: erlangCookie,
    }
    const server = this.spawnLongRunning('rabbitmq', process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', 'rabbitmq-server.bat'], {
      cwd: sbinDir,
      env,
    })
    await waitForTcp('127.0.0.1', this.config.rabbitmq.port, server, 'RabbitMQ')

    if (!await exists(path.join(baseDir, '.ming-harness-user-created'))) {
      await runCommandWithRetry(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', 'rabbitmqctl.bat', 'add_user', this.config.rabbitmq.username, this.config.rabbitmq.password], {
        cwd: sbinDir,
        env,
        logger: this.logger,
      })
      await runCommandWithRetry(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', 'rabbitmqctl.bat', 'set_permissions', '-p', '/', this.config.rabbitmq.username, '.*', '.*', '.*'], {
        cwd: sbinDir,
        env,
        logger: this.logger,
      })
      await fs.writeFile(path.join(baseDir, '.ming-harness-user-created'), '1\n')
    }
  }

  async stopRabbitMq() {
    const child = this.children.get('rabbitmq')
    if (!child) return
    const sbinDir = path.join(this.runtimeRoot, 'rabbitmq', 'sbin')
    try {
      await runCommand(process.env.ComSpec || 'cmd.exe', ['/d', '/s', '/c', 'rabbitmqctl.bat', 'stop'], {
        cwd: sbinDir,
        env: {
          ERLANG_HOME: path.join(this.runtimeRoot, 'erlang'),
          RABBITMQ_BASE: path.join(this.dataRoot, 'rabbitmq'),
          RABBITMQ_NODENAME: 'ming_harness@localhost',
          RABBITMQ_CONFIG_FILE: path.join(this.dataRoot, 'rabbitmq', 'rabbitmq.conf'),
          RABBITMQ_DIST_PORT: String(this.config.rabbitmq.distributionPort),
          RABBITMQ_ERLANG_COOKIE: (await fs.readFile(path.join(this.dataRoot, 'rabbitmq', '.erlang.cookie'), 'utf8')).trim(),
        },
        logger: this.logger,
      })
    } catch (error) {
      this.logger.warn?.(`RabbitMQ 优雅停止失败，将终止进程：${error.message}`)
    }
    await this.stopChild('rabbitmq')
  }

  spawnLongRunning(name, executable, args, options = {}) {
    const logPath = path.join(this.logRoot, `${name}.log`)
    const runtimePath = [
      // Packaged builds keep Java at resources/runtime/jre while managed
      // infrastructure lives at resources/infra/win-x64.
      path.resolve(this.runtimeRoot, '..', '..', 'runtime', 'jre', 'bin'),
      path.join(this.runtimeRoot, 'postgres', 'bin'),
      path.join(this.runtimeRoot, 'garnet'),
      path.join(this.runtimeRoot, 'garnet', 'dotnet'),
      path.join(this.runtimeRoot, 'erlang', 'bin'),
      process.env.PATH || '',
    ].filter(Boolean).join(path.delimiter)
    const child = spawn(executable, args, {
      cwd: options.cwd || path.dirname(executable),
      env: { ...process.env, ...(options.env || {}), PATH: runtimePath },
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    const output = fs.open(logPath, 'a').then(async (handle) => {
      child.stdout?.on('data', (chunk) => handle.write(chunk))
      child.stderr?.on('data', (chunk) => handle.write(chunk))
      child.once('close', () => handle.close().catch(() => {}))
    }).catch(() => {})
    void output
    child.once('error', (error) => {
      child.startError = error
      this.logger.error?.(`${name} 启动失败：${error.message}`)
    })
    child.once('exit', (code, signal) => {
      if (this.children.get(name) === child && !this.stopping) {
        this.logger.error?.(`${name} 异常退出：code=${code ?? 'null'}, signal=${signal ?? 'null'}`)
      }
    })
    this.children.set(name, child)
    return child
  }

  async stopChild(name) {
    const child = this.children.get(name)
    if (!child) return
    this.children.delete(name)
    if (child.exitCode !== null) return
    this.stopping = true
    try {
      await killProcessTree(child.pid)
    } finally {
      this.stopping = false
    }
  }
}

async function runCommand(command, args, { cwd, env, logger = console } = {}) {
  return new Promise((resolve, reject) => {
    const commandEnv = { ...process.env, ...(env || {}) }
    if (cwd) commandEnv.PATH = [cwd, commandEnv.PATH || ''].filter(Boolean).join(path.delimiter)
    const child = spawn(command, args, {
      cwd,
      env: commandEnv,
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    let stderr = ''
    child.stdout?.on('data', (chunk) => logger.debug?.(String(chunk).trimEnd()))
    child.stderr?.on('data', (chunk) => { stderr += String(chunk) })
    child.once('error', reject)
    child.once('close', (code) => {
      if (code === 0) {
        resolve()
      } else {
        reject(new Error(`命令失败（${code}）：${command} ${args.join(' ')}\n${stderr.trim()}`))
      }
    })
  })
}

async function runCommandWithRetry(command, args, options = {}, attempts = 30, waitMs = 1000) {
  let lastError
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      await runCommand(command, args, options)
      return
    } catch (error) {
      lastError = error
      if (attempt < attempts) await delay(waitMs)
    }
  }
  throw lastError
}

async function waitForTcp(host, port, child, label, timeoutMs = 90000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (child.startError) throw new Error(`${label} 启动失败：${child.startError.message}`)
    if (child.exitCode !== null) {
      throw new Error(`${label} 提前退出（退出码 ${child.exitCode ?? '未知'}）`)
    }
    const connected = await canConnect(host, port)
    if (connected) return
    await delay(250)
  }
  throw new Error(`${label} 未在 ${timeoutMs}ms 内监听 ${host}:${port}`)
}

function canConnect(host, port) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host, port })
    socket.once('connect', () => { socket.destroy(); resolve(true) })
    socket.once('error', () => { socket.destroy(); resolve(false) })
    socket.setTimeout(500, () => { socket.destroy(); resolve(false) })
  })
}

async function waitForGarnet(host, port, password, child, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`Garnet 提前退出（退出码 ${child.exitCode ?? '未知'}）`)
    }
    try {
      const response = await redisAuthPing(host, port, password)
      if (response.includes('+OK') && response.includes('+PONG')) return
    } catch {
      // Garnet may accept TCP before authentication is ready.
    }
    await delay(250)
  }
  throw new Error(`Garnet 未在 ${timeoutMs}ms 内通过 AUTH/PING 检查`)
}

function redisAuthPing(host, port, password) {
  const auth = redisCommand(['AUTH', password])
  const ping = redisCommand(['PING'])
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host, port })
    let output = ''
    let done = false
    const finish = (error) => {
      if (done) return
      done = true
      socket.destroy()
      if (error) reject(error)
      else resolve(output)
    }
    socket.setTimeout(1000, () => finish(new Error('Garnet AUTH/PING 超时')))
    socket.once('error', finish)
    socket.on('data', (chunk) => {
      output += String(chunk)
      if (output.includes('+OK') && output.includes('+PONG')) finish()
      if (output.startsWith('-')) finish(new Error(output.trim()))
    })
    socket.once('connect', () => socket.write(`${auth}${ping}`))
  })
}

function redisCommand(parts) {
  return `*${parts.length}\r\n${parts.map((part) => `$${Buffer.byteLength(part)}\r\n${part}\r\n`).join('')}`
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

function randomToken(bytes) {
  return crypto.randomBytes(bytes).toString('base64url')
}

async function atomicWriteJson(file, value) {
  const temp = `${file}.tmp-${process.pid}`
  await fs.writeFile(temp, `${JSON.stringify(value, null, 2)}\n`, 'utf8')
  await fs.rename(temp, file)
}

async function exists(file) {
  try {
    await fs.access(file)
    return true
  } catch {
    return false
  }
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
  ManagedInfrastructure,
  findFreePort,
  exists,
}
