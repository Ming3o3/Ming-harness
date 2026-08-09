import { cp, mkdir, rm, stat, readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const projectRoot = path.resolve(frontendRoot, '..')
const vendorRoot = path.resolve(
  process.env.MING_HARNESS_WIN_RUNTIME_SOURCE || path.join(projectRoot, 'runtime', 'vendor', 'win-x64'),
)
const stagedRoot = path.join(projectRoot, 'runtime', 'infra', 'win-x64')
const stagedJre = path.join(projectRoot, 'runtime', 'jre')
const stagedJar = path.join(projectRoot, 'runtime', 'ming-harness.jar')
const projectLicenses = path.join(projectRoot, 'runtime', 'licenses')
const allowUnverifiedPgvector = process.env.MING_HARNESS_ALLOW_UNVERIFIED_PGVECTOR === 'true'

const requiredFiles = [
  ['PostgreSQL initdb', path.join('postgres', 'bin', 'initdb.exe')],
  ['PostgreSQL server', path.join('postgres', 'bin', 'postgres.exe')],
  ['PostgreSQL pg_ctl', path.join('postgres', 'bin', 'pg_ctl.exe')],
  ['PostgreSQL createdb', path.join('postgres', 'bin', 'createdb.exe')],
  ['pgvector DLL', path.join('postgres', 'lib', 'vector.dll')],
  ['pgvector control file', path.join('postgres', 'share', 'extension', 'vector.control')],
  ['pgvector SQL file', path.join('postgres', 'share', 'extension', 'vector--0.8.6.sql')],
  ['Garnet server', path.join('garnet', 'GarnetServer.exe')],
  ['Garnet license', path.join('garnet', 'LICENSE')],
  ['Garnet notices', path.join('garnet', 'NOTICE.md')],
  ['Garnet .NET runtime', path.join('garnet', 'dotnet', 'dotnet.exe')],
  ['Garnet .NET license', path.join('garnet', 'dotnet', 'LICENSE.txt')],
  ['Garnet .NET notices', path.join('garnet', 'dotnet', 'ThirdPartyNotices.txt')],
  ['Garnet .NET Core runtime', path.join('garnet', 'dotnet', 'shared', 'Microsoft.NETCore.App', '8.0.18', 'coreclr.dll')],
  ['RabbitMQ server', path.join('rabbitmq', 'sbin', 'rabbitmq-server.bat')],
  ['RabbitMQ control', path.join('rabbitmq', 'sbin', 'rabbitmqctl.bat')],
  ['Erlang', path.join('erlang', 'bin', 'erl.exe')],
  ['Java Runtime', path.join('jre', 'bin', 'java.exe')],
]

export async function prepareWinRuntime() {
  await ensureDirectory(vendorRoot, 'Windows 运行时源目录')
  const sourceMissing = await missingRuntimeFiles(vendorRoot)
  sourceMissing.push(...await missingPgvectorProvenance(vendorRoot))
  if (sourceMissing.length) {
    throw new Error(`Windows 运行时源目录不完整：\n${sourceMissing.join('\n')}`)
  }
  const jar = await findSpringBootJar()

  await rm(stagedRoot, { recursive: true, force: true })
  await rm(stagedJre, { recursive: true, force: true })
  await mkdir(path.dirname(stagedRoot), { recursive: true })
  await mkdir(path.dirname(stagedJre), { recursive: true })

  await cp(path.join(vendorRoot, 'postgres'), path.join(stagedRoot, 'postgres'), { recursive: true })
  // PostgreSQL headers are needed only while building pgvector. Do not ship
  // the development headers in the end-user runtime.
  await rm(path.join(stagedRoot, 'postgres', 'include'), { recursive: true, force: true })
  await cp(path.join(vendorRoot, 'garnet'), path.join(stagedRoot, 'garnet'), { recursive: true })
  await cp(path.join(vendorRoot, 'rabbitmq'), path.join(stagedRoot, 'rabbitmq'), { recursive: true })
  await cp(path.join(vendorRoot, 'erlang'), path.join(stagedRoot, 'erlang'), { recursive: true })
  await cp(path.join(vendorRoot, 'jre'), stagedJre, { recursive: true })
  await cp(jar, stagedJar)
  if (await isDirectory(projectLicenses)) {
    await cp(projectLicenses, path.join(stagedRoot, 'licenses'), { recursive: true })
  }
  await prunePortableRuntime(stagedRoot)
  await copyWindowsCrt(stagedJre, stagedRoot)
  const longPaths = await findLongRelativePaths(stagedRoot, 180)
  if (longPaths.length) {
    throw new Error(`Windows 绿色运行时仍包含可能触发 Explorer 路径限制的文件（相对路径超过 180 个字符）：\n${longPaths.join('\n')}`)
  }

  const missing = await missingRuntimeFiles(stagedRoot, false)
  missing.push(...await missingPgvectorProvenance(stagedRoot))
  if (!await isFile(path.join(stagedJre, 'bin', 'java.exe'))) {
    missing.push(`Java Runtime: ${path.join(stagedJre, 'bin', 'java.exe')}`)
  }
  if (missing.length) {
    throw new Error(`Windows 绿色版运行时准备失败，缺少：\n${missing.join('\n')}`)
  }
  console.log(`已准备 Windows runtime：${stagedRoot}`)
  console.log(`已准备 Spring Boot JAR：${stagedJar}`)
}

// RabbitMQ's Windows archive contains every optional plugin, plus Erlang
// documentation and development headers. None of those files are used by the
// managed local-infra process, but their nested names can exceed Explorer's
// legacy MAX_PATH limit when the ZIP is extracted into a long user directory.
async function prunePortableRuntime(infraRoot) {
  const postgresRoot = path.join(infraRoot, 'postgres')
  const rabbitmqRoot = path.join(infraRoot, 'rabbitmq')
  const pluginRoot = path.join(rabbitmqRoot, 'plugins')
  const keepPlugins = new Set([
    'amqp10_common-4.3.4',
    'aten-0.6.0',
    'cowlib-2.18.0',
    'credentials_obfuscation-3.5.0',
    'cuttlefish-3.9.1',
    'enough-0.1.0',
    'gen_batch_server-0.10.0',
    'horus-0.4.0',
    'khepri-0.18.0',
    'khepri_mnesia_migration-0.8.1',
    'observer_cli-1.8.2',
    'osiris-1.13.1',
    'ra-3.1.9',
    'rabbit-4.3.4',
    'rabbit_common-4.3.4',
    'rabbitmq_prelaunch-4.3.4',
    'ranch-2.2.0',
    'recon-2.5.6',
    'redbug-2.1.0',
    'seshat-1.0.1',
    'stdout_formatter-0.2.4',
    'syslog-4.0.0',
    'sysmon_handler-1.3.0',
    'systemd-0.6.1',
    'thoas-1.2.1',
  ])

  for (const entry of await readdir(pluginRoot, { withFileTypes: true })) {
    if (entry.isDirectory() && !keepPlugins.has(entry.name)) {
      await rm(path.join(pluginRoot, entry.name), { recursive: true, force: true })
    }
  }

  // The EDB PostgreSQL ZIP also contains pgAdmin 4, StackBuilder, source and
  // development files. They are not used by the managed server and contain
  // deeply nested Python/docs paths that Windows Explorer cannot extract.
  await Promise.all([
    rm(path.join(postgresRoot, 'pgAdmin 4'), { recursive: true, force: true }),
    rm(path.join(postgresRoot, 'StackBuilder'), { recursive: true, force: true }),
    rm(path.join(postgresRoot, 'doc'), { recursive: true, force: true }),
    rm(path.join(postgresRoot, 'src'), { recursive: true, force: true }),
    rm(path.join(postgresRoot, 'lib', 'pgxs'), { recursive: true, force: true }),
    rm(path.join(postgresRoot, 'lib', 'pkgconfig'), { recursive: true, force: true }),
  ])
  await removeNamedDirectories(rabbitmqRoot, new Set(['doc', 'docs', 'examples', 'include', 'src', 'test', 'tests']))
  await removeNamedDirectories(path.join(infraRoot, 'erlang'), new Set(['doc', 'docs', 'examples', 'include', 'man', 'test', 'tests']))
  await rm(path.join(postgresRoot, 'share', 'doc'), { recursive: true, force: true })
}

async function removeNamedDirectories(root, names) {
  const pending = [root]
  while (pending.length) {
    const current = pending.pop()
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const child = path.join(current, entry.name)
      if (!entry.isDirectory()) continue
      if (names.has(entry.name)) {
        await rm(child, { recursive: true, force: true })
      } else {
        pending.push(child)
      }
    }
  }
}

