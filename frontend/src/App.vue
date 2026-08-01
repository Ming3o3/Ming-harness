<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { api } from './api'

const runs = ref([])
const tools = ref([])
const summary = ref(null)
const selectedRun = ref(null)
const auditEvents = ref([])
const documents = ref([])
const evaluations = ref([])
const tenantPolicy = ref(null)
const tenantPolicyAudits = ref([])
const tenantPolicyError = ref('')
const apiKeys = ref([])
const apiKeyAudits = ref([])
const apiKeyError = ref('')
const createdApiKeySecret = ref('')
const loading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const showCreateForm = ref(true)
const showGovernance = ref(false)
const health = ref(null)
// 工作区状态用于告知用户 Agent 是否直接连接到本地项目；接口不会返回绝对路径。
const workspace = ref(null)
// 已登记工作区是用户明确在桌面端授权的项目；选择只影响后续创建的会话。
const localWorkspaces = ref([])
const newConversationWorkspaceId = ref('')
const desktopWorkspacePicking = ref(false)
// Electron 原生文件夹拖入会创建新的固定工作区会话，不与浏览器附件导入混淆。
const desktopWorkspaceDropping = ref(false)
const runStatusFilter = ref('')
const runsLoading = ref(false)
const runPage = reactive({
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
})
const runStatusOptions = ['QUEUED', 'RUNNING', 'WAITING_APPROVAL', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT']
let runPageRequest
// 主题设置会保存在浏览器中，刷新页面后继续使用上次选择。
const THEME_STORAGE_KEY = 'harnessTheme'
const theme = ref(readTheme())
let runPollTimer
let healthPollTimer
// 聊天工作台状态：每轮消息对应一个后端 Run，助手气泡由 Run 终态回写。
const chatMode = ref(true)
const activeConsoleSection = ref('runtime')
const conversations = ref([])
const activeConversation = ref(null)
const chatInput = ref('')
const chatLoading = ref(false)
const chatSending = ref(false)
// 文件仅在点击发送时才上传，切换会话不会在后端留下未绑定的附件。
const chatAttachments = ref([])
const chatUploading = ref(false)
const chatDragActive = ref(false)
const chatAttachmentInput = ref(null)
const chatFolderInput = ref(null)
const showChatRun = ref(false)
// 项目文件面板仅浏览当前会话已绑定的工作区，不会把绝对路径带到前端。
const showChatWorkspace = ref(false)
const workspaceExplorer = ref(null)
const workspaceExplorerLoading = ref(false)
const workspaceFilePreview = ref(null)
const workspaceFilePreviewLoading = ref(false)
// Git 审阅沿用当前会话的工作区绑定，避免把磁盘路径或自由 Git 参数暴露给页面。
const workspaceGitReviewVisible = ref(false)
const workspaceGitStatus = ref(null)
const workspaceGitStatusLoading = ref(false)
const workspaceGitDiff = ref(null)
const workspaceGitDiffLoading = ref(false)
// 实时流只订阅当前查看的非终态 Run；HTTP 轮询仍用于网络异常后的兜底校验。
const runEventStreaming = ref(false)
let conversationPollTimer
let chatHighlightTimer
let runEventAbortController
let runEventReconnectTimer
let workspaceExplorerLoadToken = 0
let workspacePreviewLoadToken = 0
let workspaceGitStatusLoadToken = 0
let workspaceGitDiffLoadToken = 0
// 记录连接所属 Run，避免聊天轮询读取到同一任务时重复中断并创建 SSE 连接。
let runEventStreamRunId

function readTheme() {
  if (typeof window === 'undefined') return 'dark'
  try {
    const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
    if (storedTheme === 'light' || storedTheme === 'dark') return storedTheme
  } catch {
    // 浏览器禁用本地存储时使用默认的黑夜模式。
  }
  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

function applyTheme(nextTheme) {
  if (typeof document !== 'undefined') {
    document.documentElement.dataset.theme = nextTheme
    document.querySelector('meta[name="theme-color"]')?.setAttribute(
      'content',
      nextTheme === 'dark' ? '#080d1a' : '#f5f7fb',
    )
  }
  if (typeof window !== 'undefined') {
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, nextTheme)
    } catch {
      // 浏览器禁用本地存储时仍然允许本次会话切换主题。
    }
  }
}

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  applyTheme(theme.value)
}

function setActiveConsoleSection(section) {
  activeConsoleSection.value = section
}

function syncActiveConsoleSectionFromHash() {
  const section = window.location.hash.slice(1)
  activeConsoleSection.value = ['runtime', 'tools', 'audit'].includes(section) ? section : 'runtime'
}

// 在首屏渲染前同步主题，避免切换时出现短暂的错误背景色。
applyTheme(theme.value)

const form = reactive({
  tenantId: import.meta.env.VITE_HARNESS_TENANT_ID || 'tenant-demo',
  userId: import.meta.env.VITE_HARNESS_USER_ID || 'operator',
  title: '订单状态分析',
  input: '请分析这条任务并返回可追溯结果',
  toolName: 'demo.echo',
  modelName: '',
  promptVersion: 'prompt-v1',
  policyVersion: 'policy-v1',
  budget: 1,
  idempotencyKey: '',
  permissions: '',
  agentMode: false,
  maxTurns: 8,
})

const documentForm = reactive({
  title: '订单处理规则',
  content: '订单状态变更必须经过审核，并保留操作来源。',
  sensitivity: 'INTERNAL',
  allowedUsers: '',
})

const evaluationForm = reactive({
  name: '控制台快速回归',
  input: '请分析订单状态',
  expectedContains: '请分析订单状态',
})

const tenantPolicyForm = reactive({
  maxActiveRuns: 20,
  maxStepsPerRun: 20,
  maxInputLength: 10000,
  maxBudget: 1000,
  maxCreatesPerMinute: 60,
  allowedTools: '',
})

// 创建表单只保存过期时间和权限，生成的明文密钥不会写入浏览器存储。
const apiKeyForm = reactive({
  tenantId: form.tenantId,
  userId: form.userId,
  permissions: 'run.read, run.create, run.execute, run.approve, run.cancel, audit.read, context.read, context.write, evaluation.read, evaluation.run, tool.read, workspace.read, workspace.manage, ops.read, tenant.policy.read, tenant.policy.write, auth.key.read, auth.key.manage',
  expiresAt: '',
})

const stats = computed(() => ({
  total: summary.value?.total ?? runs.value.length,
  queued: summary.value?.queued ?? runs.value.filter((run) => run.status === 'QUEUED').length,
  running: summary.value?.running ?? runs.value.filter((run) => run.status === 'RUNNING').length,
  succeeded: summary.value?.succeeded ?? runs.value.filter((run) => run.status === 'SUCCEEDED').length,
  failed: summary.value?.failed ?? runs.value.filter((run) => run.status === 'FAILED').length,
}))

const selectedStatus = computed(() => selectedRun.value?.run?.status || 'NONE')
const canStart = computed(() => selectedStatus.value === 'QUEUED')
const canCancel = computed(() => ['QUEUED', 'RUNNING'].includes(selectedStatus.value))
const canApprove = computed(() => selectedStatus.value === 'WAITING_APPROVAL')
const canRetry = computed(() => ['FAILED', 'TIMED_OUT'].includes(selectedStatus.value))
const infraOnline = computed(() => health.value?.status === 'UP')
const infraLabel = computed(() => {
  if (!health.value) return '检查基础设施'
  if (health.value.error) return health.value.error
  return infraOnline.value ? '基础设施在线' : '基础设施异常'
})
const workerLabel = computed(() => {
  const runtime = health.value?.runtime
  if (!runtime || !runtime.workerConcurrencyLimit) return 'Worker 同步执行'
  return `Worker ${runtime.activeWorkers}/${runtime.workerConcurrencyLimit}`
})
const workerHealthClass = computed(() => {
  const runtime = health.value?.runtime
  if (!runtime || !runtime.workerConcurrencyLimit) return 'health-unknown'
  return runtime.activeWorkers >= runtime.workerConcurrencyLimit ? 'health-warning' : 'health-up'
})
const queueLabel = computed(() => {
  const runtime = health.value?.runtime
  if (!runtime || runtime.queueDepth == null || runtime.queueCapacity == null) return '队列 未监控'
  return `队列 ${runtime.queueDepth} · 余量 ${runtime.queueCapacity}`
})
const queueHealthClass = computed(() => {
  const runtime = health.value?.runtime
  if (!runtime || runtime.queueDepth == null || runtime.queueCapacity == null) return 'health-unknown'
  return runtime.queueCapacity === 0 ? 'health-warning' : 'health-up'
})
const runtimeAlerts = computed(() => {
  const runtime = health.value?.runtime
  if (!runtime) return []
  return [
    runtime.pendingOutbox > 0 && { level: 'info', label: `待投递消息 ${runtime.pendingOutbox}` },
    runtime.rabbitRetryCount > 0 && { level: 'warning', label: `Rabbit 重试 ${runtime.rabbitRetryCount}` },
    runtime.rabbitDeadLetterCount > 0 && { level: 'danger', label: `Rabbit 死信 ${runtime.rabbitDeadLetterCount}` },
    runtime.timedOutRunCount > 0 && { level: 'warning', label: `运行超时 ${runtime.timedOutRunCount}` },
  ].filter(Boolean)
})
const desktopWorkspaceAvailable = computed(() => api.isDesktop())
const activeConversationWorkspaceId = computed(() => activeConversation.value?.conversation?.workspaceId || '')
const activeRegisteredWorkspace = computed(() => localWorkspaces.value
  .find((item) => item.id === activeConversationWorkspaceId.value) || null)
const workspaceConnected = computed(() => activeRegisteredWorkspace.value
  ? activeRegisteredWorkspace.value.accessible
  : Boolean(workspace.value?.enabled && workspace.value?.accessible))
const workspaceLabel = computed(() => {
  if (activeRegisteredWorkspace.value) return activeRegisteredWorkspace.value.displayName
  if (activeConversationWorkspaceId.value) return '已授权工作区不可访问'
  if (!workspace.value) return '正在检查本地工作区'
  return workspace.value.displayName || (workspaceConnected.value ? '本地工作区' : '未连接本地工作区')
})
const workspaceDetail = computed(() => {
  if (activeRegisteredWorkspace.value) {
    return `${activeRegisteredWorkspace.value.gitRepository ? 'Git 项目' : '本地目录'} · 会话已固定绑定`
  }
  if (activeConversationWorkspaceId.value) return '授权目录不可访问，请重新选择本地项目'
  if (!workspace.value) return '正在验证本地 Agent 权限'
  if (!workspace.value.enabled) return '工作区工具未启用'
  if (!workspace.value.accessible) return '目录不可访问，请检查本地配置'
  const parts = [workspace.value.gitRepository ? 'Git 项目' : '本地目录']
  parts.push(workspace.value.commandExecutionEnabled
    ? `命令已启用（${workspace.value.allowedCommandCount || 0} 项白名单）`
    : '命令已关闭')
  return parts.join(' · ')
})
const workspaceStatusClass = computed(() => workspaceConnected.value ? 'workspace-connected' : 'workspace-disconnected')
const workspaceExplorerAvailable = computed(() => workspaceConnected.value && Boolean(activeConversationId.value))
const workspaceExplorerPath = computed(() => workspaceExplorer.value?.path || '.')
const workspaceGitAvailable = computed(() => Boolean(workspaceExplorer.value?.git?.available))
const runPageLabel = computed(() => {
  if (!runPage.totalElements) return '0 条记录'
  return `第 ${runPage.page + 1} / ${runPage.totalPages} 页 · 共 ${runPage.totalElements} 条`
})
const canPreviousRunPage = computed(() => runPage.page > 0)
const canNextRunPage = computed(() => runPage.hasNext)
const chatMessages = computed(() => activeConversation.value?.messages || [])
const activeConversationId = computed(() => activeConversation.value?.conversation?.id || '')
const pendingChatMessage = computed(() => chatMessages.value
  .slice().reverse()
  .find((message) => message.role === 'ASSISTANT' && message.status === 'PENDING'))
const chatRunStatus = computed(() => {
  const runId = pendingChatMessage.value?.runId
  if (runId && selectedRun.value?.run?.id === runId) return selectedRun.value.run.status
  return pendingChatMessage.value ? 'RUNNING' : ''
})
const chatUserMessages = computed(() => chatMessages.value
  .filter((message) => message.role === 'USER'))
const canSendChat = computed(() => Boolean(activeConversationId.value) && !chatSending.value && !chatUploading.value
  && !pendingChatMessage.value
  && (chatInput.value.trim().length > 0 || chatAttachments.value.length > 0))
// 变更预览只读取已经持久化到 Step 的工具参数，不向后端额外发送代码正文。
const workspaceChangePreviews = computed(() => (selectedRun.value?.steps || [])
  .map(workspaceChangePreview)
  .filter(Boolean))
