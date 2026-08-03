<script setup>
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import {
  Activity,
  Bot,
  ArrowUp,
  CircleAlert,
  Check,
  CircleDot,
  Command,
  FolderGit2,
  FolderOpen,
  ListChecks,
  MessageSquarePlus,
  Moon,
  PanelRight,
  Paperclip,
  PenLine,
  Plus,
  Settings2,
  RefreshCw,
  Send,
  Square,
  Sparkles,
  Sun,
  Wrench,
  X,
} from '@lucide/vue'
import { api } from './api'
import { highlightCode, languageFromPath, languageLabel, renderMarkdown } from './markdown'

// Monaco 只在打开项目文件或 Diff 审阅时加载，避免普通聊天首屏承担 3 MB+ 的编辑器包。
const MonacoEditor = defineAsyncComponent(() => import('./components/MonacoEditor.vue'))

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
const modelConfig = ref(null)
const modelConfigLoading = ref(false)
const modelConfigSaving = ref(false)
const modelConfigTesting = ref(false)
const modelConfigError = ref('')
const modelConfigTestResult = ref(null)
const showModelSettings = ref(false)
const modelConfigForm = reactive({
  enabled: false,
  baseUrl: '',
  modelName: '',
  apiKey: '',
  clearApiKey: false,
})
const modelProviderPreset = ref('custom')
const modelProviderPresets = [
  { id: 'openai', label: 'OpenAI', baseUrl: 'https://api.openai.com/v1', modelName: 'gpt-4o-mini' },
  { id: 'deepseek', label: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', modelName: 'deepseek-chat' },
  { id: 'qwen', label: '通义千问（兼容模式）', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelName: 'qwen-plus' },
  { id: 'ollama', label: '本机 Ollama', baseUrl: 'http://localhost:11434/v1', modelName: 'qwen2.5-coder' },
  { id: 'custom', label: '自定义 OpenAI 兼容服务', baseUrl: '', modelName: '' },
]
const modelConfigEditable = computed(() => !modelConfigError.value.startsWith('当前身份没有 model.configure'))
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
const conversationQuery = ref('')
const activeConversation = ref(null)
const showConversationRename = ref(false)
const conversationRenameValue = ref('')
const conversationRenaming = ref(false)
const conversationRenameInputRef = ref(null)
const chatInput = ref('')
const chatInputRef = ref(null)
const chatLoading = ref(false)
const chatSending = ref(false)
const chatCancellingRunId = ref('')
const copyingMessageId = ref('')
const retryingMessageId = ref('')
// 文件仅在点击发送时才上传，切换会话不会在后端留下未绑定的附件。
const chatAttachments = ref([])
const chatUploading = ref(false)
const chatDragActive = ref(false)
const chatAttachmentInput = ref(null)
const chatFolderInput = ref(null)
const showChatRun = ref(false)
const showRejectDialog = ref(false)
const rejectReason = ref('')
const rejectReasonInputRef = ref(null)
const showChatAgentSettings = ref(false)
const chatMaxTurns = ref(readChatMaxTurns())
const showCommandPalette = ref(false)
const commandQuery = ref('')
const commandSelectedIndex = ref(0)
const commandPaletteInputRef = ref(null)
const CHAT_DRAFT_STORAGE_KEY = 'mingHarnessChatDrafts'
const ACTIVE_CONVERSATION_STORAGE_KEY = 'mingHarnessActiveConversation'
const commandIconComponents = {
  'new-conversation': MessageSquarePlus,
  'focus-composer': ArrowUp,
  'rename-conversation': PenLine,
  'workspace-explorer': FolderOpen,
  'run-panel': Activity,
  'model-settings': Settings2,
  'toggle-console': PanelRight,
  'toggle-theme': Sun,
}
// 项目文件面板仅浏览当前会话已绑定的工作区，不会把绝对路径带到前端。
const showChatWorkspace = ref(false)
const workspaceExplorer = ref(null)
const workspaceExplorerLoading = ref(false)
const workspaceFilePreview = ref(null)
const workspaceFilePreviewLoading = ref(false)
const workspaceEditorRef = ref(null)
const workspaceEditorContent = ref('')
const workspaceEditorWritable = ref(false)
const workspaceEditorDirty = ref(false)
const workspaceEditorSaving = ref(false)
const workspaceEditorCopying = ref(false)
// Git 审阅沿用当前会话的工作区绑定，避免把磁盘路径或自由 Git 参数暴露给页面。
const workspaceGitReviewVisible = ref(false)
const workspaceGitReviewOpen = ref(false)
const workspaceGitStatus = ref(null)
const workspaceGitStatusLoading = ref(false)
const workspaceGitDiff = ref(null)
const workspaceGitDiffLoading = ref(false)
const workspaceGitReviewPath = ref('')
const workspaceGitReviewStaged = ref(false)
const workspaceGitDiffCopying = ref(false)
// 实时流只订阅当前查看的非终态 Run；HTTP 轮询仍用于网络异常后的兜底校验。
const runEventStreaming = ref(false)
const runEventConnectionState = ref('idle')
const networkOnline = ref(typeof navigator === 'undefined' ? true : navigator.onLine)
let conversationPollTimer
let chatHighlightTimer
let runEventAbortController
let runEventReconnectTimer
let runEventReconnectAttempt = 0
let auditEventsRefreshTimer
let auditEventsRefreshToken = 0
let auditEventsRefreshInFlight = false
let auditEventsRefreshQueuedRunId = ''
let workspaceExplorerLoadToken = 0
let workspacePreviewLoadToken = 0
let workspaceGitStatusLoadToken = 0
let workspaceGitDiffLoadToken = 0
// 记录连接所属 Run，避免聊天轮询读取到同一任务时重复中断并创建 SSE 连接。
let runEventStreamRunId
// 对话详情和 Run 详情都可能在用户快速点击后交错返回；只接受最后一次选择的结果。
let conversationSelectionToken = 0
let conversationListRequestToken = 0
let runDetailRequestToken = 0

function readTheme() {
  if (typeof window === 'undefined') return 'light'
  try {
    const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
    if (storedTheme === 'light' || storedTheme === 'dark') return storedTheme
  } catch {
    // 浏览器禁用本地存储时使用默认的浅色模式。
  }
  // Codex 风格以浅色工作台为默认入口；用户仍可手动切换深色主题。
  return 'light'
}

function applyTheme(nextTheme) {
  if (typeof document !== 'undefined') {
    document.documentElement.dataset.theme = nextTheme
    document.querySelector('meta[name="theme-color"]')?.setAttribute(
      'content',
      nextTheme === 'dark' ? '#171717' : '#ffffff',
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

function readStoredValue(key, fallback) {
  if (typeof window === 'undefined') return fallback
  try {
    return window.localStorage.getItem(key) || fallback
  } catch {
    return fallback
  }
}

// 在首屏渲染前同步主题，避免切换时出现短暂的错误背景色。
applyTheme(theme.value)

const form = reactive({
  tenantId: readStoredValue('harnessTenantId', import.meta.env.VITE_HARNESS_TENANT_ID || 'tenant-demo'),
  userId: readStoredValue('harnessUserId', import.meta.env.VITE_HARNESS_USER_ID || 'operator'),
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
  maxTurns: 1000,
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
  maxStepsPerRun: 1000,
  maxInputLength: 10000,
  maxBudget: 1000,
  maxCreatesPerMinute: 60,
  allowedTools: '',
})

// 创建表单只保存过期时间和权限，生成的明文密钥不会写入浏览器存储。
const apiKeyForm = reactive({
  tenantId: form.tenantId,
  userId: form.userId,
  permissions: 'run.read, run.create, run.execute, run.approve, run.cancel, audit.read, context.read, context.write, evaluation.read, evaluation.run, tool.read, workspace.read, workspace.manage, ops.read, model.configure, tenant.policy.read, tenant.policy.write, auth.key.read, auth.key.manage',
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
const canCancel = computed(() => ['QUEUED', 'RUNNING', 'WAITING_APPROVAL'].includes(selectedStatus.value))
const canApprove = computed(() => selectedStatus.value === 'WAITING_APPROVAL')
const canRetry = computed(() => ['FAILED', 'TIMED_OUT'].includes(selectedStatus.value))
const cancelActionLabel = computed(() => selectedStatus.value === 'WAITING_APPROVAL' ? '撤回审批' : '取消')
const infraOnline = computed(() => health.value?.status === 'UP')
const infraLabel = computed(() => {
  if (!health.value) return '检查基础设施'
  if (health.value.error) return health.value.error
  return infraOnline.value ? '基础设施在线' : '基础设施异常'
})
const modelLabel = computed(() => {
  const model = modelConfig.value || health.value?.model
  if (!model || !model.enabled) return '演示模型'
  return model.modelName ? `模型 ${model.modelName}` : '外部模型'
})
const modelStatusClass = computed(() => (modelConfig.value || health.value?.model)?.enabled ? 'health-up' : 'health-unknown')
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
const workspaceGitReviewEntries = computed(() => workspaceGitStatus.value?.entries || [])
const workspaceGitReviewParsed = computed(() => parseUnifiedGitDiff(workspaceGitDiff.value?.diff || ''))
const runPageLabel = computed(() => {
  if (!runPage.totalElements) return '0 条记录'
  return `第 ${runPage.page + 1} / ${runPage.totalPages} 页 · 共 ${runPage.totalElements} 条`
})
const canPreviousRunPage = computed(() => runPage.page > 0)
const canNextRunPage = computed(() => runPage.hasNext)
const chatMessages = computed(() => activeConversation.value?.messages || [])
const activeConversationId = computed(() => activeConversation.value?.conversation?.id || '')
const filteredConversations = computed(() => {
  const query = conversationQuery.value.trim().toLowerCase()
  if (!query) return conversations.value
  return conversations.value.filter((conversation) => [conversation.title, conversation.lastMessagePreview]
    .some((value) => String(value || '').toLowerCase().includes(query)))
})
const pendingChatMessage = computed(() => chatMessages.value
  .slice().reverse()
  .find((message) => message.role === 'ASSISTANT' && message.status === 'PENDING'))
const chatRunStatus = computed(() => {
  const runId = pendingChatMessage.value?.runId
  if (runId && selectedRun.value?.run?.id === runId) return selectedRun.value.run.status
  return pendingChatMessage.value ? 'RUNNING' : ''
})
const chatRunActivity = computed(() => {
  const runId = pendingChatMessage.value?.runId
  if (!runId) return ''
  if (selectedRun.value?.run?.id !== runId) return 'Agent 正在准备任务…'
  const steps = selectedRun.value.steps || []
  const activeStep = steps.find((step) => step.status === 'RUNNING')
    || steps.find((step) => step.status === 'WAITING_APPROVAL')
    || steps.find((step) => step.status === 'QUEUED')
  if (activeStep?.type === 'MODEL') {
    const fallbackStep = steps.slice().reverse().find((step) => isRecoverableToolFallback(step)
      && step.sequence < activeStep.sequence)
    if (fallbackStep?.name?.startsWith('workspace.git.')) {
      return 'Git 审阅不可用，Agent 正在改用文件工具…'
    }
    if (fallbackStep) return '文件定位未成功，Agent 正在重新浏览工作区…'
  }
  return agentActivityLabel(activeStep)
})
const chatUserMessages = computed(() => chatMessages.value
  .filter((message) => message.role === 'USER'))
const canSendChat = computed(() => Boolean(activeConversationId.value) && !chatSending.value && !chatUploading.value
  && !pendingChatMessage.value
  && (chatInput.value.trim().length > 0 || chatAttachments.value.length > 0))
// 发送接口返回 Run ID 后即可停止，不再等待右侧运行详情请求完成；详情尚未加载时
// 先按执行中展示，详情到达后仍由 canCancel 负责拦截终态 Run。
const canCancelChat = computed(() => {
  const runId = pendingChatMessage.value?.runId
  if (!runId) return false
  if (selectedRun.value?.run?.id !== runId) return true
  return canCancel.value
})
const commandPaletteItems = computed(() => [
  {
    id: 'new-conversation',
    label: '新建对话',
    description: '创建一条独立会话，继续使用当前工作区选择',
    keywords: 'new conversation chat session 对话 会话',
    icon: '＋',
    shortcut: '⌘ N',
    action: async () => {
      if (!chatMode.value) chatMode.value = true
      await nextTick()
      await createChatConversation()
    },
    disabled: chatSending.value || chatUploading.value,
  },
  {
    id: 'focus-composer',
    label: '聚焦消息输入框',
    description: '立即回到聊天输入，保留当前草稿',
    keywords: 'focus composer input message 输入 聚焦 草稿',
    icon: '↗',
    shortcut: '⌘ I',
    action: async () => {
      if (!chatMode.value) chatMode.value = true
      await nextTick()
      chatInputRef.value?.focus()
    },
    disabled: !activeConversationId.value || chatSending.value || chatUploading.value,
  },
  {
    id: 'rename-conversation',
    label: '重命名当前对话',
    description: '修改当前会话在侧边栏中的标题',
    keywords: 'rename conversation session title 重命名 对话 会话 标题',
    icon: '✎',
    action: beginConversationRename,
    disabled: !activeConversationId.value || conversationRenaming.value,
  },
  {
    id: 'workspace-explorer',
    label: showChatWorkspace.value ? '隐藏项目文件' : '打开项目文件',
    description: '查看当前会话绑定工作区的目录、文件和 Git 状态',
    keywords: 'workspace files project explorer code 项目 文件 工作区',
    icon: '▣',
    shortcut: '⌘ O',
    action: async () => {
      if (!chatMode.value) chatMode.value = true
      await nextTick()
      await toggleWorkspaceExplorer()
    },
    disabled: !workspaceExplorerAvailable.value,
  },
  {
    id: 'run-panel',
    label: showChatRun.value ? '隐藏当前 Run' : '查看当前 Run',
    description: '打开执行步骤、工具调用和审计事件',
    keywords: 'run execution steps audit trace 运行 执行 步骤 审计',
    icon: '◇',
    shortcut: '⌘ J',
    action: async () => {
      if (!chatMode.value) chatMode.value = true
      await nextTick()
      await toggleRunPanel()
    },
    disabled: !latestConversationRun(activeConversation.value),
  },
  {
    id: 'model-settings',
    label: '打开模型设置',
    description: '配置当前用户的新 Run 使用的模型连接',
    keywords: 'model provider api key settings 模型 供应商 设置 密钥',
    icon: '◈',
    shortcut: '⌘ ,',
    action: () => { showModelSettings.value = true },
    disabled: modelConfigLoading.value,
  },
  {
    id: 'toggle-console',
    label: chatMode.value ? '打开运行控制台' : '返回聊天工作台',
    description: chatMode.value ? '查看所有 Run、工具注册表和治理面板' : '回到连续 Agent 对话',
    keywords: 'console chat workspace runtime 控制台 聊天 工作台',
    icon: chatMode.value ? '▤' : '⌁',
    shortcut: '⌘ 1',
    action: () => { chatMode.value = !chatMode.value },
  },
  {
    id: 'toggle-theme',
    label: theme.value === 'dark' ? '切换到白天模式' : '切换到黑夜模式',
    description: '调整界面亮度，设置会保存在当前浏览器',
    keywords: 'theme dark light appearance 主题 黑夜 白天',
    icon: theme.value === 'dark' ? '☼' : '☾',
    shortcut: '⌘ ⇧ L',
    action: toggleTheme,
  },
].filter((command) => !command.disabled))

function commandIconComponent(commandId) {
  return commandIconComponents[commandId] || Command
}
const filteredCommandPaletteItems = computed(() => {
  const query = commandQuery.value.trim().toLowerCase()
  if (!query) return commandPaletteItems.value
  return commandPaletteItems.value.filter((command) => `${command.label} ${command.description} ${command.keywords}`.toLowerCase().includes(query))
})
const runEventStatusLabel = computed(() => ({
  connecting: '正在连接实时流…',
  connected: '实时执行',
  reconnecting: '实时流重连中…',
  offline: '网络已断开，等待恢复…',
}[runEventConnectionState.value] || ''))
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
    REJECTED: '已拒绝，Agent 调整中',
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

function activityValue(value, maximum = 46) {
  const text = String(value || '').trim()
  if (!text) return ''
  return text.length <= maximum ? text : `${text.slice(0, maximum - 1)}…`
}

function agentActivityLabel(step) {
  if (!step) return 'Agent 正在整理结果…'
  if (step.status === 'WAITING_APPROVAL') return `等待你审批：${step.name}`
  if (step.type === 'MODEL') return 'Agent 正在思考…'
  const input = decodeToolInput(step)
  const path = activityValue(input?.path)
  const query = activityValue(input?.query, 32)
  const command = activityValue(input?.command, 32)
  return {
    'workspace.list': '正在浏览工作区…',
    'workspace.search': query ? `正在搜索「${query}」…` : '正在搜索代码…',
    'workspace.read': path ? `正在读取 ${path}…` : '正在读取文件…',
    'workspace.edit': path ? `正在修改 ${path}…` : '正在修改文件…',
    'workspace.write': path ? `正在写入 ${path}…` : '正在写入文件…',
    'workspace.git.status': '正在检查 Git 状态…',
    'workspace.git.diff': path ? `正在核对 ${path}…` : '正在核对代码变更…',
    'workspace.exec': command ? `正在运行 ${command}…` : '正在运行命令…',
  }[step.name] || `正在使用 ${step.name}…`
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

function isRecoverableToolFallback(step) {
  if (!step?.output || step.status !== 'SUCCEEDED' || !String(step.name || '').startsWith('workspace.')) return false
  try {
    const parsed = JSON.parse(step.output)
    return parsed && parsed.recoverable === true && parsed.ok === false
  } catch {
    return false
  }
}

function decodeWorkspaceRecoverableFailure(step) {
  if (!step?.output || step.status !== 'SUCCEEDED' || !String(step.name || '').startsWith('workspace.')) return null
  try {
    const parsed = JSON.parse(step.output)
    return parsed && parsed.ok === false && parsed.recoverable === true ? parsed : null
  } catch {
    return null
  }
}

function decodeWorkspaceSearch(step) {
  if (!step || step.name !== 'workspace.search' || !step.output) return null
  try {
    const parsed = JSON.parse(step.output)
    return parsed && typeof parsed === 'object' && Array.isArray(parsed.matches) ? parsed : null
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

function chatStepPayload(value) {
  if (value == null || value === '') return ''
  const raw = String(value)
  try {
    return clipCodePreview(JSON.stringify(JSON.parse(raw), null, 2), 1400)
  } catch {
    return clipCodePreview(raw, 1400)
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

function activeConversationStorageScope() {
  return `${form.tenantId}:${form.userId}`
}

function readRememberedConversationId() {
  if (typeof window === 'undefined') return ''
  try {
    const value = JSON.parse(window.localStorage.getItem(ACTIVE_CONVERSATION_STORAGE_KEY) || 'null')
    if (!value || value.scope !== activeConversationStorageScope()) return ''
    return typeof value.conversationId === 'string' ? value.conversationId : ''
  } catch {
    return ''
  }
}

function rememberConversation(conversationId) {
  if (!conversationId || typeof window === 'undefined') return
  try {
    window.localStorage.setItem(ACTIVE_CONVERSATION_STORAGE_KEY, JSON.stringify({
      scope: activeConversationStorageScope(),
      conversationId,
    }))
  } catch {
    // 禁用本地存储时不影响当前页面内的会话切换。
  }
}

function readChatDrafts() {
  if (typeof window === 'undefined') return {}
  try {
    const parsed = JSON.parse(window.localStorage.getItem(CHAT_DRAFT_STORAGE_KEY) || '{}')
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function saveChatDraft(conversationId, value = chatInput.value) {
  if (!conversationId || typeof window === 'undefined') return
  try {
    const drafts = readChatDrafts()
    const content = String(value || '')
    if (content.trim()) drafts[conversationId] = content
    else delete drafts[conversationId]
    window.localStorage.setItem(CHAT_DRAFT_STORAGE_KEY, JSON.stringify(drafts))
  } catch {
    // 浏览器禁用本地存储时仍保留当前页面内的输入，不阻断发送。
  }
}

function loadChatDraft(conversationId) {
  const draft = readChatDrafts()[conversationId]
  return typeof draft === 'string' ? draft : ''
}

function readChatMaxTurns() {
  const value = Number(readStoredValue('harnessChatMaxTurns', '24'))
  return Number.isInteger(value) && value >= 1 && value <= 1000 ? value : 24
}

function persistChatMaxTurns() {
  const value = Math.max(1, Math.min(1000, Number(chatMaxTurns.value) || 1))
  chatMaxTurns.value = value
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem('harnessChatMaxTurns', String(value))
  } catch {
    // 浏览器禁用本地存储时仍保留当前页面内的 Agent 设置。
  }
}

function setChatMaxTurns(value) {
  chatMaxTurns.value = value
  persistChatMaxTurns()
}

function removeChatDraft(conversationId) {
  if (!conversationId || typeof window === 'undefined') return
  try {
    const drafts = readChatDrafts()
    delete drafts[conversationId]
    window.localStorage.setItem(CHAT_DRAFT_STORAGE_KEY, JSON.stringify(drafts))
  } catch {
    // 本地存储不可用时无需额外处理。
  }
}

function resizeChatInput() {
  const element = chatInputRef.value
  if (!element) return
  element.style.height = 'auto'
  element.style.height = `${Math.min(Math.max(element.scrollHeight, 78), 220)}px`
}

// 新会话或切换会话后直接进入可输入状态，减少一次额外点击；发送期间不抢回焦点。
function focusChatComposer() {
  void nextTick(() => {
    if (!chatMode.value || !activeConversationId.value || chatSending.value || chatUploading.value) return
    chatInputRef.value?.focus()
  })
}

function handleChatInput() {
  saveChatDraft(activeConversationId.value)
  resizeChatInput()
}

function setChatInput(value, focus = false) {
  chatInput.value = value
  saveChatDraft(activeConversationId.value, value)
  void nextTick(() => {
    resizeChatInput()
    if (focus) chatInputRef.value?.focus()
  })
}

function openCommandPalette() {
  if (showModelSettings.value) return
  showCommandPalette.value = true
  commandQuery.value = ''
  commandSelectedIndex.value = 0
  void nextTick(() => commandPaletteInputRef.value?.focus())
}

function closeCommandPalette() {
  showCommandPalette.value = false
  commandQuery.value = ''
  commandSelectedIndex.value = 0
}

function moveCommandSelection(delta) {
  const count = filteredCommandPaletteItems.value.length
  if (!count) return
  commandSelectedIndex.value = (commandSelectedIndex.value + delta + count) % count
}

function executeCommand(command) {
  if (!command || command.disabled) return
  closeCommandPalette()
  try {
    const result = command.action?.()
    if (result?.catch) result.catch((error) => { errorMessage.value = errorText(error) })
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

function executeSelectedCommand() {
  executeCommand(filteredCommandPaletteItems.value[commandSelectedIndex.value])
}

function handleChatKeydown(event) {
  if (event.isComposing || event.key !== 'Enter') return
  // Enter 保持快速发送；Shift+Enter 换行，Cmd/Ctrl+Enter 也可发送，方便从其他编辑器切换过来。
  if (event.shiftKey || event.altKey) return
  event.preventDefault()
  void sendChatMessage()
}

function handleChatGlobalKeydown(event) {
  const key = event.key.toLowerCase()
  if (!event.isComposing && (event.metaKey || event.ctrlKey) && key === 'k') {
    event.preventDefault()
    if (showCommandPalette.value) closeCommandPalette()
    else openCommandPalette()
    return
  }
  if (showCommandPalette.value) {
    if (event.key === 'Escape') {
      event.preventDefault()
      closeCommandPalette()
    } else if (event.key === 'ArrowDown') {
      event.preventDefault()
      moveCommandSelection(1)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      moveCommandSelection(-1)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      executeSelectedCommand()
    }
    return
  }
  if (event.isComposing || event.key !== 'Escape') return
  if (showModelSettings.value) {
    event.preventDefault()
    showModelSettings.value = false
    return
  }
  if (showRejectDialog.value) {
    event.preventDefault()
    closeRejectDialog()
    return
  }
  if (showConversationRename.value) {
    event.preventDefault()
    cancelConversationRename()
    return
  }
  if (!chatMode.value) return
  if (workspaceGitReviewOpen.value) {
    event.preventDefault()
    closeWorkspaceGitReviewDialog()
    return
  }
  if (canCancelChat.value) {
    event.preventDefault()
    void cancelChatRun()
  }
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

function auditEventLabel(eventType) {
  return {
    AGENT_TOOL_RECOVERABLE: 'Agent 工具可恢复降级',
    AGENT_VALIDATION_REQUIRED: '需要补充修改核验',
    AGENT_TOOL_CALL_REQUESTED: 'Agent 请求工具',
    AGENT_APPROVAL_FEEDBACK: '拒绝意见已反馈 Agent',
  }[eventType] || eventType
}

function canRetryChatMessage(message) {
  return message?.role === 'ASSISTANT'
    && Boolean(message.runId)
    && message.status === 'FAILED'
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

async function copyChatMessage(message) {
  if (!message?.content || copyingMessageId.value) return
  copyingMessageId.value = message.id
  try {
    await navigator.clipboard.writeText(message.content)
    noticeMessage.value = '助手回复已复制到剪贴板。'
  } catch {
    errorMessage.value = '复制失败，请检查浏览器剪贴板权限。'
  } finally {
    copyingMessageId.value = ''
  }
}

/** 失败气泡直接重试原 Run，保留同一轮上下文并立即恢复实时执行状态。 */
async function retryChatMessage(message) {
  if (!canRetryChatMessage(message) || retryingMessageId.value) return
  clearMessages()
  retryingMessageId.value = message.id
  try {
    const retried = await api.retryRun(message.runId)
    const retryStatus = retried?.run?.status
    noticeMessage.value = retryStatus === 'WAITING_APPROVAL'
      ? '本轮重试已进入人工审批'
      : ['QUEUED', 'RUNNING'].includes(retryStatus)
        ? '本轮已重新提交，Agent 正在执行'
        : retryStatus === 'SUCCEEDED'
          ? '本轮重试已完成'
          : `本轮重试状态：${statusLabel(retryStatus)}`
    await Promise.all([
      refreshActiveConversation(),
      selectRun(message.runId, false, false),
    ])
    showChatRun.value = false
    scrollChatToBottom()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    retryingMessageId.value = ''
  }
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

function beginConversationRename() {
  if (!activeConversationId.value || conversationRenaming.value) return
  conversationRenameValue.value = activeConversation.value?.conversation?.title || ''
  showConversationRename.value = true
  void nextTick(() => {
    conversationRenameInputRef.value?.focus()
    conversationRenameInputRef.value?.select()
  })
}

function cancelConversationRename() {
  showConversationRename.value = false
  conversationRenameValue.value = ''
}

function refreshRenamedConversation(detail) {
  if (!detail?.conversation?.id) return
  const isCurrentConversation = activeConversationId.value === detail.conversation.id
  if (isCurrentConversation) activeConversation.value = detail
  conversations.value = conversations.value
    .map((item) => item.id === detail.conversation.id ? detail.conversation : item)
    .sort((left, right) => new Date(right.updatedAt || 0) - new Date(left.updatedAt || 0))
}

async function renameActiveConversation() {
  if (!activeConversationId.value || conversationRenaming.value) return
  const title = conversationRenameValue.value.trim()
  if (!title) {
    errorMessage.value = '会话标题不能为空。'
    conversationRenameInputRef.value?.focus()
    return
  }
  clearMessages()
  conversationRenaming.value = true
  try {
    const renamed = await api.renameConversation(activeConversationId.value, title)
    refreshRenamedConversation(renamed)
    cancelConversationRename()
    noticeMessage.value = '对话标题已更新。'
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    conversationRenaming.value = false
  }
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
  const requestToken = ++conversationListRequestToken
  chatLoading.value = true
  try {
    const listedConversations = await api.listConversations()
    if (requestToken !== conversationListRequestToken) return
    conversations.value = listedConversations
    if (!conversations.value.length) {
      const created = await api.createConversation(newConversationPayload())
      if (requestToken !== conversationListRequestToken) return
      conversations.value = [created.conversation]
      activeConversation.value = created
      rememberConversation(created.conversation.id)
      focusChatComposer()
      return
    }
    const requestedId = preferredId || activeConversationId.value || readRememberedConversationId()
    const targetId = conversations.value.some((item) => item.id === requestedId)
      ? requestedId
      : conversations.value[0].id
    await selectConversation(targetId, false)
  } catch (error) {
    if (requestToken === conversationListRequestToken) errorMessage.value = errorText(error)
  } finally {
    if (requestToken === conversationListRequestToken) chatLoading.value = false
  }
}

async function createChatConversation() {
  if (!confirmWorkspaceEditorDiscard()) return null
  const creationToken = ++conversationSelectionToken
  runDetailRequestToken += 1
  stopRunEventStream()
  saveChatDraft(activeConversationId.value)
  clearMessages()
  try {
    const created = await api.createConversation(newConversationPayload())
    if (creationToken !== conversationSelectionToken) return null
    conversations.value = [created.conversation, ...conversations.value.filter((item) => item.id !== created.conversation.id)]
    activeConversation.value = created
    rememberConversation(created.conversation.id)
    setChatInput('')
    clearChatAttachments()
    stopRunEventStream()
    selectedRun.value = null
    auditEvents.value = []
    resetWorkspaceExplorerState()
    focusChatComposer()
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
  if (workspaceGitReviewVisible.value) {
    closeWorkspaceGitReview()
    return
  }
  workspaceGitReviewVisible.value = true
  await loadWorkspaceGitStatus()
}

function closeWorkspaceGitReview() {
  workspaceGitDiffLoadToken += 1
  workspaceGitReviewVisible.value = false
  workspaceGitReviewOpen.value = false
  workspaceGitReviewPath.value = ''
  workspaceGitReviewStaged.value = false
  workspaceGitDiff.value = null
  workspaceGitDiffLoading.value = false
}

function closeWorkspaceGitReviewDialog() {
  workspaceGitReviewOpen.value = false
}

async function openWorkspaceGitReviewDialog() {
  if (!workspaceGitReviewVisible.value) return
  workspaceGitReviewOpen.value = true
  if (workspaceGitReviewPath.value || workspaceGitDiffLoading.value) return
  const firstChange = workspaceGitReviewEntries.value.find((entry) => entry?.path)
  if (firstChange && !(firstChange.worktree === '?' && firstChange.index === '?')) {
    await loadWorkspaceGitDiff(firstChange.path, defaultGitDiffStage(firstChange))
  }
}

function openWorkspaceGitReviewFile(change, staged = defaultGitDiffStage(change)) {
  if (!change?.path) return
  if (change.worktree === '?' && change.index === '?') {
    previewUntrackedWorkspaceFile(change)
    return
  }
  workspaceGitReviewOpen.value = true
  void loadWorkspaceGitDiff(change.path, staged)
}

async function copyWorkspaceGitDiff() {
  if (!workspaceGitDiff.value?.diff || workspaceGitDiffCopying.value) return
  workspaceGitDiffCopying.value = true
  try {
    await navigator.clipboard.writeText(workspaceGitDiff.value.diff)
    noticeMessage.value = 'Diff 已复制到剪贴板。'
  } catch {
    errorMessage.value = '复制失败，请检查浏览器剪贴板权限。'
  } finally {
    workspaceGitDiffCopying.value = false
  }
}

/** 请求始终携带当前会话绑定的 workspaceId，后端会再次验证所属租户和用户。 */
async function loadWorkspaceDirectory(path = '.') {
  if (!workspaceExplorerAvailable.value) return
  if (!confirmWorkspaceEditorDiscard()) return
  const requestToken = ++workspaceExplorerLoadToken
  // 切换目录时使旧文件预览失效，避免异步响应把其他目录或会话的内容覆盖到面板。
  workspacePreviewLoadToken += 1
  workspaceExplorerLoading.value = true
  workspaceFilePreview.value = null
  workspaceEditorContent.value = ''
  workspaceEditorWritable.value = false
  workspaceEditorDirty.value = false
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
  workspaceGitDiffLoading.value = false
  try {
    const result = await api.workspaceGitStatus({ workspaceId: activeConversationWorkspaceId.value })
    if (requestToken === workspaceGitStatusLoadToken) {
      workspaceGitStatus.value = result
      const firstChange = result.entries?.find((entry) => entry?.path)
      const selectedChange = result.entries?.find((entry) => entry?.path === workspaceGitReviewPath.value)
      if (!selectedChange && workspaceGitReviewPath.value) {
        workspaceGitReviewPath.value = ''
        workspaceGitReviewStaged.value = false
      }
      const changeToLoad = selectedChange || firstChange
      if (workspaceGitReviewOpen.value && changeToLoad
          && !(changeToLoad.worktree === '?' && changeToLoad.index === '?')) {
        const keepSelectedStage = selectedChange && workspaceGitReviewStaged.value
          && selectedChange.index !== ' ' && selectedChange.index !== '?'
        void loadWorkspaceGitDiff(changeToLoad.path, keepSelectedStage || defaultGitDiffStage(changeToLoad))
      }
    }
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
  workspaceGitReviewPath.value = path
  workspaceGitReviewStaged.value = staged
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
  workspaceEditorContent.value = ''
  workspaceEditorWritable.value = false
  workspaceEditorDirty.value = false
  workspaceGitStatus.value = null
  workspaceGitDiff.value = null
  workspaceGitDiffLoading.value = false
  workspaceGitDiffCopying.value = false
  workspaceGitReviewPath.value = ''
  workspaceGitReviewStaged.value = false
  workspaceGitReviewVisible.value = false
  workspaceGitReviewOpen.value = false
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

/** 将受安全过滤后的 unified diff 转成 Monaco Diff Editor 所需的两份文本。 */
function parseUnifiedGitDiff(rawDiff) {
  if (!rawDiff) return { original: '', modified: '', additions: 0, deletions: 0, hasChanges: false }
  const original = []
  const modified = []
  let additions = 0
  let deletions = 0
  let inHunk = false
  for (const line of String(rawDiff).split(/\r?\n/)) {
    if (line.startsWith('@@')) {
      inHunk = true
      continue
    }
    if (!inHunk || line.startsWith('\\ No newline at end of file')) continue
    if (line.startsWith('+++') || line.startsWith('---')) continue
    if (line.startsWith('+')) {
      modified.push(line.slice(1))
      additions += 1
    } else if (line.startsWith('-')) {
      original.push(line.slice(1))
      deletions += 1
    } else if (line.startsWith(' ')) {
      const context = line.slice(1)
      original.push(context)
      modified.push(context)
    }
  }
  return {
    original: original.join('\n'),
    modified: modified.join('\n'),
    additions,
    deletions,
    hasChanges: additions > 0 || deletions > 0,
  }
}

function defaultGitDiffStage(change) {
  return change?.worktree === ' ' && change?.index !== ' ' && change?.index !== '?'
}

async function previewWorkspaceFile(entry) {
  if (!entry || entry.directory || !workspaceExplorerAvailable.value) return
  if (!confirmWorkspaceEditorDiscard()) return
  const requestToken = ++workspacePreviewLoadToken
  workspaceFilePreviewLoading.value = true
  workspaceEditorDirty.value = false
  try {
    let result
    let writable = true
    try {
      result = await api.readWorkspaceEditorFile({
        workspaceId: activeConversationWorkspaceId.value,
        path: entry.path,
      })
    } catch {
      // 没有 workspace.write 时退回原来的脱敏只读预览，聊天浏览能力不被编辑权限阻断。
      writable = false
      result = await api.readWorkspaceFile({
        workspaceId: activeConversationWorkspaceId.value,
        path: entry.path,
      })
    }
    if (requestToken === workspacePreviewLoadToken) {
      workspaceFilePreview.value = result
      workspaceEditorContent.value = result.content || ''
      workspaceEditorWritable.value = writable && !result.truncated && !result.redacted
      workspaceEditorDirty.value = false
    }
  } catch (error) {
    if (requestToken === workspacePreviewLoadToken) errorMessage.value = errorText(error)
  } finally {
    if (requestToken === workspacePreviewLoadToken) workspaceFilePreviewLoading.value = false
  }
}

function confirmWorkspaceEditorDiscard() {
  if (!workspaceEditorDirty.value || typeof window === 'undefined') return true
  return window.confirm('当前文件有未保存修改，继续操作会丢弃这些修改。是否继续？')
}

function handleWorkspaceBeforeUnload(event) {
  if (!workspaceEditorDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

async function saveWorkspaceFile() {
  if (!workspaceFilePreview.value || !workspaceEditorWritable.value
      || !workspaceEditorDirty.value || workspaceEditorSaving.value) return
  const previewToken = workspacePreviewLoadToken
  const previewPath = workspaceFilePreview.value.path
  workspaceEditorSaving.value = true
  clearMessages()
  try {
    const result = await api.saveWorkspaceEditorFile({
      workspaceId: activeConversationWorkspaceId.value,
      path: workspaceFilePreview.value.path,
      content: workspaceEditorContent.value,
      expectedSha256: workspaceFilePreview.value.sha256,
    })
    // 用户可能在保存请求返回前切换了会话、目录或文件；旧响应不能覆盖新编辑器状态。
    if (previewToken !== workspacePreviewLoadToken || workspaceFilePreview.value?.path !== previewPath) return
    workspaceFilePreview.value = { ...workspaceFilePreview.value, ...result, content: workspaceEditorContent.value }
    workspaceEditorDirty.value = false
    noticeMessage.value = `已保存 ${result.path}`
    if (workspaceGitReviewVisible.value) void loadWorkspaceGitStatus()
  } catch (error) {
    if (previewToken !== workspacePreviewLoadToken) return
    errorMessage.value = error.code === 'WORKSPACE_FILE_CHANGED'
      ? '文件已被 Agent 或其他进程修改，请重新打开后再保存。'
      : errorText(error)
  } finally {
    workspaceEditorSaving.value = false
  }
}

async function copyWorkspaceFile() {
  if (!workspaceFilePreview.value || workspaceEditorCopying.value) return
  workspaceEditorCopying.value = true
  try {
    if (workspaceEditorRef.value?.copy) {
      await workspaceEditorRef.value.copy()
    } else {
      await navigator.clipboard.writeText(workspaceEditorContent.value)
    }
    noticeMessage.value = '代码已复制到剪贴板。'
  } catch {
    errorMessage.value = '复制失败，请检查浏览器剪贴板权限。'
  } finally {
    workspaceEditorCopying.value = false
  }
}

async function selectConversation(conversationId, announce = true) {
  if (!conversationId) return
  const changingConversation = conversationId !== activeConversationId.value
  if (changingConversation && !confirmWorkspaceEditorDiscard()) return
  if (changingConversation && showConversationRename.value) cancelConversationRename()
  const selectionToken = ++conversationSelectionToken
  if (changingConversation) {
    saveChatDraft(activeConversationId.value)
    // 先断开旧 Run 的实时流并清掉详情，避免新会话加载期间仍显示旧项目的执行状态。
    runDetailRequestToken += 1
    stopRunEventStream()
    selectedRun.value = null
    auditEvents.value = []
  }
  if (announce) clearMessages()
  if (changingConversation) {
    clearChatAttachments()
  }
  chatLoading.value = true
  try {
    const detail = await api.getConversation(conversationId)
    if (selectionToken !== conversationSelectionToken) return
    activeConversation.value = detail
    rememberConversation(conversationId)
    setChatInput(loadChatDraft(conversationId))
    const runId = latestConversationRun(detail)
    if (runId) await selectRun(runId, false, false)
    else {
      stopRunEventStream()
      selectedRun.value = null
      auditEvents.value = []
    }
    if (selectionToken !== conversationSelectionToken) return
    const shouldReloadWorkspace = showChatWorkspace.value
    resetWorkspaceExplorerState({ keepPanel: shouldReloadWorkspace })
    if (showChatWorkspace.value) void loadWorkspaceDirectory('.')
    scrollChatToBottom()
    focusChatComposer()
  } catch (error) {
    if (selectionToken === conversationSelectionToken) errorMessage.value = errorText(error)
  } finally {
    if (selectionToken === conversationSelectionToken) chatLoading.value = false
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
  persistChatMaxTurns()
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
  removeChatDraft(conversationId)
  setChatInput('')
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
      maxTurns: chatMaxTurns.value,
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
      saveChatDraft(conversationId, typedContent)
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

async function cancelChatRun() {
  if (!canCancelChat.value) return
  const runId = pendingChatMessage.value.runId
  if (chatCancellingRunId.value === runId) return
  clearMessages()
  chatCancellingRunId.value = runId
  try {
    await api.cancelRun(runId)
    noticeMessage.value = chatRunStatus.value === 'WAITING_APPROVAL' ? '已撤回当前审批请求' : '已停止当前 Agent 执行'
    await refreshActiveConversation()
    await selectRun(runId, false, false)
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    if (chatCancellingRunId.value === runId) chatCancellingRunId.value = ''
  }
}

function syncSelectedRunAfterAction(detail) {
  if (!detail?.run?.id) return
  selectedRun.value = detail
  if (isTerminal(detail.run.status)) stopRunEventStream()
  else startRunEventStream(detail.run.id)
  void api.listAuditEvents(detail.run.id).then((events) => {
    if (selectedRun.value?.run?.id === detail.run.id) auditEvents.value = events
  }).catch(() => {
    // 主动作已经完成，审计列表等待下一次刷新即可，不覆盖当前状态反馈。
  })
}

async function pollConversation() {
  if (!networkOnline.value || !activeConversationId.value || chatSending.value || !pendingChatMessage.value) return
  const conversationId = activeConversationId.value
  const selectionToken = conversationSelectionToken
  try {
    const detail = await api.getConversation(conversationId)
    if (selectionToken !== conversationSelectionToken || activeConversationId.value !== conversationId) return
    activeConversation.value = detail
    const runId = latestConversationRun(detail)
    if (runId && selectedRun.value?.run?.id !== runId) {
      await selectRun(runId, false, false)
    }
    if (selectionToken !== conversationSelectionToken || activeConversationId.value !== conversationId) return
    if (!detail.messages.some((message) => message.status === 'PENDING')) {
      await loadConversations(conversationId)
    }
    scrollChatToBottom()
  } catch (error) {
    if (selectionToken === conversationSelectionToken && activeConversationId.value === conversationId) {
      errorMessage.value = errorText(error)
    }
  }
}

// 审批、取消或重试从运行面板触发后，主动刷新会话，确保助手气泡与 Run 终态同步。
async function refreshActiveConversation() {
  const conversationId = activeConversationId.value
  if (!conversationId) return
  const selectionToken = conversationSelectionToken
  const detail = await api.getConversation(conversationId)
  if (selectionToken !== conversationSelectionToken || activeConversationId.value !== conversationId) return
  activeConversation.value = detail
  const runId = latestConversationRun(detail)
  if (runId && selectedRun.value?.run?.id !== runId) {
    await selectRun(runId, false, false)
  }
  if (selectionToken !== conversationSelectionToken || activeConversationId.value !== conversationId) return
  conversations.value = await api.listConversations()
  if (selectionToken !== conversationSelectionToken || activeConversationId.value !== conversationId) return
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

async function loadModelConfig() {
  modelConfigLoading.value = true
  modelConfigError.value = ''
  try {
    const value = await api.getModelConfig()
    modelConfig.value = value
    Object.assign(modelConfigForm, {
      enabled: Boolean(value?.enabled),
      baseUrl: value?.baseUrl || '',
      modelName: value?.modelName || '',
      apiKey: '',
      clearApiKey: false,
    })
    modelProviderPreset.value = matchingModelProviderPreset(value?.baseUrl, value?.modelName)
    modelConfigTestResult.value = null
  } catch (error) {
    modelConfigError.value = error.code === 'PERMISSION_DENIED'
      ? '当前身份没有 model.configure 权限，无法修改模型连接。'
      : errorText(error)
  } finally {
    modelConfigLoading.value = false
  }
}

function matchingModelProviderPreset(baseUrl, modelName) {
  const normalizedUrl = String(baseUrl || '').replace(/\/+$/, '')
  return modelProviderPresets.find((preset) => preset.id !== 'custom'
    && preset.baseUrl === normalizedUrl
    && (!modelName || preset.modelName === modelName))?.id || 'custom'
}

function applyModelProviderPreset() {
  const preset = modelProviderPresets.find((item) => item.id === modelProviderPreset.value)
  if (!preset || preset.id === 'custom') return
  modelConfigForm.baseUrl = preset.baseUrl
  modelConfigForm.modelName = preset.modelName
  modelConfigForm.clearApiKey = false
  modelConfigTestResult.value = null
  modelConfigError.value = ''
}

function useCustomModelProvider() {
  modelProviderPreset.value = 'custom'
}

async function saveModelConfig() {
  if (modelConfigSaving.value) return
  clearMessages()
  modelConfigSaving.value = true
  modelConfigError.value = ''
  modelConfigTestResult.value = null
  try {
    const value = await api.updateModelConfig({
      enabled: Boolean(modelConfigForm.enabled),
      baseUrl: modelConfigForm.baseUrl.trim(),
      modelName: modelConfigForm.modelName.trim(),
      apiKey: modelConfigForm.apiKey,
      clearApiKey: Boolean(modelConfigForm.clearApiKey),
    })
    modelConfig.value = value
    modelConfigForm.apiKey = ''
    modelConfigForm.clearApiKey = false
    noticeMessage.value = '模型连接设置已保存；后续新 Run 会使用该配置，正在执行的 Run 保持不变。'
    showModelSettings.value = false
  } catch (error) {
    modelConfigError.value = errorText(error)
  } finally {
    modelConfigSaving.value = false
  }
}

async function testModelConfig() {
  if (modelConfigTesting.value || modelConfigSaving.value || !modelConfigForm.enabled) return
  modelConfigError.value = ''
  modelConfigTestResult.value = null
  modelConfigTesting.value = true
  try {
    modelConfigTestResult.value = await api.testModelConfig({
      enabled: true,
      baseUrl: modelConfigForm.baseUrl.trim(),
      modelName: modelConfigForm.modelName.trim(),
      apiKey: modelConfigForm.apiKey,
      clearApiKey: Boolean(modelConfigForm.clearApiKey),
    })
  } catch (error) {
    modelConfigTestResult.value = {
      success: false,
      status: 'FAILED',
      message: errorText(error),
      modelName: modelConfigForm.modelName.trim(),
      latencyMs: 0,
    }
  } finally {
    modelConfigTesting.value = false
  }
}

async function resetModelConfig() {
  if (modelConfigSaving.value) return
  if (typeof window !== 'undefined'
    && !window.confirm('恢复环境默认模型吗？当前用户保存的模型地址和密钥会被删除。')) return
  clearMessages()
  modelConfigSaving.value = true
  modelConfigError.value = ''
  try {
    const value = await api.resetModelConfig()
    modelConfig.value = value
    Object.assign(modelConfigForm, {
      enabled: Boolean(value?.enabled),
      baseUrl: value?.baseUrl || '',
      modelName: value?.modelName || '',
      apiKey: '',
      clearApiKey: false,
    })
    modelProviderPreset.value = matchingModelProviderPreset(value?.baseUrl, value?.modelName)
    noticeMessage.value = '已恢复环境默认模型设置。'
    showModelSettings.value = false
  } catch (error) {
    modelConfigError.value = errorText(error)
  } finally {
    modelConfigSaving.value = false
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
  const requestToken = ++runDetailRequestToken
  cancelScheduledAuditEventsRefresh()
  if (showLoading) detailLoading.value = true
  if (announce) clearMessages()
  try {
    const [detail, events] = await Promise.all([api.getRun(runId), api.listAuditEvents(runId)])
    if (requestToken !== runDetailRequestToken) return
    selectedRun.value = detail
    auditEvents.value = events
    startRunEventStream(runId)
  } catch (error) {
    if (requestToken === runDetailRequestToken) errorMessage.value = errorText(error)
  } finally {
    if (showLoading && requestToken === runDetailRequestToken) detailLoading.value = false
  }
}

function handleNetworkOffline() {
  networkOnline.value = false
  const runId = runEventStreamRunId || selectedRun.value?.run?.id
  if (!runId || isTerminal(selectedStatus.value)) {
    if (runEventConnectionState.value === 'connecting' || runEventConnectionState.value === 'reconnecting') {
      stopRunEventStream()
      runEventConnectionState.value = 'offline'
    }
    return
  }
  stopRunEventStream()
  runEventConnectionState.value = 'offline'
}

function handleNetworkOnline() {
  networkOnline.value = true
  if (!selectedRun.value || isTerminal(selectedStatus.value)) return
  if (runEventStreaming.value) return
  runEventReconnectAttempt = 0
  startRunEventStream(selectedRun.value.run.id, true)
}

/** SSE 快照可能高频到达；合并审计读取并校验请求序号，避免旧响应覆盖当前 Run。 */
function cancelScheduledAuditEventsRefresh() {
  auditEventsRefreshToken += 1
  window.clearTimeout(auditEventsRefreshTimer)
  auditEventsRefreshTimer = undefined
  auditEventsRefreshQueuedRunId = ''
}

function scheduleAuditEventsRefresh(runId, immediate = false) {
  if (!runId) return
  const requestToken = ++auditEventsRefreshToken
  window.clearTimeout(auditEventsRefreshTimer)
  if (auditEventsRefreshInFlight) {
    auditEventsRefreshQueuedRunId = runId
    return
  }
  auditEventsRefreshTimer = window.setTimeout(async () => {
    if (requestToken !== auditEventsRefreshToken || selectedRun.value?.run?.id !== runId) return
    auditEventsRefreshInFlight = true
    try {
      const events = await api.listAuditEvents(runId)
      if (requestToken === auditEventsRefreshToken && selectedRun.value?.run?.id === runId) {
        auditEvents.value = events
      }
    } catch {
      // 运行详情仍可继续使用；下一次快照或 HTTP 轮询会再次尝试读取审计。
    } finally {
      auditEventsRefreshInFlight = false
      const queuedRunId = auditEventsRefreshQueuedRunId
      auditEventsRefreshQueuedRunId = ''
      if (queuedRunId) scheduleAuditEventsRefresh(queuedRunId, true)
    }
  }, immediate ? 0 : 250)
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
  runEventConnectionState.value = 'idle'
}

/** 只保留一个当前 Run 的实时连接，切换对话或控制台条目时立即关闭旧连接。 */
function startRunEventStream(runId, reconnecting = false) {
  if (!runId || isTerminal(selectedRun.value?.run?.status)) {
    // 切到终态任务也必须关闭此前其他 Run 的连接。
    stopRunEventStream()
    return
  }
  if (!networkOnline.value) {
    stopRunEventStream()
    runEventConnectionState.value = 'offline'
    return
  }
  // 同一 Run 的 SSE 已建立时保持连接，避免聊天轮询每 1.2 秒触发一次重连。
  if (runEventStreamRunId === runId && runEventAbortController && !runEventAbortController.signal.aborted) {
    return
  }
  stopRunEventStream()
  if (!reconnecting) runEventReconnectAttempt = 0
  const controller = new AbortController()
  runEventAbortController = controller
  runEventStreamRunId = runId
  runEventStreaming.value = true
  runEventConnectionState.value = reconnecting ? 'reconnecting' : 'connecting'
  void api.streamRunEvents(runId, {
    signal: controller.signal,
    onEvent: ({ event, data }) => {
      if ((event !== 'snapshot' && event !== 'run') || data?.run?.id !== runId) return
      if (selectedRun.value?.run?.id !== runId) return
      runEventConnectionState.value = 'connected'
      runEventReconnectAttempt = 0
      selectedRun.value = data
      applyStreamingAssistantContent(runId, data)
      if (latestStreamingModelContent(data)) scrollChatToBottom()
      // 审计记录不放入 SSE 正文，按快照变化增量刷新，避免把额外敏感字段扩大到新接口。
      scheduleAuditEventsRefresh(runId, isTerminal(data.run.status))
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
      if (!networkOnline.value) {
        runEventConnectionState.value = 'offline'
        return
      }
      runEventConnectionState.value = 'reconnecting'
      const delay = Math.min(1000 * (2 ** Math.min(runEventReconnectAttempt, 4)), 15000)
      runEventReconnectAttempt += 1
      runEventReconnectTimer = window.setTimeout(() => startRunEventStream(runId, true), delay)
    } else {
      runEventConnectionState.value = 'idle'
    }
  })
}

async function refreshAfterTerminalRunEvent(runId) {
  const conversationId = activeConversationId.value
  const selectionToken = conversationSelectionToken
  try {
    const work = [loadRunsPage(), api.dashboardSummary()]
    if (conversationId && latestConversationRun(activeConversation.value) === runId) {
      work.push(api.getConversation(conversationId), api.listConversations())
    }
    const results = await Promise.all(work)
    summary.value = results[1]
    if (results.length > 2
      && selectionToken === conversationSelectionToken
      && activeConversationId.value === conversationId) {
      activeConversation.value = results[2]
      conversations.value = results[3]
      scrollChatToBottom()
    }
  } catch {
    // 下一轮轮询会恢复列表或消息气泡，不覆盖用户当前可见的 Run 详情。
  }
}

async function pollSelectedRun() {
  if (!networkOnline.value || !selectedRun.value || isTerminal(selectedStatus.value)) return
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
    const approved = await api.approveRun(selectedRun.value.run.id)
    syncSelectedRunAfterAction(approved)
    noticeMessage.value = '审批已通过，Run 已继续执行'
    await loadDashboard()
    await refreshActiveConversation()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

function openRejectDialog() {
  if (!selectedRun.value || !canApprove.value || loading.value) return
  clearMessages()
  rejectReason.value = ''
  showRejectDialog.value = true
  void nextTick(() => rejectReasonInputRef.value?.focus())
}

function closeRejectDialog() {
  if (loading.value) return
  showRejectDialog.value = false
  rejectReason.value = ''
}

async function rejectSelectedRun() {
  if (!selectedRun.value || !canApprove.value) {
    closeRejectDialog()
    return
  }
  clearMessages()
  loading.value = true
  try {
    const reason = rejectReason.value.trim() || '控制台人工拒绝'
    const rejected = await api.rejectRun(selectedRun.value.run.id, reason)
    syncSelectedRunAfterAction(rejected)
    showRejectDialog.value = false
    rejectReason.value = ''
    noticeMessage.value = rejected?.run?.status === 'RUNNING'
      ? '审批已拒绝，Agent 正在根据意见调整方案'
      : '审批已拒绝，Run 已结束'
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
    syncSelectedRunAfterAction(retried)
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
    const started = await api.startRun(selectedRun.value.run.id)
    syncSelectedRunAfterAction(started)
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
    const runId = selectedRun.value.run.id
    const wasWaitingForApproval = selectedStatus.value === 'WAITING_APPROVAL'
    await api.cancelRun(runId)
    await selectRun(runId, false, false)
    noticeMessage.value = wasWaitingForApproval ? '审批请求已撤回' : 'Run 已取消'
    await loadDashboard()
    await refreshActiveConversation()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  window.addEventListener('beforeunload', handleWorkspaceBeforeUnload)
  window.addEventListener('keydown', handleChatGlobalKeydown)
  window.addEventListener('offline', handleNetworkOffline)
  window.addEventListener('online', handleNetworkOnline)
  syncActiveConsoleSectionFromHash()
  window.addEventListener('hashchange', syncActiveConsoleSectionFromHash)
  if (desktopWorkspaceAvailable.value) {
    api.configureDesktopWorkspaceDrop()
    api.onDesktopWorkspaceDropped((result) => {
      void handleDesktopWorkspaceDropped(result)
    })
  }
  await Promise.all([loadDashboard(), loadHealth(), loadModelConfig(), loadWorkspace(), loadTenantPolicy(), loadApiKeys(), loadLocalWorkspaces()])
  await loadConversations()
  runPollTimer = window.setInterval(pollSelectedRun, 1500)
  conversationPollTimer = window.setInterval(pollConversation, 1200)
  healthPollTimer = window.setInterval(loadHealth, 10000)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleWorkspaceBeforeUnload)
  window.removeEventListener('keydown', handleChatGlobalKeydown)
  window.removeEventListener('offline', handleNetworkOffline)
  window.removeEventListener('online', handleNetworkOnline)
  window.removeEventListener('hashchange', syncActiveConsoleSectionFromHash)
  stopRunEventStream()
  api.clearDesktopWorkspaceDropListener()
  window.clearInterval(runPollTimer)
  window.clearInterval(conversationPollTimer)
  window.clearInterval(healthPollTimer)
  window.clearTimeout(chatHighlightTimer)
  cancelScheduledAuditEventsRefresh()
})
</script>

<template>
  <template v-if="chatMode">
    <div class="chat-app">
      <header class="chat-topbar">
        <div class="chat-brand">
          <div class="brand-mark" aria-hidden="true"><Sparkles :size="17" :stroke-width="1.8" /></div>
          <div><strong>Ming Harness</strong><span>CODE AGENT WORKSPACE</span></div>
        </div>
        <div class="chat-topbar-actions">
          <span class="chat-identity">{{ form.tenantId }} / {{ form.userId }}</span>
          <span class="chat-health" :class="infraOnline ? 'health-up' : 'health-warning'"><i></i>{{ infraLabel }}</span>
          <span class="chat-model-status" :class="modelStatusClass" :title="modelConfig?.enabled ? `当前用户模型：${modelConfig.modelName || '外部模型'}` : '当前使用本地演示模型，不会访问外部模型服务'"><i></i>{{ modelLabel }}</span>
          <button class="secondary-button chat-console-button" type="button" title="打开模型设置" @click="showModelSettings = true"><Settings2 :size="15" />模型设置</button>
          <button class="command-palette-trigger" type="button" title="打开命令面板（⌘/Ctrl + K）" @click="openCommandPalette"><Command :size="14" /><span>⌘K</span><em>命令</em></button>
          <button class="theme-toggle" type="button" :aria-label="theme === 'dark' ? '切换到白天模式' : '切换到黑夜模式'" @click="toggleTheme">
            <Sun v-if="theme === 'dark'" :size="15" aria-hidden="true" /><Moon v-else :size="15" aria-hidden="true" />{{ theme === 'dark' ? '白天' : '黑夜' }}
          </button>
          <button class="secondary-button chat-console-button" type="button" title="打开运行控制台" @click="chatMode = false"><PanelRight :size="15" />运行控制台</button>
        </div>
      </header>

      <div v-if="errorMessage" class="message error-message chat-message-banner">{{ errorMessage }}</div>
      <div v-if="noticeMessage" class="message notice-message chat-message-banner">{{ noticeMessage }}</div>

      <div class="chat-layout">
        <aside class="conversation-sidebar">
          <nav class="chat-primary-nav" aria-label="工作台导航">
            <button class="chat-primary-nav-item chat-primary-nav-item-primary" type="button" :disabled="chatLoading || chatSending || chatUploading" @click="createChatConversation">
              <MessageSquarePlus :size="15" /><span>新对话</span><kbd>⌘N</kbd>
            </button>
            <button class="chat-primary-nav-item" type="button" @click="chatMode = false; setActiveConsoleSection('runtime')">
              <CircleDot :size="15" /><span>运行中心</span>
            </button>
            <button class="chat-primary-nav-item" type="button" @click="chatMode = false; setActiveConsoleSection('tools')">
              <Wrench :size="15" /><span>工具注册</span>
            </button>
            <button class="chat-primary-nav-item" type="button" @click="chatMode = false; setActiveConsoleSection('audit')">
              <Check :size="15" /><span>审计追踪</span>
            </button>
          </nav>
          <div class="conversation-sidebar-heading">
            <div><p class="eyebrow">RECENT CHATS</p><h2>最近对话</h2></div>
          </div>
          <label class="conversation-search">
            <span class="sr-only">搜索对话</span>
            <input v-model="conversationQuery" type="search" placeholder="搜索对话…" aria-label="搜索对话" @keydown.esc="conversationQuery = ''" />
            <button v-if="conversationQuery" type="button" aria-label="清除对话搜索" @click="conversationQuery = ''"><X :size="14" /></button>
          </label>
          <div v-if="chatLoading && !conversations.length" class="chat-sidebar-empty">正在读取对话…</div>
          <div v-else-if="!conversations.length" class="chat-sidebar-empty">还没有对话</div>
          <div v-else-if="!filteredConversations.length" class="chat-sidebar-empty">没有匹配的对话<br /><small>试试标题或最近消息中的关键词</small></div>
          <div v-else class="conversation-list">
            <button
              v-for="conversation in filteredConversations"
              :key="conversation.id"
              class="conversation-row"
              :class="{ active: conversation.id === activeConversationId }"
              type="button"
              :disabled="chatSending || chatUploading"
              @click="selectConversation(conversation.id)"
            >
              <span class="conversation-row-icon" aria-hidden="true"><Bot :size="15" /></span>
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
              <form v-if="showConversationRename" class="conversation-rename-form" @submit.prevent="renameActiveConversation">
                <input ref="conversationRenameInputRef" v-model="conversationRenameValue" maxlength="255" :disabled="conversationRenaming" aria-label="对话标题" @keydown.esc.prevent="cancelConversationRename" />
                <button class="secondary-button" type="button" :disabled="conversationRenaming" @click="cancelConversationRename">取消</button>
                <button class="primary-button" type="submit" :disabled="conversationRenaming">{{ conversationRenaming ? '保存中…' : '保存' }}</button>
              </form>
              <h1 v-else>{{ activeConversation?.conversation?.title || '新的对话' }}</h1>
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
              <span
                v-if="runEventConnectionState !== 'idle' && !isTerminal(selectedStatus)"
                class="chat-live-indicator"
                :class="`chat-live-${runEventConnectionState}`"
                role="status"
                aria-live="polite"
                :title="!networkOnline ? '浏览器已离线；网络恢复后会自动续接当前 Run' : runEventStreaming ? '当前 Run 正通过 SSE 推送状态，HTTP 轮询仍作为兜底' : '实时流暂时中断，HTTP 轮询仍会继续更新状态'"
              ><i></i>{{ runEventStatusLabel }}</span>
              <span v-if="pendingChatMessage" class="chat-run-pill" :class="statusClass(chatRunStatus)"><i></i>{{ statusLabel(chatRunStatus) }}</span>
              <span v-if="pendingChatMessage && chatRunActivity" class="chat-activity-pill" role="status" aria-live="polite">{{ chatRunActivity }}</span>
              <button v-if="activeConversationId && !showConversationRename" class="secondary-button" type="button" :disabled="conversationRenaming" @click="beginConversationRename">重命名</button>
              <button v-if="workspaceExplorerAvailable" class="secondary-button" type="button" @click="toggleWorkspaceExplorer">{{ showChatWorkspace ? '隐藏文件' : '项目文件' }}</button>
              <button v-if="latestConversationRun(activeConversation)" class="secondary-button" type="button" @click="toggleRunPanel">{{ showChatRun ? '隐藏运行' : '查看运行' }}</button>
            </div>
          </div>

          <div class="chat-messages" aria-live="polite">
            <div v-if="chatLoading && !chatMessages.length" class="chat-empty-state">正在加载会话…</div>
            <div v-else-if="!chatMessages.length" class="chat-empty-state">
              <div class="chat-empty-mark" aria-hidden="true"><Sparkles :size="23" /></div>
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
                    <span class="chat-thinking"><i></i><i></i><i></i>{{ chatRunActivity || messageStatusLabel(message.status) }}</span>
                  </template>
                  <template v-else>
                    <div v-if="message.content" class="chat-markdown" v-html="renderMarkdown(message.content)"></div>
                    <p v-else>{{ messageStatusLabel(message.status) }}</p>
                    <small v-if="message.role === 'ASSISTANT' && message.status !== 'COMPLETED'">{{ messageStatusLabel(message.status) }}</small>
                  </template>
                  <div v-if="message.attachments?.length" class="chat-attachment-list" aria-label="已导入的工作区文件">
                    <span v-for="attachment in message.attachments" :key="attachment.id" :title="attachment.workspacePath">
                      <i>{{ attachment.directory ? '▣' : '⌁' }}</i><strong>{{ attachment.originalName }}</strong><em v-if="attachment.directory">{{ attachment.fileCount }} 文件</em><code>{{ attachment.workspacePath }}</code>
                    </span>
                  </div>
                </div>
                <div v-if="message.role === 'ASSISTANT' && (message.content || canRetryChatMessage(message))" class="chat-message-actions">
                  <button v-if="message.content" type="button" :disabled="copyingMessageId === message.id" @click="copyChatMessage(message)">{{ copyingMessageId === message.id ? '复制中…' : '复制回复' }}</button>
                  <button v-if="canRetryChatMessage(message)" type="button" :disabled="retryingMessageId === message.id" @click="retryChatMessage(message)">{{ retryingMessageId === message.id ? '重试中…' : '重试本轮' }}</button>
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
                <i aria-hidden="true"><FolderOpen v-if="attachment.directory" :size="13" /><Paperclip v-else :size="13" /></i><strong>{{ attachment.name }}</strong><em>{{ attachment.directory ? `${attachment.fileCount} 文件` : formatFileSize(attachment.size) }}</em>
                <button type="button" :aria-label="`移除 ${attachment.name}`" :disabled="chatSending || chatUploading" @click="removeChatAttachment(index)"><X :size="13" /></button>
              </span>
            </div>
            <div v-if="showChatAgentSettings && activeConversationId" class="chat-agent-settings" aria-label="Agent 设置">
              <div class="chat-agent-settings-heading"><div><strong>Agent 执行深度</strong><small>限制本轮最多执行的模型轮数，工具结果会继续计入同一 Run。</small></div><button type="button" aria-label="关闭 Agent 设置" @click="showChatAgentSettings = false"><X :size="14" /></button></div>
              <div class="chat-agent-settings-controls">
                <label><span>模型轮数上限</span><input v-model.number="chatMaxTurns" type="number" min="1" max="1000" step="1" :disabled="chatSending || chatUploading" @change="persistChatMaxTurns" /></label>
                <div class="chat-agent-presets" aria-label="Agent 深度预设">
                  <button v-for="preset in [8, 24, 100, 1000]" :key="preset" type="button" :class="{ active: chatMaxTurns === preset }" :disabled="chatSending || chatUploading" @click="setChatMaxTurns(preset)">{{ preset === 1000 ? '平台上限' : `${preset} 轮` }}</button>
                </div>
              </div>
            </div>
            <textarea
              ref="chatInputRef"
              v-model="chatInput"
              rows="3"
              :disabled="chatSending || chatUploading || !activeConversationId"
              placeholder="描述代码任务；桌面版可拖入项目文件夹，浏览器会导入文本副本…"
              aria-label="输入消息"
              @input="handleChatInput"
              @keydown="handleChatKeydown"
            ></textarea>
            <div class="chat-composer-footer">
              <span><kbd>Enter</kbd> 发送 · <kbd>Shift</kbd> + <kbd>Enter</kbd> 换行<span v-if="canCancelChat"> · <kbd>Esc</kbd> 停止</span> · 草稿自动保存 · {{ desktopWorkspaceDropping ? '正在授权拖入的本地项目…' : workspaceConnected ? 'Agent 可直接操作本会话绑定的本地项目' : '文件夹导入后保留层级' }}</span>
              <div class="chat-composer-actions">
                <button class="secondary-button chat-agent-settings-button" type="button" :disabled="chatSending || chatUploading || !activeConversationId" @click="showChatAgentSettings = !showChatAgentSettings"><Settings2 :size="14" /><span>Agent · {{ chatMaxTurns }} 轮</span></button>
                <button class="secondary-button chat-attachment-button" type="button" :disabled="chatSending || chatUploading || !activeConversationId" @click="openChatAttachmentPicker"><Paperclip :size="14" /><span>附件</span></button>
                <button class="secondary-button chat-attachment-button" type="button" :disabled="chatSending || chatUploading || !activeConversationId" @click="openChatFolderPicker"><FolderOpen :size="14" /><span>文件夹</span></button>
                <button v-if="canCancelChat" class="secondary-button chat-stop-button" type="button" :disabled="chatCancellingRunId === pendingChatMessage?.runId" @click="cancelChatRun"><Square :size="14" /><span>{{ chatCancellingRunId === pendingChatMessage?.runId ? '处理中…' : (chatRunStatus === 'WAITING_APPROVAL' ? '撤回审批' : '停止') }}</span></button>
                <button class="primary-button chat-send-button" type="submit" :disabled="!canSendChat"><span class="chat-send-label">{{ chatUploading ? '导入中…' : chatSending ? '提交中…' : '发送' }}</span><Send :size="14" /></button>
              </div>
            </div>
          </form>
        </main>

        <aside v-if="showChatWorkspace" class="chat-workspace-panel" :class="{ 'workspace-panel-expanded': workspaceFilePreview || workspaceFilePreviewLoading }">
          <div class="chat-run-panel-heading">
            <div><p class="eyebrow">PROJECT EXPLORER</p><h2>项目文件</h2></div>
            <button class="icon-button" type="button" aria-label="关闭项目文件" @click="showChatWorkspace = false"><X :size="15" /></button>
          </div>
          <div v-if="workspaceExplorerLoading && !workspaceExplorer" class="chat-run-empty">正在读取工作区目录…</div>
          <template v-else-if="workspaceExplorer">
            <div class="workspace-explorer-layout" :class="{ 'has-preview': workspaceFilePreview || workspaceFilePreviewLoading }">
              <section class="workspace-browser-column" aria-label="工作区文件树">
              <div class="workspace-explorer-git" :class="{ unavailable: !workspaceExplorer.git?.available }">
              <span>Git</span>
              <strong>{{ workspaceExplorer.git?.available ? workspaceExplorer.git.branch : '非 Git 项目' }}</strong>
              <em v-if="workspaceExplorer.git?.available">{{ workspaceExplorer.git.clean ? '工作区干净' : `${workspaceExplorer.git.changeCount} 项变更` }}</em>
              <button v-if="workspaceGitAvailable" type="button" @click="toggleWorkspaceGitReview">{{ workspaceGitReviewVisible ? '收起审阅' : '审阅变更' }}</button>
            </div>
            <section v-if="workspaceGitReviewVisible" class="workspace-git-review workspace-git-summary" aria-label="Git 代码变更摘要">
              <div class="workspace-git-review-heading">
                <div><strong>代码变更</strong><small>{{ workspaceGitReviewEntries.length }} 个文件</small></div>
                <div class="workspace-git-review-heading-actions">
                  <button class="workspace-git-open-button" type="button" @click="openWorkspaceGitReviewDialog">打开审阅</button>
                  <button class="icon-button" type="button" aria-label="刷新 Git 变更" :disabled="workspaceGitStatusLoading" @click="loadWorkspaceGitStatus"><RefreshCw :size="14" /></button>
                </div>
              </div>
              <p v-if="workspaceGitStatusLoading && !workspaceGitStatus" class="workspace-git-review-empty">正在读取 Git 变更…</p>
              <template v-else-if="workspaceGitStatus">
                <p v-if="workspaceGitStatus.clean" class="workspace-git-review-empty">当前工作区没有未提交变更。</p>
                <div v-else class="workspace-git-change-list">
                  <article v-for="change in workspaceGitReviewEntries" :key="`${change.index}-${change.worktree}-${change.path}`">
                    <button
                      class="workspace-git-file-row"
                      type="button"
                      :class="{ selected: workspaceGitReviewPath === change.path }"
                      @click="openWorkspaceGitReviewFile(change)"
                    >
                      <span :class="gitChangeClass(change)">{{ gitChangeLabel(change) }}</span>
                      <strong :title="change.path">{{ change.path }}</strong>
                      <em>{{ defaultGitDiffStage(change) ? '已暂存' : change.worktree === '?' ? '未跟踪' : '工作区' }}</em>
                    </button>
                    <nav>
                      <button v-if="change.worktree !== ' ' && !(change.worktree === '?' && change.index === '?')" type="button" @click="openWorkspaceGitReviewFile(change, false)">工作区</button>
                      <button v-if="change.index !== ' ' && change.index !== '?'" type="button" @click="openWorkspaceGitReviewFile(change, true)">暂存</button>
                      <button v-if="change.worktree === '?' && change.index === '?'" type="button" @click="previewUntrackedWorkspaceFile(change)">预览</button>
                    </nav>
                  </article>
                </div>
                <p v-if="workspaceGitStatus.protectedChangeCount" class="workspace-git-review-warning">有 {{ workspaceGitStatus.protectedChangeCount }} 项受保护的隐藏文件变更，已按工作区安全策略隐藏。</p>
                <p v-if="workspaceGitStatus.outputTruncated" class="workspace-git-review-warning">变更列表已按安全上限截断，请逐步处理项目中的文件。</p>
              </template>
              <p v-else class="workspace-git-review-empty">无法读取 Git 变更。</p>
            </section>
            <div class="workspace-explorer-path">
              <button class="secondary-button" type="button" aria-label="返回上级目录" :disabled="workspaceExplorerPath === '.' || workspaceExplorerLoading" @click="loadWorkspaceDirectory(workspaceExplorer.parentPath)"><ArrowUp :size="14" /></button>
              <code>{{ workspaceExplorerPath }}</code>
              <button class="icon-button" type="button" aria-label="刷新目录" :disabled="workspaceExplorerLoading" @click="loadWorkspaceDirectory(workspaceExplorerPath)"><RefreshCw :size="14" /></button>
            </div>
            <div class="workspace-explorer-list" aria-label="工作区目录列表">
              <button
                v-for="entry in workspaceExplorer.entries"
                :key="entry.path"
                type="button"
                :class="{ directory: entry.directory, active: workspaceFilePreview?.path === entry.path }"
                @click="entry.directory ? loadWorkspaceDirectory(entry.path) : previewWorkspaceFile(entry)"
              >
                <i aria-hidden="true"><FolderGit2 v-if="entry.directory" :size="13" /><FolderOpen v-else :size="13" /></i><strong>{{ entry.name }}</strong><em>{{ entry.directory ? '目录' : formatFileSize(entry.size) }}</em>
              </button>
              <p v-if="!workspaceExplorer.entries.length">当前目录没有可显示的文件。</p>
            </div>
              </section>
              <section v-if="workspaceFilePreview || workspaceFilePreviewLoading" class="workspace-file-inspector" aria-label="文件编辑器">
            <section v-if="workspaceFilePreview || workspaceFilePreviewLoading" class="workspace-file-preview" aria-label="文件预览">
              <div class="workspace-file-preview-heading">
                <div><strong>{{ workspaceFilePreview?.path || '正在读取文件…' }}</strong><span v-if="workspaceFilePreview?.path" class="workspace-file-language">{{ languageLabel('', workspaceFilePreview.path) }}</span><span v-if="workspaceFilePreview?.redacted">已脱敏</span><span v-if="workspaceFilePreview?.truncated">已截断</span></div>
                <div v-if="workspaceFilePreview" class="workspace-editor-actions">
                  <span v-if="workspaceEditorDirty" class="workspace-editor-dirty">未保存</span>
                  <button type="button" :disabled="workspaceEditorCopying" @click="copyWorkspaceFile">{{ workspaceEditorCopying ? '复制中…' : '复制' }}</button>
                  <button v-if="workspaceEditorWritable" type="button" :disabled="!workspaceEditorDirty || workspaceEditorSaving" @click="saveWorkspaceFile">{{ workspaceEditorSaving ? '保存中…' : '保存' }}</button>
                </div>
              </div>
              <div v-if="workspaceFilePreview" class="workspace-editor-shell">
                <MonacoEditor
                  ref="workspaceEditorRef"
                  v-model="workspaceEditorContent"
                  :path="workspaceFilePreview.path"
                  :language="languageFromPath(workspaceFilePreview.path) || 'plaintext'"
                  :readonly="!workspaceEditorWritable"
                  @change="workspaceEditorDirty = true"
                  @save="saveWorkspaceFile"
                />
              </div>
              <p v-if="workspaceFilePreview?.truncated" class="workspace-editor-hint">文件超过编辑器安全行数上限，当前仅展示前 {{ workspaceFilePreview.content?.split('\n').length || 0 }} 行，已禁用保存。</p>
              <p v-else-if="workspaceFilePreview && !workspaceEditorWritable" class="workspace-editor-hint">当前身份没有 workspace.write 权限，文件以只读模式打开。</p>
            </section>
              </section>
            </div>
          </template>
          <div v-else class="chat-run-empty">当前会话未连接可访问的本地项目。</div>
        </aside>

        <aside v-if="showChatRun" class="chat-run-panel">
          <div class="chat-run-panel-heading"><div><p class="eyebrow">RUN TRACE</p><h2>本轮执行</h2></div><button class="icon-button" type="button" aria-label="关闭运行详情" @click="showChatRun = false"><X :size="15" /></button></div>
          <div v-if="!selectedRun" class="chat-run-empty">选择一条助手消息查看执行链。</div>
          <template v-else>
            <div class="chat-run-summary"><strong>{{ selectedRun.run.title }}</strong><span class="status-pill" :class="statusClass(selectedRun.run.status)"><i></i>{{ statusLabel(selectedRun.run.status) }}</span></div>
            <div class="chat-run-actions">
              <button v-if="canApprove" class="secondary-button" type="button" :disabled="loading" @click="approveSelectedRun">审批通过</button>
              <button v-if="canApprove" class="danger-button" type="button" :disabled="loading" @click="openRejectDialog">拒绝</button>
              <button v-if="canRetry" class="secondary-button" type="button" :disabled="loading" @click="retrySelectedRun">重试</button>
              <button v-if="canCancel" class="danger-button" type="button" :disabled="loading" @click="cancelSelectedRun">{{ cancelActionLabel }}</button>
            </div>
            <section v-if="workspaceChangePreviews.length" class="chat-change-review" aria-label="代码变更预览">
              <div class="chat-change-review-heading">
                <div><span>CHANGE REVIEW</span><strong>{{ pendingWorkspaceChangePreviews.length ? '请先检查待审批变更' : '本轮代码变更' }}</strong></div>
                <em v-if="pendingWorkspaceChangePreviews.length">{{ pendingWorkspaceChangePreviews.length }} 项待审批</em>
              </div>
              <article v-for="change in workspaceChangePreviews" :key="change.stepId" class="chat-change-card">
                <div class="chat-change-card-meta">
                  <span>{{ change.typeLabel }}</span><span class="chat-change-language">{{ languageLabel('', change.path) }}</span><code>{{ change.path }}</code><em :class="statusClass(change.status)">{{ statusLabel(change.status) }}</em>
                </div>
                <template v-if="change.kind === 'edit'">
                  <div v-for="(edit, index) in change.edits" :key="index" class="chat-inline-diff chat-inline-monaco-diff">
                    <MonacoEditor
                      :path="change.path"
                      :language="languageFromPath(change.path) || 'plaintext'"
                      :diff="true"
                      :original="edit.oldText"
                      :modified="edit.newText"
                      height="220px"
                    />
                    <small v-if="edit.replaceAll">替换全部匹配项</small>
                  </div>
                  <p v-if="change.hiddenEditCount" class="chat-change-truncated">另有 {{ change.hiddenEditCount }} 个编辑已折叠。</p>
                </template>
                <div v-else class="chat-inline-diff chat-inline-monaco-diff">
                  <MonacoEditor
                    :path="change.path"
                    :language="languageFromPath(change.path) || 'plaintext'"
                    :model-value="change.content"
                    :readonly="true"
                    height="220px"
                  />
                </div>
              </article>
            </section>
            <div class="chat-run-meta"><span>Run</span><code>{{ selectedRun.run.id.slice(0, 12) }}</code><span>Trace</span><code>{{ selectedRun.run.traceId?.slice(0, 12) || '—' }}</code></div>
            <div class="chat-step-list">
              <div v-for="step in selectedRun.steps" :key="step.id" class="chat-step-row">
                <span class="chat-step-dot" :class="statusClass(step.status)"></span>
                <div>
                  <strong>{{ step.name }}</strong>
                  <small>{{ stepLabel(step.type) }} · {{ statusLabel(step.status) }}</small>
                  <p v-if="step.error" :class="step.status === 'REJECTED' ? 'chat-step-rejection' : ''">{{ step.error }}</p>
                  <details v-if="step.type === 'TOOL' && (step.input || step.output)" class="chat-step-details" :open="['REJECTED', 'FAILED'].includes(step.status)">
                    <summary>查看工具参数与结果</summary>
                    <div v-if="step.input" class="chat-step-payload"><span>输入</span><pre>{{ chatStepPayload(step.input) }}</pre></div>
                    <div v-if="step.output" class="chat-step-payload"><span>结果</span><pre>{{ chatStepPayload(step.output) }}</pre></div>
                  </details>
                </div>
              </div>
            </div>
          </template>
        </aside>
      </div>
      <div
        v-if="workspaceGitReviewOpen"
        class="workspace-review-overlay"
        role="dialog"
        aria-modal="true"
        aria-label="代码变更审阅"
        @click.self="closeWorkspaceGitReviewDialog"
      >
        <div class="workspace-review-dialog">
          <header class="workspace-review-header">
            <div>
              <p class="eyebrow">CODE REVIEW</p>
              <h2>审查代码变更</h2>
              <span>{{ workspaceGitReviewEntries.length }} 个文件 · {{ workspaceGitStatus?.branch || 'Git 工作区' }}</span>
            </div>
            <button class="icon-button" type="button" aria-label="关闭代码审阅" @click="closeWorkspaceGitReviewDialog">×</button>
          </header>
          <div class="workspace-review-body">
            <aside class="workspace-review-files" aria-label="变更文件列表">
              <div class="workspace-review-files-heading">
                <span>变更文件</span>
                <button class="icon-button" type="button" aria-label="刷新 Git 变更" :disabled="workspaceGitStatusLoading" @click="loadWorkspaceGitStatus">↻</button>
              </div>
              <div v-if="workspaceGitStatusLoading && !workspaceGitStatus" class="workspace-review-empty">正在读取变更…</div>
              <div v-else-if="workspaceGitStatus?.clean" class="workspace-review-empty">当前工作区干净。</div>
              <template v-else-if="workspaceGitReviewEntries.length">
                <div v-for="change in workspaceGitReviewEntries" :key="change.path + ':' + change.index + ':' + change.worktree" class="workspace-review-file-group">
                  <button
                    class="workspace-review-file"
                    type="button"
                    :class="{ selected: workspaceGitReviewPath === change.path }"
                    @click="openWorkspaceGitReviewFile(change)"
                  >
                    <span :class="gitChangeClass(change)">{{ gitChangeLabel(change) }}</span>
                    <strong :title="change.path">{{ change.path }}</strong>
                    <small>{{ defaultGitDiffStage(change) ? '已暂存' : change.worktree === '?' ? '未跟踪' : '工作区' }}</small>
                  </button>
                  <div class="workspace-review-file-actions">
                    <button v-if="change.worktree !== ' ' && !(change.worktree === '?' && change.index === '?')" type="button" :class="{ active: workspaceGitReviewPath === change.path && !workspaceGitReviewStaged }" @click="loadWorkspaceGitDiff(change.path, false)">工作区</button>
                    <button v-if="change.index !== ' ' && change.index !== '?'" type="button" :class="{ active: workspaceGitReviewPath === change.path && workspaceGitReviewStaged }" @click="loadWorkspaceGitDiff(change.path, true)">暂存</button>
                    <button v-if="change.worktree === '?' && change.index === '?'" type="button" @click="previewUntrackedWorkspaceFile(change)">打开文件</button>
                  </div>
                </div>
              </template>
              <div v-else class="workspace-review-empty">无法读取 Git 变更。</div>
              <p v-if="workspaceGitStatus?.protectedChangeCount" class="workspace-review-warning">已隐藏 {{ workspaceGitStatus.protectedChangeCount }} 项受保护文件。</p>
              <p v-if="workspaceGitStatus?.outputTruncated" class="workspace-review-warning">变更列表已达到安全上限。</p>
            </aside>
            <section class="workspace-review-diff" aria-label="文件差异对比">
              <header class="workspace-review-diff-header">
                <div>
                  <code>{{ workspaceGitReviewPath || '选择一个变更文件' }}</code>
                  <span v-if="workspaceGitReviewStaged" class="workspace-review-scope">已暂存</span>
                  <span v-else-if="workspaceGitReviewPath" class="workspace-review-scope">工作区</span>
                  <span v-if="workspaceGitReviewParsed.additions" class="workspace-review-additions">+{{ workspaceGitReviewParsed.additions }}</span>
                  <span v-if="workspaceGitReviewParsed.deletions" class="workspace-review-deletions">−{{ workspaceGitReviewParsed.deletions }}</span>
                  <span v-if="workspaceGitDiff?.outputTruncated" class="workspace-review-scope">已截断</span>
                </div>
                <button v-if="workspaceGitDiff?.hasChanges" class="workspace-review-copy" type="button" :disabled="workspaceGitDiffCopying" @click="copyWorkspaceGitDiff">{{ workspaceGitDiffCopying ? '复制中…' : '复制 Diff' }}</button>
              </header>
              <div class="workspace-review-editor">
                <MonacoEditor
                  v-if="workspaceGitDiff?.hasChanges"
                  :diff="true"
                  :path="workspaceGitReviewPath"
                  :language="languageFromPath(workspaceGitReviewPath) || 'plaintext'"
                  :original="workspaceGitReviewParsed.original"
                  :modified="workspaceGitReviewParsed.modified"
                  height="100%"
                />
                <div v-else-if="workspaceGitDiffLoading" class="workspace-review-state">正在读取差异…</div>
                <div v-else-if="workspaceGitDiff" class="workspace-review-state">当前范围没有可显示的 Diff；新增未跟踪文件请从左侧打开文件。</div>
                <div v-else class="workspace-review-state">从左侧选择文件开始审阅。</div>
              </div>
            </section>
          </div>
        </div>
      </div>
    </div>
  </template>
  <template v-else>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true"><Sparkles :size="17" :stroke-width="1.8" /></div>
        <div>
          <strong>Ming Harness</strong>
          <span>Agent Operations</span>
        </div>
      </div>

      <nav class="side-nav" aria-label="主导航">
        <a class="nav-item" :class="{ active: activeConsoleSection === 'runtime' }" href="#runtime" :aria-current="activeConsoleSection === 'runtime' ? 'page' : undefined" @click="setActiveConsoleSection('runtime')"><span class="nav-icon"><CircleDot :size="16" /></span>运行中心</a>
        <a class="nav-item" :class="{ active: activeConsoleSection === 'tools' }" href="#tools" :aria-current="activeConsoleSection === 'tools' ? 'page' : undefined" @click="setActiveConsoleSection('tools')"><span class="nav-icon"><Wrench :size="16" /></span>工具注册</a>
        <a class="nav-item" :class="{ active: activeConsoleSection === 'audit' }" href="#audit" :aria-current="activeConsoleSection === 'audit' ? 'page' : undefined" @click="setActiveConsoleSection('audit')"><span class="nav-icon"><Check :size="16" /></span>审计追踪</a>
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
          <button class="secondary-button" type="button" title="打开聊天工作台" @click="chatMode = true"><MessageSquarePlus :size="15" />聊天工作台</button>
          <button class="secondary-button" type="button" title="打开模型设置" @click="showModelSettings = true"><Settings2 :size="15" />模型设置</button>
          <button class="command-palette-trigger" type="button" title="打开命令面板（⌘/Ctrl + K）" @click="openCommandPalette"><Command :size="14" /><span>⌘K</span><em>命令</em></button>
          <button
            class="theme-toggle"
            type="button"
            :aria-label="theme === 'dark' ? '切换到白天模式' : '切换到黑夜模式'"
            :title="theme === 'dark' ? '切换到白天模式' : '切换到黑夜模式'"
            @click="toggleTheme"
          >
            <Sun v-if="theme === 'dark'" :size="15" aria-hidden="true" /><Moon v-else :size="15" aria-hidden="true" />
            {{ theme === 'dark' ? '白天' : '黑夜' }}
          </button>
          <span class="date-chip">本地演示环境</span>
          <button class="primary-button" type="button" @click="showCreateForm = !showCreateForm">
            <Plus :size="16" /> 新建 Run
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
            <span :class="modelStatusClass"><i></i>{{ modelLabel }}</span>
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
          <div class="stat-top"><span>全部 Run</span><span class="stat-icon"><ListChecks :size="16" /></span></div>
          <strong>{{ stats.total }}</strong>
          <small>最近 50 条执行记录</small>
        </div>
        <div class="stat-card stat-running">
          <div class="stat-top"><span>执行中</span><span class="stat-icon"><Activity :size="16" /></span></div>
          <strong>{{ stats.running }}</strong>
          <small>{{ stats.queued }} 条排队等待</small>
        </div>
        <div class="stat-card stat-success">
          <div class="stat-top"><span>成功率</span><span class="stat-icon"><Check :size="16" /></span></div>
          <strong>{{ stats.total ? Math.round((stats.succeeded / stats.total) * 100) : 0 }}<em>%</em></strong>
          <small>{{ stats.succeeded }} 条任务已完成</small>
        </div>
        <div class="stat-card stat-failed">
          <div class="stat-top"><span>需关注</span><span class="stat-icon"><CircleAlert :size="16" /></span></div>
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
          <button class="icon-button" type="button" aria-label="关闭创建表单" @click="showCreateForm = false"><X :size="15" /></button>
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
                <input v-model.number="form.maxTurns" type="number" min="1" max="1000" required />
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
              <button class="refresh-button" type="button" :disabled="runsLoading" @click="loadDashboard" aria-label="刷新列表"><RefreshCw :size="15" /></button>
            </div>
          </div>
          <div v-if="runsLoading && !runs.length" class="loading-state run-list-loading-state">正在加载 Run 列表…</div>
          <div v-else-if="!runs.length" class="empty-state">
            <div class="empty-orb"><ListChecks :size="19" /></div>
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
                <button v-if="canApprove" class="danger-button" type="button" :disabled="loading" @click="openRejectDialog">拒绝</button>
                <button v-if="canRetry" class="secondary-button" type="button" :disabled="loading" @click="retrySelectedRun">重试</button>
                <button v-if="canCancel" class="danger-button" type="button" :disabled="loading" @click="cancelSelectedRun">{{ cancelActionLabel }}</button>
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
                    <div v-if="decodeAgentStep(step).content" class="step-output chat-markdown step-markdown" v-html="renderMarkdown(decodeAgentStep(step).content)"></div>
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
                      <template v-if="decodeWorkspaceGitStatus(step).available === false">
                        <p class="git-line"><span>⌘</span> Git 审阅不可用</p>
                        <div class="command-summary"><span class="command-failed">{{ decodeWorkspaceGitStatus(step).message || '当前工作区不是 Git 仓库' }}</span><span>Agent 已改用文件工具继续检查</span></div>
                      </template>
                      <template v-else>
                        <p class="git-line"><span>⌘</span> {{ decodeWorkspaceGitStatus(step).branch || 'Git 工作区' }}</p>
                        <div class="command-summary">
                          <span :class="decodeWorkspaceGitStatus(step).clean ? 'command-ok' : 'command-failed'">{{ decodeWorkspaceGitStatus(step).clean ? '工作区干净' : '存在文件变更' }}</span>
                          <span>{{ decodeWorkspaceGitStatus(step).entryCount || 0 }} 个变更</span>
                        </div>
                      </template>
                    </template>
                    <template v-else-if="decodeWorkspaceSearch(step)">
                      <p class="search-line"><span>⌕</span> {{ decodeWorkspaceSearch(step).query }} · {{ decodeWorkspaceSearch(step).path || '.' }}</p>
                      <div class="command-summary">
                        <span class="command-ok">命中 {{ decodeWorkspaceSearch(step).matches.length }} 处</span>
                        <span v-if="decodeWorkspaceSearch(step).truncated">结果已截断</span>
                      </div>
                      <div v-if="decodeWorkspaceSearch(step).matches.length" class="search-match-list">
                        <code v-for="match in decodeWorkspaceSearch(step).matches.slice(0, 6)" :key="`${match.path}:${match.line}`">{{ match.path }}:{{ match.line }}</code>
                        <span v-if="decodeWorkspaceSearch(step).matches.length > 6">另有 {{ decodeWorkspaceSearch(step).matches.length - 6 }} 处</span>
                      </div>
                    </template>
                    <template v-else-if="decodeWorkspaceGitDiff(step)">
                      <template v-if="decodeWorkspaceGitDiff(step).available === false">
                        <p class="git-line"><span>⌘</span> Git Diff 不可用</p>
                        <p class="muted-line">{{ decodeWorkspaceGitDiff(step).suggestion || '请改为读取相关文件核对修改。' }}</p>
                      </template>
                      <template v-else>
                        <p class="git-line"><span>⌘</span> {{ decodeWorkspaceGitDiff(step).path || '.' }} · {{ decodeWorkspaceGitDiff(step).staged ? '已暂存' : '未暂存' }}</p>
                        <pre v-if="decodeWorkspaceGitDiff(step).diff" class="git-diff-output">{{ decodeWorkspaceGitDiff(step).diff }}</pre>
                        <p v-else class="muted-line">当前范围没有代码差异</p>
                      </template>
                    </template>
                    <template v-else-if="decodeWorkspaceRecoverableFailure(step)">
                      <p class="command-line"><span>↻</span> {{ step.name }} 未完成，Agent 将继续定位</p>
                      <div class="command-summary">
                        <span class="command-failed">{{ decodeWorkspaceRecoverableFailure(step).code || '读取未完成' }}</span>
                        <span>可恢复</span>
                      </div>
                      <p class="muted-line">{{ decodeWorkspaceRecoverableFailure(step).message }}</p>
                      <p v-if="decodeWorkspaceRecoverableFailure(step).suggestion" class="muted-line">{{ decodeWorkspaceRecoverableFailure(step).suggestion }}</p>
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
                  <span class="audit-time">{{ formatDate(event.createdAt) }}</span><strong>{{ auditEventLabel(event.eventType) }}</strong><span>{{ event.message }}</span><small v-if="event.actorId">{{ event.actorId }} · {{ event.traceId?.slice(0, 10) }}</small>
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
  <div
    v-if="showRejectDialog"
    class="reject-dialog-overlay"
    role="dialog"
    aria-modal="true"
    aria-labelledby="reject-dialog-title"
    @click.self="closeRejectDialog"
  >
    <form class="reject-dialog" @submit.prevent="rejectSelectedRun">
      <header class="reject-dialog-heading">
        <div>
          <p class="eyebrow">APPROVAL DECISION</p>
          <h2 id="reject-dialog-title">拒绝这次变更？</h2>
          <span>{{ selectedRun?.run?.title || '当前 Run' }}</span>
        </div>
        <button class="icon-button" type="button" aria-label="关闭拒绝确认" :disabled="loading" @click="closeRejectDialog">×</button>
      </header>
      <p class="reject-dialog-help">
        {{ selectedRun?.run?.agentMode
          ? '拒绝意见会作为工具结果反馈给 Agent，由它调整方案后继续执行。'
          : '拒绝后 Run 会结束。' }}
        留下修改意见会写入审计记录，方便后续调整时追溯决策。
      </p>
      <label class="field">
        <span>拒绝原因（可选）</span>
        <textarea ref="rejectReasonInputRef" v-model="rejectReason" maxlength="500" rows="4" placeholder="例如：请先补充测试，并避免修改配置文件。"></textarea>
      </label>
      <footer class="reject-dialog-actions">
        <button class="secondary-button" type="button" :disabled="loading" @click="closeRejectDialog">返回检查</button>
        <button class="danger-button" type="submit" :disabled="loading">{{ loading ? '提交中…' : '拒绝并记录意见' }}</button>
      </footer>
    </form>
  </div>
  <div
    v-if="showModelSettings"
    class="model-settings-overlay"
    role="dialog"
    aria-modal="true"
    aria-labelledby="model-settings-title"
    @click.self="showModelSettings = false"
  >
    <form class="model-settings-dialog" @submit.prevent="saveModelConfig">
      <header class="model-settings-heading">
        <div>
          <p class="eyebrow">MODEL CONNECTION</p>
          <h2 id="model-settings-title">模型连接设置</h2>
          <span>{{ modelConfig?.source === 'user' ? '当前用户覆盖' : '环境默认配置' }}</span>
        </div>
        <button class="icon-button" type="button" aria-label="关闭模型设置" @click="showModelSettings = false">×</button>
      </header>
      <div v-if="modelConfigLoading" class="model-settings-state">正在读取当前模型配置…</div>
      <template v-else>
        <p class="model-settings-help">支持 OpenAI 兼容的 Chat Completions 地址。可先选择常见服务预设自动填充，也可以改成任意兼容地址；API Key 只会提交给当前 Runtime，服务端加密保存，刷新页面不会回填明文。</p>
        <label class="check-field model-settings-toggle">
          <input v-model="modelConfigForm.enabled" type="checkbox" :disabled="!modelConfigEditable" />
          <span>使用外部模型，不使用本地演示模型</span>
        </label>
        <label class="field"><span>服务预设</span><select v-model="modelProviderPreset" :disabled="!modelConfigEditable || !modelConfigForm.enabled" @change="applyModelProviderPreset"><option v-for="preset in modelProviderPresets" :key="preset.id" :value="preset.id">{{ preset.label }}</option></select></label>
        <label class="field"><span>模型 API 地址</span><input v-model="modelConfigForm.baseUrl" :disabled="!modelConfigEditable || !modelConfigForm.enabled" :required="modelConfigForm.enabled" maxlength="512" placeholder="https://api.openai.com/v1" @input="useCustomModelProvider" /></label>
        <label class="field"><span>模型名称</span><input v-model="modelConfigForm.modelName" :disabled="!modelConfigEditable || !modelConfigForm.enabled" :required="modelConfigForm.enabled" maxlength="128" placeholder="例如：gpt-4o-mini、deepseek-chat、qwen2.5-coder" @input="useCustomModelProvider" /></label>
        <label class="field"><span>API Key（留空保留当前密钥）</span><input v-model="modelConfigForm.apiKey" :disabled="!modelConfigEditable" type="password" autocomplete="new-password" maxlength="1000" placeholder="不会回显已保存的密钥" /></label>
        <label v-if="modelConfig?.apiKeyConfigured" class="check-field model-settings-clear-key">
          <input v-model="modelConfigForm.clearApiKey" type="checkbox" :disabled="!modelConfigEditable" />
          <span>同时删除服务端已保存的 API Key（适用于无密钥本地模型）</span>
        </label>
        <p v-if="modelConfig?.apiKeyConfigured" class="model-settings-hint">当前密钥：{{ modelConfig.apiKeyHint || '已配置（不显示明文）' }}</p>
        <p v-if="modelConfigTestResult" class="model-settings-test-result" :class="modelConfigTestResult.success ? 'success' : 'failed'" role="status" aria-live="polite">
          {{ modelConfigTestResult.message }}<span v-if="modelConfigTestResult.latencyMs"> · {{ modelConfigTestResult.latencyMs }} ms</span>
        </p>
        <p v-if="modelConfigError" class="policy-error">{{ modelConfigError }}</p>
        <footer class="model-settings-actions">
          <button class="danger-button" type="button" :disabled="modelConfigSaving || !modelConfig?.configured" @click="resetModelConfig">恢复环境默认</button>
          <span></span>
          <button class="secondary-button" type="button" :disabled="modelConfigSaving || modelConfigTesting || !modelConfigEditable || !modelConfigForm.enabled" @click="testModelConfig">{{ modelConfigTesting ? '测试中…' : '测试连接' }}</button>
          <button class="secondary-button" type="button" :disabled="modelConfigSaving" @click="showModelSettings = false">取消</button>
          <button class="primary-button" type="submit" :disabled="modelConfigSaving || modelConfigLoading || !modelConfigEditable">{{ modelConfigSaving ? '保存中…' : '保存并应用' }}</button>
        </footer>
      </template>
    </form>
  </div>
  <div
    v-if="showCommandPalette"
    class="command-palette-overlay"
    role="dialog"
    aria-modal="true"
    aria-label="命令面板"
    @click.self="closeCommandPalette"
  >
    <section class="command-palette-dialog">
      <header class="command-palette-header">
        <div class="command-palette-search-icon" aria-hidden="true"><Command :size="17" /></div>
        <input
          ref="commandPaletteInputRef"
          v-model="commandQuery"
          type="search"
          autocomplete="off"
          placeholder="搜索命令…"
          aria-label="搜索命令"
          @input="commandSelectedIndex = 0"
        />
        <kbd>Esc</kbd>
      </header>
      <div class="command-palette-list" role="listbox" aria-label="可执行命令">
        <button
          v-for="(command, index) in filteredCommandPaletteItems"
          :key="command.id"
          class="command-palette-item"
          :class="{ selected: index === commandSelectedIndex }"
          type="button"
          role="option"
          :aria-selected="index === commandSelectedIndex"
          @mouseenter="commandSelectedIndex = index"
          @click="executeCommand(command)"
        >
          <span class="command-palette-item-icon" aria-hidden="true"><component :is="commandIconComponent(command.id)" :size="15" /></span>
          <span class="command-palette-item-copy"><strong>{{ command.label }}</strong><small>{{ command.description }}</small></span>
          <kbd v-if="command.shortcut">{{ command.shortcut }}</kbd>
        </button>
        <div v-if="!filteredCommandPaletteItems.length" class="command-palette-empty">没有匹配的命令</div>
      </div>
      <footer class="command-palette-footer"><span><kbd>↑</kbd><kbd>↓</kbd> 选择</span><span><kbd>Enter</kbd> 执行</span><span><kbd>Esc</kbd> 关闭</span></footer>
    </section>
  </div>
</template>
