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
    // Windows 的 Maven Wrapper 是 .cmd 批处理文件，CreateProcess 不能在
    // shell=false 时直接启动它；仅对这个固定的构建入口启用系统解释器。
    const useWindowsCmd = process.platform === 'win32' && command.toLowerCase().endsWith('.cmd')
    const child = spawn(command, args, {
      cwd,
      stdio: 'inherit',
      shell: useWindowsCmd,
    })
    child.once('error', reject)
    child.once('close', (code) => {
      if (code === 0) resolve()
      else reject(new Error(`命令失败（${code ?? '未知'}）：${command} ${args.join(' ')}`))
    })
  })
}