const pendingWorkspaceChangePreviews = computed(() => workspaceChangePreviews.value
  .filter((change) => change.status === 'WAITING_APPROVAL'))

function isTerminal(status) {
  return ['SUCCEEDED', 'FAILED', 'CANCELLED', 'TIMED_OUT'].includes(status)
}

function healthStatus(name) {
  const component = health.value?.components?.[name]
  return typeof component === 'string' ? component : component?.status || '—'
}

function healthClass(name) {
  const status = healthStatus(name)
  return status === 'UP' ? 'health-up' : status === '—' ? 'health-unknown' : 'health-down'
}

function statusLabel(status) {
  const labels = {
    QUEUED: '排队中',
    RUNNING: '执行中',
    SUCCEEDED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
    TIMED_OUT: '超时',
    WAITING_APPROVAL: '等待审批',
    NONE: '未选择',
  }
  return labels[status] || status
}

function statusClass(status) {
  return `status-${String(status || 'none').toLowerCase()}`
}

function apiKeyStatusLabel(status) {
  return {
    ACTIVE: '有效',
    REVOKED: '已撤销',
  }[status] || status || '未知'
}

function apiKeyStatusClass(status) {
  return `api-key-status-${String(status || 'unknown').toLowerCase()}`
}

function stepLabel(type) {
  return { MODEL: '模型', TOOL: '工具', APPROVAL: '审批' }[type] || type
}

function decodeAgentStep(step) {
  const fallback = { content: step?.output || '', toolCalls: [] }
  if (!step || step.type !== 'MODEL' || !selectedRun.value?.run?.agentMode || !step.output) return fallback
  try {
    const parsed = JSON.parse(step.output)
    if (!parsed || typeof parsed !== 'object' || !Array.isArray(parsed.toolCalls)) return fallback
    return {
      content: typeof parsed.content === 'string' ? parsed.content : '',
      toolCalls: parsed.toolCalls.filter((call) => call && typeof call.name === 'string'),
    }
  } catch {
    return fallback
  }
}

function runModeLabel(run) {
  return run?.agentMode ? `代码 Agent · 最多 ${run.maxTurns || '—'} 轮` : '单轮执行'
}

