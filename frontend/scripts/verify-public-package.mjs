import { readdir, stat } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const packageRoot = path.resolve(
  process.argv[2] || path.join(frontendRoot, 'release', 'win-green', 'win-unpacked'),
)

const forbidden = []
const required = [
  path.join('resources', 'infra', 'win-x64', 'garnet', 'GarnetServer.exe'),
  path.join('resources', 'infra', 'win-x64', 'licenses', 'THIRD-PARTY-NOTICES.md'),
  path.join('resources', 'infra', 'win-x64', 'licenses', 'MSVC-REDIST-NOTICE.md'),
  path.join('resources', 'infra', 'win-x64', 'postgres', 'share', 'extension', 'pgvector-build.json'),
]

async function walk(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name)
    if (/memurai/i.test(entry.name)) forbidden.push(file)
    if (entry.isDirectory()) await walk(file)
  }
}

try {
  const details = await stat(packageRoot)
  if (!details.isDirectory()) throw new Error(`发布目录不是目录：${packageRoot}`)
  await walk(packageRoot)
  for (const relative of required) {
    try {
      await stat(path.join(packageRoot, relative))
    } catch {
      throw new Error(`公开发布包缺少必需文件：${path.join(packageRoot, relative)}`)
    }
  }
  if (forbidden.length) {
    throw new Error(`公开发布包包含已禁用的 Memurai 文件：\n${forbidden.join('\n')}`)
  }
  console.log(`公开发布包校验通过：${packageRoot}`)
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
}
