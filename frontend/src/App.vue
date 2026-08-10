<script setup>
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  Activity,
  Bot,
  ArrowDown,
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
  ShieldCheck,
  Square,
  Sparkles,
  Sun,
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
const memories = ref([])
const educationSources = ref([])
const learnerProfiles = ref([])
const activeLearnerProfile = ref(null)
const learningGoals = ref([])
const activeLearningGoal = ref(null)
const learningGoalAssessments = ref([])
const learningRecommendation = ref(null)
const learningGoalRecommendationMap = ref({})
const learningTasks = ref([])
const learningNotifications = ref([])
const learningNotificationUnreadCount = ref(0)
const learningAssignmentNotifications = ref([])
const learningAssignmentNotificationUnreadCount = ref(0)
const learningAssignments = ref([])
const learningAssignmentProgressMap = ref({})
const learningAssignmentEvidenceMap = ref({})
const learningAssignmentFeedbackMap = ref({})
const educationCourses = ref([])
const activeEducationCourseId = ref('')
const educationCourseEnrollments = ref([])
const educationCourseProgress = ref(null)
const educationCourseLoading = ref(false)
const educationCourseSaving = ref(false)
const educationCourseRosterSaving = ref(false)
const educationCourseAssignmentSaving = ref(false)
const educationCourseActionId = ref('')
const learningAssignmentCourseFilter = ref('')
const learningAssignmentLearnerFilter = ref('')
const educationMetrics = ref(null)
const learningTaskLoading = ref(false)
const learningTaskStartingId = ref('')
const learningTaskDeferringId = ref('')
const learningAssignmentSaving = ref(false)
const learningAssignmentAcceptingId = ref('')
const learningAssignmentReviewSavingId = ref('')
const learningAssignmentFeedbackSavingId = ref('')
const learningAssignmentFeedbackAcknowledgingId = ref('')
const learningAssignmentFeedbackForm = reactive({
  assignmentId: '',
  action: 'COMMENT',
  message: '',
  suggestedDueAt: '',
})
const learningAssignmentForm = reactive({
  learnerUserId: '',
  title: '',
  instructions: '',
  subject: '',
  gradeLevel: '',
  curriculumVersion: '',
  conceptKey: '',
  targetMastery: '0.8',
  dueAt: '',
})
const educationCourseForm = reactive({
  code: '',
  title: '',
  subject: '数学',
  gradeLevel: '高中一年级',
  curriculumVersion: '人教A版',
})
const educationCourseEnrollmentForm = reactive({
  learnerUserId: '',
})
const educationCourseAssignmentForm = reactive({
  title: '',
  instructions: '',
  conceptKey: '',
  targetMastery: '0.8',
  dueAt: '',
})
const manualAssessmentForm = reactive({
  stepId: '',
  correct: '',
  observedMastery: '',
  evidenceText: '',
  feedback: '',
})
const manualAssessmentSaving = ref(false)
const manualAssessmentError = ref('')
const educationLoading = ref(false)
const educationError = ref('')
const contextPreviewQuery = ref('')
const contextPreviewMaxChars = ref(4000)
const contextPreviewResult = ref(null)
const contextPreviewLoading = ref(false)
const contextPreviewError = ref('')
const contextReindexForm = reactive({
  scope: 'ALL',
  parentLimit: 100,
  chunkLimit: 1000,
  rechunk: false,
})
const contextReindexResult = ref(null)
const contextReindexLoading = ref(false)
const contextReindexError = ref('')
const contextConfiguration = ref(null)
const tenantPolicy = ref(null)
const tenantPolicyAudits = ref([])
const tenantPolicyError = ref('')
const apiKeys = ref([])
const apiKeyAudits = ref([])
const apiKeyError = ref('')
const createdApiKeySecret = ref('')
const loading = ref(false)
const documentDeletingId = ref('')
const documentUploadInput = ref(null)
const documentUploadFile = ref(null)
const documentUploadDragging = ref(false)
const documentUploadError = ref('')
const documentUploading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
let noticeDismissTimer = 0
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
const embeddingConfig = ref(null)
const embeddingConfigLoading = ref(false)
const embeddingConfigSaving = ref(false)
const embeddingConfigTesting = ref(false)
const embeddingConfigError = ref('')
const embeddingConfigTestResult = ref(null)
const showEmbeddingSettings = ref(false)
const embeddingConfigForm = reactive({
  enabled: false,
  baseUrl: '',
  modelName: '',
  modelVersion: 'v1',
  dimension: 1536,
  apiKey: '',
  clearApiKey: false,
})
const embeddingConfigEditable = computed(() => !embeddingConfigError.value.startsWith('当前身份没有 context.configure'))
const embeddingProviderPreset = ref('custom')
const embeddingProviderPresets = [
  { id: 'openai', label: 'OpenAI', baseUrl: 'https://api.openai.com/v1', modelName: 'text-embedding-3-small', dimension: 1536 },
  { id: 'qwen', label: '通义千问（兼容模式）', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelName: 'text-embedding-v4', dimension: 1536 },
  { id: 'custom', label: '自定义 OpenAI 兼容服务', baseUrl: '', modelName: '', dimension: 1536 },
]
// API Key 权限使用固定目录，避免手动输入时出现拼写错误；提交协议仍保持为字符串数组。
const apiKeyPermissionOptions = [
  { value: 'run.read', label: '查看 Run' },
  { value: 'run.create', label: '创建 Run' },
  { value: 'run.execute', label: '执行 / 重试 Run' },
  { value: 'run.approve', label: '审批 Run' },
  { value: 'run.cancel', label: '取消 Run' },
  { value: 'audit.read', label: '查看审计记录' },
  { value: 'context.read', label: '读取上下文' },
  { value: 'context.write', label: '写入上下文' },
  { value: 'context.configure', label: '配置向量模型' },
  { value: 'context.reindex', label: '重建上下文索引' },
  { value: 'tool.read', label: '查看工具列表' },
  { value: 'workspace.read', label: '读取工作区' },
  { value: 'workspace.write', label: '写入工作区' },
  { value: 'workspace.exec', label: '执行工作区命令' },
  { value: 'workspace.manage', label: '管理工作区' },
  { value: 'ops.read', label: '查看基础设施状态' },
  { value: 'model.configure', label: '配置模型连接' },
  { value: 'tenant.policy.read', label: '读取组织策略' },
  { value: 'tenant.policy.write', label: '修改组织策略' },
  { value: 'tenant.policy.cross-tenant', label: '跨组织管理策略' },
  { value: 'auth.key.read', label: '查看 API Key' },
  { value: 'auth.key.manage', label: '创建 / 撤销 API Key' },
  { value: 'auth.key.cross-tenant', label: '跨组织管理 API Key' },
  { value: 'network.external', label: '访问外部网络工具' },
  { value: 'education.read', label: '读取教育知识与画像' },
  { value: 'education.write', label: '记录形成性评价' },
  { value: 'education.assign', label: '布置课程作业' },
]
const defaultApiKeyPermissions = [
  'run.read', 'run.create', 'run.execute', 'run.approve', 'run.cancel',
  'audit.read', 'context.read', 'context.write', 'context.configure',
  'tool.read', 'workspace.read', 'workspace.manage', 'ops.read',
  'model.configure', 'tenant.policy.read', 'tenant.policy.write',
  'auth.key.read', 'auth.key.manage',
  'education.read', 'education.write',
  'education.assign',
]
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
let learningNotificationPollTimer
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
// 流式输出默认跟随底部；用户主动向上浏览历史后暂停跟随，避免新内容抢走阅读位置。
const chatFollowOutput = ref(true)
const copyingMessageId = ref('')
const retryingMessageId = ref('')
const chatFeedbackByRun = ref({})
const feedbackSavingRunId = ref('')
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
const chatEducation = reactive(readChatEducation())
const showCommandPalette = ref(false)
const commandQuery = ref('')
const commandSelectedIndex = ref(0)
const commandPaletteInputRef = ref(null)
const quickStartPrompts = [
  {
    id: 'understand-project',
    label: '理解项目结构',
    description: '先浏览目录，再说明主要模块和入口。',
    prompt: '请先浏览当前项目结构，说明主要模块、启动入口和关键依赖；先不要修改代码。',
  },
  {
    id: 'find-symbol',
    label: '查找一个函数',
    description: '定位函数或类，并解释它的调用链。',
    prompt: '请在当前项目中定位这个函数或类并解释它的调用链：',
  },
  {
    id: 'review-changes',
    label: '检查 Git 变更',
    description: '总结当前改动、风险和待验证项。',
    prompt: '请检查当前 Git 变更，按文件总结改动、潜在风险和建议验证项；不要修改代码。',
  },
  {
    id: 'fix-bug',
    label: '修复一个 Bug',
    description: '描述现象，Agent 会先定位再给出最小修复。',
    prompt: '请帮我修复这个问题：\n\n',
  },
]
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

function scrollToConsoleSection(section, behavior = 'smooth') {
  if (typeof document === 'undefined') return
  window.requestAnimationFrame(() => {
    // Hash navigation can scroll the document root when the nested console
    // surface is already scrolled away from the target. Keep the app chrome
    // anchored to the viewport and let only .main-content handle scrolling.
    if (window.scrollY) window.scrollTo({ top: 0, behavior: 'auto' })
    const container = document.querySelector('.console-layout .main-content')
    if (!container) return
    const target = section === 'runtime' ? container : document.getElementById(section)
    if (!target) return
    if (section === 'runtime') {
      container.scrollTo({ top: 0, behavior })
      return
    }
    const containerRect = container.getBoundingClientRect()
    const targetRect = target.getBoundingClientRect()
    const top = container.scrollTop + targetRect.top - containerRect.top - 16
    container.scrollTo({ top: Math.max(0, top), behavior })
  })
}

function navigateConsoleSection(section) {
  if (section === 'education') showGovernance.value = true
  setActiveConsoleSection(section)
  if (window.location.hash !== `#${section}`) {
    // Updating location.hash invokes the browser's native anchor scrolling in
    // addition to our nested-container scroll, which can move the whole app
    // up by the topbar height after governance is expanded. pushState keeps
    // the URL shareable without triggering that competing scroll operation.
    window.history.pushState({ consoleSection: section }, '', `#${section}`)
  }
  scrollToConsoleSection(section)
}

function syncActiveConsoleSectionFromHash() {
  const section = window.location.hash.slice(1)
  const nextSection = ['runtime', 'audit', 'education'].includes(section) ? section : 'runtime'
  if (nextSection === 'education') showGovernance.value = true
  activeConsoleSection.value = nextSection
  scrollToConsoleSection(nextSection, 'auto')
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
  promptVersion: 'prompt-v1',
  policyVersion: 'policy-v1',
  budget: 1,
  idempotencyKey: '',
  permissions: '',
  agentMode: false,
  maxTurns: 1000,
  education: {
    enabled: false,
    learnerProfileId: '',
    learningGoalId: '',
    subject: '',
    gradeLevel: '',
    curriculumVersion: '',
    conceptKey: '',
    minDifficulty: null,
    maxDifficulty: null,
    pedagogicalMode: 'AUTO',
  },
})

// 权限选项从工具注册表的 requiredPermissions 聚合而来，避免创建 Run 时手写权限字符串。
// 权限快照仍以逗号分隔字符串保存在 form 中，兼容现有 API 和审计格式。
const permissionDescriptions = {
  'workspace.read': { label: '工作区读取', description: '浏览、搜索和读取项目文件' },
  'workspace.write': { label: '工作区写入', description: '编辑或写入项目文件（仍需审批）' },
  'workspace.exec': { label: '工作区命令执行', description: '运行白名单命令（仍需审批）' },
  'network.external': { label: '外部网络访问', description: '允许工具访问外部网络' },
  'education.read': { label: '读取教育画像', description: '读取课程元数据和学习者掌握度' },
  'education.write': { label: '更新教育状态', description: '记录形成性评价并更新学习者画像' },
  'education.assign': { label: '布置课程作业', description: '向组织内指定学习者创建课程作业' },
}

function permissionList(value) {
  return [...new Set(String(value || '').split(',').map((item) => item.trim()).filter(Boolean))]
}

const selectedPermissions = computed({
  get: () => permissionList(form.permissions),
  set: (values) => {
    form.permissions = [...new Set(values || [])].join(',')
  },
})

const permissionOptions = computed(() => {
  const values = new Set()
  tools.value.forEach((tool) => {
    const requiredPermissions = tool.requiredPermissions || []
    requiredPermissions.forEach((permission) => values.add(permission))
    if (String(tool.networkPolicy || '').toUpperCase() === 'ALLOW_EXTERNAL') values.add('network.external')
  })
  return [...values].sort().map((value) => ({
    value,
    label: permissionDescriptions[value]?.label || value,
    description: permissionDescriptions[value]?.description || '允许调用声明该权限的工具',
  }))
})

const selectedPermissionSummary = computed(() => {
  const count = selectedPermissions.value.length
  return count ? `已选择 ${count} 项：${selectedPermissions.value.join('、')}` : '未选择额外工具权限'
})

const documentForm = reactive({
  title: '订单处理规则',
  sensitivity: 'INTERNAL',
  allowedUsers: '',
})

const memoryForm = reactive({
  memoryType: 'USER_PREFERENCE',
  content: '',
  expiresAt: '',
})
const memoryDeletingId = ref('')

const learnerProfileForm = reactive({
  subject: '数学',
  gradeLevel: '高中一年级',
  curriculumVersion: '人教A版',
  learningGoal: '',
  language: 'zh-CN',
})

const learningGoalForm = reactive({
  learnerProfileId: '',
  title: '',
  conceptKey: '',
  targetMastery: 0.8,
})

const educationSourceForm = reactive({
  documentId: '',
  subject: '数学',
  gradeLevel: '高中一年级',
  curriculumVersion: '人教A版',
  chapter: '',
  learningObjectives: '',
  conceptTags: '',
  prerequisiteConcepts: '',
  difficultyLevel: 3,
  sourceType: 'TEXTBOOK',
})


const tenantPolicyForm = reactive({
  maxActiveRuns: 20,
  maxStepsPerRun: 1000,
  maxInputLength: 10000,
  maxBudget: 1000,
  maxCreatesPerMinute: 60,
  allowedTools: '',
})

// 组织策略沿用后端的逗号分隔协议，界面改为从工具注册表中勾选，避免手输工具名。
const allowedToolOptions = computed(() => tools.value
  .map((tool) => ({
    value: tool.name,
    label: tool.name,
    description: tool.description || '已注册工具',
    riskLevel: tool.riskLevel || 'UNKNOWN',
  }))
  .filter((tool) => tool.value)
  .sort((left, right) => left.value.localeCompare(right.value)))

const selectedAllowedTools = computed({
  get: () => permissionList(tenantPolicyForm.allowedTools),
  set: (values) => {
    tenantPolicyForm.allowedTools = [...new Set(values || [])].join(', ')
  },
})

const allAllowedToolsSelected = computed(() => {
  const options = allowedToolOptions.value
  return options.length > 0 && options.every((tool) => selectedAllowedTools.value.includes(tool.value))
})

const selectedAllowedToolsSummary = computed(() => {
  const selected = selectedAllowedTools.value
  if (!selected.length) return '留空：允许全部已注册工具'
  return selected.length === 1
    ? `已选择：${selected[0]}`
    : `已选择 ${selected.length} 项：${selected.join('、')}`
})

const activeEducationCourse = computed(() => educationCourses.value
  .find((course) => course.id === activeEducationCourseId.value) || null)
const activeEducationCourseIsOwner = computed(() => Boolean(
  activeEducationCourse.value && activeEducationCourse.value.ownerUserId === form.userId,
))
const visibleLearningAssignments = computed(() => {
  let entries = learningAssignments.value
  if (learningAssignmentCourseFilter.value) {
    entries = entries.filter((assignment) => assignment.courseId === learningAssignmentCourseFilter.value)
  }
  if (learningAssignmentLearnerFilter.value) {
    entries = entries.filter((assignment) => assignment.learnerUserId === learningAssignmentLearnerFilter.value)
  }
  return entries.slice(0, 8)
})

function toggleAllAllowedTools() {
  selectedAllowedTools.value = allAllowedToolsSelected.value
    ? []
    : allowedToolOptions.value.map((tool) => tool.value)
}

// 创建表单只保存过期时间和权限，生成的明文密钥不会写入浏览器存储。
const apiKeyForm = reactive({
  tenantId: form.tenantId,
  userId: form.userId,
  permissions: [...defaultApiKeyPermissions],
  expiresAt: '',
})

const stats = computed(() => ({
  total: summary.value?.total ?? runs.value.length,
  queued: summary.value?.queued ?? runs.value.filter((run) => run.status === 'QUEUED').length,
  running: summary.value?.running ?? runs.value.filter((run) => run.status === 'RUNNING').length,
  succeeded: summary.value?.succeeded ?? runs.value.filter((run) => run.status === 'SUCCEEDED').length,
  failed: summary.value?.failed ?? runs.value.filter((run) => run.status === 'FAILED').length,
  waitingApproval: summary.value?.waitingApproval ?? runs.value.filter((run) => run.status === 'WAITING_APPROVAL').length,
}))

const selectedStatus = computed(() => selectedRun.value?.run?.status || 'NONE')
const manualAssessmentSteps = computed(() => (selectedRun.value?.steps || [])
  .filter((step) => step.status === 'SUCCEEDED'))