function decodeWorkspaceExec(step) {
  if (!step || step.name !== 'workspace.exec' || !step.output) return null
  try {
    const parsed = JSON.parse(step.output)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

function decodeWorkspaceEdit(step) {
  if (!step || step.name !== 'workspace.edit' || !step.output) return null
  try {
    const parsed = JSON.parse(step.output)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

function decodeWorkspaceGitStatus(step) {
  if (!step || step.name !== 'workspace.git.status' || !step.output) return null
  try {
    const parsed = JSON.parse(step.output)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

function decodeWorkspaceGitDiff(step) {
  if (!step || step.name !== 'workspace.git.diff' || !step.output) return null
  try {
    const parsed = JSON.parse(step.output)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

function decodeToolInput(step) {
  if (!step?.input) return null
  try {
    const parsed = JSON.parse(step.input)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

function clipCodePreview(value, maximum = 900) {
  if (typeof value !== 'string') return ''
  if (value.length <= maximum) return value
  return `${value.slice(0, maximum)}\n…（已截断 ${value.length - maximum} 个字符）`
}

// 编辑工具在真正执行前就会保存参数，因此 WAITING_APPROVAL 阶段也能安全预览。
function workspaceChangePreview(step) {
  if (!step || !['workspace.edit', 'workspace.write'].includes(step.name)) return null
  const input = decodeToolInput(step)
  if (!input || typeof input.path !== 'string' || !input.path) return null
  if (step.name === 'workspace.edit') {
    const requestedEdits = Array.isArray(input.edits) ? input.edits : []
    const visibleEdits = requestedEdits.slice(0, 6).map((edit) => ({
      oldText: clipCodePreview(edit?.oldText, 500),
      newText: clipCodePreview(edit?.newText, 500),
      replaceAll: Boolean(edit?.replaceAll),
    }))
    return {
      stepId: step.id,
      status: step.status,
      path: input.path,
      typeLabel: '精确编辑',
      kind: 'edit',
      edits: visibleEdits,
      hiddenEditCount: Math.max(0, requestedEdits.length - visibleEdits.length),
    }
  }
  return {
    stepId: step.id,
    status: step.status,
    path: input.path,
    typeLabel: '整文件写入',
    kind: 'write',
    content: clipCodePreview(input.content, 1800),
  }
}

function formatDate(value) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function clearMessages() {
  errorMessage.value = ''
  noticeMessage.value = ''
}

function errorText(error) {
  if (!error) return '请求失败'
  return error.traceId ? `${error.message}（追踪 ID：${error.traceId}）` : error.message
}

function messageStatusLabel(status) {
  return {
    PENDING: 'Agent 执行中',
    COMPLETED: '已完成',
    FAILED: '执行失败',
    CANCELLED: '已取消',
  }[status] || status || ''
}

function messageStatusClass(status) {
  return `message-status-${String(status || 'unknown').toLowerCase()}`
}

function attachmentLabel(attachment) {
  if (!attachment) return '已附加文件'
  return attachment.originalName || attachment.workspacePath || '已附加文件'
}

function messageNavigationLabel(message) {
  const content = String(message?.content || '').trim()
  if (content) return content.length > 30 ? `${content.slice(0, 30)}…` : content
  return attachmentLabel(message?.attachments?.[0])
}

function formatFileSize(size) {
  const value = Number(size || 0)
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${Math.ceil(value / 1024)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

function clearChatAttachments() {
  chatAttachments.value = []
  chatDragActive.value = false
  if (chatAttachmentInput.value) chatAttachmentInput.value.value = ''
  if (chatFolderInput.value) chatFolderInput.value.value = ''
}

function isBrowserFile(file) {
  return typeof File !== 'undefined' && file instanceof File
}

function normalizeChatAttachmentPath(path, fallbackName) {
  const value = String(path || fallbackName || '').replaceAll('\\', '/').replace(/^\/+/, '')
  const segments = value.split('/').filter((segment) => segment && segment !== '.' && segment !== '..')
  return segments.join('/') || fallbackName
}

function createChatAttachmentGroups(entries) {
  const groups = new Map()
  for (const entry of entries || []) {
    if (!isBrowserFile(entry?.file) || entry.file.size <= 0) continue
    const path = normalizeChatAttachmentPath(entry.path || entry.file.webkitRelativePath, entry.file.name)
    const root = path.split('/')[0]
    if (!root) continue
    const group = groups.get(root) || { name: root, directory: false, files: [], size: 0, key: '' }
    group.directory = group.directory || path.includes('/')
    group.files.push({ file: entry.file, path })
    group.size += entry.file.size
    groups.set(root, group)
  }
  return Array.from(groups.values()).map((group) => ({
    ...group,
    fileCount: group.files.length,
    key: group.files.map((item) => `${item.path}:${item.file.size}:${item.file.lastModified}`).sort().join('|'),
  }))
}

function addChatAttachments(entries) {
  const incoming = createChatAttachmentGroups(entries)
  if (!incoming.length) return
  const current = [...chatAttachments.value]
  const previousCount = current.length
  for (const attachment of incoming) {
    const duplicate = current.some((item) => item.key === attachment.key)
    if (!duplicate && current.length < 8) current.push(attachment)
  }
  chatAttachments.value = current
  if (current.length < previousCount + incoming.length) {
    noticeMessage.value = '每轮最多附加 8 个文件或文件夹，超出的内容未加入。'
  }
}

function openChatAttachmentPicker() {
  if (!activeConversationId.value || chatSending.value || chatUploading.value) return
  chatAttachmentInput.value?.click()
}

function openChatFolderPicker() {
  if (!activeConversationId.value || chatSending.value || chatUploading.value) return
  chatFolderInput.value?.click()
}

function handleChatAttachmentInput(event) {
  addChatAttachments(Array.from(event.target?.files || []).map((file) => ({ file, path: file.name })))
  // 允许移除后再次选择同一个文件。
  event.target.value = ''
}

function handleChatFolderInput(event) {
  addChatAttachments(Array.from(event.target?.files || []).map((file) => ({
    file,
    path: file.webkitRelativePath || file.name,
  })))
  event.target.value = ''
}

async function handleChatDrop(event) {
  chatDragActive.value = false
  if (!activeConversationId.value || chatSending.value || chatUploading.value) return
  if (isDesktopWorkspaceDirectoryDrop(event.dataTransfer)) {
    desktopWorkspaceDropping.value = true
    noticeMessage.value = '正在授权拖入的本地项目，随后会创建绑定该项目的新会话…'
    return
  }
  try {
    addChatAttachments(await readDroppedChatEntries(event.dataTransfer))
  } catch {
    errorMessage.value = '读取拖入的文件夹失败，请使用“文件夹”按钮选择。'
  }
}

/** Electron 目录拖拽获得原项目授权；浏览器环境或普通文件继续使用安全的复制导入。 */
function isDesktopWorkspaceDirectoryDrop(dataTransfer) {
  if (!desktopWorkspaceAvailable.value) return false
  return Array.from(dataTransfer?.items || []).some((item) => {
    const entry = item.webkitGetAsEntry?.()
    return Boolean(entry?.isDirectory)
  })
}

async function readDroppedChatEntries(dataTransfer) {
  const items = Array.from(dataTransfer?.items || [])
  const entries = items.map((item) => item.webkitGetAsEntry?.()).filter(Boolean)
  if (!entries.length) {
    return Array.from(dataTransfer?.files || []).map((file) => ({
      file,
      path: file.webkitRelativePath || file.name,
    }))
  }
  const groups = await Promise.all(entries.map((entry) => readDroppedEntry(entry)))
  return groups.flat()
}

async function readDroppedEntry(entry, parentPath = '') {
  const path = `${parentPath}${entry.name}`
  if (entry.isFile) {
    const file = await new Promise((resolve, reject) => entry.file(resolve, reject))
    return [{ file, path }]
  }
  if (!entry.isDirectory) return []
  const children = await readAllDirectoryEntries(entry.createReader())
  const groups = await Promise.all(children.map((child) => readDroppedEntry(child, `${path}/`)))
  return groups.flat()
}

function readAllDirectoryEntries(reader, entries = []) {
  return new Promise((resolve, reject) => {
    const readNext = () => reader.readEntries((batch) => {
      if (!batch.length) {
        resolve(entries)
        return
      }
      entries.push(...batch)
      readNext()
    }, reject)
    readNext()
  })
}

function removeChatAttachment(index) {
  chatAttachments.value = chatAttachments.value.filter((_, itemIndex) => itemIndex !== index)
}

function jumpToChatMessage(messageId) {
  const target = document.getElementById(`chat-message-${messageId}`)
  if (!target) return
  target.scrollIntoView({ behavior: 'smooth', block: 'start' })
  target.classList.add('chat-message-highlighted')
  window.clearTimeout(chatHighlightTimer)
  chatHighlightTimer = window.setTimeout(() => target.classList.remove('chat-message-highlighted'), 1600)
}

function latestConversationRun(detail) {
  return detail?.messages?.slice().reverse().find((message) => message.runId)?.runId || ''
}

function pendingConversationAssistant(detail, runId) {
  return detail?.messages?.find((message) => message.runId === runId && message.role === 'ASSISTANT') || null
}

function latestStreamingModelContent(detail) {
  const step = detail?.steps?.slice().reverse().find((item) => item.type === 'MODEL' && item.status === 'RUNNING')
  if (!step?.output) return ''
  if (!detail?.run?.agentMode) return step.output
  return decodeAgentStep(step).content
}

function applyStreamingAssistantContent(runId, detail) {
  if (!activeConversation.value || latestConversationRun(activeConversation.value) !== runId) return
  const content = latestStreamingModelContent(detail)
  if (!content) return
  const message = pendingConversationAssistant(activeConversation.value, runId)
  if (message) message.content = content
}

async function loadConversations(preferredId = '') {
  chatLoading.value = true
  try {
    conversations.value = await api.listConversations()
    if (!conversations.value.length) {
      const created = await api.createConversation(newConversationPayload())
      conversations.value = [created.conversation]
      activeConversation.value = created
      return
    }
    const targetId = preferredId || activeConversationId.value || conversations.value[0].id
    await selectConversation(targetId, false)
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    chatLoading.value = false
  }
}

async function createChatConversation() {
  clearMessages()
  try {
    const created = await api.createConversation(newConversationPayload())
    conversations.value = [created.conversation, ...conversations.value.filter((item) => item.id !== created.conversation.id)]
    activeConversation.value = created
    chatInput.value = ''
    clearChatAttachments()
    stopRunEventStream()
    selectedRun.value = null
    auditEvents.value = []
    resetWorkspaceExplorerState()
    return created
  } catch (error) {
    errorMessage.value = errorText(error)
    return null
  }
}

/** 新会话创建后工作区即冻结，避免用户后续切换项目时影响正在执行的 Agent。 */
function newConversationPayload() {
  return {
    title: '新的对话',
    ...(newConversationWorkspaceId.value ? { workspaceId: newConversationWorkspaceId.value } : {}),
  }
}

async function loadLocalWorkspaces() {
  try {
    localWorkspaces.value = await api.listLocalWorkspaces()
    if (!newConversationWorkspaceId.value) {
      newConversationWorkspaceId.value = localWorkspaces.value.find((item) => item.accessible)?.id || ''
    }
  } catch {
    // 缺少 workspace.read 或未启用桌面模式时不阻断普通聊天能力。
    localWorkspaces.value = []
  }
}

async function chooseDesktopWorkspace() {
  if (!desktopWorkspaceAvailable.value || desktopWorkspacePicking.value) return
  clearMessages()
  desktopWorkspacePicking.value = true
  try {
    const result = await api.pickDesktopWorkspace()
    if (result?.cancelled) return
    if (!result?.workspace?.id) throw new Error('桌面桥接没有返回工作区摘要')
    await loadLocalWorkspaces()
    newConversationWorkspaceId.value = result.workspace.id
    const created = await createChatConversation()
    if (!created) return
    showChatWorkspace.value = true
    await loadWorkspaceDirectory('.')
    noticeMessage.value = `已授权本地项目“${result.workspace.displayName}”，已创建独立会话。`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    desktopWorkspacePicking.value = false
  }
}

/** preload 不返回绝对路径；收到工作区摘要后立即新建会话以冻结 Agent 的项目边界。 */
async function handleDesktopWorkspaceDropped(result) {
  desktopWorkspaceDropping.value = false
  if (result?.error) {
    errorMessage.value = result.error
    return
  }
  if (!result?.workspace?.id) return
  try {
    await loadLocalWorkspaces()
    newConversationWorkspaceId.value = result.workspace.id
    const created = await createChatConversation()
    if (!created) return
    showChatWorkspace.value = true
    await loadWorkspaceDirectory('.')
    noticeMessage.value = `已授权拖入的本地项目“${result.workspace.displayName}”，已创建独立会话。`
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

/** 打开或关闭当前会话的只读项目浏览器；运行详情与文件浏览共用右侧检查面板。 */
async function toggleWorkspaceExplorer() {
  if (!workspaceExplorerAvailable.value) return
  showChatWorkspace.value = !showChatWorkspace.value
  if (!showChatWorkspace.value) return
  showChatRun.value = false
  await loadWorkspaceDirectory('.')
}

/** 运行详情与文件浏览共用右侧检查位，打开执行步骤时收起项目文件，避免布局挤压到下一行。 */
async function toggleRunPanel() {
  const nextVisible = !showChatRun.value
  showChatWorkspace.value = false
  showChatRun.value = nextVisible
}

/** 从消息跳转到某个 Run 时强制占用右侧检查位，避免和项目文件面板同时渲染。 */
async function openRunPanel(runId, announce = false) {
  if (!runId) return
  showChatWorkspace.value = false
  showChatRun.value = true
  await selectRun(runId, announce, false)
}

/** 展开 Git 审阅时才请求变更明细，普通目录浏览不会额外运行 Git 命令。 */
async function toggleWorkspaceGitReview() {
  if (!workspaceExplorerAvailable.value || !workspaceGitAvailable.value) return
  workspaceGitReviewVisible.value = !workspaceGitReviewVisible.value
  if (!workspaceGitReviewVisible.value) return
  await loadWorkspaceGitStatus()
}

/** 请求始终携带当前会话绑定的 workspaceId，后端会再次验证所属租户和用户。 */
async function loadWorkspaceDirectory(path = '.') {
  if (!workspaceExplorerAvailable.value) return
  const requestToken = ++workspaceExplorerLoadToken
  // 切换目录时使旧文件预览失效，避免异步响应把其他目录或会话的内容覆盖到面板。
  workspacePreviewLoadToken += 1
  workspaceExplorerLoading.value = true
  workspaceFilePreview.value = null
  try {
    const result = await api.browseWorkspaceFiles({
      workspaceId: activeConversationWorkspaceId.value,
      path,
    })
    if (requestToken === workspaceExplorerLoadToken) workspaceExplorer.value = result
  } catch (error) {
    if (requestToken === workspaceExplorerLoadToken) {
      workspaceExplorer.value = null
      errorMessage.value = errorText(error)
    }
  } finally {
    if (requestToken === workspaceExplorerLoadToken) workspaceExplorerLoading.value = false
  }
}

/** 只读状态接口用于列出变更文件；请求 token 防止切换会话后展示旧项目结果。 */
async function loadWorkspaceGitStatus() {
  if (!workspaceExplorerAvailable.value || !workspaceGitAvailable.value) return
  const requestToken = ++workspaceGitStatusLoadToken
  workspaceGitStatusLoading.value = true
  workspaceGitDiffLoadToken += 1
  workspaceGitDiff.value = null
  try {
    const result = await api.workspaceGitStatus({ workspaceId: activeConversationWorkspaceId.value })
    if (requestToken === workspaceGitStatusLoadToken) workspaceGitStatus.value = result
  } catch (error) {
    if (requestToken === workspaceGitStatusLoadToken) {
      workspaceGitStatus.value = null
      errorMessage.value = errorText(error)
    }
  } finally {
    if (requestToken === workspaceGitStatusLoadToken) workspaceGitStatusLoading.value = false
  }
}

/** 在文件级别查看已暂存或工作区 Diff，后端会继续限制上下文行数和输出大小。 */
async function loadWorkspaceGitDiff(path, staged = false) {
  if (!workspaceExplorerAvailable.value || !workspaceGitAvailable.value || !path) return
  const requestToken = ++workspaceGitDiffLoadToken
  workspaceGitDiffLoading.value = true
  try {
    const result = await api.workspaceGitDiff({
      workspaceId: activeConversationWorkspaceId.value,
      path,
      staged,
      contextLines: 3,
    })
    if (requestToken === workspaceGitDiffLoadToken) workspaceGitDiff.value = result
  } catch (error) {
    if (requestToken === workspaceGitDiffLoadToken) errorMessage.value = errorText(error)
  } finally {
    if (requestToken === workspaceGitDiffLoadToken) workspaceGitDiffLoading.value = false
  }
}

/** 未跟踪文件没有标准 Git diff；仍可使用既有的受限文本预览核验内容。 */
function previewUntrackedWorkspaceFile(change) {
  if (!change?.path) return
  previewWorkspaceFile({ path: change.path, directory: false })
}

function resetWorkspaceExplorerState({ keepPanel = false } = {}) {
  workspaceExplorerLoadToken += 1
  workspacePreviewLoadToken += 1
  workspaceGitStatusLoadToken += 1
  workspaceGitDiffLoadToken += 1
  workspaceExplorer.value = null
  workspaceFilePreview.value = null
  workspaceGitStatus.value = null
  workspaceGitDiff.value = null
  workspaceGitReviewVisible.value = false
  if (!keepPanel) showChatWorkspace.value = false
}

function gitChangeLabel(change) {
  const index = change?.index || ' '
  const worktree = change?.worktree || ' '
  if (index === '?' && worktree === '?') return '未跟踪'
  if (index === 'D' || worktree === 'D') return '删除'
  if (index === 'A' || worktree === 'A') return '新增'
  if (index === 'R' || worktree === 'R') return '改名'
  if (index !== ' ' && worktree !== ' ') return '已暂存 + 修改'
  return index !== ' ' ? '已暂存' : '已修改'
}

function gitChangeClass(change) {
  const label = gitChangeLabel(change)
  if (label === '删除') return 'git-change-delete'
  if (label === '新增' || label === '未跟踪') return 'git-change-add'
  return 'git-change-modified'
}

async function previewWorkspaceFile(entry) {
  if (!entry || entry.directory || !workspaceExplorerAvailable.value) return
  const requestToken = ++workspacePreviewLoadToken
  workspaceFilePreviewLoading.value = true
  try {
    const result = await api.readWorkspaceFile({
      workspaceId: activeConversationWorkspaceId.value,
      path: entry.path,
    })
    if (requestToken === workspacePreviewLoadToken) workspaceFilePreview.value = result
  } catch (error) {
    if (requestToken === workspacePreviewLoadToken) errorMessage.value = errorText(error)
  } finally {
    if (requestToken === workspacePreviewLoadToken) workspaceFilePreviewLoading.value = false
  }
}

async function selectConversation(conversationId, announce = true) {
  if (!conversationId) return
  if (announce) clearMessages()
  if (conversationId !== activeConversationId.value) {
    clearChatAttachments()
  }
  chatLoading.value = true
  try {
    const detail = await api.getConversation(conversationId)
    activeConversation.value = detail
    const runId = latestConversationRun(detail)
    if (runId) await selectRun(runId, false, false)
    else {
      stopRunEventStream()
      selectedRun.value = null
      auditEvents.value = []
    }
    const shouldReloadWorkspace = showChatWorkspace.value
    resetWorkspaceExplorerState({ keepPanel: shouldReloadWorkspace })
    if (showChatWorkspace.value) void loadWorkspaceDirectory('.')
    scrollChatToBottom()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    chatLoading.value = false
  }
}

function scrollChatToBottom() {
  if (typeof window === 'undefined') return
  window.requestAnimationFrame(() => {
    const element = document.querySelector('.chat-messages')
    if (element) element.scrollTop = element.scrollHeight
  })
}

async function sendChatMessage() {
  if (!canSendChat.value) return
  clearMessages()
  chatSending.value = true
  const conversationId = activeConversationId.value
  const typedContent = chatInput.value.trim()
  const content = typedContent || '请读取并处理已附加到工作区的文件。'
  const attachments = [...chatAttachments.value]
  const files = attachments.flatMap((attachment) => attachment.files)
  let uploadedAttachments = []
  let messageSubmitted = false
  const clientMessageId = `local-user-${crypto.randomUUID?.() || Date.now()}`
  const clientAssistantId = `local-assistant-${crypto.randomUUID?.() || Date.now()}`
  const sentAt = new Date().toISOString()
  const clientAttachments = attachments.map((attachment, index) => ({
    id: `local-attachment-${index}`,
    originalName: attachment.name,
    workspacePath: '',
    mediaType: '',
    directory: attachment.directory,
    sizeBytes: attachment.size,
    fileCount: attachment.fileCount || 1,
    createdAt: sentAt,
  }))
  chatInput.value = ''
  clearChatAttachments()
  if (activeConversation.value) {
    activeConversation.value = {
      ...activeConversation.value,
      messages: [
        ...activeConversation.value.messages,
        {
          id: clientMessageId,
          runId: '',
          role: 'USER',
          status: 'COMPLETED',
          sequence: activeConversation.value.messages.length + 1,
          content,
          attachments: clientAttachments,
          createdAt: sentAt,
          updatedAt: sentAt,
          local: true,
        },
        {
          id: clientAssistantId,
          runId: '',
          role: 'ASSISTANT',
          status: 'PENDING',
          sequence: activeConversation.value.messages.length + 2,
          content: '',
          attachments: [],
          createdAt: sentAt,
          updatedAt: sentAt,
          local: true,
        },
      ],
    }
    scrollChatToBottom()
  }
  try {
    if (files.length) {
      chatUploading.value = true
      uploadedAttachments = await api.uploadConversationAttachments(conversationId, files)
      chatUploading.value = false
    }
    const detail = await api.sendConversationMessage(conversationId, {
      content,
      maxTurns: 8,
      attachmentIds: uploadedAttachments.map((attachment) => attachment.id),
    }, `chat-${crypto.randomUUID?.() || Date.now()}`)
    messageSubmitted = true
    activeConversation.value = detail
    const runId = latestConversationRun(detail)
    if (runId) void selectRun(runId, false, false)
    void loadConversations(conversationId)
    scrollChatToBottom()
  } catch (error) {
    if (!messageSubmitted) {
      // 消息提交失败时尽力回收未绑定附件；回收失败不掩盖原始请求错误。
      await Promise.allSettled(uploadedAttachments.map((attachment) =>
        api.deleteConversationAttachment(conversationId, attachment.id)))
      chatInput.value = typedContent
      chatAttachments.value = attachments
      if (activeConversation.value?.conversation?.id === conversationId) {
        activeConversation.value = {
          ...activeConversation.value,
          messages: activeConversation.value.messages.filter((message) =>
            message.id !== clientMessageId && message.id !== clientAssistantId),
        }
      }
    }
    errorMessage.value = errorText(error)
  } finally {
    chatUploading.value = false
    chatSending.value = false
  }
}

async function pollConversation() {
  if (!activeConversationId.value || chatSending.value || !pendingChatMessage.value) return
  try {
    const detail = await api.getConversation(activeConversationId.value)
    activeConversation.value = detail
    const runId = latestConversationRun(detail)
    if (runId && selectedRun.value?.run?.id !== runId) {
      await selectRun(runId, false, false)
    }
    if (!detail.messages.some((message) => message.status === 'PENDING')) {
      await loadConversations(activeConversationId.value)
    }
    scrollChatToBottom()
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

// 审批、取消或重试从运行面板触发后，主动刷新会话，确保助手气泡与 Run 终态同步。
async function refreshActiveConversation() {
  const conversationId = activeConversationId.value
  if (!conversationId) return
  const detail = await api.getConversation(conversationId)
  activeConversation.value = detail
  const runId = latestConversationRun(detail)
  if (runId && selectedRun.value?.run?.id !== runId) {
    await selectRun(runId, false, false)
  }
  conversations.value = await api.listConversations()
  scrollChatToBottom()
}

async function loadDashboard() {
  clearMessages()
  try {
    const [, toolData, summaryData, documentData, evaluationData] = await Promise.all([
      loadRunsPage(),
      api.listTools(),
      api.dashboardSummary(),
      api.listDocuments(),
      api.listEvaluations(),
    ])
    tools.value = toolData
    summary.value = summaryData
    documents.value = documentData
    evaluations.value = evaluationData
    if (selectedRun.value) {
      await selectRun(selectedRun.value.run.id, false)
    } else if (runs.value.length) {
      await selectRun(runs.value[0].id, false)
    }
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

/** 加载当前分页，避免控制台一次性拉取全部 Run。 */
async function loadRunsPage() {
  if (runPageRequest) return runPageRequest

  runsLoading.value = true
  runPageRequest = (async () => {
    const requestPage = (page) => api.listRunsPage({
      page,
      size: runPage.size,
      status: runStatusFilter.value || undefined,
    })

    let pageData = await requestPage(runPage.page)
    // 删除最后一页数据或筛选条件变化后，自动回退到仍然存在的最后一页。
    if (pageData.totalPages === 0 && runPage.page !== 0) {
      runPage.page = 0
      pageData = await requestPage(0)
    } else if (pageData.totalPages > 0 && runPage.page >= pageData.totalPages) {
      runPage.page = pageData.totalPages - 1
      pageData = await requestPage(runPage.page)
    }

    runs.value = pageData.items || []
    runPage.page = pageData.page
    runPage.size = pageData.size
    runPage.totalElements = pageData.totalElements
    runPage.totalPages = pageData.totalPages
    runPage.hasNext = pageData.hasNext
    return pageData
  })()

  try {
    return await runPageRequest
  } finally {
    runPageRequest = undefined
    runsLoading.value = false
  }
}

async function changeRunStatusFilter() {
  clearMessages()
  runPage.page = 0
  try {
    await loadRunsPage()
    if (!selectedRun.value && runs.value.length) {
      await selectRun(runs.value[0].id, false)
    }
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function goToRunPage(delta) {
  if (runsLoading.value || !delta) return
  const nextPage = runPage.page + delta
  if (nextPage < 0 || (delta > 0 && !runPage.hasNext)) return

  clearMessages()
  runPage.page = nextPage
  try {
    await loadRunsPage()
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function loadHealth() {
  try {
    health.value = await api.health()
  } catch (error) {
    health.value = {
      status: 'UNKNOWN',
      components: {},
      error: error.code === 'PERMISSION_DENIED' ? '健康状态需要 ops.read 权限' : '健康检查暂不可用',
    }
  }
}

/** 工作区状态失败不影响聊天；权限不足时仍可使用不依赖本地文件的 Agent 能力。 */
async function loadWorkspace() {
  try {
    workspace.value = await api.workspace()
  } catch {
    workspace.value = {
      enabled: false,
      accessible: false,
      displayName: '本地工作区状态不可读取',
      commandExecutionEnabled: false,
      allowedCommandCount: 0,
    }
  }
}

async function loadTenantPolicy() {
  tenantPolicyError.value = ''
  try {
    localStorage.setItem('harnessTenantId', form.tenantId)
    const [policy, audits] = await Promise.all([
      api.getTenantPolicy(form.tenantId),
      api.listTenantPolicyAudits(form.tenantId),
    ])
    tenantPolicy.value = policy
    tenantPolicyAudits.value = audits || []
    Object.assign(tenantPolicyForm, {
      maxActiveRuns: policy.maxActiveRuns,
      maxStepsPerRun: policy.maxStepsPerRun,
      maxInputLength: policy.maxInputLength,
      maxBudget: Number(policy.maxBudget),
      maxCreatesPerMinute: policy.maxCreatesPerMinute,
      allowedTools: (policy.allowedTools || []).join(', '),
    })
  } catch (error) {
    tenantPolicy.value = null
    tenantPolicyAudits.value = []
    tenantPolicyError.value = error.code === 'PERMISSION_DENIED'
      ? '当前身份缺少 tenant.policy.read/write 权限'
      : errorText(error)
  }
}

async function saveTenantPolicy() {
  clearMessages()
  loading.value = true
  try {
    localStorage.setItem('harnessTenantId', form.tenantId)
    tenantPolicy.value = await api.updateTenantPolicy(form.tenantId, {
      maxActiveRuns: Number(tenantPolicyForm.maxActiveRuns),
      maxStepsPerRun: Number(tenantPolicyForm.maxStepsPerRun),
      maxInputLength: Number(tenantPolicyForm.maxInputLength),
      maxBudget: Number(tenantPolicyForm.maxBudget),
      maxCreatesPerMinute: Number(tenantPolicyForm.maxCreatesPerMinute),
      allowedTools: String(tenantPolicyForm.allowedTools || '').split(',').map((item) => item.trim()).filter(Boolean),
    })
    tenantPolicyAudits.value = await api.listTenantPolicyAudits(form.tenantId)
    noticeMessage.value = '租户资源策略已保存，新的 Run 会立即使用最新限制'
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function resetTenantPolicy() {
  clearMessages()
  loading.value = true
  try {
    localStorage.setItem('harnessTenantId', form.tenantId)
    tenantPolicy.value = await api.resetTenantPolicy(form.tenantId)
    tenantPolicyAudits.value = await api.listTenantPolicyAudits(form.tenantId)
    Object.assign(tenantPolicyForm, {
      maxActiveRuns: tenantPolicy.value.maxActiveRuns,
      maxStepsPerRun: tenantPolicy.value.maxStepsPerRun,
      maxInputLength: tenantPolicy.value.maxInputLength,
      maxBudget: Number(tenantPolicy.value.maxBudget),
      maxCreatesPerMinute: tenantPolicy.value.maxCreatesPerMinute,
      allowedTools: (tenantPolicy.value.allowedTools || []).join(', '),
    })
    noticeMessage.value = '租户策略已恢复为平台默认值'
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

/** 加载 API Key 元数据和生命周期审计，明文 secret 不会由列表接口返回。 */
async function loadApiKeys() {
  apiKeyError.value = ''
  const tenantId = apiKeyForm.tenantId.trim() || form.tenantId
  try {
    const [keys, audits] = await Promise.all([
      api.listApiKeys(tenantId),
      api.listApiKeyAudits(tenantId),
    ])
    apiKeys.value = keys || []
    apiKeyAudits.value = audits || []
  } catch (error) {
    apiKeys.value = []
    apiKeyAudits.value = []
    apiKeyError.value = error.code === 'PERMISSION_DENIED'
      ? '当前身份缺少 auth.key.read 或 auth.key.manage 权限'
      : errorText(error)
  }
}

function parseApiKeyPermissions() {
  return String(apiKeyForm.permissions || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

async function createManagedApiKey() {
  clearMessages()
  loading.value = true
  createdApiKeySecret.value = ''
  try {
    const created = await api.createApiKey({
      tenantId: apiKeyForm.tenantId.trim(),
      userId: apiKeyForm.userId.trim(),
      permissions: parseApiKeyPermissions(),
      expiresAt: apiKeyForm.expiresAt ? new Date(apiKeyForm.expiresAt).toISOString() : null,
    })
    createdApiKeySecret.value = created.secret || ''
    noticeMessage.value = 'API Key 已创建；请立即复制明文，关闭提示后将无法再次查看'
    await loadApiKeys()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function rotateManagedApiKey(key) {
  if (!key || key.status !== 'ACTIVE') return
  if (typeof window !== 'undefined' && !window.confirm(`确认轮换 ${key.keyPrefix}… 的 API Key 吗？旧密钥会立即失效。`)) return
  clearMessages()
  loading.value = true
  createdApiKeySecret.value = ''
  try {
    const rotated = await api.rotateApiKey(key.id)
    createdApiKeySecret.value = rotated.secret || ''
    noticeMessage.value = 'API Key 已轮换；旧密钥已立即失效，请保存新 secret'
    await loadApiKeys()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function revokeManagedApiKey(key) {
  if (!key || key.status !== 'ACTIVE') return
  if (typeof window !== 'undefined' && !window.confirm(`确认立即撤销 ${key.keyPrefix}… 的 API Key 吗？`)) return
  clearMessages()
  loading.value = true
  try {
    await api.revokeApiKey(key.id)
    noticeMessage.value = `${key.keyPrefix}… 已立即撤销`
    await loadApiKeys()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function copyApiKeySecret() {
  if (!createdApiKeySecret.value) return
  try {
    await navigator.clipboard.writeText(createdApiKeySecret.value)
    noticeMessage.value = 'API Key 明文已复制到剪贴板'
  } catch {
    errorMessage.value = '浏览器拒绝访问剪贴板，请手动复制明文'
  }
}

function closeApiKeySecret() {
  createdApiKeySecret.value = ''
}

async function createDocument() {
  clearMessages()
  loading.value = true
  try {
    await api.createDocument({ ...documentForm })
    noticeMessage.value = '知识文档已保存，后续模型步骤会按租户和用户权限检索'
    await loadDashboard()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function runQuickEvaluation() {
  clearMessages()
  loading.value = true
  try {
    await api.runEvaluation({
      name: evaluationForm.name,
      modelName: form.modelName || null,
      promptVersion: form.promptVersion,
      policyVersion: form.policyVersion,
      cases: [{
        name: '控制台用例',
        input: evaluationForm.input,
        toolName: 'demo.echo',
        expectedContains: evaluationForm.expectedContains,
        budget: 1,
      }],
    })
    noticeMessage.value = '评测完成，报告已记录'
    await loadDashboard()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function selectRun(runId, announce = true, showLoading = true) {
  if (showLoading) detailLoading.value = true
  if (announce) clearMessages()
  try {
    const [detail, events] = await Promise.all([api.getRun(runId), api.listAuditEvents(runId)])
    selectedRun.value = detail
    auditEvents.value = events
    startRunEventStream(runId)
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    if (showLoading) detailLoading.value = false
  }
}

function stopRunEventStream() {
  window.clearTimeout(runEventReconnectTimer)
  runEventReconnectTimer = undefined
  if (runEventAbortController) {
    runEventAbortController.abort()
    runEventAbortController = undefined
  }
  runEventStreamRunId = undefined
  runEventStreaming.value = false
}

/** 只保留一个当前 Run 的实时连接，切换对话或控制台条目时立即关闭旧连接。 */
function startRunEventStream(runId) {
  if (!runId || isTerminal(selectedRun.value?.run?.status)) {
    // 切到终态任务也必须关闭此前其他 Run 的连接。
    stopRunEventStream()
    return
  }
  // 同一 Run 的 SSE 已建立时保持连接，避免聊天轮询每 1.2 秒触发一次重连。
  if (runEventStreamRunId === runId && runEventAbortController && !runEventAbortController.signal.aborted) {
    return
  }
  stopRunEventStream()
  const controller = new AbortController()
  runEventAbortController = controller
  runEventStreamRunId = runId
  runEventStreaming.value = true
  void api.streamRunEvents(runId, {
    signal: controller.signal,
    onEvent: ({ event, data }) => {
      if ((event !== 'snapshot' && event !== 'run') || data?.run?.id !== runId) return
      if (selectedRun.value?.run?.id !== runId) return
      selectedRun.value = data
      applyStreamingAssistantContent(runId, data)
      if (latestStreamingModelContent(data)) scrollChatToBottom()
      // 审计记录不放入 SSE 正文，按快照变化增量刷新，避免把额外敏感字段扩大到新接口。
      void api.listAuditEvents(runId).then((events) => {
        if (selectedRun.value?.run?.id === runId) auditEvents.value = events
      }).catch(() => {})
      if (isTerminal(data.run.status)) {
        stopRunEventStream()
        void refreshAfterTerminalRunEvent(runId)
      }
    },
  }).catch(() => {
    // 网络短暂中断时无须打断聊天；下面会自动重连，现有轮询继续作为兜底。
  }).finally(() => {
    if (controller.signal.aborted || runEventAbortController !== controller) return
    runEventStreaming.value = false
    runEventAbortController = undefined
    runEventStreamRunId = undefined
    if (!isTerminal(selectedRun.value?.run?.status) && selectedRun.value?.run?.id === runId) {
      runEventReconnectTimer = window.setTimeout(() => startRunEventStream(runId), 1000)
    }
  })
}

async function refreshAfterTerminalRunEvent(runId) {
  try {
    const work = [loadRunsPage(), api.dashboardSummary()]
    if (activeConversationId.value && latestConversationRun(activeConversation.value) === runId) {
      work.push(api.getConversation(activeConversationId.value), api.listConversations())
    }
    const results = await Promise.all(work)
    summary.value = results[1]
    if (results.length > 2) {
      activeConversation.value = results[2]
      conversations.value = results[3]
      scrollChatToBottom()
    }
  } catch {
    // 下一轮轮询会恢复列表或消息气泡，不覆盖用户当前可见的 Run 详情。
  }
}

async function pollSelectedRun() {
  if (!selectedRun.value || isTerminal(selectedStatus.value)) return
  if (runsLoading.value) return
  // 实时 SSE 正常存在时避免每 1.5 秒重复拉取详情；断线时会自动回到该兜底路径。
  if (runEventStreaming.value) return
  try {
    await selectRun(selectedRun.value.run.id, false, false)
    const [, summaryData] = await Promise.all([loadRunsPage(), api.dashboardSummary()])
    summary.value = summaryData
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function createAndStartRun() {
  clearMessages()
  loading.value = true
  try {
    localStorage.setItem('harnessTenantId', form.tenantId)
    localStorage.setItem('harnessUserId', form.userId)
    const created = await api.createRun({
      ...form,
      budget: Number(form.budget),
      modelName: form.modelName || null,
    })
    const started = await api.startRun(created.id)
    noticeMessage.value = started.run.status === 'WAITING_APPROVAL'
      ? 'Run 已创建，等待人工审批'
      : started.run.status === 'RUNNING'
        ? 'Run 已提交，Worker 正在异步执行'
        : 'Run 已创建并完成执行'
    showCreateForm.value = false
    runStatusFilter.value = ''
    runPage.page = 0
    await loadDashboard()
    await selectRun(created.id, false)
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function approveSelectedRun() {
  if (!selectedRun.value) return
  clearMessages()
  loading.value = true
  try {
    await api.approveRun(selectedRun.value.run.id)
    noticeMessage.value = '审批已通过，Run 已继续执行'
    await loadDashboard()
    await refreshActiveConversation()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function rejectSelectedRun() {
  if (!selectedRun.value) return
  clearMessages()
  loading.value = true
  try {
    await api.rejectRun(selectedRun.value.run.id, '控制台人工拒绝')
    noticeMessage.value = '审批已拒绝，Run 已结束'
    await loadDashboard()
    await refreshActiveConversation()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function retrySelectedRun() {
  if (!selectedRun.value) return
  clearMessages()
  loading.value = true
  try {
    const retried = await api.retryRun(selectedRun.value.run.id)
    noticeMessage.value = retried.run.status === 'WAITING_APPROVAL'
      ? '重试已进入人工审批'
      : '重试已完成'
    await loadDashboard()
    await refreshActiveConversation()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function startSelectedRun() {
  if (!selectedRun.value) return
  clearMessages()
  loading.value = true
  try {
    await api.startRun(selectedRun.value.run.id)
    noticeMessage.value = 'Run 已启动'
    await loadDashboard()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function cancelSelectedRun() {
  if (!selectedRun.value) return
  clearMessages()
  loading.value = true
  try {
    await api.cancelRun(selectedRun.value.run.id)
    noticeMessage.value = 'Run 已取消'
    await loadDashboard()
    await refreshActiveConversation()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  syncActiveConsoleSectionFromHash()
  window.addEventListener('hashchange', syncActiveConsoleSectionFromHash)
  if (desktopWorkspaceAvailable.value) {
    api.configureDesktopWorkspaceDrop()
    api.onDesktopWorkspaceDropped((result) => {
      void handleDesktopWorkspaceDropped(result)
    })
  }
  await Promise.all([loadDashboard(), loadHealth(), loadWorkspace(), loadTenantPolicy(), loadApiKeys(), loadLocalWorkspaces()])
  await loadConversations()
  runPollTimer = window.setInterval(pollSelectedRun, 1500)
  conversationPollTimer = window.setInterval(pollConversation, 1200)
  healthPollTimer = window.setInterval(loadHealth, 10000)
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncActiveConsoleSectionFromHash)
  stopRunEventStream()
  api.clearDesktopWorkspaceDropListener()
  window.clearInterval(runPollTimer)
  window.clearInterval(conversationPollTimer)
  window.clearInterval(healthPollTimer)
  window.clearTimeout(chatHighlightTimer)
})
</script>

<template>
  <template v-if="chatMode">
    <div class="chat-app">
      <header class="chat-topbar">
        <div class="chat-brand">
          <div class="brand-mark">MH</div>
          <div><strong>Ming Harness</strong><span>CODE AGENT WORKSPACE</span></div>
        </div>
        <div class="chat-topbar-actions">
          <span class="chat-identity">{{ form.tenantId }} / {{ form.userId }}</span>
          <span class="chat-health" :class="infraOnline ? 'health-up' : 'health-warning'"><i></i>{{ infraLabel }}</span>
          <button class="theme-toggle" type="button" :aria-label="theme === 'dark' ? '切换到白天模式' : '切换到黑夜模式'" @click="toggleTheme">
            <span aria-hidden="true">{{ theme === 'dark' ? '☼' : '☾' }}</span>{{ theme === 'dark' ? '白天' : '黑夜' }}
          </button>
          <button class="secondary-button chat-console-button" type="button" @click="chatMode = false">运行控制台</button>
        </div>
      </header>

      <div v-if="errorMessage" class="message error-message chat-message-banner">{{ errorMessage }}</div>
      <div v-if="noticeMessage" class="message notice-message chat-message-banner">{{ noticeMessage }}</div>

      <div class="chat-layout">
        <aside class="conversation-sidebar">
          <div class="conversation-sidebar-heading">
            <div><p class="eyebrow">CONVERSATIONS</p><h2>对话</h2></div>
            <button class="icon-button" type="button" aria-label="新建对话" title="新建对话" :disabled="chatLoading || chatSending || chatUploading" @click="createChatConversation">＋</button>
          </div>
          <div v-if="chatLoading && !conversations.length" class="chat-sidebar-empty">正在读取对话…</div>
          <div v-else-if="!conversations.length" class="chat-sidebar-empty">还没有对话</div>
          <div v-else class="conversation-list">
            <button
              v-for="conversation in conversations"
              :key="conversation.id"
              class="conversation-row"
              :class="{ active: conversation.id === activeConversationId }"
              type="button"
              :disabled="chatSending || chatUploading"
              @click="selectConversation(conversation.id)"
            >
              <span class="conversation-row-icon">⌁</span>
              <span class="conversation-row-body">
                <strong>{{ conversation.title }}</strong>
                <small>{{ conversation.lastMessagePreview || '开始一轮新的 Agent 对话' }}</small>
                <em>{{ conversation.messageCount }} 条消息 · {{ formatDate(conversation.updatedAt) }}</em>
              </span>
              <span v-if="conversation.activeRunId" class="conversation-running-dot" title="Agent 执行中"></span>
            </button>
          </div>
          <section v-if="chatUserMessages.length" class="chat-turn-navigation" aria-label="本轮消息导航">
            <p class="eyebrow">MESSAGE NAVIGATION</p>
            <strong>本轮导航</strong>
            <div class="chat-turn-navigation-list">
              <button
                v-for="message in chatUserMessages"
                :key="message.id"
                type="button"
                :title="messageNavigationLabel(message)"
                @click="jumpToChatMessage(message.id)"
              >
                <span>#{{ Math.ceil(message.sequence / 2) }}</span>{{ messageNavigationLabel(message) }}
              </button>
            </div>
          </section>
          <div class="conversation-sidebar-foot">
            <span class="pulse" :class="{ offline: !infraOnline }"></span>
            <span>{{ workerLabel }}</span>
            <small>{{ queueLabel }}</small>
          </div>
        </aside>

        <main class="chat-main">
          <div class="chat-heading">
            <div>
              <p class="eyebrow">CONTINUOUS AGENT SESSION</p>
              <h1>{{ activeConversation?.conversation?.title || '新的对话' }}</h1>
              <p class="chat-heading-meta">每一轮输入都会创建可追踪 Run，Agent 会在同一会话中继续理解上下文。</p>
            </div>
            <div class="chat-heading-actions">
              <div class="chat-workspace-chip" :class="workspaceStatusClass" :title="workspaceDetail">
                <i></i>
                <span><small>LOCAL WORKSPACE</small><strong>{{ workspaceLabel }}</strong></span>
              </div>
              <div v-if="desktopWorkspaceAvailable" class="chat-workspace-selector" title="该选择只会绑定下一次新建的会话">
                <select v-model="newConversationWorkspaceId" :disabled="desktopWorkspacePicking || chatSending || chatUploading">
                  <option value="">默认受控工作区</option>
                  <option v-for="item in localWorkspaces" :key="item.id" :value="item.id" :disabled="!item.accessible">
                    {{ item.displayName }}{{ item.accessible ? '' : '（不可访问）' }}
                  </option>
                </select>
                <button class="secondary-button chat-project-button" type="button" :disabled="desktopWorkspacePicking || chatSending || chatUploading" @click="chooseDesktopWorkspace">
                  {{ desktopWorkspacePicking ? '选择中…' : '选择本地项目' }}
                </button>
              </div>
              <span v-if="runEventStreaming && !isTerminal(selectedStatus)" class="chat-live-indicator"><i></i>实时执行</span>
              <span v-if="pendingChatMessage" class="chat-run-pill" :class="statusClass(chatRunStatus)"><i></i>{{ statusLabel(chatRunStatus) }}</span>
              <button v-if="workspaceExplorerAvailable" class="secondary-button" type="button" @click="toggleWorkspaceExplorer">{{ showChatWorkspace ? '隐藏文件' : '项目文件' }}</button>
              <button v-if="latestConversationRun(activeConversation)" class="secondary-button" type="button" @click="toggleRunPanel">{{ showChatRun ? '隐藏运行' : '查看运行' }}</button>
            </div>
          </div>

          <div class="chat-messages" aria-live="polite">
            <div v-if="chatLoading && !chatMessages.length" class="chat-empty-state">正在加载会话…</div>
            <div v-else-if="!chatMessages.length" class="chat-empty-state">
              <div class="chat-empty-mark">⌘</div>
              <strong>从一个问题开始</strong>
              <span>Agent 会读取工作区、运行工具并把每轮结果留在这里。</span>
            </div>
            <article
              v-for="message in chatMessages"
              :id="`chat-message-${message.id}`"
              :key="message.id"
              class="chat-message"
              :class="`chat-message-${message.role.toLowerCase()}`"
            >
              <div class="chat-avatar">{{ message.role === 'USER' ? '你' : 'MH' }}</div>
              <div class="chat-bubble-wrap">
                <div class="chat-message-meta"><strong>{{ message.role === 'USER' ? '你' : 'Ming Agent' }}</strong><span>{{ formatDate(message.createdAt) }}</span></div>
                <div class="chat-bubble" :class="messageStatusClass(message.status)">
                  <template v-if="message.role === 'ASSISTANT' && message.status === 'PENDING' && !message.content">
                    <span class="chat-thinking"><i></i><i></i><i></i>{{ messageStatusLabel(message.status) }}</span>
                  </template>
                  <template v-else>
                    <p>{{ message.content || messageStatusLabel(message.status) }}</p>
                    <small v-if="message.role === 'ASSISTANT' && message.status !== 'COMPLETED'">{{ messageStatusLabel(message.status) }}</small>
                  </template>
                  <div v-if="message.attachments?.length" class="chat-attachment-list" aria-label="已导入的工作区文件">
                    <span v-for="attachment in message.attachments" :key="attachment.id" :title="attachment.workspacePath">
                      <i>{{ attachment.directory ? '▣' : '⌁' }}</i><strong>{{ attachment.originalName }}</strong><em v-if="attachment.directory">{{ attachment.fileCount }} 文件</em><code>{{ attachment.workspacePath }}</code>
                    </span>
                  </div>
                </div>
                <button v-if="message.runId && message.role === 'ASSISTANT'" class="message-run-link" type="button" @click="openRunPanel(message.runId)">查看执行步骤 · {{ message.runId.slice(0, 8) }}</button>
              </div>
            </article>
          </div>

          <form
            class="chat-composer"
            :class="{ 'chat-composer-dragging': chatDragActive }"
            @submit.prevent="sendChatMessage"
            @dragenter.prevent="chatDragActive = Boolean(activeConversationId)"
            @dragover.prevent="chatDragActive = Boolean(activeConversationId)"
            @dragleave.prevent="chatDragActive = false"
            @drop.prevent="handleChatDrop"
          >
            <input
              ref="chatAttachmentInput"
              class="chat-attachment-input"
              type="file"
              multiple
              accept="text/*,.java,.kt,.kts,.js,.jsx,.ts,.tsx,.vue,.html,.css,.scss,.json,.yaml,.yml,.xml,.sql,.md,.txt,.properties,.gradle,.sh,.py,.go,.rs,.c,.cpp,.h"
              @change="handleChatAttachmentInput"
            />
            <input
              ref="chatFolderInput"
              class="chat-attachment-input"
              type="file"
              multiple
              webkitdirectory
              directory
              @change="handleChatFolderInput"
            />
            <div v-if="chatAttachments.length" class="chat-composer-attachments" aria-label="待发送附件">
              <span v-for="(attachment, index) in chatAttachments" :key="attachment.key">
                <i>{{ attachment.directory ? '▣' : '⌁' }}</i><strong>{{ attachment.name }}</strong><em>{{ attachment.directory ? `${attachment.fileCount} 文件` : formatFileSize(attachment.size) }}</em>
                <button type="button" :aria-label="`移除 ${attachment.name}`" :disabled="chatSending || chatUploading" @click="removeChatAttachment(index)">×</button>
              </span>
            </div>
            <textarea
              v-model="chatInput"
              rows="3"
              :disabled="chatSending || chatUploading || !activeConversationId"
              placeholder="描述代码任务；桌面版可拖入项目文件夹，浏览器会导入文本副本…"
              aria-label="输入消息"
              @keydown.enter.exact.prevent="sendChatMessage"
            ></textarea>
            <div class="chat-composer-footer">
              <span><kbd>Enter</kbd> 发送 · <kbd>Shift</kbd> + <kbd>Enter</kbd> 换行 · {{ desktopWorkspaceDropping ? '正在授权拖入的本地项目…' : workspaceConnected ? 'Agent 可直接操作本会话绑定的本地项目' : '文件夹导入后保留层级' }}</span>
              <div class="chat-composer-actions">
                <button class="secondary-button chat-attachment-button" type="button" :disabled="chatSending || chatUploading || !activeConversationId" @click="openChatAttachmentPicker">⌁ 附件</button>
                <button class="secondary-button chat-attachment-button" type="button" :disabled="chatSending || chatUploading || !activeConversationId" @click="openChatFolderPicker">▣ 文件夹</button>
                <button class="primary-button chat-send-button" type="submit" :disabled="!canSendChat">{{ chatUploading ? '导入中…' : chatSending ? '提交中…' : '发送' }} <span>↗</span></button>
              </div>
            </div>
          </form>
        </main>

        <aside v-if="showChatWorkspace" class="chat-workspace-panel">
          <div class="chat-run-panel-heading">
            <div><p class="eyebrow">PROJECT EXPLORER</p><h2>项目文件</h2></div>
            <button class="icon-button" type="button" aria-label="关闭项目文件" @click="showChatWorkspace = false">×</button>
          </div>
          <div v-if="workspaceExplorerLoading && !workspaceExplorer" class="chat-run-empty">正在读取工作区目录…</div>
          <template v-else-if="workspaceExplorer">
            <div class="workspace-explorer-git" :class="{ unavailable: !workspaceExplorer.git?.available }">
              <span>Git</span>
              <strong>{{ workspaceExplorer.git?.available ? workspaceExplorer.git.branch : '非 Git 项目' }}</strong>
              <em v-if="workspaceExplorer.git?.available">{{ workspaceExplorer.git.clean ? '工作区干净' : `${workspaceExplorer.git.changeCount} 项变更` }}</em>
              <button v-if="workspaceGitAvailable" type="button" @click="toggleWorkspaceGitReview">{{ workspaceGitReviewVisible ? '收起审阅' : '审阅变更' }}</button>
            </div>
            <section v-if="workspaceGitReviewVisible" class="workspace-git-review" aria-label="Git 代码变更审阅">
              <div class="workspace-git-review-heading">
                <strong>代码变更</strong>
                <button class="icon-button" type="button" aria-label="刷新 Git 变更" :disabled="workspaceGitStatusLoading" @click="loadWorkspaceGitStatus">↻</button>
              </div>
              <p v-if="workspaceGitStatusLoading && !workspaceGitStatus" class="workspace-git-review-empty">正在读取 Git 变更…</p>
              <template v-else-if="workspaceGitStatus">
                <p v-if="workspaceGitStatus.clean" class="workspace-git-review-empty">当前工作区没有未提交变更。</p>
                <div v-else class="workspace-git-change-list">
                  <article v-for="change in workspaceGitStatus.entries" :key="`${change.index}-${change.worktree}-${change.path}`">
                    <div><span :class="gitChangeClass(change)">{{ gitChangeLabel(change) }}</span><strong :title="change.path">{{ change.path }}</strong></div>
                    <nav>
                      <button v-if="change.worktree !== ' ' && !(change.worktree === '?' && change.index === '?')" type="button" @click="loadWorkspaceGitDiff(change.path, false)">工作区</button>
                      <button v-if="change.index !== ' ' && change.index !== '?'" type="button" @click="loadWorkspaceGitDiff(change.path, true)">暂存</button>
                      <button v-if="change.worktree === '?' && change.index === '?'" type="button" @click="previewUntrackedWorkspaceFile(change)">预览</button>
                    </nav>
                  </article>
                </div>
                <p v-if="workspaceGitStatus.protectedChangeCount" class="workspace-git-review-warning">有 {{ workspaceGitStatus.protectedChangeCount }} 项受保护的隐藏文件变更，已按工作区安全策略隐藏。</p>
                <p v-if="workspaceGitStatus.outputTruncated" class="workspace-git-review-warning">变更列表已按安全上限截断，请逐步处理项目中的文件。</p>
              </template>
              <p v-else class="workspace-git-review-empty">无法读取 Git 变更。</p>
              <section v-if="workspaceGitDiff || workspaceGitDiffLoading" class="workspace-git-diff" aria-label="Git 差异内容">
                <div><strong>{{ workspaceGitDiff ? `${workspaceGitDiff.path} · ${workspaceGitDiff.staged ? '已暂存' : '工作区'}` : '正在读取差异…' }}</strong><span v-if="workspaceGitDiff?.outputTruncated">已截断</span></div>
                <pre v-if="workspaceGitDiff?.hasChanges">{{ workspaceGitDiff.diff }}</pre>
                <p v-else-if="workspaceGitDiff">当前范围没有可显示的 Diff；新增未跟踪文件请使用“预览”。</p>
              </section>
            </section>
            <div class="workspace-explorer-path">
              <button class="secondary-button" type="button" :disabled="workspaceExplorerPath === '.' || workspaceExplorerLoading" @click="loadWorkspaceDirectory(workspaceExplorer.parentPath)">↑</button>
              <code>{{ workspaceExplorerPath }}</code>
              <button class="icon-button" type="button" aria-label="刷新目录" :disabled="workspaceExplorerLoading" @click="loadWorkspaceDirectory(workspaceExplorerPath)">↻</button>
            </div>
            <div class="workspace-explorer-list" aria-label="工作区目录列表">
              <button
                v-for="entry in workspaceExplorer.entries"
                :key="entry.path"
                type="button"
                :class="{ directory: entry.directory, active: workspaceFilePreview?.path === entry.path }"
                @click="entry.directory ? loadWorkspaceDirectory(entry.path) : previewWorkspaceFile(entry)"
              >
                <i>{{ entry.directory ? '▸' : '⌁' }}</i><strong>{{ entry.name }}</strong><em>{{ entry.directory ? '目录' : formatFileSize(entry.size) }}</em>
              </button>
              <p v-if="!workspaceExplorer.entries.length">当前目录没有可显示的文件。</p>
            </div>
            <section v-if="workspaceFilePreview || workspaceFilePreviewLoading" class="workspace-file-preview" aria-label="文件预览">
              <div><strong>{{ workspaceFilePreview?.path || '正在读取文件…' }}</strong><span v-if="workspaceFilePreview?.redacted">已脱敏</span><span v-if="workspaceFilePreview?.truncated">已截断</span></div>
              <pre v-if="workspaceFilePreview">{{ workspaceFilePreview.content }}</pre>
            </section>
          </template>
          <div v-else class="chat-run-empty">当前会话未连接可访问的本地项目。</div>
        </aside>

        <aside v-if="showChatRun" class="chat-run-panel">
          <div class="chat-run-panel-heading"><div><p class="eyebrow">RUN TRACE</p><h2>本轮执行</h2></div><button class="icon-button" type="button" aria-label="关闭运行详情" @click="showChatRun = false">×</button></div>
          <div v-if="!selectedRun" class="chat-run-empty">选择一条助手消息查看执行链。</div>
          <template v-else>
            <div class="chat-run-summary"><strong>{{ selectedRun.run.title }}</strong><span class="status-pill" :class="statusClass(selectedRun.run.status)"><i></i>{{ statusLabel(selectedRun.run.status) }}</span></div>
            <div class="chat-run-actions">
              <button v-if="canApprove" class="secondary-button" type="button" :disabled="loading" @click="approveSelectedRun">审批通过</button>
              <button v-if="canApprove" class="danger-button" type="button" :disabled="loading" @click="rejectSelectedRun">拒绝</button>
              <button v-if="canRetry" class="secondary-button" type="button" :disabled="loading" @click="retrySelectedRun">重试</button>
              <button v-if="canCancel" class="danger-button" type="button" :disabled="loading" @click="cancelSelectedRun">取消</button>
            </div>
            <section v-if="workspaceChangePreviews.length" class="chat-change-review" aria-label="代码变更预览">
              <div class="chat-change-review-heading">
                <div><span>CHANGE REVIEW</span><strong>{{ pendingWorkspaceChangePreviews.length ? '请先检查待审批变更' : '本轮代码变更' }}</strong></div>
                <em v-if="pendingWorkspaceChangePreviews.length">{{ pendingWorkspaceChangePreviews.length }} 项待审批</em>
              </div>
              <article v-for="change in workspaceChangePreviews" :key="change.stepId" class="chat-change-card">
                <div class="chat-change-card-meta">
                  <span>{{ change.typeLabel }}</span><code>{{ change.path }}</code><em :class="statusClass(change.status)">{{ statusLabel(change.status) }}</em>
                </div>
                <template v-if="change.kind === 'edit'">
                  <pre v-for="(edit, index) in change.edits" :key="index" class="chat-inline-diff"><span class="diff-remove">− {{ edit.oldText }}</span><span class="diff-add">＋ {{ edit.newText }}</span><small v-if="edit.replaceAll">替换全部匹配项</small></pre>
                  <p v-if="change.hiddenEditCount" class="chat-change-truncated">另有 {{ change.hiddenEditCount }} 个编辑已折叠。</p>
                </template>
                <pre v-else class="chat-inline-diff"><span class="diff-add">＋ {{ change.content }}</span></pre>
              </article>
            </section>
            <div class="chat-run-meta"><span>Run</span><code>{{ selectedRun.run.id.slice(0, 12) }}</code><span>Trace</span><code>{{ selectedRun.run.traceId?.slice(0, 12) || '—' }}</code></div>
            <div class="chat-step-list">
              <div v-for="step in selectedRun.steps" :key="step.id" class="chat-step-row"><span class="chat-step-dot" :class="statusClass(step.status)"></span><div><strong>{{ step.name }}</strong><small>{{ stepLabel(step.type) }} · {{ statusLabel(step.status) }}</small><p v-if="step.error">{{ step.error }}</p></div></div>
            </div>
          </template>
        </aside>
      </div>
    </div>
  </template>
  <template v-else>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">MH</div>
        <div>
          <strong>Ming Harness</strong>
          <span>Agent Operations</span>
        </div>
      </div>

      <nav class="side-nav" aria-label="主导航">
        <a class="nav-item" :class="{ active: activeConsoleSection === 'runtime' }" href="#runtime" :aria-current="activeConsoleSection === 'runtime' ? 'page' : undefined" @click="setActiveConsoleSection('runtime')"><span class="nav-icon">◈</span>运行中心</a>
        <a class="nav-item" :class="{ active: activeConsoleSection === 'tools' }" href="#tools" :aria-current="activeConsoleSection === 'tools' ? 'page' : undefined" @click="setActiveConsoleSection('tools')"><span class="nav-icon">⌘</span>工具注册</a>
        <a class="nav-item" :class="{ active: activeConsoleSection === 'audit' }" href="#audit" :aria-current="activeConsoleSection === 'audit' ? 'page' : undefined" @click="setActiveConsoleSection('audit')"><span class="nav-icon">↯</span>审计追踪</a>
      </nav>

      <div class="sidebar-foot">
        <div class="system-state"><span class="pulse" :class="{ offline: !infraOnline }"></span><span>{{ infraLabel }}</span></div>
        <small>Runtime v0.1 · Java 17</small>
      </div>
    </aside>

    <main class="main-content" id="runtime">
      <header class="topbar">
        <div>
          <p class="eyebrow">RUNTIME / OVERVIEW</p>
          <h1>运行中心</h1>
        </div>
        <div class="topbar-actions">
          <button class="secondary-button" type="button" @click="chatMode = true">聊天工作台</button>
          <button
            class="theme-toggle"
            type="button"
            :aria-label="theme === 'dark' ? '切换到白天模式' : '切换到黑夜模式'"
            :title="theme === 'dark' ? '切换到白天模式' : '切换到黑夜模式'"
            @click="toggleTheme"
          >
            <span aria-hidden="true">{{ theme === 'dark' ? '☼' : '☾' }}</span>
            {{ theme === 'dark' ? '白天' : '黑夜' }}
          </button>
          <span class="date-chip">本地演示环境</span>
          <button class="primary-button" type="button" @click="showCreateForm = !showCreateForm">
            <span>＋</span> 新建 Run
          </button>
        </div>
      </header>

      <div v-if="errorMessage" class="message error-message">{{ errorMessage }}</div>
      <div v-if="noticeMessage" class="message notice-message">{{ noticeMessage }}</div>

      <section class="infra-strip panel" aria-label="基础设施状态">
        <div><p class="eyebrow">INFRASTRUCTURE</p><h2>本地依赖状态</h2></div>
        <div class="infra-status">
          <div class="health-items">
            <span :class="healthClass('db')"><i></i>数据库 {{ healthStatus('db') }}</span>
            <span :class="healthClass('redis')"><i></i>Redis {{ healthStatus('redis') }}</span>
            <span :class="healthClass('rabbit')"><i></i>RabbitMQ {{ healthStatus('rabbit') }}</span>
            <span :class="healthClass('diskSpace')"><i></i>应用 {{ health?.status || '—' }}</span>
            <span :class="workerHealthClass"><i></i>{{ workerLabel }}</span>
            <span :class="queueHealthClass"><i></i>{{ queueLabel }}</span>
          </div>
          <div v-if="runtimeAlerts.length" class="runtime-alerts" aria-live="polite">
            <span v-for="alert in runtimeAlerts" :key="alert.label" :class="`runtime-alert-${alert.level}`">{{ alert.label }}</span>
          </div>
        </div>
      </section>

      <section class="stats-grid" aria-label="运行统计">
        <div class="stat-card stat-total">
          <div class="stat-top"><span>全部 Run</span><span class="stat-icon">∑</span></div>
          <strong>{{ stats.total }}</strong>
          <small>最近 50 条执行记录</small>
        </div>
        <div class="stat-card stat-running">
          <div class="stat-top"><span>执行中</span><span class="stat-icon">◌</span></div>
          <strong>{{ stats.running }}</strong>
          <small>{{ stats.queued }} 条排队等待</small>
        </div>
        <div class="stat-card stat-success">
          <div class="stat-top"><span>成功率</span><span class="stat-icon">↗</span></div>
          <strong>{{ stats.total ? Math.round((stats.succeeded / stats.total) * 100) : 0 }}<em>%</em></strong>
          <small>{{ stats.succeeded }} 条任务已完成</small>
        </div>
        <div class="stat-card stat-failed">
          <div class="stat-top"><span>需关注</span><span class="stat-icon">!</span></div>
          <strong>{{ stats.failed }}</strong>
          <small>失败或需要人工处理</small>
        </div>
      </section>

      <section v-if="showCreateForm" class="create-panel">
        <div class="section-heading">
          <div>
            <p class="eyebrow">CREATE EXECUTION</p>
            <h2>创建一次可追溯执行</h2>
          </div>
          <button class="icon-button" type="button" aria-label="关闭创建表单" @click="showCreateForm = false">×</button>
        </div>
        <form class="run-form" @submit.prevent="createAndStartRun">
          <label class="field field-wide">
            <span>任务名称</span>
            <input v-model="form.title" required maxlength="120" placeholder="例如：分析一条退款申请" />
          </label>
          <label class="field">
            <span>租户 ID</span>
            <input v-model="form.tenantId" required maxlength="64" />
          </label>
          <label class="field">
            <span>执行用户</span>
            <input v-model="form.userId" required maxlength="64" />
          </label>
          <label class="field field-wide">
            <span>任务输入</span>
            <textarea v-model="form.input" required maxlength="4000" rows="3" placeholder="输入用户任务或上下文"></textarea>
          </label>
          <label class="field">
            <span>工具</span>
            <select v-model="form.toolName" :disabled="form.agentMode">
              <option v-for="tool in tools" :key="tool.name" :value="tool.name">{{ tool.name }}</option>
            </select>
          </label>
          <label class="field">
            <span>模型（可选）</span>
            <input v-model="form.modelName" placeholder="默认演示模型" />
          </label>
          <label class="field">
            <span>Prompt 版本</span>
            <input v-model="form.promptVersion" required />
          </label>
          <label class="field">
            <span>策略版本</span>
            <input v-model="form.policyVersion" required />
          </label>
          <label class="field">
            <span>幂等键（可选）</span>
            <input v-model="form.idempotencyKey" maxlength="128" placeholder="例如：order-123" />
          </label>
          <label class="field">
            <span>权限快照（可选）</span>
            <input v-model="form.permissions" maxlength="1000" placeholder="例如：orders.read,orders.write" />
          </label>
          <div class="field field-wide agent-mode-field">
            <span>运行模式</span>
            <div class="agent-mode-controls">
              <label class="check-field">
                <input v-model="form.agentMode" type="checkbox" />
                <span>启用代码 Agent 多轮模式</span>
              </label>
              <label v-if="form.agentMode" class="turns-field">
                <span>最大轮数</span>
                <input v-model.number="form.maxTurns" type="number" min="1" max="20" required />
              </label>
            </div>
            <small class="form-hint">Agent 会根据模型 Tool Call 动态执行工作区工具；工具白名单和审批策略仍由服务端控制。</small>
          </div>
          <div class="form-actions field-wide">
            <span class="form-hint">{{ form.agentMode ? '创建后会按模型决策循环执行，并持久化每一轮模型与工具步骤。' : '创建后会依次执行模型步骤和工具步骤，并记录完整审计链。' }}</span>
            <button class="primary-button" type="submit" :disabled="loading">{{ loading ? '执行中…' : '创建并执行' }}</button>
          </div>
        </form>
      </section>

      <section class="workspace-grid">
        <div class="runs-panel panel">
          <div class="panel-heading run-panel-heading">
            <div><p class="eyebrow">RECENT RUNS</p><h2>最近执行</h2></div>
            <div class="run-panel-tools">
              <select
                v-model="runStatusFilter"
                class="run-filter"
                aria-label="按状态筛选 Run"
                :disabled="runsLoading"
                @change="changeRunStatusFilter"
              >
                <option value="">全部状态</option>
                <option v-for="status in runStatusOptions" :key="status" :value="status">{{ statusLabel(status) }}</option>
              </select>
              <span v-if="runsLoading" class="run-list-loading">加载中…</span>
              <button class="refresh-button" type="button" :disabled="runsLoading" @click="loadDashboard" aria-label="刷新列表">⟳</button>
            </div>
          </div>
          <div v-if="runsLoading && !runs.length" class="loading-state run-list-loading-state">正在加载 Run 列表…</div>
          <div v-else-if="!runs.length" class="empty-state">
            <div class="empty-orb">◈</div>
            <strong>{{ runStatusFilter ? '没有匹配的 Run' : '还没有执行记录' }}</strong>
            <span>{{ runStatusFilter ? '可以切换状态筛选，或创建一个新的 Run。' : '创建第一个 Run，开始观察执行链。' }}</span>
          </div>
          <div v-else class="run-list">
            <button
              v-for="run in runs"
              :key="run.id"
              class="run-row"
              :class="{ selected: selectedRun?.run?.id === run.id }"
              type="button"
              @click="selectRun(run.id)"
            >
              <span class="run-status-dot" :class="statusClass(run.status)"></span>
              <span class="run-row-content">
                <strong>{{ run.title }}</strong>
                <small>{{ run.id.slice(0, 8) }} · {{ formatDate(run.createdAt) }}</small>
              </span>
              <span class="run-row-meta"><em :class="statusClass(run.status)">{{ statusLabel(run.status) }}</em><small>{{ runModeLabel(run) }} · {{ run.stepCount }} steps</small></span>
            </button>
          </div>
          <div v-if="runPage.totalElements || runStatusFilter" class="run-pagination" aria-label="Run 列表分页">
            <span>{{ runPageLabel }}</span>
            <div class="run-pagination-controls">
              <button class="page-button" type="button" :disabled="runsLoading || !canPreviousRunPage" @click="goToRunPage(-1)">上一页</button>
              <button class="page-button" type="button" :disabled="runsLoading || !canNextRunPage" @click="goToRunPage(1)">下一页</button>
            </div>
          </div>
        </div>

        <div class="detail-panel panel" id="audit">
          <div v-if="detailLoading" class="loading-state">正在读取执行详情…</div>
          <div v-else-if="!selectedRun" class="empty-state detail-empty">
            <div class="empty-orb">◎</div>
            <strong>选择一个 Run</strong>
            <span>执行详情、步骤和审计事件会显示在这里。</span>
          </div>
          <template v-else>
            <div class="panel-heading detail-heading">
              <div>
                <p class="eyebrow">RUN DETAIL / {{ selectedRun.run.id.slice(0, 8) }}</p>
                <h2>{{ selectedRun.run.title }}</h2>
              </div>
              <div class="detail-actions">
                <span v-if="runEventStreaming && !isTerminal(selectedStatus)" class="run-live-indicator"><i></i>实时</span>
                <span class="status-pill" :class="statusClass(selectedStatus)"><i></i>{{ statusLabel(selectedStatus) }}</span>
                <button v-if="canStart" class="secondary-button" type="button" :disabled="loading" @click="startSelectedRun">启动</button>
                <button v-if="canApprove" class="secondary-button" type="button" :disabled="loading" @click="approveSelectedRun">审批通过</button>
                <button v-if="canApprove" class="danger-button" type="button" :disabled="loading" @click="rejectSelectedRun">拒绝</button>
                <button v-if="canRetry" class="secondary-button" type="button" :disabled="loading" @click="retrySelectedRun">重试</button>
                <button v-if="canCancel" class="danger-button" type="button" :disabled="loading" @click="cancelSelectedRun">取消</button>
              </div>
            </div>

            <div class="run-meta-grid">
              <div><span>租户 / 用户</span><strong>{{ selectedRun.run.tenantId }} / {{ selectedRun.run.userId }}</strong></div>
              <div><span>模型</span><strong>{{ selectedRun.run.modelName }}</strong></div>
              <div><span>Prompt / 策略</span><strong>{{ selectedRun.run.promptVersion }} · {{ selectedRun.run.policyVersion }}</strong></div>
              <div><span>模式 / 轮数</span><strong>{{ runModeLabel(selectedRun.run) }}</strong></div>
              <div><span>Trace / 耗时</span><strong>{{ selectedRun.run.traceId?.slice(0, 12) || '—' }} · {{ selectedRun.run.durationMs || 0 }} ms</strong></div>
            </div>

            <div class="input-preview"><span>任务输入</span><p>{{ selectedRun.run.input }}</p></div>

            <div class="subsection">
              <div class="subsection-title"><h3>执行步骤</h3><span>{{ selectedRun.steps.length }} steps</span></div>
              <div class="step-timeline">
                <div v-for="step in selectedRun.steps" :key="step.id" class="step-item">
                  <div class="step-rail"><span class="step-marker" :class="statusClass(step.status)">{{ step.sequence }}</span><span class="rail-line"></span></div>
                  <div class="step-body">
                    <div class="step-title-row"><div><span class="step-type">{{ stepLabel(step.type) }}</span><strong>{{ step.name }}</strong></div><span class="step-status" :class="statusClass(step.status)">{{ statusLabel(step.status) }}</span></div>
                    <p v-if="decodeAgentStep(step).content" class="step-output">{{ decodeAgentStep(step).content }}</p>
                    <div v-if="decodeAgentStep(step).toolCalls.length" class="tool-call-list">
                      <span class="tool-call-heading">Tool Call</span>
                      <code v-for="call in decodeAgentStep(step).toolCalls" :key="call.id">{{ call.name }} · {{ call.id }}</code>
                    </div>
                    <template v-if="decodeWorkspaceExec(step)">
                      <p class="command-line"><span>$</span> {{ decodeWorkspaceExec(step).command }} {{ (decodeWorkspaceExec(step).args || []).join(' ') }}</p>
                      <div class="command-summary">
                        <span :class="decodeWorkspaceExec(step).ok ? 'command-ok' : 'command-failed'">{{ decodeWorkspaceExec(step).ok ? '命令成功' : '命令未成功' }}</span>
                        <span>退出码 {{ decodeWorkspaceExec(step).exitCode ?? '—' }}</span>
                        <span v-if="decodeWorkspaceExec(step).timedOut">已超时</span>
                        <span v-if="decodeWorkspaceExec(step).outputTruncated">输出已截断</span>
                      </div>
                      <pre v-if="decodeWorkspaceExec(step).output" class="command-output">{{ decodeWorkspaceExec(step).output }}</pre>
                    </template>
                    <template v-else-if="decodeWorkspaceEdit(step)">
                      <p class="edit-line"><span>✎</span> {{ decodeWorkspaceEdit(step).path }}</p>
                      <div class="command-summary">
                        <span class="command-ok">文件已修改</span>
                        <span>{{ decodeWorkspaceEdit(step).edits || 0 }} 个编辑</span>
                        <span>{{ decodeWorkspaceEdit(step).replacements || 0 }} 处替换</span>
                      </div>
                    </template>
                    <template v-else-if="decodeWorkspaceGitStatus(step)">
                      <p class="git-line"><span>⌘</span> {{ decodeWorkspaceGitStatus(step).branch || 'Git 工作区' }}</p>
                      <div class="command-summary">
                        <span :class="decodeWorkspaceGitStatus(step).clean ? 'command-ok' : 'command-failed'">{{ decodeWorkspaceGitStatus(step).clean ? '工作区干净' : '存在文件变更' }}</span>
                        <span>{{ decodeWorkspaceGitStatus(step).entryCount || 0 }} 个变更</span>
                      </div>
                    </template>
                    <template v-else-if="decodeWorkspaceGitDiff(step)">
                      <p class="git-line"><span>⌘</span> {{ decodeWorkspaceGitDiff(step).path || '.' }} · {{ decodeWorkspaceGitDiff(step).staged ? '已暂存' : '未暂存' }}</p>
                      <pre v-if="decodeWorkspaceGitDiff(step).diff" class="git-diff-output">{{ decodeWorkspaceGitDiff(step).diff }}</pre>
                      <p v-else class="muted-line">当前范围没有代码差异</p>
                    </template>
                    <p v-else-if="step.type !== 'MODEL' && step.output" class="step-output">{{ step.output }}</p>
                    <p v-if="step.error" class="step-error">{{ step.error }}</p>
                    <small>尝试 {{ step.attempt }} 次 · {{ formatDate(step.finishedAt || step.startedAt) }} · {{ step.durationMs || 0 }} ms<span v-if="step.inputTokens"> · {{ step.inputTokens + step.outputTokens }} tokens</span><span v-if="step.cost"> · ${{ step.cost }}</span></small>
                  </div>
                </div>
              </div>
            </div>

            <div class="subsection audit-subsection">
              <div class="subsection-title"><h3>审计事件</h3><span>{{ auditEvents.length }} events</span></div>
              <div class="audit-list">
                <div v-for="event in auditEvents" :key="event.id" class="audit-row">
                  <span class="audit-time">{{ formatDate(event.createdAt) }}</span><strong>{{ event.eventType }}</strong><span>{{ event.message }}</span><small v-if="event.actorId">{{ event.actorId }} · {{ event.traceId?.slice(0, 10) }}</small>
                </div>
                <div v-if="!auditEvents.length" class="muted-line">暂无审计事件</div>
              </div>
            </div>
          </template>
        </div>
      </section>

      <section class="tool-section panel" id="tools">
        <div class="panel-heading"><div><p class="eyebrow">TOOL REGISTRY</p><h2>已注册工具</h2></div><span class="registry-count">{{ tools.length }} tools</span></div>
        <div class="tool-grid">
          <div v-for="tool in tools" :key="tool.name" class="tool-card">
            <div class="tool-card-top"><span class="tool-symbol">⌁</span><span class="risk-badge" :class="`risk-${tool.riskLevel.toLowerCase()}`">{{ tool.riskLevel }}</span></div>
            <strong>{{ tool.name }}</strong>
            <p>{{ tool.description }}</p>
            <small>{{ tool.readOnly ? '只读工具' : '有副作用' }} · {{ tool.requiresApproval ? '需要人工审批' : '可直接执行' }} · 超时 {{ tool.timeoutMs }}ms</small>
            <small v-if="tool.requiredPermissions?.length">权限：{{ tool.requiredPermissions.join('、') }}</small>
          </div>
        </div>
      </section>

      <section class="governance-section panel" id="governance">
        <div class="panel-heading">
          <div><p class="eyebrow">CONTEXT / EVALUATION</p><h2>上下文与评测治理</h2></div>
          <button class="secondary-button" type="button" @click="showGovernance = !showGovernance">{{ showGovernance ? '收起' : '展开治理面板' }}</button>
        </div>
        <div v-if="showGovernance" class="governance-grid">
          <form class="governance-card" @submit.prevent="createDocument">
            <h3>添加授权知识文档</h3>
            <label class="field"><span>标题</span><input v-model="documentForm.title" required /></label>
            <label class="field"><span>内容</span><textarea v-model="documentForm.content" required rows="3"></textarea></label>
            <label class="field"><span>可见用户（逗号分隔，可留空）</span><input v-model="documentForm.allowedUsers" /></label>
            <button class="secondary-button" type="submit" :disabled="loading">保存文档</button>
            <small class="form-hint">当前 {{ documents.length }} 篇文档；模型检索前会先执行租户和用户过滤。</small>
          </form>
          <form class="governance-card" @submit.prevent="runQuickEvaluation">
            <h3>运行快速回归评测</h3>
            <label class="field"><span>报告名称</span><input v-model="evaluationForm.name" required /></label>
            <label class="field"><span>测试输入</span><textarea v-model="evaluationForm.input" required rows="2"></textarea></label>
            <label class="field"><span>期望包含</span><input v-model="evaluationForm.expectedContains" /></label>
            <button class="secondary-button" type="submit" :disabled="loading">执行评测</button>
            <small class="form-hint">历史报告 {{ evaluations.length }} 份；每份报告绑定模型、Prompt 和策略版本。</small>
          </form>
          <form class="governance-card policy-card" @submit.prevent="saveTenantPolicy">
            <div class="subsection-title"><h3>租户资源策略</h3><span v-if="tenantPolicy">{{ tenantPolicy.defaulted ? '平台默认' : '租户覆盖' }}</span></div>
            <p v-if="tenantPolicyError" class="policy-error">{{ tenantPolicyError }}</p>
            <label class="field"><span>最大活动 Run 数</span><input v-model.number="tenantPolicyForm.maxActiveRuns" type="number" min="1" required /></label>
            <label class="field"><span>单次最大步骤数</span><input v-model.number="tenantPolicyForm.maxStepsPerRun" type="number" min="1" required /></label>
            <label class="field"><span>最大输入字符数</span><input v-model.number="tenantPolicyForm.maxInputLength" type="number" min="1" required /></label>
            <label class="field"><span>单次最大预算</span><input v-model.number="tenantPolicyForm.maxBudget" type="number" min="0.000001" step="0.000001" required /></label>
            <label class="field"><span>每分钟创建 Run 数</span><input v-model.number="tenantPolicyForm.maxCreatesPerMinute" type="number" min="1" required /></label>
            <label class="field"><span>工具白名单（逗号分隔，留空表示全部）</span><input v-model="tenantPolicyForm.allowedTools" placeholder="例如：demo.echo" /></label>
            <div class="policy-actions"><button class="secondary-button" type="button" :disabled="loading" @click="loadTenantPolicy">读取策略</button><button class="secondary-button" type="submit" :disabled="loading">保存策略</button><button class="danger-button" type="button" :disabled="loading" @click="resetTenantPolicy">恢复默认</button></div>
            <small class="form-hint">策略只能收紧平台硬上限；最近 {{ tenantPolicyAudits.length }} 条变更已留痕。</small>
          </form>
          <form class="governance-card api-key-card" @submit.prevent="createManagedApiKey">
            <div class="subsection-title">
              <div><h3>API Key 生命周期</h3><span>数据库凭证</span></div>
              <button class="refresh-button" type="button" :disabled="loading" aria-label="刷新 API Key" @click="loadApiKeys">⟳</button>
            </div>
            <p v-if="apiKeyError" class="policy-error">{{ apiKeyError }}</p>
            <div class="api-key-create-grid">
              <label class="field"><span>租户 ID</span><input v-model="apiKeyForm.tenantId" required maxlength="128" /></label>
              <label class="field"><span>用户 ID</span><input v-model="apiKeyForm.userId" required maxlength="128" /></label>
              <label class="field api-key-expiry-field"><span>过期时间（可选）</span><input v-model="apiKeyForm.expiresAt" type="datetime-local" /></label>
              <label class="field api-key-permissions-field"><span>权限（逗号分隔）</span><input v-model="apiKeyForm.permissions" maxlength="2000" placeholder="例如：run.read, run.create" /></label>
            </div>
            <div class="policy-actions">
              <button class="secondary-button" type="submit" :disabled="loading">{{ loading ? '生成中…' : '生成数据库 API Key' }}</button>
              <button class="secondary-button" type="button" :disabled="loading" @click="loadApiKeys">刷新列表</button>
            </div>
            <div v-if="createdApiKeySecret" class="api-key-secret-banner">
              <div class="subsection-title"><strong>仅显示一次的明文密钥</strong><button class="icon-button" type="button" aria-label="关闭明文提示" @click="closeApiKeySecret">×</button></div>
              <code>{{ createdApiKeySecret }}</code>
              <div class="policy-actions"><button class="secondary-button" type="button" @click="copyApiKeySecret">复制明文</button><small>请保存到密码管理器；关闭后服务端不会再次返回。</small></div>
            </div>
            <div class="api-key-list">
              <div class="subsection-title"><h3>当前租户密钥</h3><span>{{ apiKeys.length }} keys</span></div>
              <div v-if="!apiKeys.length" class="muted-line">暂无数据库 API Key，或当前身份没有读取权限。</div>
              <div v-for="key in apiKeys" :key="key.id" class="api-key-row">
                <div class="api-key-row-main"><strong>{{ key.keyPrefix }}…</strong><small>{{ key.userId }} · 创建于 {{ formatDate(key.createdAt) }}</small></div>
                <div class="api-key-row-meta"><span class="api-key-status" :class="apiKeyStatusClass(key.status)">{{ apiKeyStatusLabel(key.status) }}</span><small>{{ key.expiresAt ? `到期 ${formatDate(key.expiresAt)}` : '永不过期' }}</small></div>
                <div class="api-key-row-permissions">{{ key.permissions?.length ? key.permissions.join('、') : '未授予接口权限' }}</div>
                <div class="api-key-row-actions">
                  <button v-if="key.status === 'ACTIVE'" class="secondary-button" type="button" :disabled="loading" @click="rotateManagedApiKey(key)">轮换</button>
                  <button class="danger-button" type="button" :disabled="loading || key.status !== 'ACTIVE'" @click="revokeManagedApiKey(key)">{{ key.status === 'ACTIVE' ? '立即撤销' : '已撤销' }}</button>
                </div>
              </div>
            </div>
            <div class="api-key-audits">
              <div class="subsection-title"><h3>生命周期审计</h3><span>最近 {{ apiKeyAudits.length }} 条</span></div>
              <div v-if="!apiKeyAudits.length" class="muted-line">暂无 API Key 生命周期事件</div>
              <div v-for="audit in apiKeyAudits.slice(0, 8)" :key="audit.id" class="api-key-audit-row"><span>{{ formatDate(audit.createdAt) }}</span><strong>{{ audit.eventType }}</strong><small>{{ audit.actorId }} · {{ audit.details }}</small></div>
            </div>
          </form>
        </div>
        <div v-if="showGovernance && evaluations.length" class="evaluation-list">
          <div v-for="report in evaluations.slice(0, 5)" :key="report.id" class="evaluation-row">
            <strong>{{ report.name }}</strong><span>{{ report.passedCases }}/{{ report.totalCases }} 通过</span><small>{{ report.promptVersion }} · {{ formatDate(report.createdAt) }}</small>
          </div>
        </div>
      </section>

      <footer class="footer">Ming Harness · 每次执行都可恢复、可解释、可审计、可限制</footer>
    </main>
  </div>
  </template>
</template>
