import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { prepareWinRuntime } from './prepare-win-runtime.mjs'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const projectRoot = path.resolve(frontendRoot, '..')
const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw'

await run(path.join(projectRoot, wrapper), ['-DskipTests', 'package'], projectRoot)
await prepareWinRuntime()

function run(command, args, cwd) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, stdio: 'inherit', shell: false })
    child.once('error', reject)
    child.once('close', (code) => {
      if (code === 0) resolve()
      else reject(new Error(`命令失败（${code ?? '未知'}）：${command} ${args.join(' ')}`))
    })
  })
}