async function findLongRelativePaths(root, maxLength) {
  const matches = []
  const pending = [root]
  while (pending.length) {
    const current = pending.pop()
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const child = path.join(current, entry.name)
      if (entry.isDirectory()) {
        pending.push(child)
      } else if (path.relative(root, child).length > maxLength) {
        matches.push(path.relative(root, child))
      }
    }
  }
  return matches.sort().slice(0, 50)
}

async function copyWindowsCrt(jreRoot, infraRoot) {
  // The Windows ZIPs for PostgreSQL, Garnet and Erlang are portable at the
  // application level but may still resolve the MSVC/UCRT DLLs at launch.
  // Temurin ships the redistributable DLLs that its Java runtime uses; place
  // the same DLLs beside each native executable so the green build does not
  // depend on a machine-wide Visual C++ installation.
  const names = ['msvcp140.dll', 'vcruntime140.dll', 'vcruntime140_1.dll', 'ucrtbase.dll']
  const available = []
  for (const name of names) {
    const source = path.join(jreRoot, 'bin', name)
    if (await isFile(source)) available.push({ name, source })
  }
  const destinations = [
    path.join(infraRoot, 'postgres', 'bin'),
    path.join(infraRoot, 'garnet'),
  ]
  for (const entry of await readdir(path.join(infraRoot, 'erlang'), { withFileTypes: true })) {
    if (entry.isDirectory() && entry.name.startsWith('erts-')) {
      destinations.push(path.join(infraRoot, 'erlang', entry.name, 'bin'))
    }
  }
  for (const destination of destinations) {
    await mkdir(destination, { recursive: true })
    for (const { name, source } of available) await cp(source, path.join(destination, name))
  }
}