const manualAssessmentGoal = computed(() => {
  const goalId = selectedRun.value?.run?.educationLearningGoalId
  return goalId ? learningGoals.value.find((goal) => goal.id === goalId) : null
})
const manualAssessmentAvailable = computed(() => Boolean(
  selectedRun.value?.run?.educationMode
  && selectedRun.value?.run?.educationLearningGoalId
  && (selectedRun.value?.run?.educationReviewPlanId || manualAssessmentGoal.value?.status === 'ACTIVE')
  && selectedStatus.value === 'SUCCEEDED'
  && manualAssessmentSteps.value.length,
))
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
const chatMessagePresentations = computed(() => new Map(chatMessages.value.map((message) => [
  message.id,
  message.role === 'ASSISTANT'
    ? presentChatCitations(message.content)
    : { content: message.content || '', sources: [] },
])))
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
    description: '从顶部配置新 Run 使用的大语言模型连接',
    keywords: 'model provider api key settings 模型 供应商 设置 密钥',
    icon: '◈',
    shortcut: '⌘ ,',
    action: () => { chatMode.value = false; showGovernance.value = true; showModelSettings.value = true },
    disabled: modelConfigLoading.value,
  },
  {
    id: 'embedding-settings',
    label: '打开向量连接设置',
    description: '从顶部配置知识库检索使用的向量模型连接',
    keywords: 'embedding vector retrieval provider api key 向量 检索 嵌入 设置',
    icon: '◎',
    action: () => { chatMode.value = false; showGovernance.value = true; showEmbeddingSettings.value = true },
    disabled: embeddingConfigLoading.value,
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
  return run?.agentMode ? `最多 ${run.maxTurns || '—'} 轮` : '单轮执行'
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

function formatRate(value) {
  const number = Number(value)
  return `${Math.round((Number.isFinite(number) ? number : 0) * 100)}%`
}

function clearMessages() {
  errorMessage.value = ''
  if (noticeDismissTimer) {
    window.clearTimeout(noticeDismissTimer)
    noticeDismissTimer = 0
  }
  noticeMessage.value = ''
}

watch(noticeMessage, (message) => {
  if (noticeDismissTimer) {
    window.clearTimeout(noticeDismissTimer)
    noticeDismissTimer = 0
  }
  if (!message) return
  noticeDismissTimer = window.setTimeout(() => {
    if (noticeMessage.value === message) noticeMessage.value = ''
    noticeDismissTimer = 0
  }, 10000)
})

watch(chatEducation, persistChatEducation, { deep: true })

watch(() => selectedRun.value?.run?.id, () => {
  manualAssessmentForm.stepId = manualAssessmentSteps.value.at(-1)?.id || ''
  manualAssessmentForm.correct = ''
  manualAssessmentForm.observedMastery = ''
  manualAssessmentForm.evidenceText = ''
  manualAssessmentForm.feedback = ''
  manualAssessmentError.value = ''
})

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

function defaultChatEducation() {
  return {
    enabled: false,
    learnerProfileId: '',
    learningGoalId: '',
    subject: '',
    gradeLevel: '',
    curriculumVersion: '',
    conceptKey: '',
    minDifficulty: null,
    maxDifficulty: null,
    pedagogicalMode: 'AUTO',
  }
}

function readChatEducation() {
  const fallback = defaultChatEducation()
  if (typeof window === 'undefined') return fallback
  try {
    const parsed = JSON.parse(window.localStorage.getItem('harnessChatEducation') || 'null')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return fallback
    return {
      ...fallback,
      ...parsed,
      enabled: Boolean(parsed.enabled),
      minDifficulty: parsed.minDifficulty == null ? null : Number(parsed.minDifficulty),
      maxDifficulty: parsed.maxDifficulty == null ? null : Number(parsed.maxDifficulty),
    }
  } catch {
    return fallback
  }
}

function persistChatEducation() {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem('harnessChatEducation', JSON.stringify({ ...chatEducation }))
  } catch {
    // 浏览器禁用本地存储时仍保留当前页面内的教育 Agent 设置。
  }
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

// 快速开始只填充草稿，不直接提交 Run，给用户留下补充约束和确认范围的机会。
function useQuickStartPrompt(prompt) {
  if (!activeConversationId.value || chatSending.value || chatUploading.value) return
  setChatInput(prompt, true)
}

function openCommandPalette() {
  if (showModelSettings.value || showEmbeddingSettings.value) return
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
  if (showEmbeddingSettings.value) {
    event.preventDefault()
    showEmbeddingSettings.value = false
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

const chatSourceLinePattern = /(^|\n)[ \t]*(?:来源|参考来源)\s*[:：]\s*\[((?:document|memory):[^\]\s]+)\]\s*([^\n]*)/g
const chatReadableSourceLinePattern = /(^|\n)[ \t]*(?:来源|参考来源)\s*[:：]\s*(?!\[?来源\s+\d+\]?\s*$)(?!\[来源：)([^\n]+)/g
const chatReadableCitationPattern = /\[来源：([^\]\n]+)\]/g
const chatCitationPattern = /\[((?:document|memory):[^\]\s]+)\]/g

function chatSourceBase(citation) {
  return String(citation || '').split('#', 1)[0]
}

function chatSourceMetadata(citation, fallbackTitle = '') {
  const normalized = chatSourceBase(citation)
  const separator = normalized.indexOf(':')
  const type = separator > 0 ? normalized.slice(0, separator) : ''
  const id = separator > 0 ? normalized.slice(separator + 1) : ''
  const cleanFallbackTitle = String(fallbackTitle || '').trim()
  const document = type === 'document'
    ? documents.value.find((item) => item.id === id)
    : documents.value.find((item) => cleanFallbackTitle === item.title || cleanFallbackTitle.startsWith(`${item.title}（`))
  const memory = type === 'memory'
    ? memories.value.find((item) => item.id === id)
    : memories.value.find((item) => cleanFallbackTitle.includes(item.memoryType))
  const title = document?.title
    || (memory ? `长期记忆 · ${memory.memoryType}` : '')
    || cleanFallbackTitle
    || (type === 'memory' ? '长期记忆' : '知识文档')
  return {
    key: normalized || `title:${title}`,
    citation,
    title,
    kindLabel: type === 'memory' || memory ? '长期记忆' : '知识文档',
    updatedAt: document?.updatedAt || document?.createdAt || memory?.createdAt || '',
  }
}

function presentChatCitations(content) {
  const sources = []
  const sourceByKey = new Map()
  const addSource = (citation, fallbackTitle = '') => {
    const metadata = chatSourceMetadata(citation, fallbackTitle)
    const existing = sourceByKey.get(metadata.key)
    if (existing) return existing
    const source = { ...metadata, index: sources.length + 1 }
    sourceByKey.set(metadata.key, source)
    sources.push(source)
    return source
  }

  let displayContent = String(content || '')
  displayContent = displayContent.replace(chatReadableCitationPattern, (match, title) => {
    const source = addSource('', title)
    return `[来源 ${source.index}]`
  })
  displayContent = displayContent.replace(chatSourceLinePattern, (match, prefix, citation, fallbackTitle) => {
    addSource(citation, fallbackTitle)
    return prefix
  })
  displayContent = displayContent.replace(chatReadableSourceLinePattern, (match, prefix, title) => {
    addSource('', title)
    return prefix
  })
  displayContent = displayContent.replace(chatCitationPattern, (match, citation) => {
    const source = addSource(citation)
    return `[来源 ${source.index}]`
  })
  return { content: displayContent, sources }
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
  const rawContent = String(message?.content || '').trim()
  const content = message?.role === 'ASSISTANT'
    ? presentChatCitations(rawContent).content.trim()
    : rawContent
  if (content) return content.length > 30 ? `${content.slice(0, 30)}…` : content
  return attachmentLabel(message?.attachments?.[0])
}

async function loadConversationFeedback(detail) {
  const runIds = [...new Set((detail?.messages || [])
    .filter((message) => message.role === 'ASSISTANT' && message.runId)
    .map((message) => message.runId))].slice(-20)
  if (!runIds.length) return
  const entries = await Promise.all(runIds.map(async (runId) => {
    try {
      return [runId, await api.getRunFeedback(runId)]
    } catch {
      return [runId, null]
    }
  }))
  if (activeConversationId.value !== detail?.conversation?.id) return
  chatFeedbackByRun.value = {
    ...chatFeedbackByRun.value,
    ...Object.fromEntries(entries),
  }
}

async function recordRunFeedback(message, rating) {
  if (!message?.runId || message.status !== 'COMPLETED' || feedbackSavingRunId.value) return
  feedbackSavingRunId.value = message.runId
  try {
    const feedback = await api.saveRunFeedback(message.runId, {
      rating,
      reasonCode: chatEducation.enabled ? 'EDUCATION' : 'GENERAL',
      messageId: message.id,
    })
    chatFeedbackByRun.value = { ...chatFeedbackByRun.value, [message.runId]: feedback }
    const linkedAttempt = learningGoalAssessments.value.find((attempt) => attempt.runId === message.runId)
    if (linkedAttempt && activeLearningGoal.value) {
      const recommendation = await api.getGoalRecommendation(activeLearningGoal.value.id)
      learningRecommendation.value = recommendation
      learningGoalRecommendationMap.value = {
        ...learningGoalRecommendationMap.value,
        [activeLearningGoal.value.id]: recommendation,
      }
    }
    noticeMessage.value = rating === 'POSITIVE' ? '已记录“有帮助”反馈。' : '已记录反馈，下一步会调整教学方式。'
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    feedbackSavingRunId.value = ''
  }
}

async function submitManualAssessment() {
  if (!manualAssessmentAvailable.value || manualAssessmentSaving.value) return
  if (!manualAssessmentForm.stepId || manualAssessmentForm.correct === '') {
    manualAssessmentError.value = '请选择复核步骤和测评结果。'
    return
  }
  if (!manualAssessmentForm.evidenceText.trim()) {
    manualAssessmentError.value = '请填写作答或评分依据，系统不会接受无证据的人工复核。'
    return
  }
  manualAssessmentSaving.value = true
  manualAssessmentError.value = ''
  try {
    const run = selectedRun.value.run
    const attempt = await api.submitGoalAssessment(run.educationLearningGoalId, {
      runId: run.id,
      stepId: manualAssessmentForm.stepId,
      conceptKey: run.educationConceptKey || activeLearningGoal.value?.conceptKey || '',
      correct: manualAssessmentForm.correct === 'true',
      observedMastery: manualAssessmentForm.observedMastery === ''
        ? null : Number(manualAssessmentForm.observedMastery),
      evidenceText: manualAssessmentForm.evidenceText.trim(),
      feedback: manualAssessmentForm.feedback.trim() || null,
    })
    learningGoalAssessments.value = [...learningGoalAssessments.value.filter((item) => item.id !== attempt.id), attempt]
      .sort((left, right) => new Date(left.createdAt) - new Date(right.createdAt))
    const recommendation = await api.getGoalRecommendation(run.educationLearningGoalId)
    learningRecommendation.value = recommendation
    learningGoalRecommendationMap.value = {
      ...learningGoalRecommendationMap.value,
      [run.educationLearningGoalId]: recommendation,
    }
    manualAssessmentForm.correct = ''
    manualAssessmentForm.observedMastery = ''
    manualAssessmentForm.evidenceText = ''
    manualAssessmentForm.feedback = ''
    noticeMessage.value = '人工复核已记录，学习目标进度和下一步动作已更新。'
  } catch (error) {
    manualAssessmentError.value = errorText(error)
  } finally {
    manualAssessmentSaving.value = false
  }
}

async function copyChatMessage(message) {
  if (!message?.content || copyingMessageId.value) return
  copyingMessageId.value = message.id
  try {
    const presentation = chatMessagePresentations.value.get(message.id)
    await navigator.clipboard.writeText(presentation?.content || message.content)
    noticeMessage.value = '助手回复已复制到剪贴板。'
  } catch {
    errorMessage.value = '复制失败，请检查浏览器剪贴板权限。'
  } finally {
    copyingMessageId.value = ''
  }
}

async function handleChatMarkdownClick(event) {
  const target = event.target instanceof Element ? event.target.closest('[data-copy-code="true"]') : null
  if (!target) return
  const code = target.closest('.markdown-code-block')?.querySelector('code')?.textContent || ''
  if (!code) return
  event.preventDefault()
  event.stopPropagation()
  try {
    await navigator.clipboard.writeText(code)
    target.textContent = '已复制'
    target.dataset.copyState = 'copied'
    window.setTimeout(() => {
      target.textContent = '复制'
      delete target.dataset.copyState
    }, 1400)
  } catch {
    errorMessage.value = '复制代码失败，请检查浏览器剪贴板权限。'
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
    scrollChatToBottom(true)
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

function updateChatFollowOutput(event) {
  const element = event?.currentTarget || event
  if (!element) return
  const distanceFromBottom = element.scrollHeight - element.scrollTop - element.clientHeight
  chatFollowOutput.value = distanceFromBottom <= 72
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

/** 请求始终携带当前会话绑定的 workspaceId，后端会再次验证所属组织和用户。 */
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
    void loadConversationFeedback(detail)
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
    scrollChatToBottom(true)
    focusChatComposer()
  } catch (error) {
    if (selectionToken === conversationSelectionToken) errorMessage.value = errorText(error)
  } finally {
    if (selectionToken === conversationSelectionToken) chatLoading.value = false
  }
}

function scrollChatToBottom(force = false) {
  if (typeof window === 'undefined') return
  window.requestAnimationFrame(() => {
    const element = document.querySelector('.chat-messages')
    if (!element || (!force && !chatFollowOutput.value)) return
    element.scrollTop = element.scrollHeight
    chatFollowOutput.value = true
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
    scrollChatToBottom(true)
  }
  try {
    if (files.length) {
      chatUploading.value = true
      uploadedAttachments = await api.uploadConversationAttachments(conversationId, files)
      chatUploading.value = false
    }
    const education = chatEducation.enabled ? {
      ...chatEducation,
      minDifficulty: chatEducation.minDifficulty == null ? null : Number(chatEducation.minDifficulty),
      maxDifficulty: chatEducation.maxDifficulty == null ? null : Number(chatEducation.maxDifficulty),
    } : null
    const detail = await api.sendConversationMessage(conversationId, {
      content,
      maxTurns: chatMaxTurns.value,
      attachmentIds: uploadedAttachments.map((attachment) => attachment.id),
      education,
    }, `chat-${crypto.randomUUID?.() || Date.now()}`)
    messageSubmitted = true
    activeConversation.value = detail
    void loadConversationFeedback(detail)
    const runId = latestConversationRun(detail)
    if (runId) void selectRun(runId, false, false)
    void loadConversations(conversationId)
    scrollChatToBottom(true)
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
    void loadConversationFeedback(detail)
    const runId = latestConversationRun(detail)
    if (runId && selectedRun.value?.run?.id !== runId) {
      await selectRun(runId, false, false)
    }
    if (selectionToken !== conversationSelectionToken || activeConversationId.value !== conversationId) return
    if (!detail.messages.some((message) => message.status === 'PENDING')) {
      await loadConversations(conversationId)
      void loadLearningTasks()
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
  void loadConversationFeedback(detail)
  const runId = latestConversationRun(detail)
  if (runId && selectedRun.value?.run?.id !== runId) {
    await selectRun(runId, false, false)
  }
  if (selectionToken !== conversationSelectionToken || activeConversationId.value !== conversationId) return
  conversations.value = await api.listConversations()
  if (selectionToken !== conversationSelectionToken || activeConversationId.value !== conversationId) return
  scrollChatToBottom(true)
}

async function loadDashboard() {
  clearMessages()
  try {
    const [, toolData, summaryData, documentData, memoryData, contextConfigurationData] = await Promise.all([
      loadRunsPage(),
      api.listTools(),
      api.dashboardSummary(),
      api.listDocuments(),
      api.listMemories(),
      api.contextConfiguration(),
    ])
    tools.value = toolData
    summary.value = summaryData
    documents.value = documentData
    memories.value = memoryData
    contextConfiguration.value = contextConfigurationData
    await loadEducationData()
    if (selectedRun.value) {
      await selectRun(selectedRun.value.run.id, false)
    } else if (runs.value.length) {
      await selectRun(runs.value[0].id, false)
    }
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function loadEducationData() {
  try {
    const [sources, profiles, goals, tasks, assignments, metrics, courses] = await Promise.all([
      api.listEducationSources(),
      api.listLearnerProfiles(),
      api.listLearningGoals(),
      api.listLearningTasks(),
      api.listLearningAssignments(),
      api.getEducationMetrics(),
      api.listEducationCourses(),
    ])
    educationSources.value = sources
    learnerProfiles.value = profiles
    learningGoals.value = goals
    learningTasks.value = tasks
    learningAssignments.value = assignments
    educationMetrics.value = metrics
    educationCourses.value = courses || []
    const progressEntries = await Promise.all(assignments.slice(0, 20).map(async (assignment) => {
      try {
        return [assignment.id, await api.getLearningAssignmentProgress(assignment.id)]
      } catch {
        return [assignment.id, null]
      }
    }))
    learningAssignmentProgressMap.value = Object.fromEntries(
      progressEntries.filter(([, value]) => value))
    const evidenceEntries = await Promise.all(assignments.slice(0, 20).map(async (assignment) => {
      try {
        return [assignment.id, await api.getLearningAssignmentEvidence(assignment.id)]
      } catch {
        return [assignment.id, null]
      }
    }))
    learningAssignmentEvidenceMap.value = Object.fromEntries(
      evidenceEntries.filter(([, value]) => value))
    const feedbackEntries = await Promise.all(assignments.slice(0, 20).map(async (assignment) => {
      try {
        return [assignment.id, await api.listLearningAssignmentFeedback(assignment.id)]
      } catch {
        return [assignment.id, null]
      }
    }))
    learningAssignmentFeedbackMap.value = Object.fromEntries(
      feedbackEntries.filter(([, value]) => value))
    await Promise.all([loadLearningNotifications(), loadLearningAssignmentNotifications()])
    const recommendationEntries = await Promise.all(goals.map(async (goal) => {
      try {
        return [goal.id, await api.getGoalRecommendation(goal.id)]
      } catch {
        return [goal.id, null]
      }
    }))
    learningGoalRecommendationMap.value = Object.fromEntries(recommendationEntries.filter(([, value]) => value))
    activeLearnerProfile.value = profiles.find((profile) => profile.active) || profiles[0] || null
    if (activeLearnerProfile.value) {
      applyLearnerProfileToEducationRun(activeLearnerProfile.value)
    }
    const rememberedGoalId = form.education.learningGoalId || chatEducation.learningGoalId
    const nextGoal = goals.find((goal) => goal.id === rememberedGoalId)
      || goals.find((goal) => goal.status === 'ACTIVE')
      || goals[0]
    if (nextGoal) await selectLearningGoal(nextGoal, false)
    const ownerCourses = educationCourses.value.filter((course) => course.ownerUserId === form.userId)
    const nextCourse = ownerCourses.find((course) => course.id === activeEducationCourseId.value)
      || ownerCourses.find((course) => course.status === 'ACTIVE')
      || ownerCourses[0]
    if (nextCourse) {
      await loadEducationCourseWorkspace(nextCourse.id)
    } else {
      activeEducationCourseId.value = ''
      educationCourseEnrollments.value = []
      educationCourseProgress.value = null
    }
    educationError.value = ''
  } catch (error) {
    // 教育权限是可选的；不应让没有教育权限的通用 Agent 用户无法打开控制台。
    educationError.value = errorText(error)
  }
}

async function loadEducationCourseWorkspace(courseId) {
  const course = educationCourses.value.find((item) => item.id === courseId)
  if (!course || course.ownerUserId !== form.userId) {
    activeEducationCourseId.value = course?.id || ''
    educationCourseEnrollments.value = []
    educationCourseProgress.value = null
    return
  }
  activeEducationCourseId.value = course.id
  educationCourseLoading.value = true
  try {
    const [enrollments, progress] = await Promise.all([
      api.listEducationCourseEnrollments(course.id),
      api.getEducationCourseProgress(course.id),
    ])
    if (activeEducationCourseId.value === course.id) {
      educationCourseEnrollments.value = enrollments || []
      educationCourseProgress.value = progress || null
    }
  } catch (error) {
    educationCourseEnrollments.value = []
    educationCourseProgress.value = null
    educationError.value = errorText(error)
  } finally {
    educationCourseLoading.value = false
  }
}

async function selectEducationCourse(course) {
  if (!course) return
  activeEducationCourseId.value = course.id
  learningAssignmentCourseFilter.value = course.id
  learningAssignmentLearnerFilter.value = ''
  await loadEducationCourseWorkspace(course.id)
  noticeMessage.value = `已打开课程：${course.title}`
}

async function createEducationCourse() {
  if (educationCourseSaving.value
    || !educationCourseForm.code.trim()
    || !educationCourseForm.title.trim()
    || !educationCourseForm.subject.trim()
    || !educationCourseForm.gradeLevel.trim()
    || !educationCourseForm.curriculumVersion.trim()) return
  clearMessages()
  educationCourseSaving.value = true
  try {
    const course = await api.createEducationCourse({
      code: educationCourseForm.code.trim(),
      title: educationCourseForm.title.trim(),
      subject: educationCourseForm.subject.trim(),
      gradeLevel: educationCourseForm.gradeLevel.trim(),
      curriculumVersion: educationCourseForm.curriculumVersion.trim(),
    })
    educationCourses.value = [course, ...educationCourses.value.filter((item) => item.id !== course.id)]
    educationCourseForm.code = ''
    educationCourseForm.title = ''
    await loadEducationCourseWorkspace(course.id)
    noticeMessage.value = `课程“${course.title}”已创建，可以开始维护名单。`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    educationCourseSaving.value = false
  }
}

async function enrollEducationLearner() {
  const course = activeEducationCourse.value
  const learnerUserId = educationCourseEnrollmentForm.learnerUserId.trim()
  if (!course || !activeEducationCourseIsOwner.value || !learnerUserId
    || educationCourseRosterSaving.value) return
  clearMessages()
  educationCourseRosterSaving.value = true
  try {
    await api.enrollEducationLearner(course.id, { learnerUserId })
    educationCourseEnrollmentForm.learnerUserId = ''
    await loadEducationCourseWorkspace(course.id)
    await loadEducationData()
    noticeMessage.value = `已将 ${learnerUserId} 加入课程名单。`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    educationCourseRosterSaving.value = false
  }
}

async function removeEducationLearner(enrollment) {
  const course = activeEducationCourse.value
  if (!course || !activeEducationCourseIsOwner.value || !enrollment?.learnerUserId
    || educationCourseActionId.value) return
  if (!window.confirm(`确认将 ${enrollment.learnerUserId} 移出课程名单吗？`)) return
  educationCourseActionId.value = enrollment.learnerUserId
  clearMessages()
  try {
    await api.removeEducationLearner(course.id, enrollment.learnerUserId)
    await loadEducationCourseWorkspace(course.id)
    await loadEducationData()
    noticeMessage.value = `已将 ${enrollment.learnerUserId} 移出课程名单。`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    educationCourseActionId.value = ''
  }
}

async function archiveEducationCourse(course) {
  if (!course || course.ownerUserId !== form.userId || educationCourseActionId.value) return
  if (!window.confirm(`确认归档课程“${course.title}”吗？归档后不能再加名单或布置新作业。`)) return
  educationCourseActionId.value = course.id
  clearMessages()
  try {
    const archived = await api.archiveEducationCourse(course.id)
    educationCourses.value = educationCourses.value.map((item) => item.id === archived.id ? archived : item)
    await loadEducationData()
    noticeMessage.value = `课程“${course.title}”已归档，历史证据仍保留。`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    educationCourseActionId.value = ''
  }
}

async function assignEducationCourse() {
  const course = activeEducationCourse.value
  if (!course || !activeEducationCourseIsOwner.value || course.status !== 'ACTIVE'
    || educationCourseAssignmentSaving.value
    || !educationCourseAssignmentForm.title.trim()
    || !educationCourseAssignmentForm.instructions.trim()
    || !educationCourseAssignmentForm.conceptKey.trim()) return
  clearMessages()
  educationCourseAssignmentSaving.value = true
  try {
    const idempotencyKey = `course-${course.id}-${Date.now()}-${globalThis.crypto?.randomUUID?.() || Math.random().toString(36).slice(2)}`
    const result = await api.assignEducationCourse(course.id, {
      title: educationCourseAssignmentForm.title.trim(),
      instructions: educationCourseAssignmentForm.instructions.trim(),
      conceptKey: educationCourseAssignmentForm.conceptKey.trim(),
      targetMastery: Number(educationCourseAssignmentForm.targetMastery) || 0.8,
      dueAt: educationCourseAssignmentForm.dueAt
        ? new Date(educationCourseAssignmentForm.dueAt).toISOString() : null,
    }, idempotencyKey)
    educationCourseAssignmentForm.title = ''
    educationCourseAssignmentForm.instructions = ''
    educationCourseAssignmentForm.conceptKey = ''
    await loadEducationData()
    await loadEducationCourseWorkspace(course.id)
    noticeMessage.value = `已向课程活跃名单布置 ${result.assignmentCount} 份作业。`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    educationCourseAssignmentSaving.value = false
  }
}

function courseLearnerAttentionCount(learner) {
  if (!learner) return 0
  return Number(learner.awaitingEvidence || 0) + Number(learner.retryRequired || 0)
    + Number(learner.overdue || 0) + Number(learner.reviewPending || 0)
    + Number(learner.openInterventionCount || 0)
}

function focusCourseLearner(learner) {
  if (!learner?.learnerUserId) return
  learningAssignmentCourseFilter.value = activeEducationCourseId.value
  learningAssignmentLearnerFilter.value = learner.learnerUserId
  nextTick(() => document.getElementById('learning-assignment-list')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function clearLearningAssignmentFilter() {
  learningAssignmentCourseFilter.value = ''
  learningAssignmentLearnerFilter.value = ''
}

async function loadLearningTasks() {
  try {
    learningTasks.value = await api.listLearningTasks()
  } catch (error) {
    // 任务面板是教育模式的增强能力；权限不足时保留现有目标工作台。
    if (!educationError.value) educationError.value = errorText(error)
  }
}

async function loadLearningNotifications() {
  try {
    const page = await api.listLearningNotifications(false, 50)
    learningNotifications.value = page?.notifications || []
    learningNotificationUnreadCount.value = Number(page?.unreadCount || 0)
  } catch (error) {
    // 通知是教育任务的增强触达能力；任务列表仍可在迁移尚未完成时正常显示。
    if (!educationError.value) educationError.value = errorText(error)
  }
}

async function loadLearningAssignmentNotifications() {
  try {
    const page = await api.listLearningAssignmentNotifications(false, 50)
    learningAssignmentNotifications.value = page?.notifications || []
    learningAssignmentNotificationUnreadCount.value = Number(page?.unreadCount || 0)
  } catch (error) {
    // 作业触达属于教育工作台增强能力；通知接口不可用时仍保留作业和任务主流程。
    if (!educationError.value) educationError.value = errorText(error)
  }
}

async function markLearningNotificationRead(notification) {
  if (!notification?.id || !notification.unread) return
  try {
    const updated = await api.markLearningNotificationRead(notification.id)
    learningNotifications.value = learningNotifications.value.map((item) =>
      item.id === updated.id ? updated : item)
    learningNotificationUnreadCount.value = Math.max(0, learningNotificationUnreadCount.value - 1)
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function markAllLearningNotificationsRead() {
  if (!learningNotificationUnreadCount.value) return
  try {
    await api.markAllLearningNotificationsRead()
    learningNotifications.value = learningNotifications.value.map((item) => ({
      ...item,
      status: 'READ',
      unread: false,
      readAt: item.readAt || new Date().toISOString(),
    }))
    learningNotificationUnreadCount.value = 0
    noticeMessage.value = '学习任务通知已全部标记为已读。'
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function markLearningAssignmentNotificationRead(notification) {
  if (!notification?.id || !notification.unread) return
  try {
    const updated = await api.markLearningAssignmentNotificationRead(notification.id)
    learningAssignmentNotifications.value = learningAssignmentNotifications.value.map((item) =>
      item.id === updated.id ? updated : item)
    learningAssignmentNotificationUnreadCount.value = Math.max(
      0, learningAssignmentNotificationUnreadCount.value - 1)
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function markAllLearningAssignmentNotificationsRead() {
  if (!learningAssignmentNotificationUnreadCount.value) return
  try {
    await api.markAllLearningAssignmentNotificationsRead()
    learningAssignmentNotifications.value = learningAssignmentNotifications.value.map((item) => ({
      ...item,
      status: 'READ',
      unread: false,
      readAt: item.readAt || new Date().toISOString(),
    }))
    learningAssignmentNotificationUnreadCount.value = 0
    noticeMessage.value = '课程作业通知已全部标记为已读。'
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function openLearningNotification(notification) {
  if (!notification) return
  await markLearningNotificationRead(notification)
  const task = learningTasks.value.find((item) => item.id === notification.learningTaskId)
  if (!task) {
    noticeMessage.value = '通知对应的学习任务已不在当前列表中，请刷新教育状态。'
    return
  }
  if (['OPEN', 'IN_PROGRESS', 'AWAITING_EVIDENCE', 'FAILED'].includes(task.status)) {
    await startLearningTask(task)
    return
  }
  const goal = learningGoals.value.find((item) => item.id === task.learningGoalId)
  if (goal) await selectLearningGoal(goal, false)
}

function learningAssignmentStatusLabel(status) {
  return {
    ASSIGNED: '待接受',
    ACCEPTED: '学习中',
    AWAITING_EVIDENCE: '待补证据',
    RETRY_REQUIRED: '待重试/返工',
    OVERDUE: '已逾期',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
  }[status] || status || '未知'
}

function learningAssignmentReviewStatusLabel(status) {
  return {
    NOT_REQUIRED: '未进入教师确认',
    PENDING: '待教师确认',
    VERIFIED: '教师已确认',
    REVISION_REQUIRED: '教师已退回返工',
  }[status] || status || '未知'
}

function learningAssignmentFeedbackActionLabel(action) {
  return {
    COMMENT: '教师反馈',
    REQUEST_EVIDENCE: '补充证据',
    RECOMMEND_RETRY: '建议重试',
    RESCHEDULE: '重新安排',
  }[action] || action || '反馈'
}

function assessmentRetrievalEvidenceLabel(attempt) {
  return (attempt?.retrievalEvidence || [])
    .map((evidence) => evidence.title || evidence.citation || evidence.documentId)
    .filter(Boolean)
    .join('、')
}

function learningAssignmentHasOpenIntervention(assignment) {
  if (!assignment || assignment.learnerUserId !== form.userId) return false
  return (learningAssignmentFeedbackMap.value[assignment.id] || [])
    .some((feedback) => feedback.status === 'OPEN'
      && ['REQUEST_EVIDENCE', 'RECOMMEND_RETRY'].includes(feedback.action))
}

function startLearningAssignmentFeedback(assignment) {
  if (!assignment?.id || assignment.teacherUserId !== form.userId) return
  learningAssignmentFeedbackForm.assignmentId = assignment.id
  learningAssignmentFeedbackForm.action = 'COMMENT'
  learningAssignmentFeedbackForm.message = ''
  learningAssignmentFeedbackForm.suggestedDueAt = ''
}

function closeLearningAssignmentFeedback() {
  learningAssignmentFeedbackForm.assignmentId = ''
  learningAssignmentFeedbackForm.message = ''
  learningAssignmentFeedbackForm.suggestedDueAt = ''
}

async function submitLearningAssignmentFeedback() {
  const assignmentId = learningAssignmentFeedbackForm.assignmentId
  if (!assignmentId || !learningAssignmentFeedbackForm.message.trim()
    || learningAssignmentFeedbackSavingId.value) return
  if (learningAssignmentFeedbackForm.action === 'RESCHEDULE'
    && !learningAssignmentFeedbackForm.suggestedDueAt) return
  learningAssignmentFeedbackSavingId.value = assignmentId
  clearMessages()
  try {
    await api.createLearningAssignmentFeedback(assignmentId, {
      action: learningAssignmentFeedbackForm.action,
      message: learningAssignmentFeedbackForm.message.trim(),
      suggestedDueAt: learningAssignmentFeedbackForm.action === 'RESCHEDULE'
        ? new Date(learningAssignmentFeedbackForm.suggestedDueAt).toISOString() : null,
    })
    closeLearningAssignmentFeedback()
    await loadEducationData()
    noticeMessage.value = '教师反馈已发送给学习者。'
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    learningAssignmentFeedbackSavingId.value = ''
  }
}

async function acknowledgeLearningAssignmentFeedback(assignment, feedback) {
  if (!assignment?.id || !feedback?.id || feedback.status !== 'OPEN'
    || assignment.learnerUserId !== form.userId
    || learningAssignmentFeedbackAcknowledgingId.value) return
  learningAssignmentFeedbackAcknowledgingId.value = feedback.id
  try {
    const updated = await api.acknowledgeLearningAssignmentFeedback(assignment.id, feedback.id)
    learningAssignmentFeedbackMap.value = {
      ...learningAssignmentFeedbackMap.value,
      [assignment.id]: (learningAssignmentFeedbackMap.value[assignment.id] || [])
        .map((item) => item.id === updated.id ? updated : item),
    }
    noticeMessage.value = '已确认教师反馈。'
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    learningAssignmentFeedbackAcknowledgingId.value = ''
  }
}

async function verifyLearningAssignment(assignment) {
  if (!assignment?.id || assignment.teacherUserId !== form.userId
    || assignment.status !== 'COMPLETED' || assignment.reviewStatus !== 'PENDING'
    || learningAssignmentReviewSavingId.value) return
  const note = window.prompt('可填写教师确认说明（可选）：', '')
  if (note === null) return
  learningAssignmentReviewSavingId.value = assignment.id
  clearMessages()
  try {
    await api.reviewLearningAssignment(assignment.id, { decision: 'VERIFY', note: note.trim() || null })
    await loadEducationData()
    noticeMessage.value = `已确认课程作业：${assignment.title}`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    learningAssignmentReviewSavingId.value = ''
  }
}

async function returnLearningAssignmentForRevision(assignment) {
  if (!assignment?.id || assignment.teacherUserId !== form.userId
    || assignment.status !== 'COMPLETED' || assignment.reviewStatus !== 'PENDING'
    || learningAssignmentReviewSavingId.value) return
  const note = window.prompt('请填写退回返工说明（必填）：', '')
  if (note === null || !note.trim()) return
  learningAssignmentReviewSavingId.value = assignment.id
  clearMessages()
  try {
    await api.reviewLearningAssignment(assignment.id, { decision: 'RETURN', note: note.trim() })
    await loadEducationData()
    noticeMessage.value = `已退回课程作业返工：${assignment.title}`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    learningAssignmentReviewSavingId.value = ''
  }
}

async function openLearningAssignmentNotification(notification) {
  if (!notification) return
  await markLearningAssignmentNotificationRead(notification)
  let assignment = learningAssignments.value.find((item) => item.id === notification.learningAssignmentId)
  if (!assignment) {
    await loadEducationData()
    assignment = learningAssignments.value.find((item) => item.id === notification.learningAssignmentId)
  }
  if (['FEEDBACK', 'FEEDBACK_ACKNOWLEDGED'].includes(notification.notificationType)) {
    await nextTick()
    document.getElementById(`learning-assignment-${assignment.id}`)?.scrollIntoView({
      behavior: 'smooth', block: 'center',
    })
    noticeMessage.value = notification.notificationType === 'FEEDBACK_ACKNOWLEDGED'
      ? `已打开课程作业“${assignment.title}”的反馈确认回执。`
      : `已打开课程作业“${assignment.title}”的教师反馈。`
    return
  }
  if (!assignment) {
    noticeMessage.value = '通知对应的课程作业已不在当前列表中，请刷新教育状态。'
    return
  }
  if (['ASSIGNED', 'RETRY_REQUIRED', 'REVISION_REQUIRED'].includes(notification.notificationType)
    && ((notification.notificationType === 'ASSIGNED' && assignment.status === 'ASSIGNED')
      || (['RETRY_REQUIRED', 'REVISION_REQUIRED'].includes(notification.notificationType) && assignment.status === 'RETRY_REQUIRED'))
    && assignment.learnerUserId === form.userId) {
    await startLearningAssignment(assignment)
    return
  }
  const goal = learningGoals.value.find((item) => item.id === assignment.learningGoalId)
  if (goal) {
    await selectLearningGoal(goal, false)
    noticeMessage.value = `已打开课程作业“${assignment.title}”对应的学习目标。`
  } else {
    noticeMessage.value = `课程作业“${assignment.title}”当前状态：${learningAssignmentStatusLabel(assignment.status)}`
  }
}

function applyLearnerProfileToEducationRun(profile) {
  if (!profile) return
  if (activeLearningGoal.value && activeLearningGoal.value.learnerProfileId !== profile.id) {
    activeLearningGoal.value = null
    learningGoalAssessments.value = []
    learningRecommendation.value = null
    form.education.learningGoalId = ''
    chatEducation.learningGoalId = ''
    chatEducation.conceptKey = ''
  }
  form.education.learnerProfileId = profile.id
  learningGoalForm.learnerProfileId = profile.id
  form.education.subject = profile.subject || ''
  form.education.gradeLevel = profile.gradeLevel || ''
  form.education.curriculumVersion = profile.curriculumVersion || ''
  applyLearnerProfileToChat(profile)
}

function applyLearnerProfileToChat(profile) {
  if (!profile) return
  chatEducation.learnerProfileId = profile.id
  chatEducation.subject = profile.subject || ''
  chatEducation.gradeLevel = profile.gradeLevel || ''
  chatEducation.curriculumVersion = profile.curriculumVersion || ''
}

async function selectLearningGoal(goal, notify = true) {
  if (!goal) return
  activeLearningGoal.value = goal
  form.education.learningGoalId = goal.id
  form.education.learnerProfileId = goal.learnerProfileId
  form.education.conceptKey = goal.conceptKey
  chatEducation.learningGoalId = goal.id
  chatEducation.learnerProfileId = goal.learnerProfileId
  chatEducation.conceptKey = goal.conceptKey
  const profile = learnerProfiles.value.find((item) => item.id === goal.learnerProfileId)
  if (profile) {
    activeLearnerProfile.value = profile
    form.education.subject = profile.subject || ''
    form.education.gradeLevel = profile.gradeLevel || ''
    form.education.curriculumVersion = profile.curriculumVersion || ''
    applyLearnerProfileToChat(profile)
    chatEducation.learningGoalId = goal.id
    chatEducation.conceptKey = goal.conceptKey
  }
  try {
    const [assessments, recommendation] = await Promise.all([
      api.listGoalAssessments(goal.id),
      api.getGoalRecommendation(goal.id),
    ])
    if (activeLearningGoal.value?.id === goal.id) {
      learningGoalAssessments.value = assessments
      learningRecommendation.value = recommendation
      learningGoalRecommendationMap.value = {
        ...learningGoalRecommendationMap.value,
        [goal.id]: recommendation,
      }
    }
  } catch (error) {
    educationError.value = errorText(error)
  }
  if (notify) noticeMessage.value = `已选择学习目标：${goal.title}`
}

async function createLearningGoal() {
  if (educationLoading.value || !learningGoalForm.title.trim() || !learningGoalForm.conceptKey.trim()) return
  clearMessages()
  educationLoading.value = true
  try {
    const goal = await api.createLearningGoal({
      learnerProfileId: learningGoalForm.learnerProfileId || activeLearnerProfile.value?.id || null,
      title: learningGoalForm.title.trim(),
      conceptKey: learningGoalForm.conceptKey.trim(),
      targetMastery: Number(learningGoalForm.targetMastery) || 0.8,
    })
    learningGoals.value = [goal, ...learningGoals.value.filter((item) => item.id !== goal.id)]
    learningGoalForm.title = ''
    learningGoalForm.conceptKey = ''
    await selectLearningGoal(goal, false)
    noticeMessage.value = '学习目标已创建；后续教育 Run 会围绕该目标累计进度并触发下一步建议。'
    educationError.value = ''
  } catch (error) {
    educationError.value = errorText(error)
  } finally {
    educationLoading.value = false
  }
}

async function createLearningAssignment() {
  if (learningAssignmentSaving.value
    || !learningAssignmentForm.learnerUserId.trim()
    || !learningAssignmentForm.title.trim()
    || !learningAssignmentForm.instructions.trim()
    || !learningAssignmentForm.subject.trim()
    || !learningAssignmentForm.gradeLevel.trim()
    || !learningAssignmentForm.curriculumVersion.trim()
    || !learningAssignmentForm.conceptKey.trim()) return
  clearMessages()
  learningAssignmentSaving.value = true
  try {
    await api.createLearningAssignment({
      learnerUserId: learningAssignmentForm.learnerUserId.trim(),
      title: learningAssignmentForm.title.trim(),
      instructions: learningAssignmentForm.instructions.trim(),
      subject: learningAssignmentForm.subject.trim(),
      gradeLevel: learningAssignmentForm.gradeLevel.trim(),
      curriculumVersion: learningAssignmentForm.curriculumVersion.trim(),
      conceptKey: learningAssignmentForm.conceptKey.trim(),
      targetMastery: Number(learningAssignmentForm.targetMastery) || 0.8,
      dueAt: learningAssignmentForm.dueAt
        ? new Date(learningAssignmentForm.dueAt).toISOString() : null,
    })
    learningAssignmentForm.title = ''
    learningAssignmentForm.instructions = ''
    learningAssignmentForm.conceptKey = ''
    await loadEducationData()
    noticeMessage.value = `已向 ${learningAssignmentForm.learnerUserId.trim()} 布置课程作业。`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    learningAssignmentSaving.value = false
  }
}

async function startLearningAssignment(assignment) {
  if (!assignment?.id || learningAssignmentAcceptingId.value) return
  learningAssignmentAcceptingId.value = assignment.id
  clearMessages()
  try {
    const assignmentAttemptKey = `learning-assignment-${assignment.id}-${assignment.updatedAt || assignment.createdAt || assignment.status}`
    const started = await api.startLearningAssignment(
      assignment.id,
      { maxTurns: chatMaxTurns.value },
      assignmentAttemptKey,
    )
    activeConversation.value = started.conversation
    conversations.value = [started.conversation.conversation, ...conversations.value
      .filter((item) => item.id !== started.conversation.conversation.id)]
    rememberConversation(started.conversation.conversation.id)
    chatMode.value = true
    const runId = latestConversationRun(started.conversation)
    if (runId) void selectRun(runId, false, false)
    await loadEducationData()
    void loadConversations(started.conversation.conversation.id)
    noticeMessage.value = `已开始课程作业：${started.assignment.title}`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    learningAssignmentAcceptingId.value = ''
  }
}

async function cancelLearningAssignment(assignment) {
  if (!assignment?.id || assignment.teacherUserId !== form.userId) return
  if (!window.confirm(`确认取消课程作业“${assignment.title}”吗？`)) return
  clearMessages()
  try {
    await api.cancelLearningAssignment(assignment.id)
    await loadEducationData()
    noticeMessage.value = `已取消课程作业：${assignment.title}`
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function useLearningRecommendation() {
  const recommendation = learningRecommendation.value
  if (!recommendation) return
  if (chatSending.value || chatUploading.value || pendingChatMessage.value) {
    noticeMessage.value = '当前对话仍在执行；建议已保留在输入框中，请稍后发送。'
    chatMode.value = true
    return
  }
  chatEducation.enabled = true
  chatEducation.learningGoalId = recommendation.learningGoalId
  chatEducation.conceptKey = recommendation.conceptKey
  const goal = learningGoals.value.find((item) => item.id === recommendation.learningGoalId)
  if (goal) await selectLearningGoal(goal, false)
  if (recommendation.goalStatus === 'COMPLETED') {
    const task = learningTasks.value.find((item) => item.learningGoalId === recommendation.learningGoalId
      && ['OPEN', 'IN_PROGRESS', 'AWAITING_EVIDENCE', 'DEFERRED', 'FAILED'].includes(item.status))
    if (task) {
      await startLearningTask(task)
      return
    }
  }
  chatInput.value = recommendation.nextActionPrompt
  chatMode.value = true
  await nextTick()
  if (recommendation.nextActionType === 'WAIT') {
    noticeMessage.value = recommendation.nextActionPrompt
    chatInputRef.value?.focus()
    return
  }
  clearMessages()
  try {
    const detail = await api.executeLearningGoalNextAction(recommendation.learningGoalId, {
      conversationId: activeConversationId.value || null,
      maxTurns: chatMaxTurns.value,
    }, `learning-action-${crypto.randomUUID?.() || Date.now()}`)
    activeConversation.value = detail
    conversations.value = [detail.conversation, ...conversations.value
      .filter((item) => item.id !== detail.conversation.id)]
    rememberConversation(detail.conversation.id)
    setChatInput('')
    const runId = latestConversationRun(detail)
    if (runId) void selectRun(runId, false, false)
    void loadConversations(detail.conversation.id)
    noticeMessage.value = recommendation.goalStatus === 'COMPLETED'
      ? `已开始保持度复习：${recommendation.nextActionTitle}`
      : `已开始下一步：${recommendation.nextActionTitle}`
  } catch (error) {
    chatInput.value = recommendation.nextActionPrompt
    saveChatDraft(activeConversationId.value, recommendation.nextActionPrompt)
    errorMessage.value = errorText(error)
    chatInputRef.value?.focus()
  }
}

async function startLearningTask(task) {
  if (!task || learningTaskStartingId.value) return
  if (task.status === 'DEFERRED' && new Date(task.scheduledAt).getTime() > Date.now()) {
    noticeMessage.value = `任务尚未到期：${formatDate(task.scheduledAt)}`
    return
  }
  learningTaskStartingId.value = task.id
  clearMessages()
  try {
    const taskAttemptKey = `learning-task-${task.id}-${task.updatedAt || task.createdAt || task.status}`
    const result = await api.startLearningTask(task.id, { maxTurns: chatMaxTurns.value }, taskAttemptKey)
    activeConversation.value = result.conversation
    conversations.value = [result.conversation.conversation, ...conversations.value
      .filter((item) => item.id !== result.conversation.conversation.id)]
    rememberConversation(result.conversation.conversation.id)
    chatMode.value = true
    const runId = latestConversationRun(result.conversation)
    if (runId) void selectRun(runId, false, false)
    await loadLearningTasks()
    await loadLearningNotifications()
    noticeMessage.value = `已开始学习任务：${result.task.title}`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    learningTaskStartingId.value = ''
  }
}

async function deferLearningTask(task) {
  if (!task || learningTaskDeferringId.value) return
  learningTaskDeferringId.value = task.id
  try {
    await api.deferLearningTask(task.id, 1)
    await loadLearningTasks()
    await loadLearningNotifications()
    noticeMessage.value = '已延期 1 天；到期后会重新出现在学习任务中。'
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    learningTaskDeferringId.value = ''
  }
}

function selectChatLearnerProfile() {
  const profile = learnerProfiles.value.find((item) => item.id === chatEducation.learnerProfileId)
  if (profile) applyLearnerProfileToChat(profile)
}

async function loadContextConfiguration() {
  try {
    contextConfiguration.value = await api.contextConfiguration()
  } catch (error) {
    // 治理页已经有独立错误提示；配置弹窗保存成功时不因状态刷新失败而误报保存失败。
    if (!contextConfiguration.value) errorMessage.value = errorText(error)
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
      errorCode: 'CLIENT_ERROR',
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

function matchingEmbeddingProviderPreset(baseUrl, modelName) {
  return embeddingProviderPresets.find((preset) => preset.baseUrl === baseUrl && preset.modelName === modelName)?.id || 'custom'
}

function applyEmbeddingProviderPreset() {
  const preset = embeddingProviderPresets.find((item) => item.id === embeddingProviderPreset.value)
  if (!preset || preset.id === 'custom') return
  embeddingConfigForm.baseUrl = preset.baseUrl
  embeddingConfigForm.modelName = preset.modelName
  embeddingConfigForm.dimension = preset.dimension
  embeddingConfigForm.clearApiKey = false
  embeddingConfigError.value = ''
}

function useCustomEmbeddingProvider() {
  embeddingProviderPreset.value = 'custom'
}

async function loadEmbeddingConfig() {
  embeddingConfigLoading.value = true
  embeddingConfigError.value = ''
  try {
    const value = await api.getEmbeddingConfig()
    embeddingConfig.value = value
    Object.assign(embeddingConfigForm, {
      enabled: Boolean(value?.enabled),
      baseUrl: value?.baseUrl || '',
      modelName: value?.modelName || '',
      modelVersion: value?.modelVersion || 'v1',
      dimension: value?.dimension || 1536,
      apiKey: '',
      clearApiKey: false,
    })
    embeddingProviderPreset.value = matchingEmbeddingProviderPreset(value?.baseUrl, value?.modelName)
  } catch (error) {
    embeddingConfigError.value = error.code === 'PERMISSION_DENIED'
      ? '当前身份没有 context.configure 权限，无法修改向量连接。'
      : errorText(error)
  } finally {
    embeddingConfigLoading.value = false
  }
}

async function saveEmbeddingConfig() {
  if (embeddingConfigSaving.value) return
  clearMessages()
  embeddingConfigSaving.value = true
  embeddingConfigError.value = ''
  try {
    const value = await api.updateEmbeddingConfig({
      enabled: Boolean(embeddingConfigForm.enabled),
      baseUrl: embeddingConfigForm.baseUrl.trim(),
      modelName: embeddingConfigForm.modelName.trim(),
      modelVersion: embeddingConfigForm.modelVersion.trim(),
      dimension: Number(embeddingConfigForm.dimension),
      apiKey: embeddingConfigForm.apiKey,
      clearApiKey: Boolean(embeddingConfigForm.clearApiKey),
    })
    embeddingConfig.value = value
    embeddingConfigForm.apiKey = ''
    embeddingConfigForm.clearApiKey = false
    await loadContextConfiguration()
    noticeMessage.value = '向量连接设置已保存；旧向量已标记为待重建，请在治理面板执行“重建索引”。'
    showEmbeddingSettings.value = false
  } catch (error) {
    embeddingConfigError.value = errorText(error)
  } finally {
    embeddingConfigSaving.value = false
  }
}

async function testEmbeddingConfig() {
  if (embeddingConfigTesting.value || embeddingConfigSaving.value || !embeddingConfigForm.enabled) return
  embeddingConfigError.value = ''
  embeddingConfigTestResult.value = null
  embeddingConfigTesting.value = true
  try {
    embeddingConfigTestResult.value = await api.testEmbeddingConfig({
      enabled: true,
      baseUrl: embeddingConfigForm.baseUrl.trim(),
      modelName: embeddingConfigForm.modelName.trim(),
      modelVersion: embeddingConfigForm.modelVersion.trim(),
      dimension: Number(embeddingConfigForm.dimension),
      apiKey: embeddingConfigForm.apiKey,
      clearApiKey: Boolean(embeddingConfigForm.clearApiKey),
    })
  } catch (error) {
    embeddingConfigTestResult.value = {
      success: false,
      status: 'FAILED',
      message: errorText(error),
      modelName: embeddingConfigForm.modelName.trim(),
      dimension: 0,
      latencyMs: 0,
      errorCode: 'CLIENT_ERROR',
    }
  } finally {
    embeddingConfigTesting.value = false
  }
}

async function resetEmbeddingConfig() {
  if (embeddingConfigSaving.value) return
  if (typeof window !== 'undefined'
    && !window.confirm('恢复环境默认 Embedding 配置吗？当前组织保存的地址和密钥会被删除。')) return
  clearMessages()
  embeddingConfigSaving.value = true
  embeddingConfigError.value = ''
  try {
    const value = await api.resetEmbeddingConfig()
    embeddingConfig.value = value
    Object.assign(embeddingConfigForm, {
      enabled: Boolean(value?.enabled),
      baseUrl: value?.baseUrl || '',
      modelName: value?.modelName || '',
      modelVersion: value?.modelVersion || 'v1',
      dimension: value?.dimension || 1536,
      apiKey: '',
      clearApiKey: false,
    })
    embeddingProviderPreset.value = matchingEmbeddingProviderPreset(value?.baseUrl, value?.modelName)
    await loadContextConfiguration()
    noticeMessage.value = '已恢复环境默认 Embedding 配置。'
    showEmbeddingSettings.value = false
  } catch (error) {
    embeddingConfigError.value = errorText(error)
  } finally {
    embeddingConfigSaving.value = false
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
    noticeMessage.value = '组织资源策略已保存，新的 Run 会立即使用最新限制'
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
    noticeMessage.value = '组织策略已恢复为平台默认值'
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
  const values = Array.isArray(apiKeyForm.permissions)
    ? apiKeyForm.permissions
    : String(apiKeyForm.permissions || '').split(',')
  return [...new Set(values.map((item) => String(item).trim()).filter(Boolean))]
}

const apiKeyPermissionCount = computed(() => parseApiKeyPermissions().length)
const allApiKeyPermissionsSelected = computed(() => {
  const selected = parseApiKeyPermissions()
  return apiKeyPermissionOptions.every(({ value }) => selected.includes(value))
})

function toggleAllApiKeyPermissions() {
  apiKeyForm.permissions = allApiKeyPermissionsSelected.value
    ? []
    : apiKeyPermissionOptions.map(({ value }) => value)
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

const DOCUMENT_UPLOAD_MAX_BYTES = 25 * 1024 * 1024

function openDocumentUploadPicker() {
  if (loading.value || documentUploading.value) return
  documentUploadInput.value?.click()
}

function documentFileTitle(fileName) {
  const name = String(fileName || '').split(/[\\/]/).pop() || ''
  const dot = name.lastIndexOf('.')
  return dot > 0 ? name.slice(0, dot) : name
}

function setDocumentUploadFile(file) {
  documentUploadError.value = ''
  documentUploadFile.value = null
  if (!file || file.size <= 0) {
    documentUploadError.value = '请选择一个非空的 PDF 或 DOCX 文件。'
    return
  }
  const extension = String(file.name || '').split('.').pop()?.toLowerCase()
  if (!['pdf', 'docx'].includes(extension)) {
    documentUploadError.value = '知识库导入目前只支持 PDF 和 DOCX 文件。'
    return
  }
  if (file.size > DOCUMENT_UPLOAD_MAX_BYTES) {
    documentUploadError.value = `文件不能超过 ${formatFileSize(DOCUMENT_UPLOAD_MAX_BYTES)}。`
    return
  }
  documentUploadFile.value = file
  // 文件名是默认标题时自动换成来源文件名，用户改过标题则保留用户输入。
  if (!documentForm.title.trim() || documentForm.title === '订单处理规则') {
    documentForm.title = documentFileTitle(file.name)
  }
}

function handleDocumentUploadInput(event) {
  setDocumentUploadFile(event.target?.files?.[0])
  event.target.value = ''
}

function handleDocumentUploadDrop(event) {
  documentUploadDragging.value = false
  if (loading.value || documentUploading.value) return
  const files = Array.from(event.dataTransfer?.files || [])
  if (files.length > 1) noticeMessage.value = '一次只导入一个知识文档，已使用第一个文件。'
  setDocumentUploadFile(files[0])
}

function clearDocumentUploadFile() {
  documentUploadFile.value = null
  documentUploadError.value = ''
  if (documentUploadInput.value) documentUploadInput.value.value = ''
}

async function createDocument() {
  clearMessages()
  if (!documentUploadFile.value) {
    documentUploadError.value = '请先选择一个 PDF 或 DOCX 文件。'
    return
  }
  loading.value = true
  try {
    documentUploading.value = true
    const document = await api.uploadDocument({
      file: documentUploadFile.value,
      title: documentForm.title,
      sensitivity: documentForm.sensitivity,
      allowedUsers: documentForm.allowedUsers,
    })
    documents.value = [document, ...documents.value.filter((item) => item.id !== document.id)]
    clearDocumentUploadFile()
    noticeMessage.value = '文件已解析并建立知识索引；后续模型步骤会按组织和用户权限检索'
    await loadDashboard()
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    documentUploading.value = false
    loading.value = false
  }
}

async function deleteDocument(document) {
  if (!document?.id || documentDeletingId.value) return
  if (typeof window !== 'undefined'
    && !window.confirm(`确认删除知识文档“${document.title}”吗？`)) return
  clearMessages()
  documentDeletingId.value = document.id
  try {
    await api.deleteDocument(document.id)
    documents.value = documents.value.filter((item) => item.id !== document.id)
    noticeMessage.value = `知识文档“${document.title}”已删除`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    documentDeletingId.value = ''
  }
}

async function saveLearnerProfile() {
  if (!learnerProfileForm.subject.trim() || !learnerProfileForm.gradeLevel.trim()
    || !learnerProfileForm.curriculumVersion.trim() || educationLoading.value) return
  clearMessages()
  educationLoading.value = true
  try {
    const profile = await api.saveLearnerProfile({
      subject: learnerProfileForm.subject.trim(),
      gradeLevel: learnerProfileForm.gradeLevel.trim(),
      curriculumVersion: learnerProfileForm.curriculumVersion.trim(),
      learningGoal: learnerProfileForm.learningGoal.trim(),
      language: learnerProfileForm.language.trim() || 'zh-CN',
    })
    learnerProfiles.value = [profile, ...learnerProfiles.value.filter((item) => item.id !== profile.id)]
    activeLearnerProfile.value = profile
    applyLearnerProfileToEducationRun(profile)
    noticeMessage.value = '学习者画像已保存；教育 Agent 会按该画像选择课程内容和教学策略。'
    educationError.value = ''
  } catch (error) {
    educationError.value = errorText(error)
  } finally {
    educationLoading.value = false
  }
}

async function saveEducationSource() {
  if (!educationSourceForm.documentId || educationLoading.value) return
  clearMessages()
  educationLoading.value = true
  try {
    const source = await api.saveEducationSource({
      ...educationSourceForm,
      difficultyLevel: Number(educationSourceForm.difficultyLevel) || 3,
    })
    educationSources.value = [source, ...educationSources.value.filter((item) => item.documentId !== source.documentId)]
    noticeMessage.value = '课程元数据已保存；教育 Agent 检索时会执行课程约束过滤。'
    educationError.value = ''
  } catch (error) {
    educationError.value = errorText(error)
  } finally {
    educationLoading.value = false
  }
}

function selectEducationDocument(document) {
  if (!document) return
  educationSourceForm.documentId = document.id
}

async function createMemory() {
  const content = memoryForm.content.trim()
  if (!content || loading.value) return
  clearMessages()
  loading.value = true
  try {
    const memory = await api.createMemory({
      memoryType: memoryForm.memoryType.trim(),
      content,
      sourceRunId: null,
      expiresAt: memoryForm.expiresAt ? new Date(memoryForm.expiresAt).toISOString() : null,
    })
    memories.value = [memory, ...memories.value]
    memoryForm.content = ''
    memoryForm.expiresAt = ''
    noticeMessage.value = '长期记忆已保存，后续模型步骤会按当前用户权限检索'
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

async function deleteMemory(memory) {
  if (!memory?.id || memoryDeletingId.value) return
  if (typeof window !== 'undefined'
    && !window.confirm(`确认删除这条“${memory.memoryType}”长期记忆吗？`)) return
  clearMessages()
  memoryDeletingId.value = memory.id
  try {
    await api.deleteMemory(memory.id)
    memories.value = memories.value.filter((item) => item.id !== memory.id)
    noticeMessage.value = '长期记忆已删除'
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    memoryDeletingId.value = ''
  }
}

async function previewContext() {
  const query = contextPreviewQuery.value.trim()
  if (!query || contextPreviewLoading.value) return
  contextPreviewLoading.value = true
  contextPreviewError.value = ''
  try {
    contextPreviewResult.value = await api.previewContext(query, Number(contextPreviewMaxChars.value) || 4000)
  } catch (error) {
    contextPreviewError.value = errorText(error)
  } finally {
    contextPreviewLoading.value = false
  }
}

async function rebuildContextIndex() {
  if (contextReindexLoading.value) return
  contextReindexLoading.value = true
  contextReindexError.value = ''
  try {
    const result = await api.reindexContext({
      scope: contextReindexForm.scope,
      parentLimit: Number(contextReindexForm.parentLimit) || 100,
      chunkLimit: Number(contextReindexForm.chunkLimit) || 1000,
      rechunk: Boolean(contextReindexForm.rechunk),
    })
    contextReindexResult.value = result
    noticeMessage.value = contextReindexNotice(result)
  } catch (error) {
    contextReindexError.value = errorText(error)
  } finally {
    contextReindexLoading.value = false
  }
}

function contextReindexNotice(result) {
  const indexed = Number(result?.chunksIndexed) || 0
  const failed = Number(result?.chunksFailed) || 0
  const pending = Number(result?.pendingChunks) || 0
  if (failed > 0) {
    return `索引任务部分完成，${failed} 个向量处理失败，仍有 ${pending} 个待处理`
  }
  if (pending > 0 && !result?.embeddingReady) {
    return `索引任务完成，但向量服务或 pgvector 未就绪，仍有 ${pending} 个待处理`
  }
  if (indexed > 0) return `索引任务完成，已写入 ${indexed} 个向量`
  return '索引已是最新，当前没有待处理向量'
}

function contextReindexStatusClass(result) {
  if (!result) return 'is-unknown'
  if (Number(result.chunksFailed) > 0) return 'is-negative'
  if (Number(result.pendingChunks) > 0) return 'is-warning'
  return result.embeddingReady ? 'is-ready' : 'is-warning'
}

function contextReindexStatusLabel(result) {
  if (!result) return '未检查'
  if (Number(result.chunksFailed) > 0) return 'ERROR'
  if (Number(result.pendingChunks) > 0) return 'PENDING'
  return result.embeddingReady ? 'READY' : 'CHECK'
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
    const requestedPermissions = new Set(permissionList(form.permissions))
    const education = form.education.enabled ? {
      ...form.education,
      minDifficulty: form.education.minDifficulty == null ? null : Number(form.education.minDifficulty),
      maxDifficulty: form.education.maxDifficulty == null ? null : Number(form.education.maxDifficulty),
    } : null
    if (education) {
      requestedPermissions.add('education.read')
      requestedPermissions.add('education.write')
    }
    const created = await api.createRun({
      ...form,
      budget: Number(form.budget),
      permissions: [...requestedPermissions].join(','),
      education,
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
  window.addEventListener('popstate', syncActiveConsoleSectionFromHash)
  if (desktopWorkspaceAvailable.value) {
    api.configureDesktopWorkspaceDrop()
    api.onDesktopWorkspaceDropped((result) => {
      void handleDesktopWorkspaceDropped(result)
    })
  }
  await Promise.all([loadDashboard(), loadHealth(), loadModelConfig(), loadEmbeddingConfig(), loadWorkspace(), loadTenantPolicy(), loadApiKeys(), loadLocalWorkspaces()])
  await loadConversations()
  runPollTimer = window.setInterval(pollSelectedRun, 1500)
  conversationPollTimer = window.setInterval(pollConversation, 1200)
  healthPollTimer = window.setInterval(loadHealth, 10000)
  learningNotificationPollTimer = window.setInterval(() => {
    if (networkOnline.value) {
      void loadLearningNotifications()
      void loadLearningAssignmentNotifications()
    }
  }, 15000)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleWorkspaceBeforeUnload)
  window.removeEventListener('keydown', handleChatGlobalKeydown)
  window.removeEventListener('offline', handleNetworkOffline)
  window.removeEventListener('online', handleNetworkOnline)
  window.removeEventListener('hashchange', syncActiveConsoleSectionFromHash)
  window.removeEventListener('popstate', syncActiveConsoleSectionFromHash)
  stopRunEventStream()
  api.clearDesktopWorkspaceDropListener()
  window.clearInterval(runPollTimer)
  window.clearInterval(conversationPollTimer)
  window.clearInterval(healthPollTimer)
  window.clearInterval(learningNotificationPollTimer)
  window.clearTimeout(chatHighlightTimer)
  if (noticeDismissTimer) window.clearTimeout(noticeDismissTimer)
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
          <button class="command-palette-trigger" type="button" title="打开命令面板（⌘/Ctrl + K）" @click="openCommandPalette"><Command :size="14" /><span>⌘K</span><em>命令</em></button>
          <button class="theme-toggle" type="button" :aria-label="theme === 'dark' ? '切换到白天模式' : '切换到黑夜模式'" @click="toggleTheme">
            <Sun v-if="theme === 'dark'" :size="15" aria-hidden="true" /><Moon v-else :size="15" aria-hidden="true" />{{ theme === 'dark' ? '白天' : '黑夜' }}
          </button>
          <button class="secondary-button chat-console-button top-config-button" type="button" title="配置大语言模型" @click="showModelSettings = true"><Settings2 :size="15" />大语言模型</button>
          <button class="secondary-button chat-console-button top-config-button" type="button" title="配置向量模型" @click="showEmbeddingSettings = true"><Settings2 :size="15" />向量模型</button>
          <button class="secondary-button chat-console-button" type="button" title="打开运行控制台" @click="chatMode = false"><PanelRight :size="15" />运行控制台</button>
        </div>
      </header>

      <div v-if="errorMessage" :key="`error-${errorMessage}`" class="message error-message chat-message-banner">{{ errorMessage }}</div>
      <div v-if="noticeMessage" :key="`notice-${noticeMessage}`" class="message notice-message chat-message-banner">{{ noticeMessage }}</div>

      <div class="chat-layout">
        <aside class="conversation-sidebar">
          <nav class="chat-primary-nav" aria-label="工作台导航">
            <button class="chat-primary-nav-item chat-primary-nav-item-primary" type="button" :disabled="chatLoading || chatSending || chatUploading" @click="createChatConversation">
              <MessageSquarePlus :size="15" /><span>新对话</span><kbd>⌘N</kbd>
            </button>
            <button class="chat-primary-nav-item" type="button" @click="chatMode = false; navigateConsoleSection('runtime')">
              <CircleDot :size="15" /><span>运行中心</span>
            </button>
            <button class="chat-primary-nav-item" type="button" @click="chatMode = false; navigateConsoleSection('audit')">
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

          <div class="chat-messages" aria-live="polite" @scroll="updateChatFollowOutput">
            <div v-if="chatLoading && !chatMessages.length" class="chat-empty-state">正在加载会话…</div>
            <div v-else-if="!chatMessages.length" class="chat-empty-state">
              <div class="chat-empty-mark" aria-hidden="true"><Sparkles :size="23" /></div>
              <strong>从一个问题开始</strong>
              <span>{{ workspaceConnected ? 'Agent 已连接当前项目，会先理解结构，再按需读取、修改和验证代码。' : '选择本地项目或附加文件后，Agent 会先理解上下文，再按需运行工具。' }}</span>
              <div class="chat-quick-start" aria-label="快速开始">
                <button
                  v-for="item in quickStartPrompts"
                  :key="item.id"
                  class="chat-quick-start-card"
                  type="button"
                  :disabled="!activeConversationId || chatSending || chatUploading"
                  @click="useQuickStartPrompt(item.prompt)"
                >
                  <span class="chat-quick-start-card-icon" aria-hidden="true"><Sparkles :size="14" /></span>
                  <span class="chat-quick-start-card-copy"><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
                  <ArrowUp :size="14" aria-hidden="true" />
                </button>
              </div>
              <button v-if="desktopWorkspaceAvailable && !workspaceConnected" class="chat-empty-workspace-action" type="button" :disabled="desktopWorkspacePicking || chatSending || chatUploading" @click="chooseDesktopWorkspace">
                <FolderGit2 :size="14" />{{ desktopWorkspacePicking ? '选择中…' : '选择本地项目' }}
              </button>
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
                    <div v-if="message.content" class="chat-markdown" v-html="renderMarkdown(chatMessagePresentations.get(message.id)?.content || message.content)" @click="handleChatMarkdownClick"></div>
                    <section v-if="message.role === 'ASSISTANT' && chatMessagePresentations.get(message.id)?.sources?.length" class="chat-source-section" aria-label="参考来源">
                      <div class="chat-source-heading"><span>参考来源</span><small>{{ chatMessagePresentations.get(message.id).sources.length }} 个</small></div>
                      <div class="chat-source-list">
                        <article v-for="source in chatMessagePresentations.get(message.id).sources" :key="`${message.id}-${source.key}`" class="chat-source-card">
                          <div class="chat-source-card-heading">
                            <span class="chat-source-kind">{{ source.kindLabel }}</span>
                            <strong>{{ source.title }}</strong>
                          </div>
                          <small v-if="source.updatedAt">更新于 {{ formatDate(source.updatedAt) }}</small>
                        </article>
                      </div>
                    </section>
                    <p v-if="!message.content">{{ messageStatusLabel(message.status) }}</p>
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
                  <template v-if="message.content && message.runId && message.status === 'COMPLETED'">
                    <button type="button" :disabled="feedbackSavingRunId === message.runId" @click="recordRunFeedback(message, 'POSITIVE')">{{ chatFeedbackByRun[message.runId]?.rating === 'POSITIVE' ? '已标记有帮助' : '有帮助' }}</button>
                    <button type="button" :disabled="feedbackSavingRunId === message.runId" @click="recordRunFeedback(message, 'NEGATIVE')">{{ chatFeedbackByRun[message.runId]?.rating === 'NEGATIVE' ? '已标记需调整' : '需要调整' }}</button>
                  </template>
                </div>
                <div v-if="message.runId && message.role === 'ASSISTANT'" class="message-run-reference">
                  <span class="message-run-status" :class="messageStatusClass(message.status)"><i></i>{{ messageStatusLabel(message.status) }}</span>
                </div>
              </div>
            </article>
            <button
              v-if="!chatFollowOutput && pendingChatMessage"
              class="chat-jump-latest"
              type="button"
              aria-label="回到最新消息"
              @click="scrollChatToBottom(true)"
            >
              <ArrowDown :size="13" />回到最新
            </button>
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
              <div class="chat-education-settings">
                <label class="chat-education-toggle">
                  <input v-model="chatEducation.enabled" type="checkbox" :disabled="chatSending || chatUploading" />
                  <span><strong>教育知识库 Agent</strong><small>按课程版本、前置知识和学习者掌握度组织本轮回答</small></span>
                </label>
                <div v-if="chatEducation.enabled" class="chat-education-grid">
                  <label><span>学习者画像</span><select v-model="chatEducation.learnerProfileId" :disabled="chatSending || chatUploading" @change="selectChatLearnerProfile"><option value="">请选择画像</option><option v-for="profile in learnerProfiles" :key="profile.id" :value="profile.id">{{ profile.subject }} · {{ profile.gradeLevel }}</option></select></label>
                  <label><span>学习目标</span><select v-model="chatEducation.learningGoalId" :disabled="chatSending || chatUploading" @change="selectLearningGoal(learningGoals.find((goal) => goal.id === chatEducation.learningGoalId), false)"><option value="">不绑定目标</option><option v-for="goal in learningGoals.filter((item) => item.status === 'ACTIVE')" :key="goal.id" :value="goal.id">{{ goal.title }} · {{ goal.conceptKey }}</option></select></label>
                  <label><span>教学策略</span><select v-model="chatEducation.pedagogicalMode" :disabled="chatSending || chatUploading"><option value="AUTO">自动选择</option><option value="EXPLAIN">概念讲解</option><option value="SOCRATIC">启发式引导</option><option value="PRACTICE">练习优先</option><option value="DIAGNOSE">错误诊断</option></select></label>
                  <label><span>目标知识点</span><input v-model="chatEducation.conceptKey" maxlength="255" placeholder="例如：函数定义域" :disabled="chatSending || chatUploading" /></label>
                  <label><span>难度范围</span><div class="chat-education-difficulty"><input v-model.number="chatEducation.minDifficulty" type="number" min="1" max="5" placeholder="1" :disabled="chatSending || chatUploading" /><span>—</span><input v-model.number="chatEducation.maxDifficulty" type="number" min="1" max="5" placeholder="5" :disabled="chatSending || chatUploading" /></div></label>
                  <small class="chat-education-context">{{ chatEducation.subject || '未选择学科' }} · {{ chatEducation.gradeLevel || '未选择年级' }} · {{ chatEducation.curriculumVersion || '未选择课程版本' }}</small>
                </div>
              </div>
            </div>
            <textarea
              ref="chatInputRef"
              v-model="chatInput"
              rows="3"
              :disabled="chatSending || chatUploading || !activeConversationId"
              placeholder="描述你的业务目标；可提问、分析项目或发起受控流程…"
              aria-label="输入消息"
              @input="handleChatInput"
              @keydown="handleChatKeydown"
            ></textarea>
            <div class="chat-composer-footer">
              <span class="chat-composer-hint">
                <span class="chat-composer-hint-primary"><kbd>Enter</kbd> 发送 · <kbd>Shift</kbd> + <kbd>Enter</kbd> 换行<span v-if="canCancelChat"> · <kbd>Esc</kbd> 停止</span></span>
                <span class="chat-composer-hint-context">{{ desktopWorkspaceDropping ? '正在授权拖入的本地项目…' : workspaceConnected ? 'Agent 可直接操作本会话绑定的本地项目' : '文件夹导入后保留层级' }}</span>
              </span>
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
            <section v-if="manualAssessmentAvailable" class="manual-assessment-panel" aria-label="人工或学习者测评复核">
              <div class="chat-change-review-heading"><div><span>ASSESSMENT EVIDENCE</span><strong>人工 / 学习者复核</strong></div><em>必须提供依据</em></div>
              <p class="manual-assessment-help">本次复核会绑定当前教育 Run、目标知识点和具体步骤，并与 Agent 自动观察区分保存。</p>
              <form class="manual-assessment-form" @submit.prevent="submitManualAssessment">
                <label><span>复核步骤</span><select v-model="manualAssessmentForm.stepId" required><option v-for="step in manualAssessmentSteps" :key="step.id" :value="step.id">#{{ step.sequence }} · {{ step.name }}</option></select></label>
                <label><span>结果</span><select v-model="manualAssessmentForm.correct" required><option value="">请选择</option><option value="true">正确</option><option value="false">错误</option></select></label>
                <label><span>观察掌握度（可选）</span><input v-model="manualAssessmentForm.observedMastery" type="number" min="0" max="1" step="0.05" placeholder="按结果自动估计" /></label>
                <label class="manual-assessment-wide"><span>作答 / 评分依据</span><textarea v-model="manualAssessmentForm.evidenceText" rows="3" maxlength="4000" placeholder="填写学生作答、推理过程或教师评分依据" required></textarea></label>
                <label class="manual-assessment-wide"><span>复核反馈（可选）</span><input v-model="manualAssessmentForm.feedback" maxlength="1000" placeholder="例如：定义域判定正确，但理由不完整" /></label>
                <p v-if="manualAssessmentError" class="policy-error manual-assessment-error">{{ manualAssessmentError }}</p>
                <button class="secondary-button" type="submit" :disabled="manualAssessmentSaving">{{ manualAssessmentSaving ? '记录中…' : '记录复核结果' }}</button>
              </form>
            </section>
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
  <div class="console-app">
    <header class="console-topbar">
      <div class="brand console-topbar-brand">
        <div class="brand-mark" aria-hidden="true"><Sparkles :size="17" :stroke-width="1.8" /></div>
        <div>
          <strong>Ming Harness</strong>
          <span>Agent Operations</span>
        </div>
      </div>
      <div class="console-topbar-content">
        <div class="topbar-actions">
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
          <button class="secondary-button top-config-button" type="button" title="配置大语言模型" @click="showModelSettings = true"><Settings2 :size="15" />大语言模型</button>
          <button class="secondary-button top-config-button" type="button" title="配置向量模型" @click="showEmbeddingSettings = true"><Settings2 :size="15" />向量模型</button>
          <button class="secondary-button" type="button" title="打开聊天工作台" @click="chatMode = true"><MessageSquarePlus :size="15" />聊天工作台</button>
        </div>
      </div>
    </header>

    <div class="console-layout">
      <aside class="sidebar">
      <nav class="side-nav" aria-label="主导航">
        <a class="nav-item" :class="{ active: activeConsoleSection === 'runtime' }" href="#runtime" :aria-current="activeConsoleSection === 'runtime' ? 'page' : undefined" @click.prevent="navigateConsoleSection('runtime')"><span class="nav-icon"><CircleDot :size="16" /></span>运行中心</a>
        <a class="nav-item" :class="{ active: activeConsoleSection === 'education' }" href="#education" :aria-current="activeConsoleSection === 'education' ? 'page' : undefined" @click.prevent="navigateConsoleSection('education')"><span class="nav-icon"><Sparkles :size="16" /></span>教育工作台</a>
        <a class="nav-item" :class="{ active: activeConsoleSection === 'audit' }" href="#audit" :aria-current="activeConsoleSection === 'audit' ? 'page' : undefined" @click.prevent="navigateConsoleSection('audit')"><span class="nav-icon"><Check :size="16" /></span>审计追踪</a>
      </nav>

      <div class="sidebar-foot">
        <div class="system-state"><span class="pulse" :class="{ offline: !infraOnline }"></span><span>{{ infraLabel }}</span></div>
        <small>Runtime v0.1 · Java 17</small>
      </div>
    </aside>

    <main class="main-content" id="runtime">
      <div v-if="errorMessage" :key="`error-${errorMessage}`" class="message error-message console-message-banner">{{ errorMessage }}</div>
      <div v-if="noticeMessage" :key="`notice-${noticeMessage}`" class="message notice-message console-message-banner">{{ noticeMessage }}</div>

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
        <div class="stat-card stat-running">
          <div class="stat-top"><span>活动 Run</span><span class="stat-icon"><Activity :size="16" /></span></div>
          <strong>{{ stats.running + stats.queued }}</strong>
          <small>{{ stats.running }} 执行中 · {{ stats.queued }} 排队</small>
        </div>
        <div class="stat-card stat-total">
          <div class="stat-top"><span>待审批</span><span class="stat-icon"><ShieldCheck :size="16" /></span></div>
          <strong>{{ stats.waitingApproval }}</strong>
          <small>需要人工确认的高风险操作</small>
        </div>
        <div class="stat-card stat-failed">
          <div class="stat-top"><span>失败 Run</span><span class="stat-icon"><CircleAlert :size="16" /></span></div>
          <strong>{{ stats.failed }}</strong>
          <small>成功率 {{ stats.total ? Math.round((stats.succeeded / stats.total) * 100) : 0 }}%</small>
        </div>
      </section>

      <section class="create-panel" :class="{ 'create-panel-collapsed': !showCreateForm }">
        <div class="section-heading">
          <div>
            <p class="eyebrow">CREATE EXECUTION</p>
            <h2>创建一次可追溯执行</h2>
          </div>
          <button class="secondary-button" type="button" :aria-expanded="showCreateForm" @click="showCreateForm = !showCreateForm">
            {{ showCreateForm ? '收起创建面板' : '展开创建面板' }}
          </button>
        </div>
        <form v-if="showCreateForm" class="run-form" @submit.prevent="createAndStartRun">
          <label class="field field-wide">
            <span>任务名称</span>
            <input v-model="form.title" required maxlength="120" placeholder="例如：分析一条退款申请" />
          </label>
          <label class="field">
            <span>组织 ID</span>
            <input v-model="form.tenantId" required maxlength="64" />
          </label>
          <label class="field">
            <span>执行用户</span>
            <input v-model="form.userId" required maxlength="64" />
          </label>
          <label class="field field-wide run-input-field">
            <span>任务输入</span>
            <textarea v-model="form.input" required maxlength="4000" rows="3" placeholder="输入用户任务或上下文"></textarea>
          </label>
          <label class="field run-input-field">
            <span>工具</span>
            <select v-model="form.toolName" :disabled="form.agentMode">
              <option v-for="tool in tools" :key="tool.name" :value="tool.name">{{ tool.name }}</option>
            </select>
          </label>
          <label class="field">
            <span>Prompt 版本</span>
            <input v-model="form.promptVersion" required />
          </label>
          <label class="field run-secondary-field">
            <span>策略版本</span>
            <input v-model="form.policyVersion" required />
          </label>
          <label class="field run-secondary-field">
            <span>幂等键（可选）</span>
            <input v-model="form.idempotencyKey" maxlength="128" placeholder="例如：order-123" />
          </label>
          <div class="field run-secondary-field permission-field">
            <span>权限快照（可选）</span>
            <div class="permission-picker" role="group" aria-label="选择本次 Run 的工具权限">
              <label v-for="permission in permissionOptions" :key="permission.value" class="permission-option" :title="permission.description">
                <input v-model="selectedPermissions" type="checkbox" :value="permission.value" />
                <span class="permission-option-copy">
                  <strong>{{ permission.label }}</strong>
                  <code>{{ permission.value }}</code>
                </span>
              </label>
              <small v-if="!permissionOptions.length" class="permission-empty">当前注册工具没有额外权限要求</small>
            </div>
            <small class="form-hint">{{ selectedPermissionSummary }}；选项来自已注册工具的权限声明。</small>
          </div>
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
          <div class="field field-wide agent-mode-field education-run-field">
            <span>教育知识库 Agent</span>
            <div class="agent-mode-controls">
              <label class="check-field">
                <input v-model="form.education.enabled" type="checkbox" @change="form.education.enabled && (form.agentMode = true)" />
                <span>启用课程约束与学习者状态感知</span>
              </label>
              <label v-if="form.education.enabled" class="turns-field">
                <span>教学策略</span>
                <select v-model="form.education.pedagogicalMode">
                  <option value="AUTO">自动选择</option>
                  <option value="EXPLAIN">概念讲解</option>
                  <option value="SOCRATIC">启发式引导</option>
                  <option value="PRACTICE">练习优先</option>
                  <option value="DIAGNOSE">错误诊断</option>
                </select>
              </label>
            </div>
            <div v-if="form.education.enabled" class="education-run-grid">
              <label class="field"><span>学习者画像</span><select v-model="form.education.learnerProfileId"><option value="">请选择画像</option><option v-for="profile in learnerProfiles" :key="profile.id" :value="profile.id">{{ profile.subject }} · {{ profile.gradeLevel }}</option></select></label>
              <label class="field"><span>学习目标</span><select v-model="form.education.learningGoalId" @change="selectLearningGoal(learningGoals.find((goal) => goal.id === form.education.learningGoalId), false)"><option value="">不绑定目标</option><option v-for="goal in learningGoals.filter((item) => item.status === 'ACTIVE')" :key="goal.id" :value="goal.id">{{ goal.title }} · {{ goal.conceptKey }}</option></select></label>
              <label class="field"><span>学科</span><input v-model="form.education.subject" required /></label>
              <label class="field"><span>年级</span><input v-model="form.education.gradeLevel" required /></label>
              <label class="field"><span>课程版本</span><input v-model="form.education.curriculumVersion" required /></label>
              <label class="field"><span>目标知识点（可选）</span><input v-model="form.education.conceptKey" placeholder="例如：函数定义域" /></label>
              <label class="field"><span>难度范围（可选）</span><div class="education-difficulty-range"><input v-model.number="form.education.minDifficulty" type="number" min="1" max="5" placeholder="1" /><span>—</span><input v-model.number="form.education.maxDifficulty" type="number" min="1" max="5" placeholder="5" /></div></label>
            </div>
            <small class="form-hint">教育模式必须绑定学习者画像；绑定学习目标后，测评会累计目标进度，并给出下一步学习动作。</small>
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
              <div><span>组织 / 用户</span><strong>{{ selectedRun.run.tenantId }} / {{ selectedRun.run.userId }}</strong></div>
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
                    <details v-if="step.contextEvidence?.length" class="run-evidence-details" open>
                      <summary>上下文证据 · {{ step.contextEvidence.length }} 个已授权来源</summary>
                      <article v-for="evidence in step.contextEvidence" :key="`${step.id}-${evidence.citation}`" class="run-evidence-row">
                        <div><strong>{{ evidence.title || '未命名来源' }}</strong><code>{{ evidence.citation }}</code></div>
                        <p>{{ evidence.excerpt }}</p>
                      </article>
                    </details>
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
          <div><p class="eyebrow">ADVANCED GOVERNANCE</p><h2>高级治理设置</h2><p class="panel-heading-help">知识源、索引、组织策略和凭证设置只在这里维护。</p></div>
          <button class="secondary-button" type="button" @click="showGovernance = !showGovernance">{{ showGovernance ? '收起高级设置' : '展开高级设置' }}</button>
        </div>
        <div v-if="showGovernance" class="governance-grid">
          <section class="governance-card context-workbench-card">
            <div class="context-workbench-heading">
              <div>
                <p class="eyebrow">VECTOR SEARCH</p>
                <h3>检索工作台</h3>
              </div>
              <span class="context-mode-chip">语义 + 关键词</span>
            </div>
            <p class="context-workbench-help">用和 Run 相同的查询链路预览授权上下文，检查命中来源、父窗口和 citation。</p>
            <form class="context-preview-form" @submit.prevent="previewContext">
              <label class="field context-query-field">
                <span>查询内容</span>
                <textarea v-model="contextPreviewQuery" rows="3" required placeholder="请输入要检索的问题"></textarea>
              </label>
              <div class="context-preview-controls">
                <label class="field">
                  <span>上下文上限</span>
                  <select v-model.number="contextPreviewMaxChars">
                    <option :value="2000">2,000 字符</option>
                    <option :value="4000">4,000 字符</option>
                    <option :value="8000">8,000 字符</option>
                    <option :value="12000">12,000 字符</option>
                  </select>
                </label>
                <button class="primary-button context-preview-button" type="submit" :disabled="contextPreviewLoading || !contextPreviewQuery.trim()">
                  {{ contextPreviewLoading ? '检索中…' : '运行检索' }}
                </button>
              </div>
            </form>
            <p v-if="contextPreviewError" class="policy-error">{{ contextPreviewError }}</p>
            <div v-if="contextPreviewResult" class="context-preview-result">
              <div class="subsection-title">
                <div><h3>召回结果</h3><span>{{ contextPreviewResult.evidences?.length || 0 }} 个授权来源</span></div>
                <span class="context-result-state">已完成</span>
              </div>
              <pre v-if="contextPreviewResult.text" class="context-preview-text">{{ contextPreviewResult.text }}</pre>
              <div v-else class="context-preview-empty">没有达到相似度阈值的来源，Run 会继续使用关键词检索。</div>
              <div v-if="contextPreviewResult.evidences?.length" class="context-evidence-list">
                <article v-for="evidence in contextPreviewResult.evidences" :key="evidence.citation" class="context-evidence-row">
                  <div class="context-evidence-heading">
                    <strong>{{ evidence.title || '未命名来源' }}</strong>
                    <code>{{ evidence.citation }}</code>
                  </div>
                  <p>{{ evidence.excerpt }}</p>
                </article>
              </div>
            </div>
            <div v-else class="context-preview-empty context-preview-empty-initial">尚未运行查询。这里的结果与模型步骤实际收到的上下文格式一致。</div>
          </section>
          <section class="governance-card governance-fixed-card context-index-card">
            <div class="context-workbench-heading">
              <div>
                <p class="eyebrow">INDEX OPERATIONS</p>
                <h3>向量索引</h3>
              </div>
              <span class="context-index-status" :class="contextReindexStatusClass(contextReindexResult)">
                {{ contextReindexStatusLabel(contextReindexResult) }}
              </span>
            </div>
            <p class="context-workbench-help">按租户有界重建 chunk 和 embedding，不会把整租户数据一次性发送给供应商。</p>
            <form class="context-index-form" @submit.prevent="rebuildContextIndex">
              <label class="field"><span>重建范围</span><select v-model="contextReindexForm.scope"><option value="ALL">全部来源</option><option value="DOCUMENT">仅知识文档</option><option value="MEMORY">仅长期记忆</option></select></label>
              <label class="field"><span>父对象上限</span><input v-model.number="contextReindexForm.parentLimit" type="number" min="1" max="500" /></label>
              <label class="field"><span>chunk 上限</span><input v-model.number="contextReindexForm.chunkLimit" type="number" min="1" max="5000" /></label>
              <label class="check-field context-rechunk-field"><input v-model="contextReindexForm.rechunk" type="checkbox" /><span>按当前配置重新分块</span></label>
              <button class="secondary-button context-index-button" type="submit" :disabled="contextReindexLoading">
                {{ contextReindexLoading ? '重建中…' : '重建索引' }}
              </button>
            </form>
            <p v-if="contextReindexError" class="policy-error">{{ contextReindexError }}</p>
            <div v-if="contextReindexResult" class="context-index-result">
              <div><span>扫描父对象</span><strong>{{ contextReindexResult.parentsScanned }}</strong></div>
              <div><span>写入向量</span><strong class="is-positive">{{ contextReindexResult.chunksIndexed }}</strong></div>
              <div><span>待处理</span><strong :class="contextReindexResult.pendingChunks ? 'is-attention' : 'is-positive'">{{ contextReindexResult.pendingChunks }}</strong></div>
              <div><span>失败</span><strong :class="contextReindexResult.chunksFailed ? 'is-negative' : 'is-positive'">{{ contextReindexResult.chunksFailed }}</strong></div>
            </div>
            <small class="form-hint">需要 <code>context.reindex</code> 权限；开启“重新分块”后建议在低峰期执行。</small>
          </section>
          <section class="governance-card governance-fixed-card context-config-card">
            <div class="context-workbench-heading">
              <div>
                <p class="eyebrow">RUNTIME CONFIG</p>
                <h3>向量运行配置</h3>
              </div>
              <span class="context-index-status" :class="contextConfiguration?.embeddingReady ? 'is-ready' : 'is-warning'">{{ contextConfiguration?.embeddingReady ? 'READY' : 'OFFLINE' }}</span>
            </div>
            <p class="context-workbench-help">这是当前组织的脱敏快照。服务地址可在“向量设置”中维护，API Key 只会提交给 Runtime，不会回传到前端。</p>
            <button class="secondary-button context-config-edit-button" type="button" @click="showEmbeddingSettings = true">编辑向量连接</button>
            <div v-if="contextConfiguration" class="context-config-grid">
              <div><span>Embedding 模型</span><strong>{{ contextConfiguration.model }}</strong></div>
              <div><span>模型版本</span><strong>{{ contextConfiguration.modelVersion }}</strong></div>
              <div><span>向量维度</span><strong>{{ contextConfiguration.dimension }}</strong></div>
              <div><span>批量大小</span><strong>{{ contextConfiguration.batchSize }}</strong></div>
              <div><span>Chunk 上限</span><strong>{{ contextConfiguration.chunkMaxChars }}</strong></div>
              <div><span>父窗口上限</span><strong>{{ contextConfiguration.parentWindowMaxChars }}</strong></div>
              <div><span>最低相似度</span><strong>{{ contextConfiguration.minSimilarity }}</strong></div>
              <div><span>混合排序</span><strong>{{ contextConfiguration.rrfEnabled ? 'RRF' : '向量优先' }}</strong></div>
            </div>
            <div v-else class="context-preview-empty">正在读取 Runtime 配置…</div>
            <small class="form-hint">配置按组织保存；修改后旧向量会失效，请使用上方索引操作重新建立向量。</small>
          </section>
          <form class="governance-card governance-fixed-card" @submit.prevent="createDocument">
            <div class="context-workbench-heading">
              <div><h3>添加授权知识文档</h3><small class="form-hint">仅支持 PDF/DOCX 上传解析，上传后自动建立索引。</small></div>
              <span class="context-mode-chip">文件 → 文本 → 向量</span>
            </div>
            <div
              class="document-upload-dropzone"
              :class="{ 'is-dragging': documentUploadDragging, 'has-file': documentUploadFile }"
              @dragenter.prevent="documentUploadDragging = true"
              @dragover.prevent="documentUploadDragging = true"
              @dragleave.prevent="documentUploadDragging = false"
              @drop.prevent="handleDocumentUploadDrop"
            >
              <input
                ref="documentUploadInput"
                class="document-upload-input"
                type="file"
                accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                @change="handleDocumentUploadInput"
              />
              <div class="document-upload-copy">
                <strong>{{ documentUploadFile ? documentUploadFile.name : '拖入 PDF 或 DOCX 文件' }}</strong>
                <small v-if="documentUploadFile">{{ formatFileSize(documentUploadFile.size) }} · 上传后自动解析、切块并建立索引</small>
                <small v-else>单个文件最大 25 MB；扫描型 PDF 需要先经过 OCR 才能提取文字</small>
              </div>
              <div class="document-upload-actions">
                <button class="secondary-button" type="button" :disabled="loading || documentUploading" @click="openDocumentUploadPicker">{{ documentUploadFile ? '更换文件' : '选择文件' }}</button>
                <button v-if="documentUploadFile" class="text-button" type="button" :disabled="loading || documentUploading" @click="clearDocumentUploadFile">移除</button>
              </div>
            </div>
            <p v-if="documentUploadError" class="policy-error">{{ documentUploadError }}</p>
            <label class="field"><span>标题</span><input v-model="documentForm.title" required /></label>
            <label class="field"><span>可见用户（逗号分隔，可留空）</span><input v-model="documentForm.allowedUsers" /></label>
            <button class="secondary-button" type="submit" :disabled="loading || documentUploading || !documentUploadFile">上传并建立索引</button>
            <small class="form-hint">当前 {{ documents.length }} 篇文档；模型检索前会先执行组织和用户过滤。</small>
            <div v-if="documents.length" class="document-list" aria-label="已保存知识文档">
              <div v-for="document in documents" :key="document.id" class="document-row">
                <div class="document-row-content">
                  <strong>{{ document.title }}</strong>
                  <small>{{ document.allowedUsers ? `授权：${document.allowedUsers}` : '组织内可见' }} · {{ formatDate(document.createdAt) }}</small>
                </div>
                <button
                  v-if="document.ownerUserId === form.userId"
                  class="danger-button document-delete-button"
                  type="button"
                  :disabled="loading || documentDeletingId === document.id"
                  @click="deleteDocument(document)"
                >{{ documentDeletingId === document.id ? '删除中…' : '删除' }}</button>
                <small v-else class="document-owner-hint">仅所有者可删</small>
              </div>
            </div>
          </form>
          <section id="education" class="governance-card governance-fixed-card education-governance-card">
            <div class="context-workbench-heading">
              <div><p class="eyebrow">EDUCATION AGENT</p><h3>课程与学习者配置</h3></div>
              <span class="context-mode-chip">{{ learnerProfiles.length }} 个画像</span>
            </div>
            <p class="context-workbench-help">课程元数据决定检索范围；学习者画像和知识点掌握度决定讲解难度与教学策略。原始知识正文仍由知识文档权限控制。</p>
            <p v-if="educationError" class="policy-error">{{ educationError }}</p>
            <div v-if="educationMetrics" class="education-metrics" aria-label="教育业务闭环指标">
              <div><span>作业完成率</span><strong>{{ formatRate(educationMetrics.assignmentCompletionRate) }}</strong><small>{{ educationMetrics.assignmentCompleted }} / {{ educationMetrics.assignmentTotal }}</small></div>
              <div><span>任务启动率</span><strong>{{ formatRate(educationMetrics.taskStartRate) }}</strong><small>{{ educationMetrics.taskStarted }} / {{ educationMetrics.taskTotal }}</small></div>
              <div><span>任务完成率</span><strong>{{ formatRate(educationMetrics.taskCompletionRate) }}</strong><small>{{ educationMetrics.taskCompleted }} / {{ educationMetrics.taskTotal }}</small></div>
              <div><span>测评证据覆盖</span><strong>{{ formatRate(educationMetrics.taskEvidenceCoverageRate) }}</strong><small>{{ educationMetrics.taskEvidenceCovered }} / {{ educationMetrics.taskStarted }}</small></div>
              <div><span>通知读取率</span><strong>{{ formatRate(educationMetrics.notificationReadRate) }}</strong><small>{{ educationMetrics.notificationRead }} / {{ educationMetrics.notificationTotal }}</small></div>
              <div><span>测评正确率</span><strong>{{ formatRate(educationMetrics.assessmentAccuracyRate) }}</strong><small>{{ educationMetrics.correctAssessmentTotal }} / {{ educationMetrics.assessmentTotal }}</small></div>
              <div><span>教师确认率</span><strong>{{ formatRate(educationMetrics.assignmentReviewVerificationRate) }}</strong><small>{{ educationMetrics.assignmentReviewVerified }} / {{ educationMetrics.assignmentReviewPending + educationMetrics.assignmentReviewRevisionRequired + educationMetrics.assignmentReviewVerified }}</small></div>
              <div><span>反馈确认率</span><strong>{{ formatRate(educationMetrics.feedbackAcknowledgementRate) }}</strong><small>{{ educationMetrics.feedbackAcknowledged }} / {{ educationMetrics.feedbackTotal }}</small></div>
              <div><span>反馈执行率</span><strong>{{ formatRate(educationMetrics.feedbackResolutionRate) }}</strong><small>{{ educationMetrics.feedbackResolved }} / {{ educationMetrics.feedbackTotal }}</small></div>
              <div><span>重试成功率</span><strong>{{ formatRate(educationMetrics.retrySuccessRate) }}</strong><small>{{ educationMetrics.retriedTaskCompleted }} / {{ educationMetrics.retriedTaskTotal }}</small></div>
              <div><span>作业待重试</span><strong>{{ educationMetrics.assignmentRetryRequired }}</strong><small>失败/超时/取消后待处理</small></div>
              <div><span>作业待返工</span><strong>{{ educationMetrics.assignmentReviewRevisionRequired }}</strong><small>教师退回后待重新提交</small></div>
              <div><span>保持度正确率</span><strong>{{ formatRate(educationMetrics.reviewAssessmentAccuracyRate) }}</strong><small>平均掌握度提升 {{ formatRate(educationMetrics.averageMasteryGain) }}</small></div>
            </div>
            <form class="education-profile-form" @submit.prevent="saveLearnerProfile">
              <label class="field"><span>学科</span><input v-model="learnerProfileForm.subject" required maxlength="128" /></label>
              <label class="field"><span>年级</span><input v-model="learnerProfileForm.gradeLevel" required maxlength="128" /></label>
              <label class="field"><span>课程版本</span><input v-model="learnerProfileForm.curriculumVersion" required maxlength="128" /></label>
              <label class="field"><span>学习目标</span><input v-model="learnerProfileForm.learningGoal" maxlength="512" placeholder="例如：掌握函数基础并能独立完成练习" /></label>
              <button class="secondary-button" type="submit" :disabled="educationLoading">{{ educationLoading ? '保存中…' : '保存学习者画像' }}</button>
            </form>
            <div v-if="learnerProfiles.length" class="education-profile-list">
              <button v-for="profile in learnerProfiles" :key="profile.id" type="button" class="education-profile-chip" :class="{ active: profile.id === activeLearnerProfile?.id }" @click="activeLearnerProfile = profile; applyLearnerProfileToEducationRun(profile)">
                <strong>{{ profile.subject }} · {{ profile.gradeLevel }}</strong><small>{{ profile.curriculumVersion }} · {{ profile.learningGoal || '未设置学习目标' }}</small>
              </button>
            </div>
            <section class="education-course-workbench" aria-label="课程教师工作台">
              <div class="subsection-title education-course-heading">
                <div><h4>课程教师工作台</h4><span>{{ educationCourses.length }} 个课程实例</span></div>
                <span v-if="activeEducationCourse" class="context-mode-chip">{{ activeEducationCourse.status === 'ACTIVE' ? '运营中' : '已归档' }}</span>
              </div>
              <p class="learning-task-help">先创建课程实例，再维护活跃名单；批量布置会生成可追踪的独立作业，课程进度会把待证据、待重试和待教师确认集中呈现。</p>
              <form class="education-course-form" @submit.prevent="createEducationCourse">
                <label class="field"><span>课程代码</span><input v-model="educationCourseForm.code" required maxlength="128" placeholder="例如：MATH-G1-2026" /></label>
                <label class="field"><span>课程名称</span><input v-model="educationCourseForm.title" required maxlength="255" placeholder="例如：高中数学函数基础" /></label>
                <label class="field"><span>学科</span><input v-model="educationCourseForm.subject" required maxlength="128" /></label>
                <label class="field"><span>年级</span><input v-model="educationCourseForm.gradeLevel" required maxlength="128" /></label>
                <label class="field"><span>课程版本</span><input v-model="educationCourseForm.curriculumVersion" required maxlength="128" /></label>
                <button class="secondary-button" type="submit" :disabled="educationCourseSaving">{{ educationCourseSaving ? '创建中…' : '创建课程实例' }}</button>
              </form>
              <div v-if="educationCourses.length" class="education-course-list">
                <button v-for="course in educationCourses" :key="course.id" type="button" class="education-course-chip" :class="{ active: course.id === activeEducationCourseId }" @click="selectEducationCourse(course)">
                  <span><strong>{{ course.title }}</strong><small>{{ course.code }} · {{ course.subject }} · {{ course.gradeLevel }} · {{ course.curriculumVersion }}</small></span>
                  <em>{{ course.activeEnrollmentCount }} 人</em>
                </button>
              </div>
              <div v-else class="context-preview-empty">还没有课程实例；创建后才能使用课程名单和批量布置。</div>
              <div v-if="activeEducationCourse && activeEducationCourseIsOwner" class="education-course-detail">
                <div class="education-course-detail-heading">
                  <div><strong>{{ activeEducationCourse.title }}</strong><small>{{ activeEducationCourse.code }} · 课程负责人 {{ activeEducationCourse.ownerUserId }}</small></div>
                  <button v-if="activeEducationCourse.status === 'ACTIVE'" class="text-button" type="button" :disabled="educationCourseActionId === activeEducationCourse.id" @click="archiveEducationCourse(activeEducationCourse)">{{ educationCourseActionId === activeEducationCourse.id ? '归档中…' : '归档课程' }}</button>
                </div>
                <div class="education-course-columns">
                  <div class="education-course-roster">
                    <div class="subsection-title"><div><h4>活跃名单</h4><span>{{ educationCourseEnrollments.filter((item) => item.status === 'ACTIVE').length }} 人</span></div></div>
                    <form class="education-course-enrollment-form" @submit.prevent="enrollEducationLearner">
                      <label class="field"><span>学习者 ID</span><input v-model="educationCourseEnrollmentForm.learnerUserId" required maxlength="255" placeholder="例如：student-1" /></label>
                      <button class="secondary-button" type="submit" :disabled="educationCourseRosterSaving">{{ educationCourseRosterSaving ? '加入中…' : '加入名单' }}</button>
                    </form>
                    <div v-if="educationCourseEnrollments.length" class="education-course-roster-list">
                      <div v-for="enrollment in educationCourseEnrollments" :key="enrollment.id" class="education-course-roster-row" :class="{ inactive: enrollment.status !== 'ACTIVE' }">
                        <span><strong>{{ enrollment.learnerUserId }}</strong><small>{{ enrollment.status === 'ACTIVE' ? '活跃成员' : '已移除' }} · {{ formatDate(enrollment.enrolledAt) }}</small></span>
                        <button v-if="enrollment.status === 'ACTIVE'" class="text-button" type="button" :disabled="educationCourseActionId === enrollment.learnerUserId" @click="removeEducationLearner(enrollment)">{{ educationCourseActionId === enrollment.learnerUserId ? '处理中…' : '移除' }}</button>
                      </div>
                    </div>
                    <div v-else class="context-preview-empty">名单为空；请先加入学习者。</div>
                  </div>
                  <div class="education-course-assignment">
                    <div class="subsection-title"><div><h4>批量布置作业</h4><span>一次提交，逐人追踪</span></div></div>
                    <form class="education-course-assignment-form" @submit.prevent="assignEducationCourse">
                      <label class="field"><span>作业标题</span><input v-model="educationCourseAssignmentForm.title" required maxlength="255" placeholder="例如：函数定义域练习" /></label>
                      <label class="field"><span>目标知识点</span><input v-model="educationCourseAssignmentForm.conceptKey" required maxlength="255" placeholder="函数定义域" /></label>
                      <label class="field"><span>目标掌握度</span><input v-model="educationCourseAssignmentForm.targetMastery" type="number" min="0.01" max="1" step="0.05" required /></label>
                      <label class="field"><span>截止时间（可选）</span><input v-model="educationCourseAssignmentForm.dueAt" type="datetime-local" /></label>
                      <label class="field education-course-wide"><span>作业说明</span><textarea v-model="educationCourseAssignmentForm.instructions" required maxlength="4000" rows="2" placeholder="说明作答范围、提交要求或迁移任务"></textarea></label>
                      <button class="secondary-button" type="submit" :disabled="educationCourseAssignmentSaving || !educationCourseEnrollments.some((item) => item.status === 'ACTIVE')">{{ educationCourseAssignmentSaving ? '布置中…' : '向活跃名单布置' }}</button>
                    </form>
                  </div>
                </div>
                <div v-if="educationCourseProgress" class="education-course-progress">
                  <div class="subsection-title"><div><h4>课程进度与干预队列</h4><span>{{ educationCourseProgress.truncated ? '仅展示最近 500 份作业' : '覆盖全部课程作业' }}</span></div><button class="text-button" type="button" :disabled="educationCourseLoading" @click="loadEducationCourseWorkspace(activeEducationCourse.id)">{{ educationCourseLoading ? '刷新中…' : '刷新进度' }}</button></div>
                  <div class="education-course-summary-grid">
                    <div><span>作业完成</span><strong>{{ formatRate(educationCourseProgress.assignmentCompletionRate) }}</strong><small>{{ educationCourseProgress.completed }} / {{ educationCourseProgress.assignmentTotal }}</small></div>
                    <div><span>教师确认</span><strong>{{ formatRate(educationCourseProgress.teacherVerificationRate) }}</strong><small>{{ educationCourseProgress.reviewVerified }} / {{ educationCourseProgress.reviewPending + educationCourseProgress.reviewVerified + educationCourseProgress.revisionRequired }}</small></div>
                    <div><span>待证据</span><strong>{{ educationCourseProgress.awaitingEvidence }}</strong><small>Run 已结束但证据未回写</small></div>
                    <div><span>待重试</span><strong>{{ educationCourseProgress.retryRequired }}</strong><small>失败、超时或返工</small></div>
                    <div><span>开放干预</span><strong>{{ educationCourseProgress.openInterventionCount }}</strong><small>补证据或建议重试</small></div>
                  </div>
                  <div v-if="educationCourseProgress.learners?.length" class="education-course-progress-list">
                    <div class="education-course-progress-header"><span>学习者</span><span>作业状态</span><span>掌握度进度</span><span>下一步</span></div>
                    <div v-for="learner in educationCourseProgress.learners" :key="learner.learnerUserId" class="education-course-progress-row">
                      <span><strong>{{ learner.learnerUserId }}</strong><small>{{ learner.lastActivityAt ? `最近 ${formatDate(learner.lastActivityAt)}` : '尚无作业活动' }}</small></span>
                      <span class="education-course-status-copy">{{ learner.completed }} 完成 · {{ learner.awaitingEvidence }} 待证据 · {{ learner.retryRequired }} 待重试 · {{ learner.reviewPending }} 待确认</span>
                      <span><strong>{{ formatRate(learner.averageMasteryProgress) }}</strong><small>提升 {{ learner.averageMasteryGain >= 0 ? '+' : '' }}{{ formatRate(learner.averageMasteryGain) }}</small></span>
                      <button class="text-button" type="button" @click="focusCourseLearner(learner)">{{ courseLearnerAttentionCount(learner) ? `处理 ${courseLearnerAttentionCount(learner)} 项` : '查看作业' }}</button>
                    </div>
                  </div>
                  <div v-else class="context-preview-empty">名单中的学习者还没有作业；布置作业后，这里会显示每人的业务状态。</div>
                </div>
              </div>
            </section>
            <section class="learning-assignment-workbench" aria-label="课程作业入口">
              <div class="subsection-title"><h4>课程作业入口</h4><div><span v-if="learningAssignmentCourseFilter || learningAssignmentLearnerFilter">当前已筛选</span><button v-if="learningAssignmentCourseFilter || learningAssignmentLearnerFilter" class="text-button" type="button" @click="clearLearningAssignmentFilter">清除筛选</button><span v-else>{{ learningAssignments.length }} 个作业</span></div></div>
              <p class="learning-task-help">教师或组织可把课程约束和目标知识点布置给指定学习者；学习者接受后自动生成画像与学习目标。</p>
              <div class="subsection-title learning-task-heading"><div><h4>作业通知</h4><span>{{ learningAssignmentNotifications.length }} 条</span></div><div class="learning-notification-heading-actions"><span>{{ learningAssignmentNotificationUnreadCount }} 条未读</span><button v-if="learningAssignmentNotificationUnreadCount" class="text-button" type="button" @click="markAllLearningAssignmentNotificationsRead">全部已读</button></div></div>
              <div v-if="learningAssignmentNotifications.length" class="learning-notification-list" aria-label="课程作业通知">
                <article v-for="notification in learningAssignmentNotifications.slice(0, 5)" :key="notification.id" class="learning-notification-row" :class="{ unread: notification.unread }">
                  <div class="learning-notification-main"><div class="learning-notification-meta"><strong>{{ notification.title }}</strong><small>{{ formatDate(notification.createdAt) }}</small></div><p>{{ notification.body }}</p></div>
                  <div class="learning-notification-actions"><button class="secondary-button" type="button" @click="openLearningAssignmentNotification(notification)">{{ notification.notificationType === 'ASSIGNED' ? '接受作业' : (['FEEDBACK', 'FEEDBACK_ACKNOWLEDGED'].includes(notification.notificationType) ? '查看反馈' : (['RETRY_REQUIRED', 'REVISION_REQUIRED'].includes(notification.notificationType) ? '重试/返工作业' : (notification.assignmentStatus === 'ACCEPTED' ? '查看目标' : '查看作业'))) }}</button><button v-if="notification.unread" class="text-button" type="button" @click="markLearningAssignmentNotificationRead(notification)">标记已读</button></div>
                </article>
              </div>
              <form class="learning-assignment-form" @submit.prevent="createLearningAssignment">
                <label class="field"><span>学习者 ID</span><input v-model="learningAssignmentForm.learnerUserId" required maxlength="255" placeholder="例如：student-1" /></label>
                <label class="field"><span>作业标题</span><input v-model="learningAssignmentForm.title" required maxlength="255" placeholder="例如：函数定义域作业" /></label>
                <label class="field"><span>学科</span><input v-model="learningAssignmentForm.subject" required maxlength="128" placeholder="数学" /></label>
                <label class="field"><span>年级</span><input v-model="learningAssignmentForm.gradeLevel" required maxlength="128" placeholder="高中一年级" /></label>
                <label class="field"><span>课程版本</span><input v-model="learningAssignmentForm.curriculumVersion" required maxlength="128" placeholder="人教A版" /></label>
                <label class="field"><span>目标知识点</span><input v-model="learningAssignmentForm.conceptKey" required maxlength="255" placeholder="函数定义域" /></label>
                <label class="field"><span>目标掌握度</span><input v-model="learningAssignmentForm.targetMastery" type="number" min="0.01" max="1" step="0.05" required /></label>
                <label class="field"><span>截止时间（可选）</span><input v-model="learningAssignmentForm.dueAt" type="datetime-local" /></label>
                <label class="field learning-assignment-wide"><span>作业说明</span><textarea v-model="learningAssignmentForm.instructions" required maxlength="4000" rows="2" placeholder="说明作业要求、作答范围或迁移任务"></textarea></label>
                <button class="secondary-button learning-assignment-submit" type="submit" :disabled="learningAssignmentSaving">{{ learningAssignmentSaving ? '布置中…' : '布置课程作业' }}</button>
              </form>
              <div v-if="visibleLearningAssignments.length" id="learning-assignment-list" class="learning-assignment-list">
                <article v-for="assignment in visibleLearningAssignments" :id="`learning-assignment-${assignment.id}`" :key="assignment.id" class="learning-assignment-row">
                  <div class="learning-assignment-main">
                    <div class="learning-assignment-meta"><strong>{{ assignment.title }}</strong><span>{{ learningAssignmentStatusLabel(assignment.status) }}</span></div>
                    <small>{{ assignment.teacherUserId }} → {{ assignment.learnerUserId }} · {{ assignment.subject }} · {{ assignment.gradeLevel }} · {{ assignment.curriculumVersion }}</small>
                    <small v-if="assignment.reviewStatus !== 'NOT_REQUIRED'" class="learning-assignment-progress">业务结果：{{ learningAssignmentReviewStatusLabel(assignment.reviewStatus) }}<span v-if="assignment.teacherReviewedAt"> · {{ formatDate(assignment.teacherReviewedAt) }}</span></small>
                    <p>{{ assignment.instructions }}</p>
                    <small v-if="learningAssignmentProgressMap[assignment.id]" class="learning-assignment-progress">掌握度 {{ formatRate(learningAssignmentProgressMap[assignment.id].currentMastery) }} / {{ formatRate(learningAssignmentProgressMap[assignment.id].targetMastery) }} · 提升 {{ learningAssignmentProgressMap[assignment.id].masteryGain >= 0 ? '+' : '' }}{{ formatRate(learningAssignmentProgressMap[assignment.id].masteryGain) }} · 目标进度 {{ formatRate(learningAssignmentProgressMap[assignment.id].masteryProgress) }} · 测评 {{ learningAssignmentProgressMap[assignment.id].assessmentTotal }} 次 · 任务 {{ learningAssignmentProgressMap[assignment.id].taskCompleted }} / {{ learningAssignmentProgressMap[assignment.id].taskTotal }}</small>
                    <small v-if="learningAssignmentProgressMap[assignment.id]" class="learning-assignment-progress">Run 证据覆盖 {{ formatRate(learningAssignmentProgressMap[assignment.id].runEvidenceCoverageRate) }}（{{ learningAssignmentProgressMap[assignment.id].runWithAssessmentEvidence }} / {{ learningAssignmentProgressMap[assignment.id].runTotal }}） · 教师反馈确认 {{ formatRate(learningAssignmentProgressMap[assignment.id].feedbackAcknowledgementRate) }}（{{ learningAssignmentProgressMap[assignment.id].feedbackAcknowledged }} / {{ learningAssignmentProgressMap[assignment.id].feedbackTotal }}）</small>
                    <details v-if="learningAssignmentEvidenceMap[assignment.id]?.length" class="learning-assessment-history"><summary>查看测评证据（{{ learningAssignmentEvidenceMap[assignment.id].length }}）</summary><div v-for="attempt in learningAssignmentEvidenceMap[assignment.id].slice().reverse().slice(0, 5)" :key="attempt.id"><span :class="attempt.correct ? 'assessment-correct' : 'assessment-wrong'">{{ attempt.correct ? '正确' : '错误' }}</span><span>{{ attempt.evidenceText || '未填写证据文本' }}<small v-if="attempt.feedback"> · {{ attempt.feedback }}</small></span><small>{{ attempt.evidenceSource === 'MANUAL_REVIEW' ? '人工复核' : (attempt.assessmentType === 'REVIEW' ? '保持度复习' : 'Agent观察') }} · {{ formatDate(attempt.createdAt) }}</small><small v-if="assessmentRetrievalEvidenceLabel(attempt)">知识源：{{ assessmentRetrievalEvidenceLabel(attempt) }}</small></div></details>
                    <details v-if="learningAssignmentFeedbackMap[assignment.id]?.length" class="learning-assessment-history"><summary>教师反馈（{{ learningAssignmentFeedbackMap[assignment.id].length }}）</summary><div v-for="feedback in learningAssignmentFeedbackMap[assignment.id].slice(0, 5)" :key="feedback.id"><span>{{ learningAssignmentFeedbackActionLabel(feedback.action) }}</span><span>{{ feedback.message }}<small v-if="feedback.suggestedDueAt"> · 截止 {{ formatDate(feedback.suggestedDueAt) }}</small></span><small>{{ feedback.status === 'RESOLVED' ? '已执行' : (feedback.status === 'ACKNOWLEDGED' ? '已确认' : '待确认') }} · {{ formatDate(feedback.createdAt) }}<button v-if="assignment.learnerUserId === form.userId && feedback.status === 'OPEN'" class="text-button" type="button" :disabled="learningAssignmentFeedbackAcknowledgingId === feedback.id" @click="acknowledgeLearningAssignmentFeedback(assignment, feedback)">确认</button></small></div></details>
                  </div>
                  <div class="learning-assignment-actions">
                    <button v-if="assignment.learnerUserId === form.userId && (assignment.status === 'ASSIGNED' || assignment.status === 'AWAITING_EVIDENCE' || assignment.status === 'RETRY_REQUIRED' || (['ACCEPTED', 'OVERDUE'].includes(assignment.status) && learningAssignmentHasOpenIntervention(assignment)))" class="secondary-button" type="button" :disabled="learningAssignmentAcceptingId === assignment.id" @click="startLearningAssignment(assignment)">{{ learningAssignmentAcceptingId === assignment.id ? '启动中…' : (assignment.status === 'ASSIGNED' ? '接受并开始学习' : (assignment.status === 'AWAITING_EVIDENCE' ? '补充证据并继续' : (assignment.status === 'RETRY_REQUIRED' ? '重试课程作业' : '按反馈继续学习'))) }}</button>
                    <button v-if="assignment.teacherUserId === form.userId && assignment.status === 'COMPLETED' && assignment.reviewStatus === 'PENDING'" class="secondary-button" type="button" :disabled="learningAssignmentReviewSavingId === assignment.id" @click="verifyLearningAssignment(assignment)">{{ learningAssignmentReviewSavingId === assignment.id ? '确认中…' : '确认作业结果' }}</button>
                    <button v-if="assignment.teacherUserId === form.userId && assignment.status === 'COMPLETED' && assignment.reviewStatus === 'PENDING'" class="text-button" type="button" :disabled="learningAssignmentReviewSavingId === assignment.id" @click="returnLearningAssignmentForRevision(assignment)">{{ learningAssignmentReviewSavingId === assignment.id ? '处理中…' : '退回返工' }}</button>
                    <button v-if="assignment.teacherUserId === form.userId && assignment.status !== 'CANCELLED'" class="text-button" type="button" @click="startLearningAssignmentFeedback(assignment)">写教师反馈</button>
                    <button v-if="assignment.teacherUserId === form.userId && ['ASSIGNED', 'ACCEPTED', 'RETRY_REQUIRED', 'OVERDUE'].includes(assignment.status)" class="text-button" type="button" @click="cancelLearningAssignment(assignment)">取消作业</button>
                  </div>
                </article>
              </div>
              <div v-else class="context-preview-empty">{{ learningAssignments.length ? '当前筛选范围没有作业。' : '还没有课程作业；可以先在上方创建课程并向活跃名单布置。' }}</div>
              <form v-if="learningAssignmentFeedbackForm.assignmentId" class="learning-assignment-feedback-form" @submit.prevent="submitLearningAssignmentFeedback">
                <div class="subsection-title"><h4>教师反馈</h4><button class="text-button" type="button" @click="closeLearningAssignmentFeedback">关闭</button></div>
                <label class="field"><span>反馈动作</span><select v-model="learningAssignmentFeedbackForm.action"><option value="COMMENT">教师反馈</option><option value="REQUEST_EVIDENCE">要求补充证据</option><option value="RECOMMEND_RETRY">建议重新学习</option><option value="RESCHEDULE">重新安排截止时间</option></select></label>
                <label v-if="learningAssignmentFeedbackForm.action === 'RESCHEDULE'" class="field"><span>新的截止时间</span><input v-model="learningAssignmentFeedbackForm.suggestedDueAt" type="datetime-local" required /></label>
                <label class="field learning-assignment-wide"><span>反馈内容</span><textarea v-model="learningAssignmentFeedbackForm.message" required maxlength="4000" rows="2" placeholder="写明证据判断和下一步行动"></textarea></label>
                <button class="secondary-button" type="submit" :disabled="learningAssignmentFeedbackSavingId === learningAssignmentFeedbackForm.assignmentId">{{ learningAssignmentFeedbackSavingId ? '发送中…' : '发送反馈' }}</button>
              </form>
            </section>
            <div class="learning-goal-workbench">
              <div class="learning-task-workbench">
                <div class="subsection-title learning-task-heading"><div><h4>待处理学习任务</h4><span>{{ learningTasks.filter((task) => ['OPEN', 'IN_PROGRESS', 'AWAITING_EVIDENCE', 'DEFERRED', 'FAILED'].includes(task.status)).length }} 条</span></div><div class="learning-notification-heading-actions"><span>{{ learningNotificationUnreadCount }} 条未读</span><button v-if="learningNotificationUnreadCount" class="text-button" type="button" @click="markAllLearningNotificationsRead">全部已读</button></div></div>
                <p class="learning-task-help">复习计划到期后会自动生成任务；Run 失败会进入可重试，Run 成功但没有测评证据会进入待补证据。</p>
                <div v-if="learningNotifications.length" class="learning-notification-list" aria-label="学习任务通知">
                  <article v-for="notification in learningNotifications.slice(0, 5)" :key="notification.id" class="learning-notification-row" :class="{ unread: notification.unread }">
                    <div class="learning-notification-main"><div class="learning-notification-meta"><strong>{{ notification.title }}</strong><small>{{ formatDate(notification.createdAt) }}</small></div><p>{{ notification.body }}</p></div>
                    <div class="learning-notification-actions"><button class="secondary-button" type="button" @click="openLearningNotification(notification)">{{ notification.notificationType === 'EVIDENCE_REQUIRED' ? '补充证据' : (notification.notificationType === 'FAILED' ? '重试任务' : '打开任务') }}</button><button v-if="notification.unread" class="text-button" type="button" @click="markLearningNotificationRead(notification)">标记已读</button></div>
                  </article>
                </div>
                <div v-if="learningTasks.length" class="learning-task-list">
                  <article v-for="task in learningTasks.filter((item) => ['OPEN', 'IN_PROGRESS', 'AWAITING_EVIDENCE', 'DEFERRED', 'FAILED'].includes(item.status)).slice(0, 8)" :key="task.id" class="learning-task-row">
                    <div class="learning-task-main"><strong>{{ task.title }}</strong><small>{{ task.status === 'IN_PROGRESS' ? '进行中' : (task.status === 'AWAITING_EVIDENCE' ? '待补测评证据' : (task.status === 'FAILED' ? `执行失败${task.failureReason ? `：${task.failureReason}` : ''}` : (task.status === 'DEFERRED' ? `延期至 ${formatDate(task.scheduledAt)}` : `到期 ${formatDate(task.scheduledAt)}`))) }} · 第 {{ task.reviewSequence + 1 }} 次复习</small><p>{{ task.prompt }}</p></div>
                    <div class="learning-task-actions">
                      <button class="secondary-button" type="button" :disabled="learningTaskStartingId === task.id || learningTaskDeferringId === task.id" @click="startLearningTask(task)">{{ learningTaskStartingId === task.id ? '启动中…' : (task.status === 'FAILED' ? '重试任务' : (task.status === 'AWAITING_EVIDENCE' ? '补充证据' : (task.status === 'IN_PROGRESS' ? '继续复习' : '开始复习'))) }}</button>
                      <button v-if="task.status === 'OPEN'" class="text-button" type="button" :disabled="learningTaskDeferringId === task.id" @click="deferLearningTask(task)">{{ learningTaskDeferringId === task.id ? '延期中…' : '明天再复习' }}</button>
                    </div>
                  </article>
                </div>
                <div v-else class="context-preview-empty">暂无待处理任务；完成学习目标后，系统会自动建立保持度复习任务。</div>
              </div>
              <div class="subsection-title"><h4>结构化学习目标</h4><span>{{ learningGoals.length }} 个目标</span></div>
              <form class="learning-goal-form" @submit.prevent="createLearningGoal">
                <label class="field"><span>画像</span><select v-model="learningGoalForm.learnerProfileId" required><option value="">请选择画像</option><option v-for="profile in learnerProfiles" :key="profile.id" :value="profile.id">{{ profile.subject }} · {{ profile.gradeLevel }}</option></select></label>
                <label class="field"><span>目标名称</span><input v-model="learningGoalForm.title" required maxlength="255" placeholder="例如：掌握函数定义域" /></label>
                <label class="field"><span>知识点</span><input v-model="learningGoalForm.conceptKey" required maxlength="255" placeholder="例如：函数定义域" /></label>
                <label class="field"><span>目标掌握度</span><input v-model.number="learningGoalForm.targetMastery" type="number" min="0.01" max="1" step="0.05" required /></label>
                <button class="secondary-button" type="submit" :disabled="educationLoading || !learnerProfiles.length">创建目标</button>
              </form>
              <div v-if="learningGoals.length" class="learning-goal-list">
                <button v-for="goal in learningGoals" :key="goal.id" type="button" class="learning-goal-row" :class="{ active: goal.id === activeLearningGoal?.id }" @click="selectLearningGoal(goal)">
                  <span class="learning-goal-row-main"><strong>{{ goal.title }}</strong><small>{{ goal.conceptKey }} · {{ goal.status === 'COMPLETED' ? '已完成' : '进行中' }}</small></span>
                  <span class="learning-goal-row-progress"><span>{{ Math.round((learningGoalRecommendationMap[goal.id]?.progressRatio || 0) * 100) }}%</span><i><b :style="{ width: `${(learningGoalRecommendationMap[goal.id]?.progressRatio || 0) * 100}%` }"></b></i></span>
                </button>
              </div>
              <div v-if="activeLearningGoal && learningRecommendation" class="learning-recommendation">
                <div class="learning-recommendation-heading"><div><span>下一步学习动作</span><strong>{{ learningRecommendation.nextActionTitle }}</strong></div><button class="secondary-button" type="button" @click="useLearningRecommendation">带着建议开始</button></div>
                <p>{{ learningRecommendation.rationale }}</p>
                <small>掌握度 {{ Math.round(learningRecommendation.currentMastery * 100) }}% / 目标 {{ Math.round(learningRecommendation.targetMastery * 100) }}% · 测评 {{ learningRecommendation.attemptCount }} 次 · 正确 {{ learningRecommendation.correctAttemptCount }} 次</small>
                <small v-if="learningRecommendation.reviewPlanId">保持度复习 {{ learningRecommendation.reviewCount }} 次 · 成功 {{ learningRecommendation.successfulReviewCount }} 次 · 下次 {{ formatDate(learningRecommendation.nextReviewAt) }}</small>
                <details v-if="learningGoalAssessments.length" class="learning-assessment-history"><summary>查看测评历史（{{ learningGoalAssessments.length }}）</summary><div v-for="attempt in learningGoalAssessments.slice().reverse().slice(0, 5)" :key="attempt.id"><span :class="attempt.correct ? 'assessment-correct' : 'assessment-wrong'">{{ attempt.correct ? '正确' : '错误' }}</span><span>{{ Math.round(attempt.masteryBefore * 100) }}% → {{ Math.round(attempt.masteryAfter * 100) }}%</span><small>{{ attempt.assessmentType === 'REVIEW' ? '保持度复习' : (attempt.evidenceSource === 'MANUAL_REVIEW' ? '人工复核' : 'Agent观察') }} · {{ formatDate(attempt.createdAt) }}</small></div></details>
              </div>
            </div>
            <div class="education-source-editor">
              <div class="subsection-title"><h4>绑定知识文档课程元数据</h4><span>{{ educationSources.length }} 个课程来源</span></div>
              <form class="education-source-form" @submit.prevent="saveEducationSource">
                <label class="field field-wide"><span>知识文档</span><select v-model="educationSourceForm.documentId" required><option value="">选择已上传文档</option><option v-for="document in documents" :key="document.id" :value="document.id">{{ document.title }}</option></select></label>
                <label class="field"><span>学科</span><input v-model="educationSourceForm.subject" required /></label>
                <label class="field"><span>年级</span><input v-model="educationSourceForm.gradeLevel" required /></label>
                <label class="field"><span>课程版本</span><input v-model="educationSourceForm.curriculumVersion" required /></label>
                <label class="field"><span>章节</span><input v-model="educationSourceForm.chapter" /></label>
                <label class="field"><span>难度（1-5）</span><input v-model.number="educationSourceForm.difficultyLevel" type="number" min="1" max="5" required /></label>
                <label class="field field-wide"><span>知识点标签（逗号分隔）</span><input v-model="educationSourceForm.conceptTags" placeholder="例如：函数,定义域,值域" /></label>
                <label class="field field-wide"><span>前置知识（逗号分隔）</span><input v-model="educationSourceForm.prerequisiteConcepts" placeholder="例如：集合,不等式" /></label>
                <label class="field field-wide"><span>学习目标</span><textarea v-model="educationSourceForm.learningObjectives" rows="2" maxlength="4000"></textarea></label>
                <button class="secondary-button" type="submit" :disabled="educationLoading || !educationSourceForm.documentId">保存课程元数据</button>
              </form>
              <div v-if="educationSources.length" class="education-source-list">
                <div v-for="source in educationSources" :key="source.id" class="education-source-row">
                  <div><strong>{{ documents.find((document) => document.id === source.documentId)?.title || source.documentId }}</strong><small>{{ source.subject }} · {{ source.gradeLevel }} · {{ source.curriculumVersion }} · 难度 {{ source.difficultyLevel }}</small></div>
                  <button class="text-button" type="button" @click="educationSourceForm.documentId = source.documentId; educationSourceForm.subject = source.subject; educationSourceForm.gradeLevel = source.gradeLevel; educationSourceForm.curriculumVersion = source.curriculumVersion; educationSourceForm.chapter = source.chapter || ''; educationSourceForm.conceptTags = source.conceptTags || ''; educationSourceForm.prerequisiteConcepts = source.prerequisiteConcepts || ''; educationSourceForm.learningObjectives = source.learningObjectives || ''; educationSourceForm.difficultyLevel = source.difficultyLevel">编辑</button>
                </div>
              </div>
            </div>
          </section>
          <form class="governance-card governance-fixed-card memory-card" @submit.prevent="createMemory">
            <div class="context-workbench-heading">
              <div>
                <p class="eyebrow">PERSONAL CONTEXT</p>
                <h3>长期记忆</h3>
              </div>
              <span class="context-mode-chip">{{ memories.length }} 条</span>
            </div>
            <p class="context-workbench-help">仅当前用户可检索。适合保存偏好、工作习惯和可过期的运行背景，不建议写入密钥或凭证。</p>
            <label class="field"><span>记忆类型</span><input v-model="memoryForm.memoryType" required maxlength="64" placeholder="例如：USER_PREFERENCE" /></label>
            <label class="field"><span>记忆内容</span><textarea v-model="memoryForm.content" rows="3" required maxlength="20000" placeholder="例如：用户偏好在回答中给出文件路径和验证命令"></textarea></label>
            <label class="field"><span>过期时间（可选）</span><input v-model="memoryForm.expiresAt" type="datetime-local" /></label>
            <button class="secondary-button" type="submit" :disabled="loading || !memoryForm.content.trim()">保存记忆</button>
            <div v-if="memories.length" class="memory-list" aria-label="已保存长期记忆">
              <article v-for="memory in memories" :key="memory.id" class="memory-row">
                <div class="memory-row-content">
                  <div class="memory-row-heading"><strong>{{ memory.memoryType }}</strong><span>{{ memory.expiresAt ? `到期 ${formatDate(memory.expiresAt)}` : '长期有效' }}</span></div>
                  <p>{{ memory.content }}</p>
                </div>
                <button class="danger-button document-delete-button" type="button" :disabled="loading || memoryDeletingId === memory.id" @click="deleteMemory(memory)">{{ memoryDeletingId === memory.id ? '删除中…' : '删除' }}</button>
              </article>
            </div>
            <div v-else class="context-preview-empty">还没有当前用户的长期记忆。</div>
          </form>
          <form class="governance-card policy-card" @submit.prevent="saveTenantPolicy">
            <div class="subsection-title"><h3>组织资源策略</h3><span v-if="tenantPolicy">{{ tenantPolicy.defaulted ? '平台默认' : '组织覆盖' }}</span></div>
            <p v-if="tenantPolicyError" class="policy-error">{{ tenantPolicyError }}</p>
            <label class="field"><span>最大活动 Run 数</span><input v-model.number="tenantPolicyForm.maxActiveRuns" type="number" min="1" required /></label>
            <label class="field"><span>单次最大步骤数</span><input v-model.number="tenantPolicyForm.maxStepsPerRun" type="number" min="1" required /></label>
            <label class="field"><span>最大输入字符数</span><input v-model.number="tenantPolicyForm.maxInputLength" type="number" min="1" required /></label>
            <label class="field"><span>单次最大预算</span><input v-model.number="tenantPolicyForm.maxBudget" type="number" min="0.000001" step="0.000001" required /></label>
            <label class="field"><span>每分钟创建 Run 数</span><input v-model.number="tenantPolicyForm.maxCreatesPerMinute" type="number" min="1" required /></label>
            <div class="field tenant-policy-tools-field">
              <span>工具白名单（可多选，留空表示全部）</span>
              <details class="tenant-policy-tool-picker">
                <summary>
                  <span>{{ selectedAllowedTools.length ? `已选择 ${selectedAllowedTools.length} 项` : '允许全部已注册工具' }}</span>
                  <small>展开选择</small>
                </summary>
                <div class="tenant-policy-tool-menu">
                  <div class="tenant-policy-tool-menu-actions">
                    <span>只允许勾选的工具参与组织 Run</span>
                    <button class="text-button" type="button" :disabled="!allowedToolOptions.length" @click="toggleAllAllowedTools">{{ allAllowedToolsSelected ? '清空' : '全选' }}</button>
                  </div>
                  <label v-for="tool in allowedToolOptions" :key="tool.value" class="tenant-policy-tool-option" :title="tool.description">
                    <input v-model="selectedAllowedTools" type="checkbox" :value="tool.value" />
                    <span class="tenant-policy-tool-copy">
                      <strong>{{ tool.label }}</strong>
                      <small>{{ tool.description }}</small>
                    </span>
                    <code>{{ tool.riskLevel }}</code>
                  </label>
                  <small v-if="!allowedToolOptions.length" class="tenant-policy-tool-empty">暂未读取到已注册工具，请先刷新页面或检查 tool.read 权限。</small>
                </div>
              </details>
              <small class="form-hint">{{ selectedAllowedToolsSummary }}</small>
            </div>
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
              <label class="field"><span>组织 ID</span><input v-model="apiKeyForm.tenantId" required maxlength="128" /></label>
              <label class="field"><span>用户 ID</span><input v-model="apiKeyForm.userId" required maxlength="128" /></label>
              <label class="field api-key-expiry-field"><span>过期时间（可选）</span><input v-model="apiKeyForm.expiresAt" type="datetime-local" /></label>
              <div class="field api-key-permissions-field">
                <span>权限（可多选）</span>
                <details class="api-key-permission-picker">
                  <summary>
                    <span>{{ apiKeyPermissionCount ? `已选择 ${apiKeyPermissionCount} 项` : '请选择权限' }}</span>
                    <small>展开选择</small>
                  </summary>
                  <div class="api-key-permission-menu">
                    <div class="api-key-permission-menu-actions">
                      <span>勾选后会随 API Key 一起授予</span>
                      <button class="text-button" type="button" @click="toggleAllApiKeyPermissions">{{ allApiKeyPermissionsSelected ? '清空' : '全选' }}</button>
                    </div>
                    <label v-for="permission in apiKeyPermissionOptions" :key="permission.value" class="api-key-permission-option">
                      <input v-model="apiKeyForm.permissions" type="checkbox" :value="permission.value" />
                      <span>{{ permission.label }}</span>
                      <code>{{ permission.value }}</code>
                    </label>
                  </div>
                </details>
                <small class="form-hint">{{ apiKeyPermissionCount ? parseApiKeyPermissions().join('、') : '未授予接口权限' }}</small>
              </div>
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
              <div class="subsection-title"><h3>当前组织密钥</h3><span>{{ apiKeys.length }} keys</span></div>
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
      </section>

      <footer class="footer">Ming Harness · 每次执行都可恢复、可解释、可审计、可限制</footer>
    </main>
  </div>
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
          <span v-if="modelConfigTestResult.errorCode" class="model-settings-test-code">{{ modelConfigTestResult.errorCode }}</span>
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
    v-if="showEmbeddingSettings"
    class="model-settings-overlay"
    role="dialog"
    aria-modal="true"
    aria-labelledby="embedding-settings-title"
    @click.self="showEmbeddingSettings = false"
  >
    <form class="model-settings-dialog embedding-settings-dialog" @submit.prevent="saveEmbeddingConfig">
      <header class="model-settings-heading">
        <div>
          <p class="eyebrow">EMBEDDING CONNECTION</p>
          <h2 id="embedding-settings-title">向量连接设置</h2>
          <span>{{ embeddingConfig?.source === 'tenant' ? '当前组织配置' : '环境默认配置' }}</span>
        </div>
        <button class="icon-button" type="button" aria-label="关闭向量设置" @click="showEmbeddingSettings = false">×</button>
      </header>
      <div v-if="embeddingConfigLoading" class="model-settings-state">正在读取当前向量配置…</div>
      <template v-else>
        <p class="model-settings-help">支持 OpenAI 兼容的 <code>/embeddings</code> 地址。知识库向量是组织共享索引，保存后旧向量会标记为待重建；API Key 只会提交给当前 Runtime，服务端加密保存。</p>
        <label class="check-field model-settings-toggle">
          <input v-model="embeddingConfigForm.enabled" type="checkbox" :disabled="!embeddingConfigEditable" />
          <span>启用外部 Embedding，不使用关键词降级</span>
        </label>
        <label class="field"><span>服务预设</span><select v-model="embeddingProviderPreset" :disabled="!embeddingConfigEditable || !embeddingConfigForm.enabled" @change="applyEmbeddingProviderPreset"><option v-for="preset in embeddingProviderPresets" :key="preset.id" :value="preset.id">{{ preset.label }}</option></select></label>
        <label class="field"><span>Embedding API 地址</span><input v-model="embeddingConfigForm.baseUrl" :disabled="!embeddingConfigEditable || !embeddingConfigForm.enabled" :required="embeddingConfigForm.enabled" maxlength="512" placeholder="https://api.openai.com/v1" @input="useCustomEmbeddingProvider" /></label>
        <label class="field"><span>Embedding 模型名称</span><input v-model="embeddingConfigForm.modelName" :disabled="!embeddingConfigEditable || !embeddingConfigForm.enabled" :required="embeddingConfigForm.enabled" maxlength="128" placeholder="例如：text-embedding-3-small" @input="useCustomEmbeddingProvider" /></label>
        <div class="embedding-settings-inline-fields">
          <label class="field"><span>模型版本</span><input v-model="embeddingConfigForm.modelVersion" :disabled="!embeddingConfigEditable || !embeddingConfigForm.enabled" required maxlength="128" placeholder="v1" /></label>
          <label class="field"><span>向量维度</span><input v-model.number="embeddingConfigForm.dimension" :disabled="!embeddingConfigEditable || !embeddingConfigForm.enabled" required type="number" min="1" max="8192" /></label>
        </div>
        <small class="form-hint">当前数据库向量列固定为 1536 维；其他维度会被拒绝，需先做数据库迁移。</small>
        <label class="field"><span>API Key（留空保留当前密钥）</span><input v-model="embeddingConfigForm.apiKey" :disabled="!embeddingConfigEditable" type="password" autocomplete="new-password" maxlength="1000" placeholder="不会回显已保存的密钥" /></label>
        <label v-if="embeddingConfig?.apiKeyConfigured" class="check-field model-settings-clear-key">
          <input v-model="embeddingConfigForm.clearApiKey" type="checkbox" :disabled="!embeddingConfigEditable" />
          <span>同时删除服务端已保存的 API Key（适用于无密钥本地模型）</span>
        </label>
        <p v-if="embeddingConfig?.apiKeyConfigured" class="model-settings-hint">当前密钥：{{ embeddingConfig.apiKeyHint || '已配置（不显示明文）' }}</p>
        <p v-if="embeddingConfigTestResult" class="model-settings-test-result" :class="embeddingConfigTestResult.success ? 'success' : 'failed'" role="status" aria-live="polite">
          <span v-if="embeddingConfigTestResult.errorCode" class="model-settings-test-code">{{ embeddingConfigTestResult.errorCode }}</span>
          {{ embeddingConfigTestResult.message }}<span v-if="embeddingConfigTestResult.dimension"> · {{ embeddingConfigTestResult.dimension }} 维</span><span v-if="embeddingConfigTestResult.latencyMs"> · {{ embeddingConfigTestResult.latencyMs }} ms</span>
        </p>
        <p v-if="embeddingConfigError" class="policy-error">{{ embeddingConfigError }}</p>
        <footer class="model-settings-actions embedding-settings-actions">
          <button class="danger-button" type="button" :disabled="embeddingConfigSaving || !embeddingConfig?.configured" @click="resetEmbeddingConfig">恢复环境默认</button>
          <span></span>
          <button class="secondary-button" type="button" :disabled="embeddingConfigSaving || embeddingConfigTesting || !embeddingConfigEditable || !embeddingConfigForm.enabled" @click="testEmbeddingConfig">{{ embeddingConfigTesting ? '测试中…' : '测试连接' }}</button>
          <button class="secondary-button" type="button" :disabled="embeddingConfigSaving" @click="showEmbeddingSettings = false">取消</button>
          <button class="primary-button" type="submit" :disabled="embeddingConfigSaving || embeddingConfigLoading || !embeddingConfigEditable">{{ embeddingConfigSaving ? '保存中…' : '保存并应用' }}</button>
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
