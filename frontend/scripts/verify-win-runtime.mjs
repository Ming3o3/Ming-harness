import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { validateWinRuntimeSource } from './prepare-win-runtime.mjs'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const projectRoot = path.resolve(frontendRoot, '..')
const source = path.resolve(
  process.env.MING_HARNESS_WIN_RUNTIME_SOURCE || path.join(projectRoot, 'runtime', 'vendor', 'win-x64'),
)

try {
  const missing = await validateWinRuntimeSource(source)
  if (missing.length) {
    throw new Error(`Windows 运行时不完整：\n${missing.join('\n')}`)
  }
  console.log(`Windows runtime 校验通过：${source}`)
} catch (error) {
  console.error(error.message)
  process.exitCode = 1
}