export async function validateWinRuntimeSource(source = vendorRoot) {
  await ensureDirectory(source, 'Windows 运行时源目录')
  const missing = await missingRuntimeFiles(source)
  missing.push(...await missingPgvectorProvenance(source))
  return missing
}

async function missingPgvectorProvenance(root) {
  const marker = path.resolve(root, 'postgres', 'share', 'extension', 'pgvector-build.json')
  if (!await isFile(marker)) {
    if (allowUnverifiedPgvector) {
      console.warn(`警告：未找到 pgvector 来源标记，当前仅允许技术验证，不得公开发布：${marker}`)
      return []
    }
    return [`pgvector 来源标记: ${marker}（请先运行 runtime/scripts/build-pgvector.ps1；技术验证可临时设置 MING_HARNESS_ALLOW_UNVERIFIED_PGVECTOR=true）`]
  }
  try {
    const provenance = JSON.parse((await readFile(marker, 'utf8')).replace(/^\uFEFF/, ''))
    const required = ['component', 'version', 'source', 'sourceCommit', 'sourceTreeSha256', 'vectorDllSha256']
    const invalid = required.filter((field) => typeof provenance[field] !== 'string' || provenance[field].trim() === '')
    if (provenance.component !== 'pgvector') invalid.push('component=pgvector')
    if (String(provenance.postgresMajor) !== '17') invalid.push('postgresMajor=17')
    if (provenance.architecture !== 'x64') invalid.push('architecture=x64')
    if (invalid.length) return [`pgvector 来源标记无效：${marker}（缺少或不匹配 ${invalid.join(', ')}）`]
  } catch (error) {
    return [`pgvector 来源标记无法读取：${marker}（${error.message}）`]
  }
  return []
}

async function missingRuntimeFiles(root, includeJava = true) {
  const missing = []
  for (const [label, relative] of requiredFiles) {
    if (!includeJava && relative.startsWith(`jre${path.sep}`)) continue
    const file = path.resolve(root, relative)
    if (!await isFile(file)) missing.push(`${label}: ${file}`)
  }
  return missing
}

async function findSpringBootJar() {
  const target = path.join(projectRoot, 'target')
  const entries = await readdir(target)
  const candidates = entries
    .filter((name) => /^Ming-harness-.*\.jar$/.test(name) && !name.endsWith('.original'))
    .map((name) => path.join(target, name))
  if (!candidates.length) {
    throw new Error('未找到 Spring Boot JAR，请先执行 Maven package')
  }
  const withStats = await Promise.all(candidates.map(async (file) => ({ file, mtime: (await stat(file)).mtimeMs })))
  withStats.sort((a, b) => b.mtime - a.mtime)
  return withStats[0].file
}

async function ensureDirectory(directory, label) {
  try {
    const details = await stat(directory)
    if (!details.isDirectory()) throw new Error('不是目录')
  } catch {
    throw new Error(`${label}不存在：${directory}\n请准备授权版本的 Windows runtime 后再构建。`)
  }
}

async function isFile(file) {
  try {
    const details = await stat(file)
    return details.isFile()
  } catch {
    return false
  }
}

async function isDirectory(directory) {
  try {
    return (await stat(directory)).isDirectory()
  } catch {
    return false
  }
}

if (process.argv[1] && path.resolve(fileURLToPath(import.meta.url)) === path.resolve(process.argv[1])) {
  prepareWinRuntime().catch((error) => {
    console.error(error.message)
    process.exitCode = 1
  })
}
