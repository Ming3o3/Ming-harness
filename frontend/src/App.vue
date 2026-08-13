<script setup>
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  Activity,
  Bot,
  ArrowDown,
  ArrowRight,
  ArrowUp,
  BookOpen,
  Brain,
  CalendarClock,
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
  Target,
  Trash2,
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
const learnerMastery = ref([])
const learnerMasteryLoading = ref(false)
const learningGoals = ref([])
const activeLearningGoal = ref(null)
const learningGoalAssessments = ref([])
// 对话中的证据按 Run 保存，切换学习目标后仍能看见该轮回答如何改变学习状态。
const chatAssessmentEvidenceByRun = ref({})
// Run 步骤里的授权检索证据按 Run 保存，聊天来源卡片不再只依赖模型是否输出
// 规范化 citation；即使模型漏写来源，学习者仍能核对 Agent 实际使用的课程摘录。
const chatRuntimeEvidenceByRun = ref({})
const learningRecommendation = ref(null)
const learningGoalRecommendationMap = ref({})
const learningTasks = ref([])
const learningNotifications = ref([])
const learningNotificationUnreadCount = ref(0)
const learningAssignmentNotifications = ref([])
const learningAssignmentNotificationUnreadCount = ref(0)
const learningAssignments = ref([])
const learningEvaluationQueue = ref([])
const learningAssignmentProgressMap = ref({})
const learningAssignmentEvidenceMap = ref({})
const learningAssignmentFeedbackMap = ref({})
const learningAssignmentSubmissionMap = ref({})
const learningAssignmentEvaluationMap = ref({})
const educationChatRefreshes = new Map()
// 课程作业首屏只加载少量详情；筛选或聚焦大班作业时按需补齐证据链，避免
// 结课阻塞清单指向未加载的提交物、测评、反馈或量规评价。
const educationAssignmentDetailRequests = new Map()
const educationCourses = ref([])
const activeEducationCourseId = ref('')
const educationCourseEnrollments = ref([])
const educationCourseProgress = ref(null)
const educationCourseResult = ref(null)
const educationCourseLoading = ref(false)
const educationCourseSaving = ref(false)
const educationCourseRosterSaving = ref(false)
const educationCourseAssignmentSaving = ref(false)
const educationCourseActionId = ref('')
const learningAssignmentCourseFilter = ref('')
const learningAssignmentLearnerFilter = ref('')
const learningAssignmentIssueFilter = ref('')
const educationMetrics = ref(null)
const learningTaskLoading = ref(false)
const learningTaskStartingId = ref('')
const learningTaskDeferringId = ref('')
const learningAssignmentSaving = ref(false)
const learningAssignmentAcceptingId = ref('')
const learningAssignmentReviewSavingId = ref('')
const learningIndependentEvaluationSavingId = ref('')
const learningAssignmentFeedbackSavingId = ref('')
const learningAssignmentFeedbackAcknowledgingId = ref('')
const learningAssignmentSubmissionSavingId = ref('')
const learningAssignmentFeedbackForm = reactive({
  assignmentId: '',
  action: 'COMMENT',
  message: '',
  suggestedDueAt: '',
})
const learningAssignmentSubmissionForm = reactive({
  assignmentId: '',
  content: '',
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
const showQuickLearningGoalForm = ref(false)
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
const learnerProfileDeletingId = ref('')
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
  { value: 'education.evaluate', label: '独立评价课程作业' },
]
const defaultApiKeyPermissions = [
  'run.read', 'run.create', 'run.execute', 'run.approve', 'run.cancel',
  'audit.read', 'context.read', 'context.write', 'context.configure',
  'tool.read', 'workspace.read', 'workspace.manage', 'ops.read',
  'model.configure', 'tenant.policy.read', 'tenant.policy.write',
  'auth.key.read', 'auth.key.manage',
  'education.read', 'education.write',
  'education.assign', 'education.evaluate',
]
// 管理员创建正式登录凭证时优先选择角色模板；权限仍由服务端校验，模板不会绕过 RBAC。
// 模板使用一组稳定的最小权限，避免把“角色”退化成让管理员逐项猜权限名称。
const apiKeyRoleTemplates = [
  {
    value: 'ADMIN',
    label: '管理员',
    description: '系统治理、模型、策略、凭证和审计。',
    permissions: [...defaultApiKeyPermissions],
  },
  {
    value: 'TEACHER',
    label: '教师',
    description: '课程资料、课程作业、学生进度和复核。',
    permissions: [
      'run.read', 'run.create', 'run.execute', 'run.approve', 'run.cancel', 'audit.read',
      'context.read', 'context.write', 'tool.read', 'education.read', 'education.write',
      'education.assign', 'education.evaluate',
    ],
  },
  {
    value: 'STUDENT',
    label: '学生',
    description: '我的课程、学习对话、作业和学习证据。',
    permissions: [
      'run.read', 'run.create', 'run.execute', 'run.cancel', 'context.read',
      'education.read', 'education.write',
    ],
  },
]

function samePermissionSet(left, right) {
  const normalize = (values) => [...new Set((values || []).map((item) => String(item).trim()).filter(Boolean))].sort()
  const a = normalize(left)
  const b = normalize(right)
  return a.length === b.length && a.every((item, index) => item === b[index])
}

const selectedApiKeyRoleTemplate = computed(() => {
  const match = apiKeyRoleTemplates.find((template) => samePermissionSet(apiKeyForm.permissions, template.permissions))
  return match?.value || 'CUSTOM'
})

function applyApiKeyRoleTemplate(role) {
  const template = apiKeyRoleTemplates.find((item) => item.value === role)
  if (!template) return
  apiKeyForm.permissions = [...template.permissions]
}

function apiKeyRoleFromPermissions(permissions) {
  const values = permissions || []
  if (values.some((permission) => ['auth.key.manage', 'tenant.policy.write', 'model.configure', 'context.configure', 'ops.read'].includes(permission))) {
    return '管理员'
  }
  if (values.includes('education.assign') || values.includes('education.evaluate')) return '教师'
  if (values.includes('education.read') || values.includes('education.write')) return '学生'
  return '自定义'
}
// 工作区状态用于告知用户 Agent 是否直接连接到本地项目；接口不会返回绝对路径。
const workspace = ref(null)
// 当前身份用于决定工作台入口；正式 API Key/OIDC 模式由 Runtime 返回，local 模式可切换演示角色。
const currentUser = ref(null)
const demoRole = ref(readStoredValue('harnessDemoRole', import.meta.env.VITE_HARNESS_DEMO_ROLE || 'STUDENT'))
const demoRoleSwitching = ref(false)
const identityLoading = ref(true)
const localDemoLoginBusy = ref(false)
const localDemoLoginError = ref('')
const localDemoSessionActive = ref(readStoredValue('harnessLocalSession', '') === 'active')
const demoRoleUserIds = {
  ADMIN: 'admin-demo',
  TEACHER: 'teacher-demo',
  STUDENT: 'student-demo',
}
const localDemoUsers = [
  {
    role: 'ADMIN',
    userId: 'admin-demo',
    name: '管理员演示账号',
    detail: '管理模型、知识库、权限、策略和审计。',
  },
  {
    role: 'TEACHER',
    userId: 'teacher-demo',
    name: '老师演示账号',
    detail: '配置课程资料、发布课程、布置作业和复核反馈。',
  },
  {
    role: 'STUDENT',
    userId: 'student-demo',
    name: '学生演示账号',
    detail: '建立学习档案、加入课程、完成任务和查看反馈。',
  },
]
const currentPrimaryRole = computed(() => String(
  currentUser.value?.primaryRole || demoRole.value || 'STUDENT',
).toUpperCase())
const currentRoles = computed(() => new Set(
  (currentUser.value?.roles || [currentPrimaryRole.value]).map((role) => String(role).toUpperCase()),
))
const isAdminRole = computed(() => currentRoles.value.has('ADMIN'))
const isTeacherRole = computed(() => currentRoles.value.has('TEACHER'))
const isStudentRole = computed(() => currentRoles.value.has('STUDENT'))
const isTeacherOnlyRole = computed(() => isTeacherRole.value && !isAdminRole.value)
// 多权限身份仍以主工作台为准：管理员和教师不应因为同时拥有
// education.read/run.create 权限而被渲染成学生自助工作台。
const isLearnerOnlyRole = computed(() => isStudentRole.value && !isAdminRole.value && !isTeacherRole.value)
const isAdminWorkspace = computed(() => currentPrimaryRole.value === 'ADMIN')
const isTeacherWorkspace = computed(() => currentPrimaryRole.value === 'TEACHER')
const isLearnerWorkspace = computed(() => currentPrimaryRole.value === 'STUDENT')
const canManageEducationOperations = computed(() => isTeacherWorkspace.value)
const canViewRuntimeConsole = computed(() => isAdminRole.value)
const canViewEducationConsole = computed(() => isAdminRole.value || isTeacherRole.value || isStudentRole.value)
const roleWorkspaceTitle = computed(() => ({
  ADMIN: '管理员治理中心',
  TEACHER: '教师课程工作台',
  STUDENT: '学生学习工作台',
}[currentPrimaryRole.value] || '工作台'))
const roleWorkspaceDetail = computed(() => ({
  ADMIN: '管理用户权限、模型连接、知识库索引、组织策略和审计记录。',
  TEACHER: '配置课程资料，维护课程边界，布置作业并跟进学生进度。',
  STUDENT: '查看我的课程、学习任务和反馈，按下一步行动完成学习。',
}[currentPrimaryRole.value] || '选择一个入口开始使用系统。'))
const roleWorkspaceSteps = computed(() => ({
  ADMIN: [
    { title: '确认系统状态', detail: '检查模型、向量库和基础设施是否正常。' },
    { title: '配置组织能力', detail: '维护模型、知识库索引、策略和 API Key。' },
    { title: '核对审计记录', detail: '追踪高风险 Run、审批和关键配置变更。' },
  ],
  TEACHER: [
    { title: '上传课程资料', detail: '导入 PDF/DOCX，建立可检索的知识文档。' },
    { title: '补充课程元数据', detail: '填写学科、年级、版本、章节、知识点和难度。' },
    { title: '开课并布置作业', detail: '加入学生、发布作业，再根据证据确认或退回。' },
  ],
  STUDENT: [
    { title: '建立学习档案', detail: '填写学科、年级和课程版本，绑定你的学习目标。' },
    { title: '加入一门课程', detail: '在“我的课程”中查看教师发布的课程和作业。' },
    { title: '完成下一步行动', detail: '进入学习对话，提交作业并查看反馈与掌握度。' },
  ],
}[currentPrimaryRole.value] || []))
const adminNextAction = computed(() => {
  if (!health.value) {
    return { kind: 'health', label: '检查系统状态', detail: '先确认数据库、队列、模型和 Worker 是否正常。', section: 'runtime' }
  }
  if (health.value.error || !infraOnline.value) {
    return { kind: 'health', label: '检查基础设施', detail: '基础设施存在异常，先确认 Runtime 依赖状态。', section: 'runtime' }
  }
  if (stats.waitingApproval > 0) {
    return { kind: 'run-filter', status: 'WAITING_APPROVAL', label: '处理待审批 Run', detail: `${stats.waitingApproval} 个高风险 Run 等待人工确认。`, section: 'runtime' }
  }
  if (Number(health.value?.runtime?.timedOutRunCount || 0) > 0) {
    return { kind: 'run-filter', status: 'TIMED_OUT', label: '查看超时 Run', detail: `${health.value.runtime.timedOutRunCount} 个 Run 已超时，需要确认是否重试或调整配置。`, section: 'runtime' }
  }
  if (stats.failed > 0) {
    return { kind: 'run-filter', status: 'FAILED', label: '查看失败 Run', detail: `${stats.failed} 个 Run 执行失败，先检查错误与可恢复动作。`, section: 'runtime' }
  }
  if (contextConfiguration.value && contextConfiguration.value.embeddingReady === false) {
    return { kind: 'embedding', label: '检查向量连接', detail: '向量服务尚未就绪，课程资料可能只能使用降级检索。', section: 'runtime' }
  }
  return { kind: 'education', label: '查看教育概览', detail: '系统运行正常，可以查看课程、作业和学习证据规模。', section: 'education' }
})
const roleQuickStartAction = computed(() => {
  if (currentPrimaryRole.value === 'TEACHER') {
    return { label: teacherNextAction.value.label, detail: teacherNextAction.value.detail, section: 'education' }
  }
  if (currentPrimaryRole.value === 'STUDENT') {
    return studentQuickStartAction.value
  }
  return adminNextAction.value
})

async function runRoleQuickStartAction() {
  const action = roleQuickStartAction.value
  if (currentPrimaryRole.value === 'TEACHER') {
    runTeacherNextAction()
    return
  }
  if (currentPrimaryRole.value === 'STUDENT') {
    if (action.kind === 'assignment') {
      await takeLearnerCourseNextAction()
      return
    }
    if (action.kind === 'profile') {
      openEducationAgentSetup()
      return
    }
    if (action.kind === 'goal') {
      chatMode.value = false
      navigateConsoleSection('education')
      showQuickLearningGoalForm.value = true
      return
    }
    if (action.kind === 'setup') {
      openEducationAgentSetup()
      return
    }
  }
  if (currentPrimaryRole.value === 'ADMIN') {
    if (action.kind === 'run-filter') {
      chatMode.value = false
      runStatusFilter.value = action.status
      runPage.page = 0
      await changeRunStatusFilter()
      return
    }
    if (action.kind === 'embedding') {
      showEmbeddingSettings.value = true
      return
    }
  }
  navigateConsoleSection(action.section)
}
const showLocalDemoLogin = computed(() => Boolean(
  !identityLoading.value
  && currentUser.value?.localDemo
  && !localDemoSessionActive.value,
))
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
const chatAssignmentSubmissionInputRef = ref(null)
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
// 教学依据是需要时展开的解释层；默认把可用宽度留给课程、学习目标和对话本身。
const showLearningTrace = ref(false)
// 学习概览默认折叠，避免课程契约和决策板挤占连续对话；用户的选择会保存在当前浏览器中。
const LEARNING_OVERVIEW_COLLAPSED_STORAGE_KEY = 'mingHarnessLearningOverviewCollapsed'
const learningOverviewCollapsed = ref(readLearningOverviewCollapsed())
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
    id: 'map-lesson',
    label: '梳理本章知识点',
    description: '从课程来源提炼概念、前置知识和易错点。',
    prompt: '请基于当前课程知识库，梳理本章的核心知识点、前置知识和常见误区，并给我一份适合当前学习阶段的学习路径。',
  },
  {
    id: 'diagnose-mastery',
    label: '诊断我的薄弱点',
    description: '结合掌握度记录，找出下一步最值得补的知识点。',
    prompt: '请结合我的学习者画像和已有掌握度记录，诊断当前最薄弱的知识点，并给出一个可执行的补强建议。',
  },
  {
    id: 'make-review-plan',
    label: '制定复习计划',
    description: '按课程约束和目标掌握度安排复习节奏。',
    prompt: '请围绕当前学习目标制定一份分阶段复习计划，包含每阶段目标、练习方式、检查点和预计完成条件。',
  },
  {
    id: 'start-practice',
    label: '开始分层练习',
    description: '从当前掌握度出发，生成一道带提示的练习题。',
    prompt: '请根据我的当前掌握度和课程版本，出一道难度合适的练习题。先不要直接给答案，按需要提供分层提示，并在我作答后帮我复盘。',
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

function roleLabel(role) {
  return {
    ADMIN: '管理员',
    TEACHER: '老师',
    STUDENT: '学生',
  }[String(role || '').toUpperCase()] || '未分配角色'
}

function resetRoleNavigation() {
  if (typeof window === 'undefined') return
  // 角色切换不是普通页面导航：清除上一角色留下的 hash，避免管理员继承
  // 学生/教师的教育入口，或学生继承管理员的运行追踪位置。
  if (window.location.hash) {
    window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`)
  }
}

async function loadCurrentUser() {
  identityLoading.value = true
  localDemoLoginError.value = ''
  try {
    currentUser.value = await api.currentUser()
    if (currentUser.value?.localDemo && currentUser.value.primaryRole) {
      demoRole.value = currentUser.value.primaryRole
    }
    // 管理员的第一入口始终是治理中心；不能因为上一角色残留的 hash
    // 或聊天状态，让管理员误进入学生式学习对话。
    if (currentUser.value?.primaryRole === 'ADMIN') {
      chatMode.value = false
      showGovernance.value = false
      activeConsoleSection.value = 'runtime'
    } else if (currentUser.value?.primaryRole !== 'ADMIN') {
      chatMode.value = false
      showGovernance.value = true
      activeConsoleSection.value = 'education'
    }
  } catch (error) {
    // 身份摘要失败不阻断已有本地演示能力；具体接口仍会返回真实权限错误。
    currentUser.value = null
  } finally {
    identityLoading.value = false
  }
}

function beginLocalDemoSession(user) {
  if (!user || localDemoLoginBusy.value) return
  localDemoLoginBusy.value = true
  localDemoLoginError.value = ''
  try {
    localStorage.setItem('harnessDemoRole', user.role)
    localStorage.setItem('harnessUserId', user.userId)
    localStorage.setItem('harnessLocalSession', 'active')
    resetRoleNavigation()
    demoRole.value = user.role
    localDemoSessionActive.value = true
    window.location.reload()
  } catch (error) {
    localDemoLoginError.value = '无法保存本地演示登录状态，请检查浏览器存储权限。'
  } finally {
    localDemoLoginBusy.value = false
  }
}

function endLocalDemoSession() {
  if (localDemoLoginBusy.value) return
  localDemoLoginBusy.value = true
  try {
    localStorage.removeItem('harnessLocalSession')
    resetRoleNavigation()
    localDemoSessionActive.value = false
    window.location.reload()
  } finally {
    localDemoLoginBusy.value = false
  }
}

function switchDemoRole(nextRole) {
  const normalized = String(nextRole || '').toUpperCase()
  if (!currentUser.value?.localDemo || !demoRoleUserIds[normalized] || demoRoleSwitching.value) return
  demoRoleSwitching.value = true
  try {
    localStorage.setItem('harnessDemoRole', normalized)
    localStorage.setItem('harnessUserId', demoRoleUserIds[normalized])
    localStorage.setItem('harnessLocalSession', 'active')
    resetRoleNavigation()
    form.userId = demoRoleUserIds[normalized]
    window.location.reload()
  } finally {
    demoRoleSwitching.value = false
  }
}

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  applyTheme(theme.value)
}

function readLearningOverviewCollapsed() {
  if (typeof window === 'undefined') return true
  try {
    const stored = window.localStorage.getItem(LEARNING_OVERVIEW_COLLAPSED_STORAGE_KEY)
    return stored == null ? true : stored === 'true'
  } catch {
    return true
  }
}

function toggleLearningOverview() {
  learningOverviewCollapsed.value = !learningOverviewCollapsed.value
  try {
    window.localStorage.setItem(LEARNING_OVERVIEW_COLLAPSED_STORAGE_KEY, String(learningOverviewCollapsed.value))
  } catch {
    // 浏览器禁用本地存储时仍允许当前页面内折叠和展开。
  }
}

async function runLearningOverviewNextAction() {
  const action = learningOverviewNextAction.value
  if (action.kind === 'setup') {
    openEducationAgentSetup()
    return
  }
  if (action.kind === 'task') {
    await startLearningTask(activeLearningTask.value)
    return
  }
  if (action.kind === 'task-scheduled') {
    focusLearningTask(activeLearningTask.value)
    return
  }
  if (action.kind === 'assignment') {
    await takeLearnerCourseNextAction()
    return
  }
  if (action.kind === 'recommendation') {
    await useLearningRecommendation()
    return
  }
  if (action.kind === 'focus') {
    chatInputRef.value?.focus()
    return
  }
  learningOverviewCollapsed.value = false
  try {
    window.localStorage.setItem(LEARNING_OVERVIEW_COLLAPSED_STORAGE_KEY, 'false')
  } catch {
    // 浏览器禁用本地存储时，当前页面仍会展开学习计划。
  }
  await nextTick()
  if (action.kind === 'expand') {
    document.querySelector('#learning-overview-content')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
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

// 教育工作台的内容位于独立的 .main-content 滚动容器内。直接调用
// Element.scrollIntoView() 会把外层窗口也纳入滚动链，在 Electron 的原生
// 标题栏下方造成页面顶部被遮住的偏移；这里只调整工作台自己的滚动位置。
function scrollConsoleTargetIntoView(target, behavior = 'smooth') {
  if (typeof document === 'undefined' || !(target instanceof HTMLElement)) return
  const container = document.querySelector('.console-layout .main-content')
  if (!(container instanceof HTMLElement)) return
  const containerRect = container.getBoundingClientRect()
  const targetRect = target.getBoundingClientRect()
  const centeredOffset = (container.clientHeight - targetRect.height) / 2
  const top = container.scrollTop + targetRect.top - containerRect.top - Math.max(24, centeredOffset)
  container.scrollTo({ top: Math.max(0, top), behavior })
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

/** 课程知识库资料仍由 Runtime 统一上传、解析和索引；教育工作台只负责把入口
 * 放在当前课程语境里，避免教师在“课程约束”和“高级治理”之间来回寻找。 */
function openEducationDocumentUpload() {
  chatMode.value = false
  showGovernance.value = true
  setActiveConsoleSection('runtime')
  if (window.location.hash !== '#runtime') {
    window.history.pushState({ consoleSection: 'runtime' }, '', '#runtime')
  }
  scrollToConsoleSection('runtime', 'auto')
  void nextTick(() => scrollConsoleTargetIntoView(document.getElementById('education-document-upload')))
}

/**
 * 把 Agent 的阻断原因直接映射到可执行的配置入口，避免用户在聊天页和
 * 教育工作台之间来回寻找。课程资料和画像仍由后端做最终权限校验。
 */
function openEducationAgentSetup() {
  if (educationWorkspaceMode.value === 'teacher' && !manageableEducationDocuments.value.length) {
    openEducationDocumentUpload()
    return
  }
  chatMode.value = false
  navigateConsoleSection('education')
  void nextTick(() => {
    const selector = educationWorkspaceMode.value === 'learner'
      ? (activeLearnerProfile.value ? '.education-knowledge-base-bridge, .education-agent-state-card' : '.education-profile-setup')
      : '.education-source-editor'
    const target = document.querySelector(selector)
    if (target instanceof HTMLDetailsElement) target.open = true
    scrollConsoleTargetIntoView(target)
  })
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
    courseId: '',
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
  'education.evaluate': { label: '独立评价作业', description: '作为第二评分者提交独立量规评价' },
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
const teacherEducationCourses = computed(() => educationCourses.value
  .filter((course) => course.ownerUserId === form.userId))
const enrolledEducationCourses = computed(() => educationCourses.value
  .filter((course) => course.ownerUserId !== form.userId))
const learnerLearningAssignmentCount = computed(() => learningAssignments.value
  .filter((assignment) => assignment.learnerUserId === form.userId).length)
const hasTeacherEducationContext = computed(() => teacherEducationCourses.value.length > 0
  || learningEvaluationQueue.value.length > 0)
const hasLearnerEducationContext = computed(() => enrolledEducationCourses.value.length > 0
  || learnerLearningAssignmentCount.value > 0
  || learnerProfiles.value.length > 0
  || learningGoals.value.length > 0)
const educationWorkspaceMode = computed(() => {
  // 角色决定工作台的默认心智模型；是否已经有课程只决定空状态提示，
  // 不能让一位刚加入系统的老师被误导成“学习者模式”。
  if (isAdminWorkspace.value) return 'admin'
  if (isTeacherWorkspace.value) return 'teacher'
  if (isLearnerWorkspace.value) return 'learner'
  if (hasTeacherEducationContext.value) return 'teacher'
  if (hasLearnerEducationContext.value) return 'learner'
  return 'setup'
})
const educationWorkspaceModeLabel = computed(() => {
  if (educationWorkspaceMode.value === 'admin') return '教育概览'
  if (educationWorkspaceMode.value === 'teacher') return '课程负责人模式'
  if (educationWorkspaceMode.value === 'learner') return '学习者模式'
  return '待建立学习上下文'
})
const educationWorkspaceModeDetail = computed(() => {
  if (educationWorkspaceMode.value === 'admin') {
    return '查看课程资料、课程实例、作业和学习证据的整体状态；具体课程运营由教师负责。'
  }
  if (educationWorkspaceMode.value === 'teacher') {
    return '先查看班级状态和干预信号，再进入名单、布置和复核操作。'
  }
  if (educationWorkspaceMode.value === 'learner') {
    return '先看课程边界、当前状态和下一步行动；教师管理入口按需展开。'
  }
  return '先建立学习者画像或加入课程，Agent 才能把知识检索和学习证据串起来。'
})
const educationAssignmentsForView = computed(() => ['admin', 'teacher'].includes(educationWorkspaceMode.value)
  ? learningAssignments.value
  : learningAssignments.value.filter((assignment) => assignment.learnerUserId === form.userId))
const ownedKnowledgeDocuments = computed(() => documents.value
  .filter((document) => document.ownerUserId === form.userId))
const ownedEducationSources = computed(() => educationSources.value
  .filter((source) => ownedKnowledgeDocuments.value.some(
    (document) => document.id === source.documentId,
  )))
const manageableEducationDocuments = computed(() => isTeacherOnlyRole.value
  ? documents.value
  : ownedKnowledgeDocuments.value)
const manageableEducationSources = computed(() => educationSources.value
  .filter((source) => manageableEducationDocuments.value.some(
    (document) => document.id === source.documentId,
  )))
const teacherActiveLearnerCount = computed(() => teacherEducationCourses.value
  .reduce((total, course) => total + Number(course.activeEnrollmentCount || 0), 0))
const teacherAssignmentCount = computed(() => learningAssignments.value
  .filter((assignment) => assignment.teacherUserId === form.userId
    || teacherEducationCourses.value.some((course) => course.id === assignment.courseId)).length)
const teacherCoursePendingCount = computed(() => {
  const metrics = educationMetrics.value || {}
  return Number(metrics.assignmentReviewPending || 0)
    + Number(metrics.assignmentReviewRevisionRequired || 0)
    + Number(metrics.assignmentRetryRequired || 0)
    + Number(metrics.taskAwaitingEvidence || 0)
    + learningEvaluationQueue.value.length
})
// 待办总数用于概览，但教师的主按钮必须指向真实队列；否则“待重试”会被
// 错误地带到“待确认”筛选，造成用户以为系统没有可处理的作业。
const teacherPendingAction = computed(() => {
  const metrics = educationMetrics.value || {}
  if (Number(metrics.assignmentReviewPending || 0)) {
    return {
      label: '完成教师确认',
      detail: `${metrics.assignmentReviewPending} 份作业等待依据提交物和测评证据确认。`,
      kind: 'review',
      issue: 'review',
    }
  }
  if (Number(metrics.assignmentReviewRevisionRequired || 0)) {
    return {
      label: '跟进返工作业',
      detail: `${metrics.assignmentReviewRevisionRequired} 份作业已退回，需要学习者重新提交。`,
      kind: 'review',
      issue: 'revision',
    }
  }
  if (Number(metrics.assignmentRetryRequired || 0)) {
    return {
      label: '安排失败作业重试',
      detail: `${metrics.assignmentRetryRequired} 份作业因失败、超时或取消等待重新执行。`,
      kind: 'review',
      issue: 'retry',
    }
  }
  if (Number(metrics.taskAwaitingEvidence || 0)) {
    return {
      label: '补回形成性证据',
      detail: `${metrics.taskAwaitingEvidence} 个学习任务已结束，但还缺少可验证测评证据。`,
      kind: 'review',
      issue: 'evidence',
    }
  }
  if (learningEvaluationQueue.value.length) {
    return {
      label: '处理独立评价',
      detail: `${learningEvaluationQueue.value.length} 份已完成作业等待第二评分者评价。`,
      kind: 'evaluation',
      issue: '',
    }
  }
  return null
})
const teacherAgentReady = computed(() => manageableEducationSources.value.length > 0)
const teacherNextAction = computed(() => {
  if (!manageableEducationDocuments.value.length) {
    return { label: '上传课程资料', detail: '先导入 PDF/DOCX，建立课程知识库。', kind: 'upload' }
  }
  if (!manageableEducationSources.value.length) {
    return { label: '维护课程元数据', detail: '为资料补充学科、版本、章节、知识点和难度。', kind: 'metadata' }
  }
  if (!teacherEducationCourses.value.length) {
    return { label: '创建课程实例', detail: '把课程资料绑定到一门可运营的课程。', kind: 'course' }
  }
  if (!teacherActiveLearnerCount.value) {
    return { label: '加入学生', detail: '先维护活跃名单，课程作业才有接收人。', kind: 'roster' }
  }
  if (!teacherAssignmentCount.value) {
    return { label: '布置第一份作业', detail: '把课程约束和目标知识点下发给学生。', kind: 'assignment' }
  }
  if (teacherPendingAction.value) {
    return teacherPendingAction.value
  }
  return { label: '查看课程进度', detail: '课程状态正常，可继续跟进学生掌握度。', kind: 'progress' }
})
function runTeacherNextAction() {
  const action = teacherNextAction.value
  if (action.kind === 'upload') {
    openEducationDocumentUpload()
    return
  }
  if (action.kind === 'metadata') {
    openEducationAgentSetup()
    return
  }
  navigateConsoleSection('education')
  void nextTick(() => {
    if (action.kind === 'roster') focusEducationCourseRoster()
    else if (action.kind === 'assignment') focusEducationCourseAssignment()
    else if (action.kind === 'review') focusCourseBlocker(action.issue)
    else if (action.kind === 'evaluation') document.querySelector('[aria-label="独立评价队列"]')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    else if (action.kind === 'progress') document.querySelector('.education-course-progress')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    else document.querySelector('.education-course-workbench')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

/**
 * 聊天侧栏的角色入口必须落到对应业务模块，而不是都只回到教育页顶部。
 * 入口只负责定位，不改变当前角色权限；真正的读写校验仍由 Runtime 处理。
 */
function openRoleWorkspaceEntry(entry) {
  chatMode.value = false
  navigateConsoleSection('education')
  void nextTick(() => {
    if (entry === 'teacher-course') {
      document.querySelector('.education-course-workbench')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      return
    }
    if (entry === 'teacher-roster') {
      focusEducationCourseRoster()
      return
    }
    if (entry === 'teacher-review') {
      if (teacherPendingAction.value?.issue) {
        focusCourseBlocker(teacherPendingAction.value.issue)
      } else {
        document.querySelector('#learning-assignment-list')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
      return
    }
    if (entry === 'student-plan') {
      document.querySelector('[aria-label="教育 Agent 当前状态"]')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      return
    }
    if (entry === 'student-profile') {
      const profile = document.querySelector('.education-profile-setup')
      if (profile instanceof HTMLDetailsElement) profile.open = true
      profile?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      return
    }
    if (entry === 'student-evidence') {
      document.querySelector('#learning-assignment-list, .learning-goal-workbench')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  })
}
const activeChatCourse = computed(() => educationCourses.value
  .find((course) => course.id === chatEducation.courseId) || null)
const availableChatCourses = computed(() => {
  const profile = activeLearnerProfile.value
  if (!profile) return []
  return educationCourses.value.filter((course) => course.status === 'ACTIVE'
    && course.ownerUserId !== form.userId
    && course.subject === profile.subject
    && course.gradeLevel === profile.gradeLevel
    && course.curriculumVersion === profile.curriculumVersion)
})
// 管理员工作台始终是治理只读视角。即使旧数据里管理员曾经创建过课程，
// 也不能因为“课程所有者”字段让管理员重新看到教师运营写入口；课程运营由教师角色负责。
const activeEducationCourseIsOwner = computed(() => Boolean(
  !isAdminRole.value
    && activeEducationCourse.value
    && activeEducationCourse.value.ownerUserId === form.userId,
))
const activeEducationCourseLearnerResult = computed(() => {
  const course = activeEducationCourse.value
  if (!course || course.ownerUserId === form.userId) return null
  return educationCourseResult.value?.learners
    ?.find((learner) => learner.learnerUserId === form.userId) || null
})

function learningAssignmentNextAction(assignment) {
  if (!assignment) return { label: '查看作业', detail: '', issue: '', actionable: false }
  const openFeedback = learningAssignmentOpenFeedback(assignment)
  if (['REQUEST_EVIDENCE', 'RECOMMEND_RETRY'].includes(openFeedback?.action)) {
    return {
      label: learningAssignmentFeedbackContinueLabel(openFeedback),
      detail: openFeedback.action === 'REQUEST_EVIDENCE'
        ? '教师要求补充可验证证据。确认反馈后会直接启动下一轮。'
        : '教师建议重新学习。确认反馈后会直接启动下一轮。',
      issue: 'intervention',
      actionable: true,
    }
  }
  if (assignment.status === 'ASSIGNED') {
    return { label: '接受并开始', detail: '按课程约束启动第一轮学习。', issue: 'assigned', actionable: true }
  }
  if (assignment.status === 'AWAITING_EVIDENCE') {
    return { label: '补充证据并继续', detail: '上一轮已结束，但还缺少测评证据。', issue: 'evidence', actionable: true }
  }
  if (assignment.status === 'RETRY_REQUIRED') {
    return assignment.reviewStatus === 'REVISION_REQUIRED'
      ? { label: '按要求返工', detail: '教师已退回作业，请按说明重新完成。', issue: 'revision', actionable: true }
      : { label: '重试课程作业', detail: '上一轮未完成，先重新启动课程作业。', issue: 'retry', actionable: true }
  }
  if (assignment.status === 'OVERDUE') {
    return { label: '查看逾期作业', detail: '作业已逾期，需提交已有成果或联系教师重新安排。', issue: 'overdue', actionable: true }
  }
  if (assignment.status === 'COMPLETED' && assignment.reviewStatus === 'PENDING') {
    const hasSubmission = (learningAssignmentSubmissionMap.value[assignment.id] || []).length > 0
    return hasSubmission
      ? { label: '等待教师确认', detail: '提交物已收到，当前等待教师依据证据完成确认。', issue: 'review', actionable: false }
      : { label: '补齐提交物', detail: '学习目标已达到完成条件，但还缺少可追溯作答。', issue: 'submission', actionable: true }
  }
  if (assignment.status === 'ACCEPTED') {
    return { label: '继续课程作业', detail: '继续当前学习对话，完成后再提交作业内容。', issue: 'accepted', actionable: true }
  }
  return { label: '查看完整记录', detail: '当前没有需要立即处理的动作。', issue: '', actionable: false }
}

const activeEducationCourseLearnerProgress = computed(() => {
  const course = activeEducationCourse.value
  if (!course || course.ownerUserId === form.userId) return null
  const assignments = learningAssignments.value
    .filter((assignment) => assignment.courseId === course.id
      && assignment.learnerUserId === form.userId
      && assignment.status !== 'CANCELLED')
  if (!assignments.length) return {
    total: 0, completed: 0, attention: 0, averageMasteryProgress: null, nextAction: null,
  }
  const progressValues = assignments
    .map((assignment) => Number(learningAssignmentProgressMap.value[assignment.id]?.masteryProgress))
    .filter((value) => Number.isFinite(value))
  const nextAssignment = assignments
    .map((assignment) => ({ assignment, action: learningAssignmentNextAction(assignment) }))
    .find(({ action }) => action.actionable)
  return {
    total: assignments.length,
    completed: assignments.filter((assignment) => assignment.status === 'COMPLETED').length,
    attention: assignments.filter((assignment) => ['ASSIGNED', 'AWAITING_EVIDENCE', 'RETRY_REQUIRED', 'OVERDUE'].includes(assignment.status)
      || ['PENDING', 'REVISION_REQUIRED'].includes(assignment.reviewStatus)
      || learningAssignmentHasOpenIntervention(assignment)).length,
    averageMasteryProgress: progressValues.length
      ? progressValues.reduce((sum, value) => sum + value, 0) / progressValues.length
      : null,
    nextAction: nextAssignment
      ? { ...nextAssignment.action, assignmentId: nextAssignment.assignment.id }
      : { label: '查看我的作业', detail: '当前没有需要立即处理的作业。', issue: '' },
  }
})
function canEditEducationSource(source) {
  return Boolean(source && manageableEducationDocuments.value.some(
    (document) => document.id === source.documentId,
  ))
}
const visibleLearningAssignments = computed(() => {
  let entries = educationAssignmentsForView.value
  if (learningAssignmentCourseFilter.value) {
    entries = entries.filter((assignment) => assignment.courseId === learningAssignmentCourseFilter.value)
  }
  if (learningAssignmentLearnerFilter.value) {
    entries = entries.filter((assignment) => assignment.learnerUserId === learningAssignmentLearnerFilter.value)
  }
  if (learningAssignmentIssueFilter.value) {
    entries = entries.filter((assignment) => learningAssignmentMatchesIssue(assignment, learningAssignmentIssueFilter.value))
  }
  return entries.slice(0, 8)
})

function learningAssignmentDetailsLoaded(assignmentId) {
  if (!assignmentId) return false
  return [
    learningAssignmentProgressMap.value,
    learningAssignmentEvidenceMap.value,
    learningAssignmentSubmissionMap.value,
    learningAssignmentFeedbackMap.value,
    learningAssignmentEvaluationMap.value,
  ].every((map) => Object.prototype.hasOwnProperty.call(map, assignmentId))
}
const learningAssignmentIssueLabel = computed(() => ({
  completion: '结课待处理',
  assigned: '待接受',
  accepted: '学习中',
  evidence: '待补证据',
  retry: '待重试',
  review: '待教师确认',
  revision: '待返工',
  submission: '缺提交物',
  submitted: '查看提交物',
  intervention: '开放干预',
  overdue: '已逾期',
}[learningAssignmentIssueFilter.value] || '问题作业'))
const learnerCourseAssignments = computed(() => learningAssignments.value
  .filter((assignment) => assignment.learnerUserId === form.userId && assignment.status !== 'CANCELLED')
  .sort((left, right) => {
    const leftPriority = ['ASSIGNED', 'RETRY_REQUIRED', 'AWAITING_EVIDENCE', 'OVERDUE', 'ACCEPTED', 'COMPLETED'].indexOf(left.status)
    const rightPriority = ['ASSIGNED', 'RETRY_REQUIRED', 'AWAITING_EVIDENCE', 'OVERDUE', 'ACCEPTED', 'COMPLETED'].indexOf(right.status)
    return (leftPriority < 0 ? 10 : leftPriority) - (rightPriority < 0 ? 10 : rightPriority)
      || new Date(left.dueAt || left.createdAt) - new Date(right.dueAt || right.createdAt)
  }))
const nextLearnerCourseAssignment = computed(() => learnerCourseAssignments.value[0] || null)
// 首页课程作业卡片需要和聊天作业卡片共享同一份“待确认反馈”判断。
// 之前模板引用了未定义的状态，教师反馈虽已写入数据，却不会出现“确认并继续”入口。
const nextLearnerCourseAssignmentOpenFeedback = computed(() => {
  const assignment = nextLearnerCourseAssignment.value
  return learningAssignmentOpenFeedback(assignment)
})
const activeChatLearningAssignment = computed(() => {
  const assignmentId = chatEducation.learningAssignmentId
  return assignmentId
    ? learningAssignments.value.find((assignment) => assignment.id === assignmentId) || null
    : null
})
const chatCourseAssignment = computed(() => activeChatLearningAssignment.value || nextLearnerCourseAssignment.value)
const chatCourseAssignmentOpenFeedback = computed(() => {
  return learningAssignmentOpenFeedback(chatCourseAssignment.value)
})
const chatCourseAssignmentLatestSubmission = computed(() => {
  const assignment = chatCourseAssignment.value
  if (!assignment) return null
  return (learningAssignmentSubmissionMap.value[assignment.id] || [])[0] || null
})
// 作业状态通知是行动入口；状态已经推进后，旧的通知不应继续显示旧按钮。
// assignmentStatus 让前端在旧 Runtime 尚未完成通知收敛时也能安全过滤，
// 反馈和提交物属于证据历史，即使已读也保留，方便回看教师与学习者之间的闭环。
function learningAssignmentNotificationIsCurrent(notification) {
  if (!notification) return false
  if (['FEEDBACK', 'FEEDBACK_ACKNOWLEDGED', 'SUBMISSION_RECEIVED'].includes(notification.notificationType)) {
    return true
  }
  // 新 Runtime 会直接在通知中返回 assignmentStatus；旧 Runtime 没有这个字段时，
  // 仍然可以从已经加载的作业列表拿到最新状态，避免旧的“接受并开始”入口残留。
  const assignment = notification.learningAssignmentId
    ? learningAssignments.value.find((item) => item.id === notification.learningAssignmentId)
    : null
  const status = notification.assignmentStatus || assignment?.status
  const currentStatusByType = {
    ASSIGNED: 'ASSIGNED',
    ACCEPTED: 'ACCEPTED',
    EVIDENCE_REQUIRED: 'AWAITING_EVIDENCE',
    RETRY_REQUIRED: 'RETRY_REQUIRED',
    REVISION_REQUIRED: 'RETRY_REQUIRED',
    OVERDUE: 'OVERDUE',
    REVIEW_REQUIRED: 'COMPLETED',
    REVIEW_VERIFIED: 'COMPLETED',
    COMPLETED: 'COMPLETED',
    CANCELLED: 'CANCELLED',
  }[notification.notificationType]
  return !currentStatusByType || !status || status === currentStatusByType
}

const learningAssignmentNotificationsForView = computed(() => learningAssignmentNotifications.value
  .filter((notification) => learningAssignmentNotificationIsCurrent(notification)
    && (notification.unread
      || ['FEEDBACK', 'FEEDBACK_ACKNOWLEDGED', 'SUBMISSION_RECEIVED'].includes(notification.notificationType))))
const learningAssignmentNotificationUnreadCountForView = computed(() =>
  learningAssignmentNotificationsForView.value.filter((notification) => notification.unread).length)

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
const activeLearningRecommendation = computed(() => {
  const goalId = activeLearningGoal.value?.id
  if (!goalId) return null
  return learningGoalRecommendationMap.value[goalId]
    || (learningRecommendation.value?.learningGoalId === goalId ? learningRecommendation.value : null)
})
const activeLearningProgress = computed(() => {
  const progress = Number(activeLearningRecommendation.value?.progressRatio)
  if (Number.isFinite(progress)) return Math.max(0, Math.min(1, progress))
  const current = Number(activeLearningRecommendation.value?.currentMastery)
  const target = Number(activeLearningRecommendation.value?.targetMastery)
  if (Number.isFinite(current) && Number.isFinite(target) && target > 0) {
    return Math.max(0, Math.min(1, current / target))
  }
  return 0
})
const activeLearningTask = computed(() => {
  const goalId = activeLearningGoal.value?.id
  if (!goalId) return null
  const activeStatuses = new Set(['OPEN', 'IN_PROGRESS', 'AWAITING_EVIDENCE', 'DEFERRED', 'FAILED'])
  return learningTasks.value
    .filter((task) => task.learningGoalId === goalId && activeStatuses.has(task.status))
    .sort((left, right) => new Date(left.scheduledAt || left.createdAt) - new Date(right.scheduledAt || right.createdAt))[0] || null
})
// 折叠学习概览时仍保留一条可执行的主路径；按钮复用展开面板使用的任务、
// 推荐和课程资料阻断状态，避免用户看到“下一步”却还要再找一次入口。
const learningOverviewNextAction = computed(() => {
  if (!activeLearnerProfile.value) {
    return { kind: 'expand', label: '建立学习画像', detail: '先告诉 Agent 你正在学习的课程和年级。' }
  }
  if (educationSendBlockReason.value) {
    return { kind: 'setup', label: educationSetupActionLabel.value, detail: educationSendBlockReason.value }
  }
  const assignmentAction = learningAssignmentNextAction(nextLearnerCourseAssignment.value)
  if (assignmentAction.actionable && nextLearnerCourseAssignment.value) {
    return { kind: 'assignment', label: assignmentAction.label, detail: assignmentAction.detail }
  }
  if (!activeLearningGoal.value) {
    return { kind: 'expand', label: '设定学习目标', detail: '设定知识点和达标标准，后续作答才能形成学习证据。' }
  }
  if (activeLearningTask.value) {
    const scheduled = activeLearningTask.value.status === 'DEFERRED'
      && new Date(activeLearningTask.value.scheduledAt).getTime() > Date.now()
    return {
      kind: scheduled ? 'task-scheduled' : 'task',
      label: activeLearningTask.value.status === 'AWAITING_EVIDENCE'
        ? '补充本轮证据'
        : (scheduled ? '查看复习安排' : '开始学习任务'),
      detail: activeLearningTask.value.title,
    }
  }
  if (activeLearningRecommendation.value) {
    return {
      kind: 'recommendation',
      label: activeLearningRecommendation.value.nextActionType === 'WAIT' ? '查看复习安排' : '开始下一步',
      detail: activeLearningRecommendation.value.nextActionTitle,
    }
  }
  return { kind: 'focus', label: '进入本轮作答', detail: agentTeachingAction.value.title }
})
const pedagogicalModeLabel = computed(() => ({
  AUTO: '自动选择（基于学习状态）',
  EXPLAIN: '概念讲解',
  SOCRATIC: '启发式引导',
  PRACTICE: '练习优先',
  DIAGNOSE: '错误诊断',
}[chatEducation.pedagogicalMode] || '自动选择（基于学习状态）'))

// 这里与服务端 EducationRetrievalFilter 保持同一组比较规则。课程版本下有资料，
// 不代表当前 Run 一定能使用：知识点标签与难度范围仍会继续收紧证据范围。
function normalizeEducationFilterValue(value) {
  const normalized = String(value ?? '').trim()
  return normalized ? normalized.toLocaleLowerCase('en-US') : ''
}

function normalizeEducationDifficulty(value) {
  if (value === null || value === undefined || String(value).trim() === '') return null
  const parsed = Number(value)
  return Number.isInteger(parsed) ? Math.max(1, Math.min(5, parsed)) : null
}

function sourceHasConcept(source, conceptKey) {
  const expected = normalizeEducationFilterValue(conceptKey)
  if (!expected) return true
  return String(source?.conceptTags || '')
    .split(/[,，;；\n]/)
    .map((item) => normalizeEducationFilterValue(item))
    .some((item) => item === expected)
}

function sourceMatchesEducationScope(source, scope) {
  if (!source || !scope) return false
  return normalizeEducationFilterValue(source.subject) === normalizeEducationFilterValue(scope.subject)
    && normalizeEducationFilterValue(source.gradeLevel) === normalizeEducationFilterValue(scope.gradeLevel)
    && normalizeEducationFilterValue(source.curriculumVersion) === normalizeEducationFilterValue(scope.curriculumVersion)
    && sourceHasConcept(source, scope.conceptKey)
    && (scope.minDifficulty === null || Number(source.difficultyLevel) >= scope.minDifficulty)
    && (scope.maxDifficulty === null || Number(source.difficultyLevel) <= scope.maxDifficulty)
}

function educationSourceLabel(source) {
  const documentTitle = String(source?.documentTitle || '').trim()
  if (documentTitle) return documentTitle
  const chapter = String(source?.chapter || '').trim()
  if (chapter) return chapter
  const firstConcept = String(source?.conceptTags || '')
    .split(/[,，;；\n]/)
    .map((item) => item.trim())
    .find(Boolean)
  if (firstConcept) return firstConcept
  return source?.documentId ? `课程资料 ${source.documentId}` : '课程资料'
}

function educationSourceOwnerLabel(source) {
  const owner = String(source?.documentOwnerUserId || '').trim()
  return owner ? `资料所有者：${owner}` : ''
}

/**
 * 学习目标、复习任务和课程作业都可能从聊天输入区以外的入口启动。
 * 这里复用服务端的课程元数据、知识点和难度比较规则，让所有教学动作在请求前
 * 都能给出同一份“缺少课程资料”的解释，而不是等接口返回错误。
 */
function courseSourceAvailability(scope) {
  if (!scope?.subject || !scope?.gradeLevel || !scope?.curriculumVersion) {
    return {
      sourceCount: 0,
      courseSourceCount: 0,
      conceptSourceCount: 0,
      sameSubjectGradeSourceCount: 0,
      availableCurriculumVersions: [],
    }
  }
  const sameSubjectGradeSources = educationSources.value.filter((source) =>
    normalizeEducationFilterValue(source.subject) === normalizeEducationFilterValue(scope.subject)
      && normalizeEducationFilterValue(source.gradeLevel) === normalizeEducationFilterValue(scope.gradeLevel))
  const courseSources = sameSubjectGradeSources.filter((source) =>
    normalizeEducationFilterValue(source.curriculumVersion)
      === normalizeEducationFilterValue(scope.curriculumVersion))
  return {
    sourceCount: courseSources.filter((source) => sourceMatchesEducationScope(source, scope)).length,
    courseSourceCount: courseSources.length,
    conceptSourceCount: courseSources.filter((source) => sourceHasConcept(source, scope.conceptKey)).length,
    sameSubjectGradeSourceCount: sameSubjectGradeSources.length,
    availableCurriculumVersions: [...new Set(sameSubjectGradeSources
      .map((source) => String(source.curriculumVersion || '').trim())
      .filter(Boolean))],
  }
}

function courseSourceBlockReason(scope) {
  const availability = courseSourceAvailability(scope)
  if (!scope?.subject || !scope?.gradeLevel || !scope?.curriculumVersion) {
    return '学习动作缺少完整课程约束，请先补齐学科、年级和课程版本。'
  }
  if (availability.sourceCount) return ''
  if (!availability.courseSourceCount) {
    if (availability.sameSubjectGradeSourceCount) {
      const versions = availability.availableCurriculumVersions.join('、')
      return `当前课程版本「${scope.curriculumVersion}」没有匹配来源，但同学科「${scope.subject}」${scope.gradeLevel}已有 ${availability.sameSubjectGradeSourceCount} 个来源（${versions}）。请统一学习者画像与课程资料的课程版本。`
    }
    return `课程版本「${scope.curriculumVersion}」还没有可检索的课程资料。请先绑定与当前课程匹配的知识文档。`
  }
  if (scope.conceptKey) {
    if (!availability.conceptSourceCount) {
      return `课程版本「${scope.curriculumVersion}」已有 ${availability.courseSourceCount} 个来源，但没有标注知识点「${scope.conceptKey}」。请补充课程来源的知识点标签，或调整当前学习目标。`
    }
    if (scope.minDifficulty !== null || scope.maxDifficulty !== null) {
      return `知识点「${scope.conceptKey}」已有匹配来源，但没有落在当前难度范围（${formatDifficultyRange(scope)}）。请放宽难度范围，或维护课程来源难度。`
    }
    return `知识点「${scope.conceptKey}」没有匹配的课程资料。请补充对应来源或调整知识点范围。`
  }
  if (scope.minDifficulty !== null || scope.maxDifficulty !== null) {
    return `当前课程版本已有 ${availability.courseSourceCount} 个来源，但没有落在难度范围（${formatDifficultyRange(scope)}）。请放宽难度范围，或维护课程来源难度。`
  }
  return '当前课程约束下没有可检索的课程资料，请先补充匹配的知识文档。'
}

function formatDifficultyRange(scope) {
  if (scope?.minDifficulty !== null && scope?.maxDifficulty !== null) {
    return `${scope.minDifficulty}–${scope.maxDifficulty}`
  }
  if (scope?.minDifficulty !== null) return `≥ ${scope.minDifficulty}`
  if (scope?.maxDifficulty !== null) return `≤ ${scope.maxDifficulty}`
  return '未设置'
}

function learningGoalSourceBlockReason(goalId) {
  if (!goalId) return '学习动作尚未绑定学习目标。'
  const goal = learningGoals.value.find((item) => item.id === goalId)
  if (!goal) return '当前学习目标不可用，请刷新学习状态后重试。'
  const profile = learnerProfiles.value.find((item) => item.id === goal.learnerProfileId)
  if (!profile) return '学习目标缺少对应学习者画像，请先恢复或重新选择学习上下文。'
  return courseSourceBlockReason({
    subject: profile.subject,
    gradeLevel: profile.gradeLevel,
    curriculumVersion: profile.curriculumVersion,
    conceptKey: goal.conceptKey,
    minDifficulty: null,
    maxDifficulty: null,
  })
}

function learningTaskSourceBlockReason(task) {
  return task?.learningGoalId ? learningGoalSourceBlockReason(task.learningGoalId) : '学习任务缺少学习目标。'
}

function learningTaskIsScheduled(task) {
  return task?.status === 'DEFERRED'
    && new Date(task.scheduledAt).getTime() > Date.now()
}

function learningTaskActionLabel(task, starting = false) {
  if (starting) return '启动中…'
  if (task?.status === 'FAILED') return '重试任务'
  if (task?.status === 'AWAITING_EVIDENCE') return '补充证据'
  if (task?.status === 'IN_PROGRESS') return '继续复习'
  if (learningTaskIsScheduled(task)) return '查看复习安排'
  return '开始复习'
}

function learningNotificationBody(notification) {
  if (notification?.taskStatus === 'DEFERRED' && notification.scheduledAt) {
    return `${notification.title || '复习任务'}已延期，将在 ${formatDate(notification.scheduledAt)} 开放；当前无需提前作答。`
  }
  return notification?.body || ''
}

function learningAssignmentSourceBlockReason(assignment) {
  if (!assignment) return '课程作业不可用。'
  return courseSourceBlockReason({
    subject: assignment.subject,
    gradeLevel: assignment.gradeLevel,
    curriculumVersion: assignment.curriculumVersion,
    conceptKey: assignment.conceptKey,
    minDifficulty: null,
    maxDifficulty: null,
  })
}

function notifyEducationActionBlocked(reason) {
  if (!reason) return false
  errorMessage.value = reason
  return true
}

const currentChatLearningGoal = computed(() => {
  const goalId = chatEducation.learningGoalId
  return goalId ? learningGoals.value.find((goal) => goal.id === goalId) || null : null
})
const currentEducationRetrievalScope = computed(() => {
  const profile = activeLearnerProfile.value
  if (!profile) {
    return { configured: false, sourceCount: 0, courseSourceCount: 0, filterSummary: '' }
  }
  const course = activeChatCourse.value
  let minDifficulty = normalizeEducationDifficulty(chatEducation.minDifficulty)
  let maxDifficulty = normalizeEducationDifficulty(chatEducation.maxDifficulty)
  if (minDifficulty !== null && maxDifficulty !== null && minDifficulty > maxDifficulty) {
    const previousMinDifficulty = minDifficulty
    minDifficulty = maxDifficulty
    maxDifficulty = previousMinDifficulty
  }
  const scope = {
    // 服务端同样以课程实例/学习者画像为权威来源。这里不再使用浏览器残留的可编辑
    // 字段，避免界面展示的检索范围与实际 Run 快照不一致。
    subject: course?.subject || profile.subject,
    gradeLevel: course?.gradeLevel || profile.gradeLevel,
    curriculumVersion: course?.curriculumVersion || profile.curriculumVersion,
    conceptKey: currentChatLearningGoal.value?.conceptKey || String(chatEducation.conceptKey || '').trim(),
    minDifficulty,
    maxDifficulty,
  }
  const availability = courseSourceAvailability(scope)
  const filters = []
  if (scope.conceptKey) filters.push(`知识点「${scope.conceptKey}」`)
  if (scope.minDifficulty !== null && scope.maxDifficulty !== null) {
    filters.push(`难度 ${scope.minDifficulty}–${scope.maxDifficulty}`)
  } else if (scope.minDifficulty !== null) {
    filters.push(`难度 ≥ ${scope.minDifficulty}`)
  } else if (scope.maxDifficulty !== null) {
    filters.push(`难度 ≤ ${scope.maxDifficulty}`)
  }
  return {
    configured: Boolean(scope.subject && scope.gradeLevel && scope.curriculumVersion),
    ...scope,
    ...availability,
    filterSummary: filters.length ? filters.join(' · ') : '未附加知识点或难度过滤',
  }
})
const currentEducationSourceCount = computed(() => {
  if (isTeacherRole.value && !activeLearnerProfile.value) return manageableEducationSources.value.length
  return currentEducationRetrievalScope.value.sourceCount
})
// 不只告诉学习者“有几份资料”，还把本轮实际允许 Agent 检索的课程条目摆到
// 决策面板中。这样课程边界是可见、可核对的，而不是隐藏在一次请求的参数里。
const currentEducationSourcePreview = computed(() => {
  const scope = currentEducationRetrievalScope.value
  return educationSources.value
    .filter((source) => sourceMatchesEducationScope(source, scope))
    .slice(0, 3)
})
const currentLearningEvidenceCount = computed(() => {
  if (!activeLearningGoal.value) return 0
  const recommendedCount = Number(activeLearningRecommendation.value?.attemptCount)
  return Number.isFinite(recommendedCount)
    ? recommendedCount
    : learningGoalAssessments.value.length
})
// 学习者状态不能只表现为“已建档多少知识点”。这里把 Agent 能据以教学的结论、
// 证据数量和不确定性拆开呈现；没有可验证作答时明确标为未知，而不伪造掌握度。
const learnerStateDiagnosis = computed(() => {
  if (!activeLearnerProfile.value) {
    return {
      state: 'pending',
      title: '尚未建立学习者状态',
      detail: '先绑定画像，Agent 才能把后续作答写入同一份学习档案。',
      currentMastery: null,
      targetMastery: null,
    }
  }
  if (!activeLearningGoal.value) {
    return {
      state: 'pending',
      title: '尚未定义本轮达标标准',
      detail: '可以先问问题，但没有学习目标时，Agent 无法判断何时达标或安排复习。',
      currentMastery: null,
      targetMastery: null,
    }
  }
  const recommendation = activeLearningRecommendation.value
  const currentMastery = Number(recommendation?.currentMastery)
  const targetMastery = Number(recommendation?.targetMastery ?? activeLearningGoal.value.targetMastery)
  const evidenceCount = currentLearningEvidenceCount.value
  if (Number.isFinite(currentMastery) && Number.isFinite(targetMastery)) {
    const gap = Math.max(0, targetMastery - currentMastery)
    return {
      state: evidenceCount ? (gap > 0.01 ? 'observed' : 'ready') : 'unverified',
      title: !evidenceCount
        ? `待建立基线 · 初始差距 ${formatRate(gap)}`
        : (gap > 0.01 ? `距离目标还差 ${formatRate(gap)}` : '当前证据已达到目标'),
      detail: evidenceCount
        ? `围绕「${activeLearningGoal.value.conceptKey}」已有 ${evidenceCount} 次形成性证据；Agent 会按此状态调整难度与动作。`
        : '尚无形成性证据；当前数值只作为初始状态，下一轮需要用作答或评分验证。',
      currentMastery,
      targetMastery,
    }
  }
  if (learnerMasteryPreview.value.length) {
    return {
      state: 'observed',
      title: `优先诊断：${learnerMasteryPreview.value[0].conceptKey}`,
      detail: `已发现 ${learnerMasteryPreview.value.length} 个待补强知识点；先完成本轮目标的作答，才会写入新的掌握度。`,
      currentMastery: null,
      targetMastery: Number(activeLearningGoal.value.targetMastery),
    }
  }
  return {
    state: 'unverified',
    title: '还没有可靠的作答证据',
    detail: 'Agent 会先用诊断题或追问建立基线，不会仅根据提问内容推断你已经掌握。',
    currentMastery: null,
    targetMastery: Number(activeLearningGoal.value.targetMastery),
  }
})
const agentTeachingAction = computed(() => {
  if (!currentEducationSourceCount.value) {
    return {
      state: 'blocked',
      title: '先补齐可检索的课程资料',
      detail: educationSendBlockReason.value || '课程边界为空，Agent 不会退化为通用问答。',
    }
  }
  if (!activeLearningGoal.value) {
    return {
      state: 'pending',
      title: '先把学习诉求变成达标目标',
      detail: '目标会提供知识点、目标掌握度和后续证据的归属。',
    }
  }
  if (activeLearningTask.value) {
    if (learningTaskIsScheduled(activeLearningTask.value)) {
      return {
        state: 'scheduled',
        title: '下一次复习已安排',
        detail: `任务将在 ${formatDate(activeLearningTask.value.scheduledAt)} 开放；到期后再开始本轮复习。`,
      }
    }
    return {
      state: activeLearningTask.value.status === 'AWAITING_EVIDENCE' ? 'evidence' : 'ready',
      title: activeLearningTask.value.title,
      detail: learningTaskSourceBlockReason(activeLearningTask.value)
        || (activeLearningTask.value.status === 'AWAITING_EVIDENCE'
          ? '先补充本轮作答或评分依据，再决定是否进入下一步。'
          : activeLearningTask.value.prompt),
    }
  }
  if (activeLearningRecommendation.value) {
    return {
      state: 'ready',
      title: activeLearningRecommendation.value.nextActionTitle,
      detail: activeLearningRecommendation.value.rationale || '根据当前掌握度与目标自动选择教学动作。',
    }
  }
  return {
    state: 'pending',
    title: '等待 Agent 生成教学策略',
    detail: '开始一轮学习对话后，Agent 会在课程边界内选择讲解、诊断、练习或复习。',
  }
})
const agentEvidenceRequest = computed(() => {
  if (!activeLearningGoal.value) {
    return {
      state: 'pending',
      title: '先绑定学习目标',
      detail: '没有目标时，作答无法沉淀为可追踪的掌握度证据。',
    }
  }
  if (activeLearningTask.value?.status === 'AWAITING_EVIDENCE') {
    return {
      state: 'required',
      title: '需要补充本轮证据',
      detail: '提交解题过程、作答理由或教师评分；仅完成对话不会自动提升掌握度。',
    }
  }
  if (learningTaskIsScheduled(activeLearningTask.value)) {
    return {
      state: 'scheduled',
      title: `等待 ${formatDate(activeLearningTask.value.scheduledAt)} 开放`,
      detail: '当前无需提前作答；到期后完成复习，新的作答或评分才会写回保持度证据。',
    }
  }
  if (!currentLearningEvidenceCount.value) {
    return {
      state: 'required',
      title: '先用一次作答建立基线',
      detail: 'Agent 会记录正确性、推理过程和反馈，再更新掌握度。',
    }
  }
  return {
    state: 'ready',
    title: `继续收集 ${activeLearningGoal.value.conceptKey} 的证据`,
    detail: `已有 ${currentLearningEvidenceCount.value} 次测评记录；新的作答会决定是否调整下一次练习难度。`,
  }
})
const educationSendBlockReason = computed(() => {
  if (!activeLearnerProfile.value) {
    return '教育 Agent 尚未绑定学习者画像。画像提供学科、年级、课程版本和当前学习状态。'
  }
  const scope = currentEducationRetrievalScope.value
  if (!scope.configured) {
    return '教育 Agent 缺少完整课程约束，请先补齐学科、年级和课程版本。'
  }
  if (!scope.sourceCount) {
    return courseSourceBlockReason(scope)
  }
  return ''
})
const educationSetupActionLabel = computed(() => {
  if (educationWorkspaceMode.value === 'teacher' && !manageableEducationDocuments.value.length) return '上传课程资料'
  if (!activeLearnerProfile.value) return '建立学习画像'
  const scope = currentEducationRetrievalScope.value
  const availability = courseSourceAvailability(scope)
  if (scope.configured && !availability.courseSourceCount && availability.sameSubjectGradeSourceCount) {
    return '统一课程版本'
  }
  if (educationWorkspaceMode.value === 'learner' && !scope.sourceCount) return '查看课程状态'
  return '配置课程资料'
})
const studentQuickStartAction = computed(() => {
  if (!activeLearnerProfile.value) {
    return { kind: 'profile', label: '建立学习画像', detail: '先告诉 Agent 你正在学习的学科、年级和课程版本。', section: 'education' }
  }
  // 资料缺失时，作业和学习目标入口都无法真正启动；先把阻断原因交给学生，
  // 避免首屏按钮看似可执行、点击后才得到资料错误。
  const assignment = nextLearnerCourseAssignment.value
  const assignmentAction = learningAssignmentNextAction(assignment)
  const assignmentSourceBlockReason = assignmentAction.actionable
    ? learningAssignmentSourceBlockReason(assignment)
    : ''
  if (educationSendBlockReason.value || assignmentSourceBlockReason) {
    return {
      kind: 'setup',
      label: educationSetupActionLabel.value,
      detail: educationSendBlockReason.value || assignmentSourceBlockReason,
      section: 'education',
    }
  }
  if (assignmentAction.actionable && assignment) {
    return { kind: 'assignment', label: assignmentAction.label, detail: assignmentAction.detail, section: 'education' }
  }
  if (!activeLearningGoal.value) {
    return { kind: 'goal', label: '设定学习目标', detail: '设定知识点和达标标准，后续作答才能形成学习证据。', section: 'education' }
  }
  return { kind: 'education', label: '进入我的学习', detail: '查看课程边界、学习状态和下一步行动。', section: 'education' }
})
const educationAgentReady = computed(() => !educationSendBlockReason.value)
const educationComposerPlaceholder = computed(() => {
  if (educationSendBlockReason.value) return '先完成课程资料配置，再提交学习任务…'
  if (activeLearningTask.value?.status === 'AWAITING_EVIDENCE') {
    return '补充本轮作答证据：写出解题过程、判断依据，或指出卡住的步骤…'
  }
  if (activeLearningGoal.value) {
    return `围绕「${activeLearningGoal.value.conceptKey}」完成本轮学习：写下你的理解、答案或推理…`
  }
  return '提交一个学习任务：题目、知识点、学习困难或目标…'
})
const currentEducationSourceLabel = computed(() => {
  const scope = currentEducationRetrievalScope.value
  if (!scope.configured) return '待配置学习上下文'
  if (scope.sourceCount) return `${scope.sourceCount} 个当前可检索来源`
  return scope.conceptKey ? '当前知识点暂无匹配来源' : '当前约束下暂无匹配来源'
})
const currentEducationRetrievalDetail = computed(() => {
  const scope = currentEducationRetrievalScope.value
  if (!scope.configured) return '先配置学习者画像，Agent 才能锁定课程知识范围'
  const base = `${scope.subject} · ${scope.gradeLevel} · ${scope.curriculumVersion}`
  const available = scope.courseSourceCount
    ? (scope.sourceCount === scope.courseSourceCount
      ? `当前版本下 ${scope.courseSourceCount} 个来源`
      : `当前版本 ${scope.sourceCount} 个可用 · ${scope.courseSourceCount} 个候选`)
    : scope.sameSubjectGradeSourceCount
      ? `当前版本 0 个来源 · 同学科/年级另有 ${scope.sameSubjectGradeSourceCount} 个（${scope.availableCurriculumVersions.join('、')}）`
      : '当前版本 0 个来源'
  return `${base} · ${scope.filterSummary} · ${available}`
})
const educationVersionRepairHint = computed(() => {
  const scope = currentEducationRetrievalScope.value
  const availability = courseSourceAvailability(scope)
  if (!scope.configured || availability.courseSourceCount || !availability.sameSubjectGradeSourceCount) return ''
  return `可用课程版本：${availability.availableCurriculumVersions.join('、')}。${educationWorkspaceMode.value === 'learner' ? '请在学习者画像中选择与课程资料一致的版本。' : '请在课程资料元数据中统一版本。'}`
})
const educationAgentTrace = computed(() => [
  {
    id: 'course',
    label: '课程约束',
    value: activeChatCourse.value
      ? `${activeChatCourse.value.code} · ${activeChatCourse.value.title}`
      : activeLearnerProfile.value
        ? `${activeLearnerProfile.value.subject} · ${activeLearnerProfile.value.gradeLevel}`
        : '尚未绑定课程或学习者画像',
    detail: activeChatCourse.value
      ? `${activeChatCourse.value.curriculumVersion} · 课程实例已锁定`
      : activeLearnerProfile.value
        ? `${activeLearnerProfile.value.curriculumVersion} · 将按画像约束检索`
        : '先配置画像，Agent 才能限制知识检索范围',
    state: activeLearnerProfile.value ? 'ready' : 'pending',
    icon: BookOpen,
  },
  {
    id: 'knowledge',
    label: '知识库检索',
    value: currentEducationSourceLabel.value,
    detail: currentEducationRetrievalDetail.value,
    state: currentEducationSourceCount.value ? 'ready' : 'pending',
    icon: ShieldCheck,
  },
  {
    id: 'learner',
    label: '学习者状态',
    value: activeLearnerProfile.value
      ? (learnerMasteryLoading.value ? '正在读取掌握度…' : `${learnerMastery.value.length} 个知识点已建档`)
      : '等待学习者画像',
    detail: activeLearningRecommendation.value
      ? `当前掌握度 ${formatRate(activeLearningRecommendation.value.currentMastery)} · 目标 ${formatRate(activeLearningRecommendation.value.targetMastery)}`
      : learnerMasteryPreview.value.length
        ? `优先关注：${learnerMasteryPreview.value.map((item) => item.conceptKey).join('、')}`
        : '完成带证据的测评后会更新状态',
    state: activeLearnerProfile.value ? 'ready' : 'pending',
    icon: Brain,
  },
  {
    id: 'teaching',
    label: '教学决策',
    value: educationAgentReady.value ? pedagogicalModeLabel.value : '等待课程资料与学习状态就绪',
    detail: educationAgentReady.value
      ? (chatEducation.conceptKey ? `目标知识点：${chatEducation.conceptKey}` : '会根据问题和掌握度选择讲解、练习或诊断')
      : educationSendBlockReason.value || '先绑定学习者画像，再由 Agent 决定合适的教学动作',
    state: educationAgentReady.value ? 'ready' : 'pending',
    icon: Target,
  },
  {
    id: 'evidence',
    label: '形成性证据',
    value: activeLearningGoal.value ? `${learningGoalAssessments.value.length} 次测评记录` : '回答后可生成测评证据',
    detail: activeLearningGoal.value ? '结果会累计到学习目标并触发下一步动作' : '绑定学习目标后，Agent 会追踪进度和复习任务',
    state: activeLearningGoal.value ? 'ready' : 'pending',
    icon: ListChecks,
  },
])
const teacherOperationsTrace = computed(() => [
  {
    id: 'source',
    label: '课程资料',
    value: manageableEducationSources.value.length ? `${manageableEducationSources.value.length} 个来源已配置` : '待上传与维护',
    detail: manageableEducationSources.value.length ? '课程资料已经具备可检索的元数据边界。' : '上传文档并补充学科、版本、章节、知识点和难度。',
    state: manageableEducationSources.value.length ? 'ready' : 'pending',
  },
  {
    id: 'course',
    label: '课程实例',
    value: teacherEducationCourses.value.length ? `${teacherEducationCourses.value.length} 门课程` : '待创建课程',
    detail: teacherEducationCourses.value.length ? '课程已进入运营工作台。' : '创建课程实例，绑定课程版本和教学范围。',
    state: teacherEducationCourses.value.length ? 'ready' : 'pending',
  },
  {
    id: 'roster',
    label: '学生名单',
    value: `${teacherActiveLearnerCount.value} 名活跃学生`,
    detail: teacherActiveLearnerCount.value ? '名单可以接收课程作业。' : '加入学生后才能批量布置作业。',
    state: teacherActiveLearnerCount.value ? 'ready' : 'pending',
  },
  {
    id: 'assignment',
    label: '课程作业',
    value: teacherAssignmentCount.value ? `${teacherAssignmentCount.value} 份作业` : '待布置作业',
    detail: teacherAssignmentCount.value ? '作业已下发，可继续查看完成和提交证据。' : '把目标知识点和作业说明下发给活跃名单。',
    state: teacherAssignmentCount.value ? 'ready' : 'pending',
  },
  {
    id: 'review',
    label: '复核与反馈',
    value: teacherCoursePendingCount.value ? `${teacherCoursePendingCount.value} 项待处理` : '暂无待办',
    detail: teacherCoursePendingCount.value ? '依据提交物和测评证据确认、退回或反馈。' : '新的学生提交后会出现在这里。',
    state: teacherCoursePendingCount.value ? 'attention' : 'ready',
  },
])
const adminOperationsTrace = computed(() => [
  {
    id: 'sources',
    label: '课程知识源',
    value: `${educationSources.value.length} 个课程来源`,
    detail: '查看课程知识边界是否已经接入；具体资料维护由教师负责。',
    state: educationSources.value.length ? 'ready' : 'pending',
  },
  {
    id: 'courses',
    label: '课程实例',
    value: `${educationCourses.value.length} 门课程`,
    detail: '查看组织内课程实例和课程状态。',
    state: educationCourses.value.length ? 'ready' : 'pending',
  },
  {
    id: 'assignments',
    label: '课程作业',
    value: `${learningAssignments.value.length} 份作业`,
    detail: '查看作业闭环规模和学习证据覆盖。',
    state: learningAssignments.value.length ? 'ready' : 'pending',
  },
  {
    id: 'evidence',
    label: '学习证据',
    value: `${Number(educationMetrics.value?.assessmentTotal || 0)} 条测评`,
    detail: '审阅教育业务指标，不代替教师进行课程复核。',
    state: Number(educationMetrics.value?.assessmentTotal || 0) ? 'ready' : 'pending',
  },
  {
    id: 'governance',
    label: '系统治理',
    value: '模型 · 索引 · 审计',
    detail: '管理员的主要职责是保持教育 Agent 的运行边界和可审计性。',
    state: 'ready',
  },
])
const matchingEducationSourceCount = computed(() => {
  const profile = activeLearnerProfile.value
  if (!profile) return 0
  return educationSources.value.filter((source) => source.subject === profile.subject
    && source.gradeLevel === profile.gradeLevel
    && source.curriculumVersion === profile.curriculumVersion).length
})
const learnerMasteryPreview = computed(() => [...learnerMastery.value]
  .sort((left, right) => Number(left.masteryScore) - Number(right.masteryScore)
    || String(left.conceptKey || '').localeCompare(String(right.conceptKey || ''), 'zh-CN'))
  .slice(0, 3))
const learningSetupProgress = computed(() => {
  const completed = [
    Boolean(activeLearnerProfile.value),
    Boolean(activeLearningGoal.value),
    matchingEducationSourceCount.value > 0,
  ].filter(Boolean).length
  return { completed, total: 3 }
})
const learningEvidenceSummary = computed(() => {
  if (!activeLearningGoal.value) return '等待学习目标'
  const count = learningGoalAssessments.value.length
  return count ? `${count} 条测评证据` : '尚无测评证据'
})
// 把课程来源、当前目标和已有掌握度合并成一条可读的课程路径。教育 Agent
// 的核心不是“回答得像老师”，而是能指出学习者正在课程中的哪个节点、
// 哪些前置节点还不稳，以及下一次作答要验证什么。
const learningConceptTrail = computed(() => {
  const scope = currentEducationRetrievalScope.value
  const goalConcept = String(activeLearningGoal.value?.conceptKey || scope.conceptKey || '').trim()
  const sourceConcepts = currentEducationSourcePreview.value.flatMap((source) =>
    String(source?.conceptTags || '')
      .split(/[,，;；\n]/)
      .map((item) => item.trim())
      .filter(Boolean))
  const concepts = [goalConcept, ...sourceConcepts, ...learnerMasteryPreview.value.map((item) => item.conceptKey)]
    .map((item) => String(item || '').trim())
    .filter(Boolean)
  const uniqueConcepts = [...new Set(concepts)].slice(0, 5)
  const mastery = new Map(learnerMastery.value.map((item) => [String(item.conceptKey || '').trim(), Number(item.masteryScore)]))
  if (!uniqueConcepts.length) {
    return [{ concept: '等待课程节点', detail: '配置课程资料后显示知识路径', state: 'pending', score: null }]
  }
  return uniqueConcepts.map((concept, index) => {
    const score = mastery.get(concept)
    const isTarget = concept === goalConcept
    const state = isTarget
      ? 'target'
      : (Number.isFinite(score) ? (score >= 0.8 ? 'ready' : 'attention') : (index === 0 ? 'available' : 'pending'))
    return {
      concept,
      detail: isTarget ? '本轮目标' : (Number.isFinite(score) ? `掌握度 ${formatRate(score)}` : '课程来源'),
      state,
      score: Number.isFinite(score) ? score : null,
    }
  })
})
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
const educationRuntimeDiagnostic = computed(() => {
  const currentHealth = health.value
  if (!currentHealth || currentHealth.error) return ''
  if (currentHealth.education?.courseBoundRunsEnabled === true) return ''
  return '当前连接的 Runtime 未包含教育知识库 Agent；请使用 npm run desktop:dev 启动当前源码。'
})
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
    ? presentChatCitations(message.content, runtimeEvidenceForRun(message.runId))
    : { content: message.content || '', sources: [] },
])))
const activeConversationId = computed(() => activeConversation.value?.conversation?.id || '')
// 学习入口只展示教育会话和当前尚未开始的空学习会话。通用工作区历史仍保留在
// 运行控制台中，避免页面标题是教育 Agent、首条内容却变成普通问答。
const learningConversations = computed(() => {
  const activeId = activeConversationId.value
  const active = conversations.value.find((conversation) => conversation.id === activeId)
  const educationSessions = conversations.value.filter((conversation) => conversation.educationMode)
  const unique = new Map()
  if (active && !active.educationMode && !active.messageCount && !active.lastMessagePreview) {
    unique.set(active.id, active)
  }
  educationSessions.forEach((conversation) => unique.set(conversation.id, conversation))
  return [...unique.values()]
})
const filteredConversations = computed(() => {
  const query = conversationQuery.value.trim().toLowerCase()
  if (!query) return learningConversations.value
  return learningConversations.value.filter((conversation) => [
    conversation.title,
    conversation.lastMessagePreview,
    conversationLearningContext(conversation),
  ]
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
  && !educationSendBlockReason.value
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

function formatScore(value) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? number.toFixed(2) : '—'
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

watch(
  [learningAssignmentCourseFilter, learningAssignmentLearnerFilter, learningAssignmentIssueFilter],
  () => {
    void nextTick(() => ensureVisibleLearningAssignmentDetails())
  },
)

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
    // 教育知识库 Agent 是本项目的主路径；没有画像时发送前会提示先完成配置。
    enabled: true,
    learnerProfileId: '',
    learningGoalId: '',
    learningAssignmentId: '',
    courseId: '',
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
      // 旧版会把开关状态保存在本地，曾关闭过开关的浏览器会让“学习对话”悄悄
      // 退回通用聊天。聊天工作台现在是教育 Agent 的固定入口，通用 Run 仍可在
      // 运行控制台创建，因此这里始终恢复教育模式。
      enabled: true,
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

function chatFailureTitle(message) {
  const content = String(message?.content || '').toLowerCase()
  return content.includes('超时') || content.includes('timed out') || content.includes('timeout')
    ? '本轮执行超时' : '本轮执行失败'
}

function chatFailureGuidance(message) {
  if (!message || message.status !== 'FAILED') return ''
  const run = selectedRun.value?.run?.id === message.runId ? selectedRun.value.run : null
  const assignmentId = run?.educationLearningAssignmentId || ''
  const assignment = assignmentId
    ? learningAssignments.value.find((item) => item.id === assignmentId)
    : null
  if (assignment?.learnerUserId === form.userId && assignment.status === 'RETRY_REQUIRED') {
    return '课程作业已回流为“待重试/返工”。点击“重试本轮”，或从下方作业卡片重新开始。'
  }
  if (assignment?.learnerUserId === form.userId && assignment.status === 'ACCEPTED') {
    return '课程作业仍在同步状态；稍后刷新作业卡片，确认是否已进入“待重试/返工”。'
  }
  return '可以点击“重试本轮”；如果多次失败，请联系教师检查课程资料，或联系管理员检查模型与 Runtime。'
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

function runtimeEvidenceForRun(runId) {
  if (!runId) return []
  if (selectedRun.value?.run?.id === runId) {
    return selectedRun.value.steps
      ?.flatMap((step) => step.contextEvidence || [])
      || chatRuntimeEvidenceByRun.value[runId]
      || []
  }
  return chatRuntimeEvidenceByRun.value[runId] || []
}

function cacheRunContextEvidence(detail) {
  const runId = detail?.run?.id
  if (!runId) return
  const evidence = (detail.steps || []).flatMap((step) => (step.contextEvidence || [])
    .map((item) => ({
      ...item,
      stepName: step.name || '',
      stepSequence: step.sequence,
    })))
  chatRuntimeEvidenceByRun.value = {
    ...chatRuntimeEvidenceByRun.value,
    [runId]: evidence,
  }
}

function mergeChatSourceProvenance(source, provenance, evidence = null) {
  const current = source.provenance || ''
  source.provenance = !current || current === provenance || current === 'BOTH' ? (current || provenance) : 'BOTH'
  source.provenanceLabel = source.provenance === 'BOTH'
    ? '模型引用 · Runtime 授权'
    : provenance === 'RUNTIME' ? 'Runtime 授权证据' : '模型引用'
  if (evidence) {
    source.runtimeEvidence = true
    source.excerpt = evidence.excerpt || source.excerpt || ''
    source.citation = evidence.citation || source.citation || ''
    source.stepName = evidence.stepName || source.stepName || ''
  }
  return source
}

function presentChatCitations(content, runtimeEvidence = []) {
  const sources = []
  const sourceByKey = new Map()
  const addSource = (citation, fallbackTitle = '', provenance = 'MODEL', evidence = null) => {
    const metadata = chatSourceMetadata(citation, fallbackTitle)
    const existing = sourceByKey.get(metadata.key)
      || sources.find((item) => item.title && metadata.title && item.title === metadata.title)
    if (existing) return mergeChatSourceProvenance(existing, provenance, evidence)
    const source = mergeChatSourceProvenance({ ...metadata, index: sources.length + 1 }, provenance, evidence)
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
  ;(runtimeEvidence || []).forEach((evidence) => {
    if (!evidence?.citation && !evidence?.title) return
    addSource(evidence.citation, evidence.title, 'RUNTIME', evidence)
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

function conversationLearningContext(conversation) {
  if (!conversation?.educationMode) return ''
  const course = [conversation.educationCourseCode, conversation.educationCourseTitle]
    .filter(Boolean).join(' · ')
  const curriculum = [conversation.educationSubject, conversation.educationGradeLevel,
    conversation.educationCurriculumVersion].filter(Boolean).join(' · ')
  const goal = conversation.educationLearningAssignmentTitle
    || conversation.educationLearningGoalTitle
    || conversation.educationConceptKey
  return [course || curriculum, goal].filter(Boolean).join(' / ')
}

// 早期通用会话可能仍保留“新的对话”标题。学习工作台展示时统一称为学习任务，
// 不修改历史记录，也避免让学习入口重新呈现为通用聊天产品。
function learningConversationTitle(conversation) {
  const title = String(conversation?.title || '').trim()
  return !title || title === '新的对话' || title === '新对话' ? '新的学习任务' : title
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
  void loadConversationRunEvidence(detail)
}

// 打开历史教育会话时补读最近回答的 Run 详情。请求失败只影响来源卡片，不影响
// 聊天正文；已从当前 Run 或 SSE 缓存过的证据不会重复请求。
async function loadConversationRunEvidence(detail) {
  const runIds = [...new Set((detail?.messages || [])
    .filter((message) => message.role === 'ASSISTANT' && message.runId)
    .map((message) => message.runId))]
    .filter((runId) => !chatRuntimeEvidenceByRun.value[runId])
    .slice(-20)
  if (!runIds.length) return
  const entries = await Promise.all(runIds.map(async (runId) => {
    try {
      const runDetail = await api.getRun(runId)
      cacheRunContextEvidence(runDetail)
      return [runId, runtimeEvidenceForRun(runId)]
    } catch {
      return [runId, null]
    }
  }))
  if (activeConversationId.value !== detail?.conversation?.id) return
  chatRuntimeEvidenceByRun.value = {
    ...chatRuntimeEvidenceByRun.value,
    ...Object.fromEntries(entries.filter(([, value]) => value)),
  }
}

async function recordRunFeedback(message, rating) {
  if (!message?.runId || message.status !== 'COMPLETED' || feedbackSavingRunId.value) return
  feedbackSavingRunId.value = message.runId
  try {
    const feedback = await api.saveRunFeedback(message.runId, {
      rating,
      reasonCode: 'EDUCATION',
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
    await refreshLearnerMastery(run.educationLearnerProfileId)
    // 人工复核会让待补证据任务进入下一状态；同步任务和通知列表，
    // 让“提交证据 → 状态更新 → 下一步推荐”在当前页面立即闭环。
    await Promise.all([
      loadLearningTasks(),
      loadLearningNotifications(),
    ])
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
    const educationSessions = conversations.value.filter((conversation) => conversation.educationMode)
    const requestedId = preferredId || activeConversationId.value || readRememberedConversationId()
    const requested = conversations.value.find((conversation) => conversation.id === requestedId)
    const targetEducation = requested?.educationMode
      ? requested
      : educationSessions[0]
    if (targetEducation) {
      await selectConversation(targetEducation.id, false)
      return
    }
    // 历史数据可能只有通用会话。保留它们，但给教育 Agent 建立一个清晰的空入口，
    // 让首屏直接进入画像、课程约束和学习证据闭环。
    const blankConversation = conversations.value.find((conversation) =>
      !conversation.educationMode && !conversation.messageCount && !conversation.lastMessagePreview)
    if (blankConversation) {
      await selectConversation(blankConversation.id, false)
      return
    }
    const created = await api.createConversation(newConversationPayload())
    if (requestToken !== conversationListRequestToken) return
    conversations.value = [created.conversation, ...conversations.value]
    activeConversation.value = created
    rememberConversation(created.conversation.id)
    focusChatComposer()
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
    title: '新的学习任务',
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
  showLearningTrace.value = false
  await loadWorkspaceDirectory('.')
}

/** 运行详情与文件浏览共用右侧检查位，打开执行步骤时收起项目文件，避免布局挤压到下一行。 */
async function toggleRunPanel() {
  const nextVisible = !showChatRun.value
  showChatWorkspace.value = false
  showLearningTrace.value = false
  showChatRun.value = nextVisible
}

/** 从消息跳转到某个 Run 时强制占用右侧检查位，避免和项目文件面板同时渲染。 */
async function openRunPanel(runId, announce = false) {
  if (!runId) return
  showChatWorkspace.value = false
  showLearningTrace.value = false
  showChatRun.value = true
  await selectRun(runId, announce, false)
}

/** 课程约束、学习状态和证据链共享右侧检查位，便于学习者理解 Agent 的决策依据。 */
function toggleLearningTrace() {
  const nextVisible = !showLearningTrace.value
  showChatWorkspace.value = false
  showChatRun.value = false
  showLearningTrace.value = nextVisible
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
  if (!chatEducation.learnerProfileId) {
    errorMessage.value = '教育 Agent 需要先绑定学习者画像；请打开教育工作台创建或选择画像。'
    return
  }
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
    const educationProfile = activeLearnerProfile.value
    const educationCourse = activeChatCourse.value
    const education = {
      ...chatEducation,
      enabled: true,
      subject: educationCourse?.subject || educationProfile?.subject || '',
      gradeLevel: educationCourse?.gradeLevel || educationProfile?.gradeLevel || '',
      curriculumVersion: educationCourse?.curriculumVersion || educationProfile?.curriculumVersion || '',
      minDifficulty: chatEducation.minDifficulty == null ? null : Number(chatEducation.minDifficulty),
      maxDifficulty: chatEducation.maxDifficulty == null ? null : Number(chatEducation.maxDifficulty),
    }
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
  cacheRunContextEvidence(detail)
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
      // SSE 断线时由轮询兜底，把完成的教育 Run 同样回写为消息级学习证据。
      if (runId) void refreshEducationAfterChatRun(runId).catch(() => {})
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
    const commonRequests = [
      api.listDocuments().catch(() => []),
      api.listMemories().catch(() => []),
      api.contextConfiguration().catch(() => null),
    ]
    const [documentData, memoryData, contextConfigurationData] = await Promise.all(commonRequests)
    const visibleDocuments = documentData || []
    documents.value = visibleDocuments
    if (!visibleDocuments.some((document) => document.id === educationSourceForm.documentId
      && (document.ownerUserId === form.userId || isTeacherOnlyRole.value))) {
      educationSourceForm.documentId = ''
    }
    memories.value = memoryData || []
    contextConfiguration.value = contextConfigurationData
    await loadEducationData()
    if (isAdminRole.value) {
      const [runData, toolData, summaryData] = await Promise.all([
        loadRunsPage(),
        api.listTools().catch(() => []),
        api.dashboardSummary().catch(() => null),
      ])
      tools.value = toolData || []
      summary.value = summaryData
      if (selectedRun.value) {
        await selectRun(selectedRun.value.run.id, false)
      } else if (runData?.items?.length || runs.value.length) {
        await selectRun((runData?.items || runs.value)[0].id, false)
      }
    } else {
      tools.value = []
      summary.value = null
      runs.value = []
      selectedRun.value = null
    }
  } catch (error) {
    errorMessage.value = errorText(error)
  }
}

async function refreshLearnerMastery(profileId = activeLearnerProfile.value?.id) {
  if (!profileId) {
    learnerMastery.value = []
    return
  }
  learnerMasteryLoading.value = true
  try {
    learnerMastery.value = await api.listLearnerMastery(profileId)
  } catch {
    // 学习主界面仍应可用；画像不存在或权限不足时只隐藏掌握度明细。
    learnerMastery.value = []
  } finally {
    learnerMasteryLoading.value = false
  }
}

async function ensureLearningAssignmentDetails(assignmentId) {
  if (!assignmentId || learningAssignmentDetailsLoaded(assignmentId)) return
  const existing = educationAssignmentDetailRequests.get(assignmentId)
  if (existing) return existing
  const request = (async () => {
    const assignment = learningAssignments.value.find((item) => item.id === assignmentId)
    if (!assignment) return
    const [progress, evidence, submissions, feedback, evaluations] = await Promise.all([
      api.getLearningAssignmentProgress(assignmentId).catch(() => null),
      api.getLearningAssignmentEvidence(assignmentId).catch(() => null),
      api.listLearningAssignmentSubmissions(assignmentId).catch(() => null),
      api.listLearningAssignmentFeedback(assignmentId).catch(() => null),
      api.listLearningAssignmentEvaluations(assignmentId).catch(() => null),
    ])
    if (progress) learningAssignmentProgressMap.value = { ...learningAssignmentProgressMap.value, [assignmentId]: progress }
    if (evidence) learningAssignmentEvidenceMap.value = { ...learningAssignmentEvidenceMap.value, [assignmentId]: evidence }
    if (submissions) learningAssignmentSubmissionMap.value = { ...learningAssignmentSubmissionMap.value, [assignmentId]: submissions }
    if (feedback) learningAssignmentFeedbackMap.value = { ...learningAssignmentFeedbackMap.value, [assignmentId]: feedback }
    if (evaluations) learningAssignmentEvaluationMap.value = { ...learningAssignmentEvaluationMap.value, [assignmentId]: evaluations }
  })()
  educationAssignmentDetailRequests.set(assignmentId, request)
  return request.finally(() => educationAssignmentDetailRequests.delete(assignmentId))
}

/** 通知是跨角色写入后的入口，点击时必须绕过“已加载”缓存读取权威证据。 */
async function refreshLearningAssignmentDetails(assignmentId) {
  if (!assignmentId) return null
  const [assignment, progress, evidence, submissions, feedback, evaluations] = await Promise.all([
    api.getLearningAssignment(assignmentId).catch(() => null),
    api.getLearningAssignmentProgress(assignmentId).catch(() => null),
    api.getLearningAssignmentEvidence(assignmentId).catch(() => null),
    api.listLearningAssignmentSubmissions(assignmentId).catch(() => null),
    api.listLearningAssignmentFeedback(assignmentId).catch(() => null),
    api.listLearningAssignmentEvaluations(assignmentId).catch(() => null),
  ])
  if (assignment) {
    learningAssignments.value = learningAssignments.value.map((item) =>
      item.id === assignment.id ? assignment : item)
  }
  if (progress) learningAssignmentProgressMap.value = {
    ...learningAssignmentProgressMap.value, [assignmentId]: progress,
  }
  if (evidence) learningAssignmentEvidenceMap.value = {
    ...learningAssignmentEvidenceMap.value, [assignmentId]: evidence,
  }
  if (submissions) learningAssignmentSubmissionMap.value = {
    ...learningAssignmentSubmissionMap.value, [assignmentId]: submissions,
  }
  if (feedback) learningAssignmentFeedbackMap.value = {
    ...learningAssignmentFeedbackMap.value, [assignmentId]: feedback,
  }
  if (evaluations) learningAssignmentEvaluationMap.value = {
    ...learningAssignmentEvaluationMap.value, [assignmentId]: evaluations,
  }
  return assignment
}

async function ensureVisibleLearningAssignmentDetails() {
  await Promise.all(visibleLearningAssignments.value.map((assignment) =>
    ensureLearningAssignmentDetails(assignment.id)))
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
    const boundChatAssignment = chatEducation.learningAssignmentId
      ? assignments.find((assignment) => assignment.id === chatEducation.learningAssignmentId)
      : null
    if (chatEducation.learningAssignmentId
      && (!boundChatAssignment || ['COMPLETED', 'CANCELLED'].includes(boundChatAssignment.status))) {
      chatEducation.learningAssignmentId = ''
    }
    educationMetrics.value = metrics
    educationCourses.value = courses || []
    learningEvaluationQueue.value = await api.listLearningEvaluationQueue().catch(() => [])
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
    const submissionEntries = await Promise.all(assignments.slice(0, 20).map(async (assignment) => {
      try {
        return [assignment.id, await api.listLearningAssignmentSubmissions(assignment.id)]
      } catch {
        return [assignment.id, null]
      }
    }))
    learningAssignmentSubmissionMap.value = Object.fromEntries(
      submissionEntries.filter(([, value]) => value))
    const feedbackEntries = await Promise.all(assignments.slice(0, 20).map(async (assignment) => {
      try {
        return [assignment.id, await api.listLearningAssignmentFeedback(assignment.id)]
      } catch {
        return [assignment.id, null]
      }
    }))
    learningAssignmentFeedbackMap.value = Object.fromEntries(
      feedbackEntries.filter(([, value]) => value))
    const evaluationEntries = await Promise.all(assignments.slice(0, 20).map(async (assignment) => {
      try {
        return [assignment.id, await api.listLearningAssignmentEvaluations(assignment.id)]
      } catch {
        return [assignment.id, null]
      }
    }))
    learningAssignmentEvaluationMap.value = Object.fromEntries(
      evaluationEntries.filter(([, value]) => value))
    await Promise.all([loadLearningNotifications(), loadLearningAssignmentNotifications()])
    const recommendationEntries = await Promise.all(goals.map(async (goal) => {
      try {
        return [goal.id, await api.getGoalRecommendation(goal.id)]
      } catch {
        return [goal.id, null]
      }
    }))
    learningGoalRecommendationMap.value = Object.fromEntries(recommendationEntries.filter(([, value]) => value))
    const nextProfile = profiles.find((profile) => profile.active) || profiles[0] || null
    activeLearnerProfile.value = nextProfile
    if (nextProfile) {
      applyLearnerProfileToEducationRun(nextProfile)
      await refreshLearnerMastery(nextProfile.id)
    } else {
      learnerMastery.value = []
    }
    const rememberedGoalId = form.education.learningGoalId || chatEducation.learningGoalId
    const nextGoal = goals.find((goal) => goal.id === rememberedGoalId)
      || goals.find((goal) => goal.status === 'ACTIVE')
      || goals[0]
    if (nextGoal) await selectLearningGoal(nextGoal, false)
    const ownerCourses = teacherEducationCourses.value
    // 课程列表同时包含我创建和我已加入的课程。优先保留用户刚刚选择的课程，
    // 这样教师在查看自己加入的课程、学习者刷新页面时都不会被强行切回教师视图。
    const nextCourse = educationCourses.value.find((course) => course.id === activeEducationCourseId.value)
      || ownerCourses.find((course) => course.status === 'ACTIVE')
      || ownerCourses[0]
      || enrolledEducationCourses.value.find((course) => course.status === 'ACTIVE')
      || enrolledEducationCourses.value[0]
    if (nextCourse) {
      await loadEducationCourseWorkspace(nextCourse.id)
    } else {
      activeEducationCourseId.value = ''
      educationCourseEnrollments.value = []
      educationCourseProgress.value = null
      educationCourseResult.value = null
    }
    educationError.value = ''
  } catch (error) {
    // 教育权限是可选的；不应让没有教育权限的通用 Agent 用户无法打开控制台。
    educationError.value = await educationLoadErrorText(error)
  }
}

/**
 * 旧版桌面 Runtime 可能仍能返回基础页面，但不认识教育接口，最终只给出泛化的 500。
 * 复用健康摘要中的能力标记，把这个部署问题转成用户可以直接执行的修复提示。
 */
async function educationLoadErrorText(error) {
  let currentHealth = health.value
  if (!currentHealth || currentHealth.error) {
    try {
      currentHealth = await api.health()
      health.value = currentHealth
    } catch {
      // 健康接口本身不可用时保留原始教育接口错误，避免误判部署状态。
    }
  }
  if (currentHealth && !currentHealth.error
    && currentHealth.education?.courseBoundRunsEnabled !== true) {
    return educationRuntimeDiagnostic.value
      || '当前连接的 Runtime 未包含教育知识库 Agent；请使用 npm run desktop:dev 启动当前源码。'
  }
  return errorText(error)
}

async function loadEducationCourseWorkspace(courseId) {
  const course = educationCourses.value.find((item) => item.id === courseId)
  if (!course) {
    activeEducationCourseId.value = ''
    educationCourseEnrollments.value = []
    educationCourseProgress.value = null
    educationCourseResult.value = null
    return
  }
  activeEducationCourseId.value = course.id
  educationCourseLoading.value = true
  try {
    // 班级名单与汇总进度属于课程教师；管理员使用同租户只读治理接口查看全班，
    // 学习者只读取后端已按本人过滤的结课快照，避免把三种心智模型混在一起。
    const isOwner = course.ownerUserId === form.userId
    const canViewGovernance = isOwner || isAdminWorkspace.value
    const [enrollments, progress, result] = canViewGovernance
      ? await Promise.all([
        api.listEducationCourseEnrollments(course.id),
        api.getEducationCourseProgress(course.id),
        api.getEducationCourseResult(course.id).catch(() => null),
      ])
      : [[], null, await api.getEducationCourseResult(course.id).catch(() => null)]
    if (activeEducationCourseId.value === course.id) {
      educationCourseEnrollments.value = enrollments || []
      educationCourseProgress.value = progress || null
      educationCourseResult.value = result || null
    }
  } catch (error) {
    educationCourseEnrollments.value = []
    educationCourseProgress.value = null
    educationCourseResult.value = null
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
  learningAssignmentIssueFilter.value = ''
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
    noticeMessage.value = `已将 ${enrollment.learnerUserId} 移出课程名单；历史作业与证据会保留，但不再计入当前班级进度和结课判定。`
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

function educationCourseStatusLabel(status) {
  return {
    ACTIVE: '运营中',
    COMPLETED: '已结课',
    ARCHIVED: '已归档',
  }[status] || status || '未知状态'
}

async function completeEducationCourse(course) {
  if (!course || course.ownerUserId !== form.userId || course.status !== 'ACTIVE'
    || educationCourseActionId.value) return
  const progress = educationCourseProgress.value
  if (!progress?.readyToComplete) {
    noticeMessage.value = `课程尚未满足结课条件：待处理作业 ${Number(progress?.completionBlockerCount || 0)}，缺提交物 ${Number(progress?.submissionBlockerCount || 0)}，名单覆盖缺口 ${Number(progress?.rosterCoverageBlockerCount || 0)}。`
    return
  }
  const note = window.prompt('可填写结课说明（可选）：', '')
  if (note === null) return
  educationCourseActionId.value = course.id
  clearMessages()
  try {
    const completed = await api.completeEducationCourse(course.id, { note: note.trim() || null })
    educationCourses.value = educationCourses.value.map((item) => item.id === completed.id ? completed : item)
    await loadEducationData()
    await loadEducationCourseWorkspace(course.id)
    noticeMessage.value = `课程“${course.title}”已结课，结课事实和说明已留存。`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    educationCourseActionId.value = ''
  }
}

async function exportEducationCourseResult(course) {
  if (!course || course.ownerUserId !== form.userId || !educationCourseResult.value
    || educationCourseActionId.value) return
  educationCourseActionId.value = course.id
  clearMessages()
  try {
    const download = await api.downloadEducationCourseResult(course.id)
    const url = URL.createObjectURL(download.blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = download.filename
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    setTimeout(() => URL.revokeObjectURL(url), 0)
    noticeMessage.value = `已导出课程“${course.title}”的结课结果报告。`
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

function learningAssignmentMatchesIssue(assignment, issue) {
  if (!assignment || !issue) return true
  if (issue === 'completion') {
    return assignment.status !== 'CANCELLED'
      && (assignment.status !== 'COMPLETED' || assignment.reviewStatus !== 'VERIFIED')
  }
  if (issue === 'assigned') return assignment.status === 'ASSIGNED'
  if (issue === 'accepted') return assignment.status === 'ACCEPTED'
  if (issue === 'evidence') return assignment.status === 'AWAITING_EVIDENCE'
  if (issue === 'retry') return assignment.status === 'RETRY_REQUIRED'
  if (issue === 'review') return assignment.status === 'COMPLETED' && assignment.reviewStatus === 'PENDING'
  if (issue === 'revision') return assignment.reviewStatus === 'REVISION_REQUIRED'
  if (issue === 'submission') {
    return assignment.status === 'COMPLETED'
      && !learningAssignmentSubmissionMap.value[assignment.id]?.length
  }
  if (issue === 'submitted') {
    return assignment.status === 'COMPLETED'
      && Boolean(learningAssignmentSubmissionMap.value[assignment.id]?.length)
  }
  if (issue === 'intervention') return learningAssignmentHasOpenInterventionForView(assignment)
  if (issue === 'overdue') return assignment.status === 'OVERDUE'
  return true
}

function courseLearnerAttentionCount(learner) {
  if (!learner) return 0
  return Number(learner.attentionCount || 0)
}

function courseLearnerNextAction(learner) {
  if (!learner) return { label: '查看作业', issue: '' }
  if (Number(learner.assigned || 0)) return { label: '看待接受', issue: 'assigned' }
  if (Number(learner.awaitingEvidence || 0)) return { label: '补证据', issue: 'evidence' }
  if (Number(learner.revisionRequired || 0)) return { label: '看返工', issue: 'revision' }
  if (Number(learner.retryRequired || 0)) return { label: '看重试', issue: 'retry' }
  if (Number(learner.openInterventionCount || 0)) return { label: '看干预', issue: 'intervention' }
  if (Number(learner.submissionMissing || 0)) return { label: '看提交物', issue: 'submission' }
  if (Number(learner.reviewPending || 0)) return { label: '去确认', issue: 'review' }
  if (Number(learner.overdue || 0)) return { label: '看逾期', issue: 'overdue' }
  return { label: '查看作业', issue: '' }
}

function focusCourseLearner(learner) {
  if (!learner?.learnerUserId) return
  learningAssignmentCourseFilter.value = activeEducationCourseId.value
  learningAssignmentLearnerFilter.value = learner.learnerUserId
  learningAssignmentIssueFilter.value = ''
  nextTick(() => document.getElementById('learning-assignment-list')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function focusCourseLearnerAction(learner) {
  if (!learner?.learnerUserId) return
  const action = courseLearnerNextAction(learner)
  learningAssignmentCourseFilter.value = activeEducationCourseId.value
  learningAssignmentLearnerFilter.value = learner.learnerUserId
  learningAssignmentIssueFilter.value = ['assigned', 'accepted', 'evidence', 'retry', 'revision', 'review', 'submission', 'intervention', 'overdue'].includes(action.issue)
    ? action.issue : ''
  nextTick(() => document.getElementById('learning-assignment-list')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

async function takeLearnerCourseNextAction() {
  const course = activeEducationCourse.value
  const nextAction = activeEducationCourseLearnerProgress.value?.nextAction
  const assignment = nextAction?.assignmentId
    ? learningAssignments.value.find((item) => item.id === nextAction.assignmentId)
    : null
  if (!course || !assignment) {
    focusMyCourseAssignments()
    return
  }
  const openFeedback = learningAssignmentOpenFeedback(assignment)
  if (['REQUEST_EVIDENCE', 'RECOMMEND_RETRY'].includes(openFeedback?.action)) {
    await acknowledgeLearningAssignmentFeedback(assignment, openFeedback)
    return
  }
  if (['ASSIGNED', 'ACCEPTED', 'AWAITING_EVIDENCE', 'RETRY_REQUIRED'].includes(assignment.status)) {
    await startLearningAssignment(assignment)
    return
  }
  if (learningAssignmentSubmissionOpen(assignment)) {
    startLearningAssignmentSubmission(assignment)
    focusMyCourseAssignments()
    return
  }
  focusLearnerCourseAssignment(assignment)
}

function focusMyCourseAssignments() {
  const course = activeEducationCourse.value
  if (!course) return
  learningAssignmentCourseFilter.value = course.id
  learningAssignmentLearnerFilter.value = form.userId
  learningAssignmentIssueFilter.value = ''
  nextTick(() => document.getElementById('learning-assignment-list')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function clearLearningAssignmentFilter() {
  learningAssignmentCourseFilter.value = ''
  learningAssignmentLearnerFilter.value = ''
  learningAssignmentIssueFilter.value = ''
}

function focusCourseBlocker(issue) {
  const course = activeEducationCourse.value
  if (!course) return
  learningAssignmentCourseFilter.value = course.id
  learningAssignmentLearnerFilter.value = ''
  learningAssignmentIssueFilter.value = issue
  nextTick(() => document.getElementById('learning-assignment-list')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function focusEducationCourseRoster() {
  nextTick(() => document.getElementById('education-course-roster')?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
}

function focusEducationCourseAssignment() {
  nextTick(() => document.getElementById('education-course-assignment')?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
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
  if (!learningAssignmentNotificationUnreadCountForView.value) return
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
  if (learningTaskIsScheduled(task)) {
    focusLearningTask(task)
    noticeMessage.value = `复习任务已安排在 ${formatDate(task.scheduledAt)} 开放。`
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

function learningAssignmentReviewNoteLabel(assignment) {
  if (assignment?.reviewStatus === 'REVISION_REQUIRED') return '教师返工要求'
  if (assignment?.reviewStatus === 'VERIFIED') return '教师确认说明'
  return '教师审核说明'
}

function learningAssignmentStartLabel(assignment, starting = false) {
  if (starting) return '启动中…'
  if (assignment?.status === 'ASSIGNED') return '接受并开始学习'
  if (assignment?.status === 'AWAITING_EVIDENCE') return '补充证据并继续'
  if (assignment?.status === 'RETRY_REQUIRED') {
    return assignment.reviewStatus === 'REVISION_REQUIRED' ? '按教师要求返工' : '重试课程作业'
  }
  return '按反馈继续学习'
}

function learningAssignmentFeedbackActionLabel(action) {
  return {
    COMMENT: '教师反馈',
    REQUEST_EVIDENCE: '补充证据',
    RECOMMEND_RETRY: '建议重试',
    RESCHEDULE: '重新安排',
  }[action] || action || '反馈'
}

function learningAssignmentFeedbackContinueLabel(feedback) {
  if (['REQUEST_EVIDENCE', 'RECOMMEND_RETRY'].includes(feedback?.action)) {
    return feedback?.status === 'ACKNOWLEDGED' ? '继续下一步' : '确认并开始下一步'
  }
  return '确认反馈'
}

function learningAssignmentFeedbackStatusLabel(feedback) {
  if (feedback?.status === 'RESOLVED') return '已完成'
  if (feedback?.status === 'ACKNOWLEDGED') {
    return ['REQUEST_EVIDENCE', 'RECOMMEND_RETRY'].includes(feedback.action)
      ? '已确认，待新证据' : '已确认'
  }
  return '待确认'
}

function learningAssignmentNotificationActionLabel(notification) {
  if (!notification) return '查看作业'
  if (notification.notificationType === 'ASSIGNED') return '接受并开始'
  if (notification.notificationType === 'EVIDENCE_REQUIRED') {
    return notification.assignmentStatus === 'AWAITING_EVIDENCE' ? '补证据并继续' : '查看补证据'
  }
  if (['RETRY_REQUIRED', 'REVISION_REQUIRED'].includes(notification.notificationType)) {
    return '重试/返工作业'
  }
  if (notification.notificationType === 'OVERDUE') return '查看逾期作业'
  if (notification.notificationType === 'REVIEW_REQUIRED') return '去教师确认'
  if (notification.notificationType === 'REVIEW_VERIFIED') return '查看确认结果'
  if (notification.notificationType === 'SUBMISSION_RECEIVED') return '查看提交物'
  if (notification.notificationType === 'FEEDBACK_ACKNOWLEDGED') return '查看确认回执'
  if (notification.notificationType === 'FEEDBACK') return '查看反馈'
  if (notification.notificationType === 'ACCEPTED') return '查看作业进度'
  if (notification.notificationType === 'COMPLETED') return '查看完成结果'
  return notification.assignmentStatus === 'ACCEPTED' ? '查看作业进度' : '查看作业'
}

function learningAssignmentOpenFeedback(assignment) {
  if (!assignment) return null
  const feedbacks = learningAssignmentFeedbackMap.value[assignment.id] || []
  return feedbacks.find((feedback) => ['OPEN', 'ACKNOWLEDGED'].includes(feedback.status)
    && ['REQUEST_EVIDENCE', 'RECOMMEND_RETRY'].includes(feedback.action))
    || feedbacks.find((feedback) => ['OPEN', 'ACKNOWLEDGED'].includes(feedback.status))
    || null
}

function learningAssignmentHasExecutableFeedback(assignment) {
  return ['REQUEST_EVIDENCE', 'RECOMMEND_RETRY'].includes(
    learningAssignmentOpenFeedback(assignment)?.action,
  )
}

// 通知必须回到同一份作业证据链，而不是把用户丢回泛化目标页。
async function focusLearningAssignmentNotificationAssignment(assignment, issue = '', expandFeedback = false) {
  if (!assignment?.id) return
  learningAssignmentCourseFilter.value = assignment.courseId || ''
  learningAssignmentLearnerFilter.value = assignment.learnerUserId || form.userId
  learningAssignmentIssueFilter.value = issue
  chatMode.value = false
  navigateConsoleSection('education')
  await ensureLearningAssignmentDetails(assignment.id)
  await nextTick()
  const assignmentElement = document.getElementById(`learning-assignment-${assignment.id}`)
  if (expandFeedback) {
    const feedbackDetails = [...(assignmentElement?.querySelectorAll('details.learning-assessment-history') || [])]
      .find((details) => details.querySelector('summary')?.textContent?.includes('教师反馈'))
    if (feedbackDetails) feedbackDetails.open = true
  }
  assignmentElement?.scrollIntoView({
    behavior: 'smooth', block: 'center',
  })
}

function learningAssignmentActionHint(assignment) {
  if (!assignment) return ''
  if (assignment.status === 'ASSIGNED') return '先接受作业，Agent 会按课程约束启动第一轮学习。'
  if (assignment.status === 'ACCEPTED') return '继续当前学习对话；完成后再提交作业内容。'
  if (assignment.status === 'AWAITING_EVIDENCE') {
    return '上一轮已完成，但还缺少可验证的测评证据；先补证据并继续。'
  }
  if (assignment.status === 'RETRY_REQUIRED') {
    return assignment.reviewStatus === 'REVISION_REQUIRED'
      ? '教师已退回返工；先按要求启动下一轮，完成后再提交。'
      : '上一轮未完成；先重试课程作业，避免提交不完整结果。'
  }
  if (assignment.status === 'OVERDUE') return '作业已逾期；提交已有成果，或等待教师重新安排。'
  if (assignment.status === 'COMPLETED' && assignment.reviewStatus === 'PENDING') {
    return '学习目标已达到作业完成条件，当前等待教师确认。'
  }
  if (assignment.status === 'COMPLETED' && assignment.reviewStatus === 'VERIFIED') {
    return '教师已确认结果；可在完整证据链中复盘。'
  }
  if (assignment.status === 'CANCELLED') return '作业已取消，不能继续提交或启动学习。'
  return ''
}

function assessmentRetrievalEvidenceLabel(attempt) {
  return (attempt?.retrievalEvidence || [])
    .map((evidence) => evidence.title || evidence.citation || evidence.documentId)
    .filter(Boolean)
    .join('、')
}

function chatAssessmentsForRun(runId) {
  return runId ? chatAssessmentEvidenceByRun.value[runId] || [] : []
}

function assessmentObservationLabel(attempt) {
  if (attempt?.evidenceSource === 'MANUAL_REVIEW') return '人工复核'
  if (attempt?.assessmentType === 'REVIEW') return '保持度复习'
  return 'Agent 形成性评价'
}

function formatMasteryDelta(attempt) {
  const before = Number(attempt?.masteryBefore)
  const after = Number(attempt?.masteryAfter)
  if (!Number.isFinite(before) || !Number.isFinite(after)) return '—'
  const delta = Math.round((after - before) * 100)
  return `${delta > 0 ? '+' : ''}${delta} 个百分点`
}

function learningGoalTitleForAssessment(attempt) {
  return learningGoals.value.find((goal) => goal.id === attempt?.learningGoalId)?.title
    || attempt?.conceptKey
    || '当前学习目标'
}

function learningAssignmentHasOpenIntervention(assignment) {
  if (!assignment || assignment.learnerUserId !== form.userId) return false
  return learningAssignmentHasOpenInterventionForView(assignment)
}

function learningAssignmentHasOpenInterventionForView(assignment) {
  if (!assignment) return false
  return (learningAssignmentFeedbackMap.value[assignment.id] || [])
    .some((feedback) => ['OPEN', 'ACKNOWLEDGED'].includes(feedback.status)
      && ['REQUEST_EVIDENCE', 'RECOMMEND_RETRY'].includes(feedback.action))
}

function collectLearningAssignmentRubric() {
  const dimensions = [
    ['contentCorrectnessScore', '内容正确性评分（1-5）'],
    ['evidenceQualityScore', '证据质量评分（1-5）'],
    ['transferReadinessScore', '迁移准备度评分（1-5）'],
  ]
  const rubric = {}
  for (const [key, label] of dimensions) {
    const raw = window.prompt(label, '5')
    if (raw === null) return null
    const score = Number(raw.trim())
    if (!Number.isInteger(score) || score < 1 || score > 5) {
      errorMessage.value = `${label}必须是 1 到 5 的整数。`
      return null
    }
    rubric[key] = score
  }
  return rubric
}

async function submitIndependentLearningEvaluation(assignment) {
  if (!assignment?.id || learningIndependentEvaluationSavingId.value) return
  const rubric = collectLearningAssignmentRubric()
  if (!rubric) return
  const note = window.prompt('可填写独立评价说明（可选）：', '')
  if (note === null) return
  learningIndependentEvaluationSavingId.value = assignment.id
  clearMessages()
  try {
    const evaluation = await api.submitIndependentLearningEvaluation(assignment.id, {
      ...rubric, note: note.trim() || null,
    })
    learningEvaluationQueue.value = learningEvaluationQueue.value
      .filter((item) => item.id !== assignment.id)
    learningAssignmentEvaluationMap.value = {
      ...learningAssignmentEvaluationMap.value,
      [assignment.id]: [evaluation, ...(learningAssignmentEvaluationMap.value[assignment.id] || [])],
    }
    noticeMessage.value = `已提交“${assignment.title}”的独立评价。`
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    learningIndependentEvaluationSavingId.value = ''
  }
}

function startLearningAssignmentSubmission(assignment) {
  if (!learningAssignmentSubmissionOpen(assignment)) return
  learningAssignmentSubmissionForm.assignmentId = assignment.id
  learningAssignmentSubmissionForm.content = ''
}

async function openChatLearningAssignmentSubmission(assignment) {
  if (!learningAssignmentSubmissionOpen(assignment)) return
  startLearningAssignmentSubmission(assignment)
  await nextTick()
  chatAssignmentSubmissionInputRef.value?.focus()
}

function learningAssignmentSubmissionOpen(assignment) {
  if (!assignment?.id || assignment.learnerUserId !== form.userId) return false
  if (['ACCEPTED', 'OVERDUE'].includes(assignment.status)) return true
  return assignment.status === 'COMPLETED'
    && assignment.reviewStatus === 'PENDING'
    && !(learningAssignmentSubmissionMap.value[assignment.id] || []).length
}

function closeLearningAssignmentSubmission() {
  learningAssignmentSubmissionForm.assignmentId = ''
  learningAssignmentSubmissionForm.content = ''
}

async function submitLearningAssignmentSubmission() {
  const assignmentId = learningAssignmentSubmissionForm.assignmentId
  const content = learningAssignmentSubmissionForm.content.trim()
  if (!assignmentId || !content || learningAssignmentSubmissionSavingId.value) return
  learningAssignmentSubmissionSavingId.value = assignmentId
  clearMessages()
  try {
    const submission = await api.submitLearningAssignmentSubmission(assignmentId, { content })
    const current = learningAssignmentSubmissionMap.value[assignmentId] || []
    learningAssignmentSubmissionMap.value = {
      ...learningAssignmentSubmissionMap.value,
      [assignmentId]: [submission, ...current.filter((item) => item.id !== submission.id)],
    }
    closeLearningAssignmentSubmission()
    if (activeEducationCourseId.value) await loadEducationCourseWorkspace(activeEducationCourseId.value)
    await loadLearningAssignmentNotifications()
    noticeMessage.value = '作业提交物已保存，并已通知教师查看。'
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    learningAssignmentSubmissionSavingId.value = ''
  }
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

async function refreshLearningAssignmentProgress(assignmentId) {
  if (!assignmentId) return
  try {
    const progress = await api.getLearningAssignmentProgress(assignmentId)
    if (progress) {
      learningAssignmentProgressMap.value = {
        ...learningAssignmentProgressMap.value,
        [assignmentId]: progress,
      }
    }
  } catch {
    // 反馈动作已经成功；进度指标等待下一次教育数据刷新，不覆盖主操作结果。
  }
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
  if (!assignment?.id || !feedback?.id || !['OPEN', 'ACKNOWLEDGED'].includes(feedback.status)
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
    await refreshLearningAssignmentProgress(assignment.id)
    await loadLearningAssignmentNotifications()
    if (['REQUEST_EVIDENCE', 'RECOMMEND_RETRY'].includes(feedback.action)) {
      // 可执行反馈的确认不是终点：直接启动同一份作业约束下的下一轮 Run，
      // 让“教师反馈 → 学习者行动 → 新证据”在一次点击内连起来。
      await startLearningAssignment(assignment)
      return
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
  const rubric = collectLearningAssignmentRubric()
  if (!rubric) return
  learningAssignmentReviewSavingId.value = assignment.id
  clearMessages()
  try {
    await api.reviewLearningAssignment(assignment.id, {
      decision: 'VERIFY', note: note.trim() || null, ...rubric,
    })
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
  const rubric = collectLearningAssignmentRubric()
  if (!rubric) return
  learningAssignmentReviewSavingId.value = assignment.id
  clearMessages()
  try {
    await api.reviewLearningAssignment(assignment.id, {
      decision: 'RETURN', note: note.trim(), ...rubric,
    })
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
  const refreshedAssignment = await refreshLearningAssignmentDetails(notification.learningAssignmentId)
  if (refreshedAssignment) assignment = refreshedAssignment
  if (!assignment) {
    await loadEducationData()
    assignment = learningAssignments.value.find((item) => item.id === notification.learningAssignmentId)
  }
  if (!assignment) {
    noticeMessage.value = '通知对应的课程作业已不在当前列表中，请刷新教育状态。'
    return
  }
  const isLearner = assignment.learnerUserId === form.userId
  const isTeacher = assignment.teacherUserId === form.userId
  const type = notification.notificationType

  // 学习者通知优先落到可执行动作；逾期和已确认结果只打开证据链，避免误导为可启动 Run。
  if (isLearner && type === 'ASSIGNED' && assignment.status === 'ASSIGNED') {
    await startLearningAssignment(assignment)
    return
  }
  if (isLearner && type === 'EVIDENCE_REQUIRED' && assignment.status === 'AWAITING_EVIDENCE') {
    await startLearningAssignment(assignment)
    return
  }
  if (isLearner && ['RETRY_REQUIRED', 'REVISION_REQUIRED'].includes(type)
    && assignment.status === 'RETRY_REQUIRED') {
    await startLearningAssignment(assignment)
    return
  }
  if (type === 'OVERDUE') {
    await focusLearningAssignmentNotificationAssignment(assignment, 'overdue')
    noticeMessage.value = `已打开逾期作业“${assignment.title}”；提交已有成果，或等待教师重新安排。`
    return
  }
  if (type === 'REVIEW_REQUIRED' && isTeacher) {
    await focusLearningAssignmentNotificationAssignment(assignment, 'review')
    noticeMessage.value = `已定位待教师确认作业“${assignment.title}”。`
    return
  }
  if (type === 'SUBMISSION_RECEIVED' && isTeacher) {
    await focusLearningAssignmentNotificationAssignment(assignment, 'submitted')
    noticeMessage.value = `已定位“${assignment.title}”的提交物和复核入口。`
    return
  }
  if (type === 'REVIEW_VERIFIED' && isLearner) {
    await focusLearningAssignmentNotificationAssignment(assignment)
    if (assignment.learningGoalId) {
      const goal = learningGoals.value.find((item) => item.id === assignment.learningGoalId)
      if (goal) await selectLearningGoal(goal, false)
    }
    noticeMessage.value = `教师已确认“${assignment.title}”；可查看当前目标并继续保持度复习。`
    return
  }
  if (['FEEDBACK', 'FEEDBACK_ACKNOWLEDGED'].includes(type)) {
    await focusLearningAssignmentNotificationAssignment(assignment, '', true)
    noticeMessage.value = type === 'FEEDBACK_ACKNOWLEDGED'
      ? `已打开课程作业“${assignment.title}”的反馈确认回执。`
      : `已打开课程作业“${assignment.title}”的教师反馈。`
    return
  }
  if (['EVIDENCE_REQUIRED', 'RETRY_REQUIRED', 'REVISION_REQUIRED'].includes(type)) {
    await focusLearningAssignmentNotificationAssignment(assignment,
      type === 'EVIDENCE_REQUIRED' ? 'evidence' : (type === 'REVISION_REQUIRED' ? 'revision' : 'retry'))
    noticeMessage.value = `已打开课程作业“${assignment.title}”的下一步处理入口。`
    return
  }
  if (type === 'ACCEPTED') {
    await focusLearningAssignmentNotificationAssignment(assignment, 'accepted')
    noticeMessage.value = `已打开课程作业“${assignment.title}”的学习进度。`
    return
  }
  if (type === 'COMPLETED') {
    await focusLearningAssignmentNotificationAssignment(assignment,
      assignment.reviewStatus === 'PENDING' ? 'review' : '')
    noticeMessage.value = `已打开课程作业“${assignment.title}”的完成结果。`
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

/** Run 控制台选择画像后冻结同一份课程三元组，和服务端解析规则保持一致。 */
function syncEducationRunProfile() {
  const profile = learnerProfiles.value.find((item) => item.id === form.education.learnerProfileId)
  if (!profile) return
  form.education.subject = profile.subject || ''
  form.education.gradeLevel = profile.gradeLevel || ''
  form.education.curriculumVersion = profile.curriculumVersion || ''
  const selectedGoal = learningGoals.value.find((goal) => goal.id === form.education.learningGoalId)
  if (selectedGoal && selectedGoal.learnerProfileId !== profile.id) {
    form.education.learningGoalId = ''
    form.education.conceptKey = ''
  }
}

function applyLearnerProfileToChat(profile) {
  if (!profile) return
  const boundAssignment = chatEducation.learningAssignmentId
    ? learningAssignments.value.find((assignment) => assignment.id === chatEducation.learningAssignmentId)
    : null
  if (boundAssignment && boundAssignment.learnerProfileId !== profile.id) {
    chatEducation.learningAssignmentId = ''
  }
  chatEducation.learnerProfileId = profile.id
  chatEducation.subject = profile.subject || ''
  chatEducation.gradeLevel = profile.gradeLevel || ''
  chatEducation.curriculumVersion = profile.curriculumVersion || ''
  const course = ensureChatCourseMatchesProfile(profile)
  form.education.courseId = course?.id || ''
}

function ensureChatCourseMatchesProfile(profile = activeLearnerProfile.value) {
  const course = educationCourses.value.find((item) => item.id === chatEducation.courseId)
  const matchesProfile = Boolean(course && profile
    && course.status === 'ACTIVE'
    && course.ownerUserId !== form.userId
    && course.subject === profile.subject
    && course.gradeLevel === profile.gradeLevel
    && course.curriculumVersion === profile.curriculumVersion)
  if (!matchesProfile) {
    chatEducation.courseId = ''
    return null
  }
  return course
}

async function selectChatCourse() {
  const course = availableChatCourses.value.find((item) => item.id === chatEducation.courseId)
  const boundAssignment = chatEducation.learningAssignmentId
    ? learningAssignments.value.find((assignment) => assignment.id === chatEducation.learningAssignmentId)
    : null
  if (boundAssignment && boundAssignment.courseId !== (course?.id || null)) {
    chatEducation.learningAssignmentId = ''
  }
  const mustStartNewConversation = chatUserMessages.value.length > 0
  const draft = chatInput.value
  if (!course) {
    chatEducation.courseId = ''
    form.education.courseId = ''
    if (mustStartNewConversation) {
      const created = await createChatConversation()
      if (created) {
        setChatInput(draft)
        noticeMessage.value = '已解除课程实例绑定，并创建新学习对话以隔离历史上下文。'
      }
    }
    return
  }
  chatEducation.subject = course.subject
  chatEducation.gradeLevel = course.gradeLevel
  chatEducation.curriculumVersion = course.curriculumVersion
  form.education.courseId = course.id
  if (mustStartNewConversation) {
    const created = await createChatConversation()
    if (!created) return
    setChatInput(draft)
    noticeMessage.value = `已锁定课程约束：${course.code} · ${course.title}；已创建独立学习对话。`
    return
  }
  noticeMessage.value = `已锁定课程约束：${course.code} · ${course.title}`
}

async function selectLearnerProfile(profile, notify = true) {
  if (!profile) return
  activeLearnerProfile.value = profile
  applyLearnerProfileToEducationRun(profile)
  applyLearnerProfileToChat(profile)
  await refreshLearnerMastery(profile.id)
  const nextGoal = learningGoals.value.find((goal) => goal.learnerProfileId === profile.id
    && goal.status === 'ACTIVE')
    || learningGoals.value.find((goal) => goal.learnerProfileId === profile.id)
  if (nextGoal && activeLearningGoal.value?.id !== nextGoal.id) {
    await selectLearningGoal(nextGoal, false)
  }
  if (notify) noticeMessage.value = `已切换学习上下文：${profile.subject} · ${profile.gradeLevel}`
}

async function selectLearningGoal(goal, notify = true) {
  if (!goal) return
  const boundAssignment = chatEducation.learningAssignmentId
    ? learningAssignments.value.find((assignment) => assignment.id === chatEducation.learningAssignmentId)
    : null
  if (boundAssignment && boundAssignment.learningGoalId !== goal.id) {
    chatEducation.learningAssignmentId = ''
  }
  activeLearningGoal.value = goal
  form.education.learningGoalId = goal.id
  form.education.learnerProfileId = goal.learnerProfileId
  form.education.conceptKey = goal.conceptKey
  chatEducation.learningGoalId = goal.id
  chatEducation.learnerProfileId = goal.learnerProfileId
  chatEducation.conceptKey = goal.conceptKey
  const profile = learnerProfiles.value.find((item) => item.id === goal.learnerProfileId)
  if (profile) {
    const profileChanged = activeLearnerProfile.value?.id !== profile.id
    activeLearnerProfile.value = profile
    form.education.subject = profile.subject || ''
    form.education.gradeLevel = profile.gradeLevel || ''
    form.education.curriculumVersion = profile.curriculumVersion || ''
    applyLearnerProfileToChat(profile)
    chatEducation.learningGoalId = goal.id
    chatEducation.conceptKey = goal.conceptKey
    if (profileChanged) await refreshLearnerMastery(profile.id)
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
    showQuickLearningGoalForm.value = false
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
  if (!assignment?.id || learningAssignmentAcceptingId.value) return false
  if (notifyEducationActionBlocked(learningAssignmentSourceBlockReason(assignment))) return false
  learningAssignmentAcceptingId.value = assignment.id
  clearMessages()
  try {
    const assignmentAttemptKey = `learning-assignment-${assignment.id}-${assignment.updatedAt || assignment.createdAt || assignment.status}`
    const started = await api.startLearningAssignment(
      assignment.id,
      { maxTurns: chatMaxTurns.value },
      assignmentAttemptKey,
    )
    await bindLearningAssignmentToChat(started.assignment)
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
    return true
  } catch (error) {
    errorMessage.value = errorText(error)
    return false
  } finally {
    learningAssignmentAcceptingId.value = ''
  }
}

/** 作业入口创建的后续消息必须继续携带同一作业约束，不能退回到仅按画像推断。 */
async function bindLearningAssignmentToChat(assignment) {
  if (!assignment?.id) return
  chatEducation.enabled = true
  chatEducation.learningAssignmentId = assignment.id
  chatEducation.learnerProfileId = assignment.learnerProfileId || ''
  chatEducation.learningGoalId = assignment.learningGoalId || ''
  chatEducation.courseId = assignment.courseId || ''
  chatEducation.subject = assignment.subject || ''
  chatEducation.gradeLevel = assignment.gradeLevel || ''
  chatEducation.curriculumVersion = assignment.curriculumVersion || ''
  chatEducation.conceptKey = assignment.conceptKey || ''
  form.education.learnerProfileId = chatEducation.learnerProfileId
  form.education.learningGoalId = chatEducation.learningGoalId
  form.education.courseId = chatEducation.courseId
  form.education.subject = chatEducation.subject
  form.education.gradeLevel = chatEducation.gradeLevel
  form.education.curriculumVersion = chatEducation.curriculumVersion
  form.education.conceptKey = chatEducation.conceptKey
  const profile = learnerProfiles.value.find((item) => item.id === assignment.learnerProfileId)
  if (profile) {
    activeLearnerProfile.value = profile
    await refreshLearnerMastery(profile.id)
  }
  const goal = learningGoals.value.find((item) => item.id === assignment.learningGoalId)
  if (goal) await selectLearningGoal(goal, false)
  // selectLearningGoal may reapply the profile defaults; the assignment remains
  // the authoritative binding for this conversation, so restore it explicitly.
  chatEducation.learningAssignmentId = assignment.id
  chatEducation.courseId = assignment.courseId || ''
  chatEducation.learningGoalId = assignment.learningGoalId || ''
  chatEducation.conceptKey = assignment.conceptKey || ''
  form.education.courseId = chatEducation.courseId
  form.education.learningGoalId = chatEducation.learningGoalId
  form.education.conceptKey = chatEducation.conceptKey
}

function focusLearnerCourseAssignment(assignment) {
  if (!assignment?.id) return
  learningAssignmentCourseFilter.value = assignment.courseId || ''
  learningAssignmentLearnerFilter.value = assignment.learnerUserId || form.userId
  chatMode.value = false
  navigateConsoleSection('education')
  void nextTick(() => document.getElementById(`learning-assignment-${assignment.id}`)
    ?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
}

function focusLearningTask(task) {
  if (!task?.id) return
  chatMode.value = false
  navigateConsoleSection('education')
  void nextTick(() => document.getElementById(`learning-task-${task.id}`)
    ?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
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
  if (notifyEducationActionBlocked(learningGoalSourceBlockReason(recommendation.learningGoalId))) return
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
      courseId: chatEducation.courseId || null,
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
  if (notifyEducationActionBlocked(learningTaskSourceBlockReason(task))) return
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
    if (runId) {
      if (task.status === 'AWAITING_EVIDENCE') {
        // 待补证据任务已经有一个成功 Run；把学习者直接带到复核表单，
        // 不要求用户先理解“打开 Run 详情”这一内部概念。
        await openRunPanel(runId, false)
        await nextTick()
        document.querySelector('.chat-run-panel .manual-assessment-panel')
          ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      } else {
        void selectRun(runId, false, false)
      }
    }
    await loadLearningTasks()
    await loadLearningNotifications()
    noticeMessage.value = task.status === 'AWAITING_EVIDENCE'
      ? `已打开证据复核：${result.task.title}`
      : `已开始学习任务：${result.task.title}`
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

async function selectChatLearnerProfile() {
  const profile = learnerProfiles.value.find((item) => item.id === chatEducation.learnerProfileId)
  if (!profile) return
  const previousProfileId = activeLearnerProfile.value?.id || ''
  if (previousProfileId && previousProfileId !== profile.id && chatUserMessages.value.length > 0) {
    const draft = chatInput.value
    const created = await createChatConversation()
    if (!created) {
      chatEducation.learnerProfileId = previousProfileId
      return
    }
    setChatInput(draft)
    noticeMessage.value = '已切换学习者画像，并创建新学习对话以隔离历史上下文。'
  }
  await selectLearnerProfile(profile, false)
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
    if (educationSourceForm.documentId === document.id) educationSourceForm.documentId = ''
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
    chatEducation.enabled = true
    await refreshLearnerMastery(profile.id)
    noticeMessage.value = '学习者画像已保存；教育 Agent 会按该画像选择课程内容和教学策略。'
    educationError.value = ''
  } catch (error) {
    educationError.value = errorText(error)
  } finally {
    educationLoading.value = false
  }
}

async function deleteLearnerProfile(profile) {
  if (!profile?.id || learnerProfileDeletingId.value) return
  if (typeof window !== 'undefined'
    && !window.confirm(`确认删除学习画像“${profile.subject} · ${profile.gradeLevel} · ${profile.curriculumVersion}”吗？\n\n已产生的学习目标、测评和历史 Run 会保留，画像只会从当前可选列表中移除。`)) return
  clearMessages()
  learnerProfileDeletingId.value = profile.id
  try {
    await api.deleteLearnerProfile(profile.id)
    const remainingProfiles = learnerProfiles.value.filter((item) => item.id !== profile.id)
    learnerProfiles.value = remainingProfiles
    if (activeLearnerProfile.value?.id === profile.id) {
      activeLearningGoal.value = null
      learningGoalAssessments.value = []
      learningRecommendation.value = null
      form.education.learningGoalId = ''
      form.education.conceptKey = ''
      chatEducation.learningGoalId = ''
      chatEducation.conceptKey = ''
      const nextProfile = remainingProfiles[0] || null
      if (nextProfile) {
        await selectLearnerProfile(nextProfile, false)
      } else {
        activeLearnerProfile.value = null
        learnerMastery.value = []
        form.education.learnerProfileId = ''
        form.education.subject = ''
        form.education.gradeLevel = ''
        form.education.curriculumVersion = ''
        form.education.conceptKey = ''
        form.education.courseId = ''
        chatEducation.learnerProfileId = ''
        chatEducation.subject = ''
        chatEducation.gradeLevel = ''
        chatEducation.curriculumVersion = ''
        chatEducation.conceptKey = ''
        chatEducation.courseId = ''
      }
    }
    noticeMessage.value = `学习画像“${profile.subject} · ${profile.gradeLevel}”已删除`
    educationError.value = ''
  } catch (error) {
    educationError.value = errorText(error)
  } finally {
    learnerProfileDeletingId.value = ''
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
    cacheRunContextEvidence(detail)
    syncActiveConversationEducationContext(detail.run)
    auditEvents.value = events
    startRunEventStream(runId)
    if (isTerminal(detail.run.status)) void refreshEducationAfterChatRun(runId, detail.run).catch(() => {})
  } catch (error) {
    if (requestToken === runDetailRequestToken) errorMessage.value = errorText(error)
  } finally {
    if (showLoading && requestToken === runDetailRequestToken) detailLoading.value = false
  }
}

/** 打开历史教育会话时恢复它冻结的上下文，避免浏览器残留设置触发跨课程请求。 */
function syncActiveConversationEducationContext(run) {
  if (!run?.educationMode || !activeConversation.value?.messages?.some((message) => message.runId === run.id)) return
  chatEducation.enabled = true
  chatEducation.learnerProfileId = run.educationLearnerProfileId || ''
  chatEducation.learningGoalId = run.educationLearningGoalId || ''
  chatEducation.courseId = run.educationCourseId || ''
  chatEducation.subject = run.educationSubject || ''
  chatEducation.gradeLevel = run.educationGradeLevel || ''
  chatEducation.curriculumVersion = run.educationCurriculumVersion || ''
  chatEducation.conceptKey = run.educationConceptKey || ''
  chatEducation.pedagogicalMode = run.educationPedagogicalMode || 'AUTO'
  form.education.courseId = chatEducation.courseId
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
      cacheRunContextEvidence(data)
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
  // 聊天页不只更新回复文本，也要同步本轮形成性评价及课程作业状态。
  // 这条刷新是异步的，避免证据接口短暂不可用时影响已完成的聊天回复。
  void refreshEducationAfterChatRun(runId).catch(() => {})
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

/**
 * Run 终态和教育作业收敛在服务端同一条业务链路中，但两者的事务提交可能与
 * SSE/聊天刷新竞速。对当前作业再读几次权威详情，避免学生在短暂旧快照上看到
 * “提交作业内容”，随后才变成“重试课程作业”。
 */
async function refreshLearningAssignmentAfterTerminalRun(assignmentId) {
  if (!assignmentId) return null
  let latest = null
  for (let attempt = 0; attempt < 3; attempt += 1) {
    latest = await api.getLearningAssignment(assignmentId).catch(() => latest)
    if (!latest || !['ACCEPTED', 'OVERDUE'].includes(latest.status)) return latest
    if (attempt < 2) {
      await new Promise((resolve) => window.setTimeout(resolve, 180 * (attempt + 1)))
    }
  }
  return latest
}

/**
 * 将一次教育 Run 的评价结果局部回写到聊天页。这里不调用 loadEducationData，
 * 以免每轮对话重新加载整套课程工作台；只更新学习者此刻能感知到的状态。
 */
function refreshEducationAfterChatRun(runId, runSnapshot = selectedRun.value?.run) {
  if (!runId) return Promise.resolve()
  const inFlight = educationChatRefreshes.get(runId)
  if (inFlight) return inFlight
  const refresh = (async () => {
    let run = runSnapshot
    if (!run || run.id !== runId || !isTerminal(run.status)) {
      const detail = await api.getRun(runId)
      run = detail?.run
    }
    if (!run?.educationMode || !isTerminal(run.status) || !run.educationLearningGoalId) return

    const goalId = run.educationLearningGoalId
    const profileId = run.educationLearnerProfileId
    const assignmentId = run.educationLearningAssignmentId
    let [assessments, recommendation, mastery, tasks, assignments] = await Promise.all([
      api.listGoalAssessments(goalId),
      api.getGoalRecommendation(goalId).catch(() => null),
      profileId ? api.listLearnerMastery(profileId).catch(() => null) : Promise.resolve(null),
      api.listLearningTasks().catch(() => null),
      assignmentId ? api.listLearningAssignments().catch(() => null) : Promise.resolve(null),
    ])
    const runAttempts = assessments.filter((attempt) => attempt.runId === runId)
    chatAssessmentEvidenceByRun.value = {
      ...chatAssessmentEvidenceByRun.value,
      [runId]: runAttempts,
    }
    if (activeLearningGoal.value?.id === goalId) {
      learningGoalAssessments.value = assessments
      if (recommendation) learningRecommendation.value = recommendation
    }
    if (recommendation) {
      learningGoalRecommendationMap.value = {
        ...learningGoalRecommendationMap.value,
        [goalId]: recommendation,
      }
    }
    if (profileId && activeLearnerProfile.value?.id === profileId && mastery) {
      learnerMastery.value = mastery
    }
    if (tasks) learningTasks.value = tasks
    // 通知原本只按 15 秒轮询；Run 终态已经是最可靠的状态边界，
    // 此刻立即刷新可让用户马上看到“待补证据 / 新复习任务”的下一步。
    await Promise.all([
      loadLearningNotifications(),
      loadLearningAssignmentNotifications(),
    ])
    if (!assignmentId || !assignments) return

    const authoritativeAssignment = await refreshLearningAssignmentAfterTerminalRun(assignmentId)
    if (authoritativeAssignment) {
      assignments = assignments.map((item) => item.id === assignmentId
        ? authoritativeAssignment : item)
    }
    learningAssignments.value = assignments
    const assignment = assignments.find((item) => item.id === assignmentId)
    if (!assignment) {
      if (chatEducation.learningAssignmentId === assignmentId) chatEducation.learningAssignmentId = ''
      return
    }
    // 终态作业同样需要读取最后一轮证据和提交物；否则列表虽显示“已完成”，
    // 但用户仍看不到刚刚产生的测评、掌握度和教师反馈。
    const [progress, evidence, submissions, feedback] = await Promise.all([
      api.getLearningAssignmentProgress(assignmentId).catch(() => null),
      api.getLearningAssignmentEvidence(assignmentId).catch(() => null),
      api.listLearningAssignmentSubmissions(assignmentId).catch(() => null),
      api.listLearningAssignmentFeedback(assignmentId).catch(() => null),
    ])
    if (progress) {
      learningAssignmentProgressMap.value = {
        ...learningAssignmentProgressMap.value,
        [assignmentId]: progress,
      }
    }
    if (evidence) {
      learningAssignmentEvidenceMap.value = {
        ...learningAssignmentEvidenceMap.value,
        [assignmentId]: evidence,
      }
    }
    if (submissions) {
      learningAssignmentSubmissionMap.value = {
        ...learningAssignmentSubmissionMap.value,
        [assignmentId]: submissions,
      }
    }
    if (feedback) {
      learningAssignmentFeedbackMap.value = {
        ...learningAssignmentFeedbackMap.value,
        [assignmentId]: feedback,
      }
    }
    // Run 已进入终态后，服务端会把课程作业同步收敛为待重试、待补证据或待教师确认。
    // 清理已结束的聊天绑定，避免回到课程页时继续沿用上一轮旧上下文；失败/超时作业
    // 必须保留在作业列表中，才能让学生看到“重试课程作业”而不是提交未完成结果。
    if (['COMPLETED', 'CANCELLED', 'RETRY_REQUIRED', 'AWAITING_EVIDENCE'].includes(assignment.status)
      && chatEducation.learningAssignmentId === assignmentId) {
      chatEducation.learningAssignmentId = ''
    }
  })()
  educationChatRefreshes.set(runId, refresh)
  return refresh.finally(() => educationChatRefreshes.delete(runId))
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
  await loadCurrentUser()
  if (showLocalDemoLogin.value) return
  if (!isAdminRole.value) {
    await nextTick()
    navigateConsoleSection('education', 'auto')
  }
  if (isAdminRole.value) {
    await Promise.all([
      loadDashboard(),
      loadHealth(),
      loadModelConfig(),
      loadEmbeddingConfig(),
      loadWorkspace(),
      loadTenantPolicy(),
      loadApiKeys(),
      loadLocalWorkspaces(),
    ])
  } else {
    await Promise.all([loadDashboard(), loadWorkspace()])
  }
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
  <section v-if="showLocalDemoLogin" class="local-demo-login" aria-labelledby="local-demo-login-title">
    <div class="local-demo-login-card">
      <div class="local-demo-login-brand">
        <div class="brand-mark" aria-hidden="true"><Sparkles :size="17" :stroke-width="1.8" /></div>
        <div><strong>Ming Harness</strong><span>LOCAL DEMO LOGIN</span></div>
      </div>
      <p class="eyebrow">选择体验身份</p>
      <h1 id="local-demo-login-title">你要以什么身份进入系统？</h1>
      <p class="local-demo-login-help">本地演示账号只用于体验不同工作台。正式环境会使用 API Key 或企业 OIDC 登录，角色由服务端权限决定。</p>
      <div class="local-demo-user-list">
        <button v-for="user in localDemoUsers" :key="user.role" class="local-demo-user-card" type="button" :disabled="localDemoLoginBusy" @click="beginLocalDemoSession(user)">
          <span class="local-demo-user-icon"><ShieldCheck v-if="user.role === 'ADMIN'" :size="17" /><PenLine v-else-if="user.role === 'TEACHER'" :size="17" /><BookOpen v-else :size="17" /></span>
          <span><strong>{{ user.name }}</strong><small>{{ user.detail }}</small><em>{{ user.userId }}</em></span>
          <ArrowRight :size="15" />
        </button>
      </div>
      <p v-if="localDemoLoginError" class="local-demo-login-error">{{ localDemoLoginError }}</p>
    </div>
  </section>
  <template v-else>
  <template v-if="chatMode">
    <div class="chat-app">
      <header class="chat-topbar">
        <div class="chat-brand">
          <div class="brand-mark" aria-hidden="true"><Sparkles :size="17" :stroke-width="1.8" /></div>
          <div><strong>学习知识库 Agent</strong><span>COURSE + LEARNER STATE</span></div>
        </div>
        <div class="chat-topbar-actions">
          <button class="command-palette-trigger" type="button" title="打开命令面板（⌘/Ctrl + K）" @click="openCommandPalette"><Command :size="14" /><span>⌘K</span><em>命令</em></button>
          <button class="theme-toggle" type="button" :aria-label="theme === 'dark' ? '切换到白天模式' : '切换到黑夜模式'" @click="toggleTheme">
            <Sun v-if="theme === 'dark'" :size="15" aria-hidden="true" /><Moon v-else :size="15" aria-hidden="true" />{{ theme === 'dark' ? '白天' : '黑夜' }}
          </button>
          <div v-if="currentUser && localDemoSessionActive" class="current-user-chip" :title="`${currentUser.userId} · ${currentUser.tenantId}`">
            <span>{{ roleLabel(currentUser.primaryRole) }}</span><strong>{{ currentUser.userId }}</strong>
            <select v-if="currentUser.localDemo" v-model="demoRole" aria-label="切换本地演示角色" :disabled="demoRoleSwitching" @change="switchDemoRole(demoRole)">
              <option value="STUDENT">学生</option>
              <option value="TEACHER">老师</option>
              <option value="ADMIN">管理员</option>
            </select>
            <button v-if="currentUser.localDemo" class="current-user-logout" type="button" :disabled="localDemoLoginBusy" @click="endLocalDemoSession">退出</button>
          </div>
          <button class="secondary-button chat-console-button" type="button" :title="roleWorkspaceDetail" @click="chatMode = false; navigateConsoleSection('education')"><Settings2 :size="15" />{{ roleWorkspaceTitle }}</button>
        </div>
      </header>

      <div v-if="errorMessage" :key="`error-${errorMessage}`" class="message error-message chat-message-banner">{{ errorMessage }}</div>
      <div v-if="noticeMessage" :key="`notice-${noticeMessage}`" class="message notice-message chat-message-banner">{{ noticeMessage }}</div>
      <div v-if="educationRuntimeDiagnostic" class="message education-runtime-message chat-message-banner" role="alert">{{ educationRuntimeDiagnostic }}</div>

      <div class="chat-layout">
        <aside class="conversation-sidebar">
          <nav class="chat-primary-nav" aria-label="工作台导航">
            <button class="chat-primary-nav-item chat-primary-nav-item-primary" type="button" :disabled="chatLoading || chatSending || chatUploading" @click="createChatConversation">
              <MessageSquarePlus :size="15" /><span>新建学习任务</span><kbd>⌘N</kbd>
            </button>
            <button v-if="!isAdminRole" class="chat-primary-nav-item chat-primary-nav-item-education" type="button" @click="openRoleWorkspaceEntry(isTeacherRole ? 'teacher-course' : 'student-plan')">
              <Sparkles :size="15" /><span>{{ isTeacherRole ? '课程运营' : '学习计划与目标' }}</span>
            </button>
            <button v-if="!isAdminRole" class="chat-primary-nav-item" type="button" @click="openRoleWorkspaceEntry(isTeacherRole ? 'teacher-roster' : 'student-profile')">
              <CircleDot :size="15" /><span>{{ isTeacherRole ? '课程与学生' : '学习档案' }}</span>
            </button>
            <button v-if="!isAdminRole" class="chat-primary-nav-item" type="button" @click="openRoleWorkspaceEntry(isTeacherRole ? 'teacher-review' : 'student-evidence')">
              <Check :size="15" /><span>{{ isTeacherRole ? '作业复核' : '测评证据与反馈' }}</span>
            </button>
            <button v-if="isAdminRole" class="chat-primary-nav-item" type="button" @click="chatMode = false; navigateConsoleSection('runtime')">
              <ShieldCheck :size="15" /><span>系统治理与审计</span>
            </button>
          </nav>
          <section v-if="isLearnerOnlyRole" class="learning-sidebar-contract" :class="{ ready: educationAgentReady }" aria-label="当前学习契约">
            <div class="learning-sidebar-contract-heading">
              <div><p class="eyebrow">CURRENT LEARNING CONTRACT</p><strong>当前学习契约</strong></div>
              <span><i></i>{{ educationAgentReady ? '已生效' : '待补齐' }}</span>
            </div>
            <template v-if="activeLearnerProfile">
              <strong class="learning-sidebar-contract-course">{{ activeChatCourse?.title || `${activeLearnerProfile.subject} · ${activeLearnerProfile.gradeLevel}` }}</strong>
              <p>{{ activeChatCourse?.code ? `${activeChatCourse.code} · ` : '' }}{{ activeLearnerProfile.curriculumVersion }}</p>
              <div class="learning-sidebar-contract-metrics">
                <div><small>课程资料</small><strong>{{ currentEducationSourceCount }} <em>份</em></strong></div>
                <div><small>学习目标</small><strong>{{ activeLearningGoal ? '已绑定' : '待设定' }}</strong></div>
              </div>
              <section class="learning-sidebar-learning-state" aria-label="学习状态摘要">
                <div class="learning-sidebar-learning-state-heading">
                  <small>学习状态</small>
                  <strong :class="`is-${learnerStateDiagnosis.state}`">{{ learnerStateDiagnosis.title }}</strong>
                </div>
                <div v-if="Number.isFinite(learnerStateDiagnosis.currentMastery) && Number.isFinite(learnerStateDiagnosis.targetMastery)" class="learning-sidebar-mastery">
                  <div><small>当前掌握度</small><b>{{ formatRate(learnerStateDiagnosis.currentMastery) }}</b></div>
                  <div><small>目标掌握度</small><b>{{ formatRate(learnerStateDiagnosis.targetMastery) }}</b></div>
                  <i aria-hidden="true"><span :style="{ width: `${Math.min(100, Math.max(0, learnerStateDiagnosis.currentMastery / Math.max(learnerStateDiagnosis.targetMastery, 0.01) * 100))}%` }"></span></i>
                </div>
                <p>{{ learnerStateDiagnosis.detail }}</p>
                <button class="learning-sidebar-next-action" type="button" :disabled="chatSending || chatUploading || (learningOverviewNextAction.kind === 'task' && learningTaskStartingId)" @click="runLearningOverviewNextAction">
                  <span><small>下一步</small><strong>{{ learningOverviewNextAction.label }}</strong></span>
                  <ArrowUp :size="12" />
                </button>
              </section>
              <button type="button" @click="educationAgentReady ? (showChatAgentSettings = true) : openEducationAgentSetup()">{{ educationAgentReady ? '调整本轮约束' : educationSetupActionLabel }} <ArrowUp :size="12" /></button>
            </template>
            <template v-else>
              <p class="learning-sidebar-contract-empty">先建立学习者画像，才能把课程版本、资料范围和后续掌握度绑定到同一个学习任务。</p>
              <button type="button" @click="openEducationAgentSetup">建立学习画像 <ArrowUp :size="12" /></button>
            </template>
          </section>
          <section v-if="teacherEducationCourses.length" class="chat-teaching-brief" aria-label="教师课程待办">
            <div class="chat-teaching-brief-heading">
              <div><p class="eyebrow">TEACHING CONTROL</p><strong>教师课程待办</strong></div>
              <span>{{ teacherEducationCourses.length }} 门课程</span>
            </div>
            <div class="chat-teaching-brief-stats">
              <div><strong>{{ teacherCoursePendingCount }}</strong><small>待处理</small></div>
              <div><strong>{{ teacherActiveLearnerCount }}</strong><small>活跃学习者</small></div>
            </div>
            <p>{{ teacherCoursePendingCount ? '有证据、返工或第二评分待处理，建议先处理干预队列。' : '课程状态正常；可以继续布置下一项学习任务。' }}</p>
            <button type="button" @click="chatMode = false; navigateConsoleSection('education')">打开课程工作台 <ArrowUp :size="13" /></button>
          </section>
          <div class="conversation-sidebar-heading">
            <div><p class="eyebrow">LEARNING TRAJECTORY</p><h2>学习轨迹</h2></div>
          </div>
          <label class="conversation-search">
            <span class="sr-only">搜索学习任务</span>
            <input v-model="conversationQuery" type="search" placeholder="搜索学习任务…" aria-label="搜索学习任务" @keydown.esc="conversationQuery = ''" />
            <button v-if="conversationQuery" type="button" aria-label="清除学习任务搜索" @click="conversationQuery = ''"><X :size="14" /></button>
          </label>
          <div v-if="chatLoading && !learningConversations.length" class="chat-sidebar-empty">正在读取学习任务…</div>
          <div v-else-if="!learningConversations.length" class="chat-sidebar-empty">还没有学习任务</div>
          <div v-else-if="!filteredConversations.length" class="chat-sidebar-empty">没有匹配的学习任务<br /><small>试试标题、学习目标或知识点</small></div>
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
                <strong>{{ learningConversationTitle(conversation) }}</strong>
                <small v-if="conversationLearningContext(conversation)" class="conversation-learning-context"><BookOpen :size="11" />{{ conversationLearningContext(conversation) }}</small>
                <small>{{ conversation.lastMessagePreview || '从一个学习问题开始' }}</small>
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
              <p class="eyebrow">COURSE-CONSTRAINED LEARNING AGENT</p>
              <form v-if="showConversationRename" class="conversation-rename-form" @submit.prevent="renameActiveConversation">
                <input ref="conversationRenameInputRef" v-model="conversationRenameValue" maxlength="255" :disabled="conversationRenaming" aria-label="对话标题" @keydown.esc.prevent="cancelConversationRename" />
                <button class="secondary-button" type="button" :disabled="conversationRenaming" @click="cancelConversationRename">取消</button>
                <button class="primary-button" type="submit" :disabled="conversationRenaming">{{ conversationRenaming ? '保存中…' : '保存' }}</button>
              </form>
              <h1 v-else>{{ learningConversationTitle(activeConversation?.conversation) }}</h1>
              <p class="chat-heading-meta">不是通用问答：Agent 会先锁定课程资料，再结合你的掌握度选择讲解、诊断、练习或复习，并把结果回写到学习档案。</p>
            </div>
            <div class="chat-heading-actions">
              <button class="chat-education-status-chip" type="button" title="打开教育工作台配置课程与学习者" @click="chatMode = false; navigateConsoleSection('education')">
                <Sparkles :size="14" />
                <span><small>当前学习上下文</small><strong>{{ activeChatCourse ? `${activeChatCourse.code} · ${activeChatCourse.title}` : (activeLearnerProfile ? `${activeLearnerProfile.subject} · ${activeLearnerProfile.gradeLevel}` : '待配置学习者画像') }}</strong></span>
              </button>
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
              <button class="secondary-button chat-agent-trace-button" type="button" :class="{ active: showLearningTrace }" @click="toggleLearningTrace"><Brain :size="14" />{{ showLearningTrace ? '收起决策依据' : '查看决策依据' }}</button>
              <button v-if="activeConversationId && !showConversationRename" class="secondary-button" type="button" :disabled="conversationRenaming" @click="beginConversationRename">重命名</button>
            </div>
          </div>

          <section class="chat-learning-overview" :class="{ 'is-collapsed': learningOverviewCollapsed && activeLearnerProfile }" aria-label="本轮学习概览">
          <section v-if="activeLearnerProfile" v-show="!learningOverviewCollapsed" class="education-agent-context-strip" aria-label="当前教育 Agent 上下文">
            <div class="education-agent-context-heading">
              <div>
                <p class="eyebrow">EDUCATION AGENT CONTEXT</p>
                <strong>这轮教学由课程边界与学习证据共同驱动</strong>
                <span>先限定可用知识，再根据当前状态选择动作；完成后把可验证证据写回学习档案。</span>
              </div>
              <span class="education-agent-context-state" :class="{ ready: educationAgentReady }">
                <i></i>{{ educationAgentReady ? '可以开始教学' : '需要补齐上下文' }}
              </span>
            </div>
            <div class="education-agent-context-grid">
              <article v-for="(trace, index) in educationAgentTrace" :key="trace.id" :class="`is-${trace.state}`">
                <span class="education-agent-context-icon"><component :is="trace.icon" :size="14" /></span>
                <div>
                  <small>{{ String(index + 1).padStart(2, '0') }} · {{ trace.label }}</small>
                  <strong :title="trace.value">{{ trace.value }}</strong>
                  <em :title="trace.detail">{{ trace.detail }}</em>
                </div>
              </article>
            </div>
          </section>
          <div v-if="activeLearnerProfile" class="learning-overview-collapse-bar">
            <div class="learning-overview-collapse-copy">
              <p class="eyebrow">CURRENT LEARNING PLAN</p>
              <strong>{{ activeLearningGoal?.title || '本轮学习计划' }}</strong>
              <span>{{ activeChatCourse?.title || `${activeLearnerProfile.subject} · ${activeLearnerProfile.gradeLevel}` }} · {{ learningOverviewNextAction.detail }}</span>
            </div>
            <div class="learning-overview-collapse-actions">
              <span class="learning-overview-collapse-status" :class="{ ready: educationAgentReady }"><i></i>{{ educationAgentReady ? '已就绪' : '待配置' }}</span>
              <button
                class="learning-overview-next-action"
                type="button"
                :disabled="chatSending || chatUploading || (learningOverviewNextAction.kind === 'task' && learningTaskStartingId)"
                @click="runLearningOverviewNextAction"
              >
                {{ learningOverviewNextAction.label }}
                <ArrowUp :size="12" aria-hidden="true" />
              </button>
              <button
                class="learning-overview-toggle"
                type="button"
                aria-controls="learning-overview-content"
                :aria-expanded="!learningOverviewCollapsed"
                :aria-label="learningOverviewCollapsed ? '展开本轮学习概览' : '收起本轮学习概览'"
                @click="toggleLearningOverview"
              >
                {{ learningOverviewCollapsed ? '展开学习计划' : '收起学习计划' }}
                <ArrowDown v-if="learningOverviewCollapsed" :size="13" aria-hidden="true" />
                <ArrowUp v-else :size="13" aria-hidden="true" />
              </button>
            </div>
          </div>
          <div id="learning-overview-content" v-show="!learningOverviewCollapsed || !activeLearnerProfile" class="learning-overview-content">
          <section v-if="!activeLearnerProfile" class="learning-onboarding" aria-label="建立教育 Agent 学习上下文">
            <div class="learning-onboarding-intro">
              <div class="learning-onboarding-mark" aria-hidden="true"><Sparkles :size="20" /></div>
              <div>
                <p class="eyebrow">START A LEARNING LOOP</p>
                <h2>先建立你的学习上下文</h2>
                <p>这不是一次通用问答。先告诉 Agent 你在学什么、遵循哪套课程，再让它根据后续测评不断调整讲解、练习和复习。</p>
              </div>
              <span class="learning-onboarding-progress">{{ learningSetupProgress.completed }} / {{ learningSetupProgress.total }} 已就绪</span>
            </div>
            <div class="learning-onboarding-steps" aria-label="教育 Agent 启动流程">
              <article class="learning-onboarding-step" :class="{ ready: activeLearnerProfile }">
                <span>1</span>
                <div><strong>学习者状态</strong><small>{{ activeLearnerProfile ? `${activeLearnerProfile.subject} · ${activeLearnerProfile.gradeLevel}` : '建立学科、年级与课程版本画像' }}</small></div>
              </article>
              <article class="learning-onboarding-step" :class="{ ready: matchingEducationSourceCount > 0 }">
                <span>2</span>
                <div><strong>课程知识范围</strong><small>{{ matchingEducationSourceCount ? `课程版本下已有 ${matchingEducationSourceCount} 个来源` : '按课程版本补充课程资料' }}</small></div>
              </article>
              <article class="learning-onboarding-step" :class="{ ready: activeLearningGoal }">
                <span>3</span>
                <div><strong>学习目标与证据</strong><small>{{ activeLearningGoal ? activeLearningGoal.title : '设定目标后，测评结果会持续回写' }}</small></div>
              </article>
            </div>
            <div class="learning-onboarding-content">
              <div class="learning-onboarding-profile">
                <div class="learning-onboarding-section-heading">
                  <div><span>第一步</span><strong>建立学习者画像</strong></div>
                  <small>画像同时锁定课程范围，并承载掌握度、目标和后续测评证据</small>
                </div>
                <form class="learning-onboarding-profile-form" @submit.prevent="saveLearnerProfile">
                  <label><span>学科</span><input v-model="learnerProfileForm.subject" required maxlength="128" /></label>
                  <label><span>年级</span><input v-model="learnerProfileForm.gradeLevel" required maxlength="128" /></label>
                  <label><span>课程版本</span><input v-model="learnerProfileForm.curriculumVersion" required maxlength="128" /></label>
                  <label class="learning-onboarding-wide"><span>当前学习诉求（可选）</span><input v-model="learnerProfileForm.learningGoal" maxlength="512" placeholder="例如：理解函数定义域，并能独立完成基础题" /></label>
                  <button class="primary-button" type="submit" :disabled="educationLoading">{{ educationLoading ? '建立中…' : '建立学习画像' }}</button>
                </form>
              </div>
              <aside class="learning-onboarding-existing">
                <div class="learning-onboarding-section-heading">
                  <div><span>继续学习</span><strong>已有学习上下文</strong></div>
                  <button type="button" @click="chatMode = false; navigateConsoleSection('education')">课程与资料管理 <ArrowUp :size="13" /></button>
                </div>
                <div v-if="learnerProfiles.length" class="learning-onboarding-profile-list">
                  <div v-for="profile in learnerProfiles.slice(0, 4)" :key="profile.id" class="learning-onboarding-profile-item">
                    <button type="button" class="learning-onboarding-profile-select" @click="selectLearnerProfile(profile)">
                      <span class="learning-onboarding-profile-icon"><Brain :size="14" /></span>
                      <span><strong>{{ profile.subject }} · {{ profile.gradeLevel }}</strong><small>{{ profile.curriculumVersion }} · {{ profile.learningGoal || '尚未设置学习诉求' }}</small></span>
                      <ArrowUp :size="13" />
                    </button>
                    <button type="button" class="learning-onboarding-profile-delete" :disabled="learnerProfileDeletingId === profile.id" title="删除学习画像" @click.stop="deleteLearnerProfile(profile)"><Trash2 :size="13" /></button>
                  </div>
                </div>
                <p v-else>还没有保存过学习画像。完成左侧第一步后，Agent 才会开始按课程和学习状态决策。</p>
              </aside>
            </div>
          </section>

          <section v-if="activeLearnerProfile" class="learning-agent-workbench" aria-label="教育 Agent 本轮决策">
            <div class="learning-agent-workbench-heading">
              <div>
                <p>AGENT DECISION CONTRACT</p>
                <strong>本轮学习将如何被约束与推进</strong>
                <span>课程资料限定可用知识，学习状态决定教学动作，作答结果回写为下一轮决策的证据。</span>
              </div>
              <div class="learning-agent-workbench-state" :class="{ ready: educationAgentReady }"><i></i>{{ educationAgentReady ? '课程与学情已接入' : '还缺少课程资料' }}</div>
            </div>
            <section class="learning-session-focus" aria-label="当前学习任务">
              <header class="learning-session-focus-heading">
                <div>
                  <p>ACTIVE LEARNING SESSION</p>
                  <strong>{{ activeLearningGoal?.title || '尚未设定学习目标' }}</strong>
                  <span>{{ activeLearningGoal ? `围绕「${activeLearningGoal.conceptKey}」把一次作答变成可验证的学习进展。` : '先设定目标，Agent 才能判断什么算一次有效进展。' }}</span>
                </div>
                <span class="learning-session-focus-status" :class="`is-${agentTeachingAction.state}`">{{ agentTeachingAction.state === 'blocked' ? '等待课程边界' : (agentTeachingAction.state === 'scheduled' ? '已安排' : (agentTeachingAction.state === 'ready' ? '正在推进' : '等待证据')) }}</span>
              </header>
              <div class="learning-session-focus-grid">
                <div class="learning-course-path">
                  <div class="learning-session-subheading"><span>课程路径</span><small>{{ activeChatCourse?.title || `${activeLearnerProfile.subject} · ${activeLearnerProfile.gradeLevel}` }}</small></div>
                  <div class="learning-course-path-nodes">
                    <article v-for="(node, index) in learningConceptTrail" :key="`${node.concept}-${index}`" class="learning-course-path-node" :class="`is-${node.state}`">
                      <i><Check v-if="node.state === 'ready'" :size="11" /><Target v-else-if="node.state === 'target'" :size="11" /><CircleDot v-else :size="10" /></i>
                      <div><strong>{{ node.concept }}</strong><small>{{ node.detail }}</small></div>
                    </article>
                  </div>
                </div>
                <div class="learning-session-next-move">
                  <div class="learning-session-subheading"><span>Agent 下一动作</span><small>{{ pedagogicalModeLabel }}</small></div>
                  <strong>{{ agentTeachingAction.title }}</strong>
                  <p>{{ agentTeachingAction.detail }}</p>
                  <div class="learning-session-evidence-callout"><ListChecks :size="14" /><span><b>{{ agentEvidenceRequest.title }}</b><small>{{ agentEvidenceRequest.detail }}</small></span></div>
                  <button class="primary-button" type="button" :disabled="Boolean(!educationSendBlockReason && learningTaskIsScheduled(activeLearningTask))" @click="educationSendBlockReason ? openEducationAgentSetup() : (learningTaskIsScheduled(activeLearningTask) ? focusLearningTask(activeLearningTask) : chatInputRef?.focus())">{{ educationSendBlockReason ? `先${educationSetupActionLabel}` : (learningTaskIsScheduled(activeLearningTask) ? '查看复习安排' : '进入本轮作答') }} <ArrowDown :size="13" /></button>
                </div>
              </div>
            </section>
            <details class="learning-agent-decision-details">
              <summary><span>查看 Agent 本轮决策依据</span><small>课程边界 · 学情诊断 · 教学动作 · 证据回写</small></summary>
            <section class="learning-agent-decision-board" aria-label="Agent 实时教学决策">
              <header class="learning-agent-decision-board-heading">
                <div><p>LIVE DECISION BOARD</p><strong>先限定知识，再根据证据决定教学动作</strong><span>每一项都是 Agent 本轮可观察、可解释、可改变的决策依据。</span></div>
                <button class="text-button" type="button" @click="toggleLearningTrace"><Brain :size="13" />{{ showLearningTrace ? '收起完整依据' : '查看完整依据' }}</button>
              </header>
              <div class="learning-agent-decision-summary">
                <article class="learning-agent-boundary-card" :class="{ empty: !currentEducationSourceCount }">
                  <header><span><BookOpen :size="16" /></span><div><small>01 · 知识边界</small><strong>本轮允许参考的课程资料</strong></div><em :class="{ ready: currentEducationSourceCount }">{{ currentEducationSourceCount ? `${currentEducationSourceCount} 份可检索` : '尚未就绪' }}</em></header>
                  <p :title="currentEducationRetrievalDetail">{{ currentEducationRetrievalDetail }}</p>
                  <div v-if="currentEducationSourcePreview.length" class="learning-agent-source-list" aria-label="当前可检索课程来源">
                    <span v-for="source in currentEducationSourcePreview" :key="source.id" :title="`${source.documentId} · ${source.conceptTags || '未标注知识点'}`"><BookOpen :size="11" /><b>{{ educationSourceLabel(source) }}</b><small>{{ source.sourceType || 'TEXTBOOK' }} · 难度 {{ source.difficultyLevel || 3 }}</small></span>
                  </div>
                  <div v-else class="learning-agent-source-empty"><ShieldCheck :size="13" /><span>课程外资料不会被 Agent 用来替代本轮教学依据。</span></div>
                  <footer><b>{{ currentEducationSourceCount ? '知识边界已锁定' : '需先配置课程资料' }}</b><button type="button" @click="openEducationAgentSetup">管理知识边界 <ArrowUp :size="12" /></button></footer>
                </article>
                <article class="learning-agent-diagnosis-card" :class="`is-${learnerStateDiagnosis.state}`">
                  <header><span><Brain :size="16" /></span><div><small>02 · 学情诊断</small><strong>{{ activeLearningGoal?.title || '当前学习者状态' }}</strong></div><em>{{ currentLearningEvidenceCount }} 条证据</em></header>
                  <strong>{{ learnerMasteryLoading ? '正在读取学习状态…' : learnerStateDiagnosis.title }}</strong>
                  <p>{{ learnerStateDiagnosis.detail }}</p>
                  <div v-if="Number.isFinite(learnerStateDiagnosis.currentMastery) && Number.isFinite(learnerStateDiagnosis.targetMastery)" class="learning-agent-diagnosis-meter">
                    <span>当前 <b>{{ formatRate(learnerStateDiagnosis.currentMastery) }}</b></span><i><b :style="{ width: `${Math.min(100, Math.max(0, learnerStateDiagnosis.currentMastery / Math.max(learnerStateDiagnosis.targetMastery, 0.01) * 100))}%` }"></b></i><span>目标 {{ formatRate(learnerStateDiagnosis.targetMastery) }}</span>
                  </div>
                  <div v-else class="learning-agent-diagnosis-note"><CircleDot :size="12" />{{ activeLearningGoal ? `目标掌握度 ${formatRate(activeLearningGoal.targetMastery)}` : '创建目标后显示达标条件' }}</div>
                </article>
              </div>
              <section class="learning-agent-action-plan" aria-label="Agent 教学计划">
                <header><div><p>03 · AGENT TEACHING PLAN</p><strong>从当前状态到下一次可验证改变</strong></div><span :class="`is-${agentTeachingAction.state}`">{{ agentTeachingAction.state === 'blocked' ? '等待输入' : (agentTeachingAction.state === 'scheduled' ? '已安排' : (agentTeachingAction.state === 'ready' ? '可执行' : '待确认')) }}</span></header>
                <ol>
                  <li class="learning-agent-plan-action"><span>1</span><div><small>教学动作</small><strong>{{ agentTeachingAction.title }}</strong><p>{{ agentTeachingAction.detail }}</p></div></li>
                  <li class="learning-agent-plan-evidence"><span>2</span><div><small>需要观察的证据</small><strong>{{ agentEvidenceRequest.title }}</strong><p>{{ agentEvidenceRequest.detail }}</p></div></li>
                  <li class="learning-agent-plan-writeback"><span>3</span><div><small>状态如何改变</small><strong>{{ activeLearningGoal ? `回写「${activeLearningGoal.conceptKey}」学习档案` : '等待建立学习档案目标' }}</strong><p>只有作答、推理过程或教师评分会改变掌握度；聊天文本本身不会被当作已掌握。</p></div></li>
                </ol>
                <footer>
                  <span><ListChecks :size="13" />{{ activeLearningGoal ? learningEvidenceSummary : '先定义目标，再开始累积证据' }}</span>
                  <div>
                    <button v-if="agentTeachingAction.state === 'blocked'" class="secondary-button" type="button" @click="openEducationAgentSetup">{{ educationSetupActionLabel }}</button>
                    <button v-else-if="!activeLearningGoal" class="secondary-button" type="button" @click="showQuickLearningGoalForm = true">设定学习目标</button>
                    <button v-else-if="activeLearningTask" class="primary-button" type="button" :title="learningTaskIsScheduled(activeLearningTask) ? `任务将在 ${formatDate(activeLearningTask.scheduledAt)} 开放` : learningTaskSourceBlockReason(activeLearningTask)" :disabled="learningTaskStartingId === activeLearningTask.id || chatSending || chatUploading || Boolean(learningTaskSourceBlockReason(activeLearningTask))" @click="learningTaskIsScheduled(activeLearningTask) ? focusLearningTask(activeLearningTask) : startLearningTask(activeLearningTask)">{{ learningTaskSourceBlockReason(activeLearningTask) ? '补充课程资料' : learningTaskActionLabel(activeLearningTask, learningTaskStartingId === activeLearningTask.id) }} <ArrowUp :size="12" /></button>
                    <button v-else-if="activeLearningRecommendation" class="primary-button" type="button" :title="learningGoalSourceBlockReason(activeLearningRecommendation.learningGoalId)" :disabled="chatSending || chatUploading || Boolean(learningGoalSourceBlockReason(activeLearningRecommendation.learningGoalId))" @click="useLearningRecommendation">按 Agent 建议开始 <ArrowUp :size="12" /></button>
                    <button v-else class="secondary-button" type="button" @click="chatInputRef?.focus()">提出学习问题 <ArrowUp :size="12" /></button>
                  </div>
                </footer>
              </section>
            </section>
            </details>
            <div v-if="nextLearnerCourseAssignment" class="learning-agent-assignment-inline">
              <span><BookOpen :size="14" /></span>
              <div><small>课程作业</small><strong>{{ nextLearnerCourseAssignment.title }}</strong><p>{{ learningAssignmentStatusLabel(nextLearnerCourseAssignment.status) }} · {{ nextLearnerCourseAssignment.conceptKey }}<span v-if="nextLearnerCourseAssignment.dueAt"> · 截止 {{ formatDate(nextLearnerCourseAssignment.dueAt) }}</span></p><p v-if="nextLearnerCourseAssignment.teacherReviewNote" class="learning-agent-assignment-review-note"><b>{{ learningAssignmentReviewNoteLabel(nextLearnerCourseAssignment) }}：</b>{{ nextLearnerCourseAssignment.teacherReviewNote }}</p></div>
              <button v-if="['ASSIGNED', 'RETRY_REQUIRED'].includes(nextLearnerCourseAssignment.status) && !learningAssignmentHasExecutableFeedback(nextLearnerCourseAssignment)" class="secondary-button" type="button" :title="learningAssignmentSourceBlockReason(nextLearnerCourseAssignment)" :disabled="learningAssignmentAcceptingId === nextLearnerCourseAssignment.id || chatSending || chatUploading || Boolean(learningAssignmentSourceBlockReason(nextLearnerCourseAssignment))" @click="startLearningAssignment(nextLearnerCourseAssignment)">{{ learningAssignmentSourceBlockReason(nextLearnerCourseAssignment) ? '需课程资料' : learningAssignmentStartLabel(nextLearnerCourseAssignment, learningAssignmentAcceptingId === nextLearnerCourseAssignment.id) }}</button>
              <button v-else-if="nextLearnerCourseAssignmentOpenFeedback" class="secondary-button" type="button" :disabled="learningAssignmentFeedbackAcknowledgingId === nextLearnerCourseAssignmentOpenFeedback.id || learningAssignmentAcceptingId === nextLearnerCourseAssignment.id" @click="acknowledgeLearningAssignmentFeedback(nextLearnerCourseAssignment, nextLearnerCourseAssignmentOpenFeedback)">{{ learningAssignmentFeedbackAcknowledgingId === nextLearnerCourseAssignmentOpenFeedback.id || learningAssignmentAcceptingId === nextLearnerCourseAssignment.id ? '处理中…' : learningAssignmentFeedbackContinueLabel(nextLearnerCourseAssignmentOpenFeedback) }}</button>
              <button v-else-if="nextLearnerCourseAssignment.status === 'AWAITING_EVIDENCE' && !learningAssignmentHasExecutableFeedback(nextLearnerCourseAssignment)" class="secondary-button" type="button" :title="learningAssignmentSourceBlockReason(nextLearnerCourseAssignment)" :disabled="learningAssignmentAcceptingId === nextLearnerCourseAssignment.id || chatSending || chatUploading || Boolean(learningAssignmentSourceBlockReason(nextLearnerCourseAssignment))" @click="startLearningAssignment(nextLearnerCourseAssignment)">{{ learningAssignmentSourceBlockReason(nextLearnerCourseAssignment) ? '需课程资料' : learningAssignmentStartLabel(nextLearnerCourseAssignment, learningAssignmentAcceptingId === nextLearnerCourseAssignment.id) }}</button>
              <button v-else-if="learningAssignmentSubmissionOpen(nextLearnerCourseAssignment) && !learningAssignmentHasExecutableFeedback(nextLearnerCourseAssignment)" class="secondary-button" type="button" @click="openChatLearningAssignmentSubmission(nextLearnerCourseAssignment)">提交作业内容</button>
              <button v-else class="text-button" type="button" @click="focusLearnerCourseAssignment(nextLearnerCourseAssignment)">{{ nextLearnerCourseAssignment.status === 'COMPLETED' && nextLearnerCourseAssignment.reviewStatus === 'PENDING' ? '查看教师确认' : '查看完整记录' }}</button>
            </div>
            <form v-if="showQuickLearningGoalForm && !activeLearningGoal" class="learning-goal-quick-form" @submit.prevent="createLearningGoal">
              <div class="learning-goal-quick-form-heading">
                <div><span>第二步</span><strong>把学习诉求转成可追踪目标</strong><small>Agent 会把后续的练习与测评证据累计到这个目标。</small></div>
                <button type="button" aria-label="取消设定学习目标" @click="showQuickLearningGoalForm = false"><X :size="14" /></button>
              </div>
              <label><span>目标名称</span><input v-model="learningGoalForm.title" required maxlength="255" placeholder="例如：掌握函数定义域" /></label>
              <label><span>目标知识点</span><input v-model="learningGoalForm.conceptKey" required maxlength="255" placeholder="例如：函数定义域" /></label>
              <label><span>目标掌握度</span><input v-model.number="learningGoalForm.targetMastery" type="number" min="0.01" max="1" step="0.05" required /></label>
              <button class="primary-button" type="submit" :disabled="educationLoading">{{ educationLoading ? '创建中…' : '开始追踪目标' }}</button>
            </form>
            <div v-if="learnerMasteryPreview.length" class="learning-agent-mastery-strip" aria-label="需要关注的知识点">
              <span>优先关注</span>
              <button v-for="item in learnerMasteryPreview" :key="item.id || item.conceptKey" type="button" @click="chatInput = `请帮我诊断并练习「${item.conceptKey}」`"><strong>{{ item.conceptKey }}</strong><em>{{ formatRate(item.masteryScore) }}</em></button>
            </div>
          </section>

          <section v-if="chatCourseAssignment" class="chat-course-assignment-panel" aria-label="当前课程作业">
            <header class="chat-course-assignment-heading">
              <div>
                <p class="eyebrow">COURSE ASSIGNMENT / EVIDENCE LOOP</p>
                <h2>{{ chatCourseAssignment.title }}</h2>
                <p>{{ chatCourseAssignment.instructions }}</p>
              </div>
              <div class="chat-course-assignment-statuses">
                <span :class="`assignment-status-${chatCourseAssignment.status.toLowerCase()}`">{{ learningAssignmentStatusLabel(chatCourseAssignment.status) }}</span>
                <span v-if="chatCourseAssignment.reviewStatus && chatCourseAssignment.reviewStatus !== 'NOT_REQUIRED'">{{ learningAssignmentReviewStatusLabel(chatCourseAssignment.reviewStatus) }}</span>
              </div>
            </header>
            <div class="chat-course-assignment-meta">
              <span><BookOpen :size="13" />{{ chatCourseAssignment.courseTitle || chatCourseAssignment.subject }} · {{ chatCourseAssignment.gradeLevel }} · {{ chatCourseAssignment.curriculumVersion }}</span>
              <span><Target :size="13" />{{ chatCourseAssignment.conceptKey }} · 目标掌握度 {{ formatRate(chatCourseAssignment.targetMastery) }}</span>
              <span v-if="chatCourseAssignment.dueAt"><CalendarClock :size="13" />截止 {{ formatDate(chatCourseAssignment.dueAt) }}</span>
            </div>
            <div v-if="chatCourseAssignment.teacherReviewNote" class="chat-course-assignment-review-note" :class="{ revision: chatCourseAssignment.reviewStatus === 'REVISION_REQUIRED' }">
              <span><CircleAlert :size="15" /></span>
              <div><strong>{{ learningAssignmentReviewNoteLabel(chatCourseAssignment) }}</strong><p>{{ chatCourseAssignment.teacherReviewNote }}</p><small v-if="chatCourseAssignment.teacherReviewedAt">教师已于 {{ formatDate(chatCourseAssignment.teacherReviewedAt) }} 留下此说明。</small></div>
            </div>
            <div v-if="chatCourseAssignmentOpenFeedback" class="chat-course-assignment-feedback">
              <span class="chat-course-assignment-feedback-icon"><CircleAlert :size="15" /></span>
              <div><strong>{{ learningAssignmentFeedbackActionLabel(chatCourseAssignmentOpenFeedback.action) }}</strong><p>{{ chatCourseAssignmentOpenFeedback.message }}</p><small v-if="chatCourseAssignmentOpenFeedback.suggestedDueAt">建议截止：{{ formatDate(chatCourseAssignmentOpenFeedback.suggestedDueAt) }}</small></div>
              <button class="secondary-button" type="button" :disabled="learningAssignmentFeedbackAcknowledgingId === chatCourseAssignmentOpenFeedback.id || learningAssignmentAcceptingId === chatCourseAssignment.id" @click="acknowledgeLearningAssignmentFeedback(chatCourseAssignment, chatCourseAssignmentOpenFeedback)">{{ learningAssignmentFeedbackAcknowledgingId === chatCourseAssignmentOpenFeedback.id || learningAssignmentAcceptingId === chatCourseAssignment.id ? '处理中…' : learningAssignmentFeedbackContinueLabel(chatCourseAssignmentOpenFeedback) }}</button>
            </div>
            <div class="chat-course-assignment-actions">
              <button v-if="['ASSIGNED', 'RETRY_REQUIRED', 'AWAITING_EVIDENCE'].includes(chatCourseAssignment.status) && !learningAssignmentHasExecutableFeedback(chatCourseAssignment)" class="primary-button" type="button" :title="learningAssignmentSourceBlockReason(chatCourseAssignment)" :disabled="learningAssignmentAcceptingId === chatCourseAssignment.id || chatSending || chatUploading || Boolean(learningAssignmentSourceBlockReason(chatCourseAssignment))" @click="startLearningAssignment(chatCourseAssignment)">{{ learningAssignmentSourceBlockReason(chatCourseAssignment) ? '需课程资料' : learningAssignmentStartLabel(chatCourseAssignment, learningAssignmentAcceptingId === chatCourseAssignment.id) }}</button>
              <button v-if="learningAssignmentSourceBlockReason(chatCourseAssignment)" class="secondary-button" type="button" @click="openEducationAgentSetup">{{ educationSetupActionLabel }}</button>
              <button v-if="learningAssignmentSubmissionOpen(chatCourseAssignment) && !learningAssignmentHasExecutableFeedback(chatCourseAssignment)" class="secondary-button" type="button" @click="openChatLearningAssignmentSubmission(chatCourseAssignment)">{{ learningAssignmentSubmissionForm.assignmentId === chatCourseAssignment.id ? '正在填写提交物' : '提交作业内容' }}</button>
              <button class="text-button" type="button" @click="focusLearnerCourseAssignment(chatCourseAssignment)">查看完整证据链</button>
            </div>
            <form v-if="learningAssignmentSubmissionForm.assignmentId === chatCourseAssignment.id" class="chat-course-assignment-submission" @submit.prevent="submitLearningAssignmentSubmission">
              <div><strong>提交作业内容</strong><small>提交物会绑定本次课程作业与最近一次教育 Run，供教师结合测评证据审核。</small></div>
              <textarea ref="chatAssignmentSubmissionInputRef" v-model="learningAssignmentSubmissionForm.content" required maxlength="8000" rows="4" placeholder="填写解题过程、答案、实验结果或反思；尽量说明你的判断依据。"></textarea>
              <div><button class="text-button" type="button" @click="closeLearningAssignmentSubmission">稍后再写</button><button class="primary-button" type="submit" :disabled="learningAssignmentSubmissionSavingId === chatCourseAssignment.id">{{ learningAssignmentSubmissionSavingId === chatCourseAssignment.id ? '提交中…' : '保存提交物并通知教师' }}</button></div>
            </form>
            <footer class="chat-course-assignment-evidence">
              <span><ListChecks :size="13" />{{ learningAssignmentProgressMap[chatCourseAssignment.id] ? `测评 ${learningAssignmentProgressMap[chatCourseAssignment.id].assessmentTotal} 次 · Run 证据 ${formatRate(learningAssignmentProgressMap[chatCourseAssignment.id].runEvidenceCoverageRate)}` : '完成学习对话后，这里会汇总测评与 Run 证据。' }}</span>
              <span v-if="chatCourseAssignmentLatestSubmission"><Check :size="13" />最近提交：{{ formatDate(chatCourseAssignmentLatestSubmission.submittedAt) }}</span>
              <span v-else><PenLine :size="13" />尚未提交作业内容</span>
            </footer>
          </section>
          </div>
          </section>

          <div class="chat-messages" aria-live="polite" @scroll="updateChatFollowOutput">
            <div v-if="chatLoading && !chatMessages.length" class="chat-empty-state">正在加载会话…</div>
            <div v-else-if="!chatMessages.length && !activeLearnerProfile" class="chat-empty-state chat-empty-state-preparing">
              <span>完成上方的学习画像后，即可开始一段受课程约束的学习对话。</span>
            </div>
            <div v-else-if="!chatMessages.length" class="chat-empty-state">
              <div class="chat-empty-mark" aria-hidden="true"><Sparkles :size="23" /></div>
              <strong>从一个学习问题开始</strong>
              <span>{{ activeLearnerProfile ? `当前画像：${activeLearnerProfile.subject} · ${activeLearnerProfile.gradeLevel} · ${activeLearnerProfile.curriculumVersion}${activeChatCourse ? ` · 课程：${activeChatCourse.title}` : ''}。${currentEducationSourceCount ? `已锁定 ${currentEducationSourceCount} 个可检索课程来源，Agent 会按掌握度选择讲解、练习或诊断方式。` : '请先补充匹配的课程资料，避免 Agent 退化为通用问答。'}` : '先在教育工作台创建学习者画像，Agent 才能按课程版本和学习状态给出分层回答。' }}</span>
              <div class="chat-learning-context-card" aria-label="当前学习上下文">
                <div class="chat-learning-context-heading"><span>学习上下文</span><button type="button" @click="chatMode = false; navigateConsoleSection('education')">{{ activeLearnerProfile ? '调整画像' : '创建画像' }}</button></div>
                <div v-if="activeLearnerProfile" class="chat-learning-context-body">
                  <div class="chat-learning-profile-mark"><Sparkles :size="15" /></div>
                  <div><strong>{{ activeLearnerProfile.subject }} · {{ activeLearnerProfile.gradeLevel }}</strong><small>{{ activeLearnerProfile.curriculumVersion }}<span v-if="activeChatCourse"> · 课程：{{ activeChatCourse.title }}</span><span v-if="activeLearningGoal"> · 目标：{{ activeLearningGoal.title }}</span></small></div>
                  <span class="chat-learning-context-state" :class="{ ready: educationAgentReady }">{{ educationAgentReady ? '课程资料已锁定' : '等待课程资料' }}</span>
                </div>
                <div v-else class="chat-learning-context-empty">还没有学习者画像；完成配置后会自动带入学科、年级、课程版本和掌握度。</div>
              </div>
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
                <FolderGit2 :size="14" />{{ desktopWorkspacePicking ? '选择中…' : '导入知识材料' }}
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
                <div class="chat-message-meta"><strong>{{ message.role === 'USER' ? '你' : 'Ming 教学 Agent' }}</strong><span>{{ formatDate(message.createdAt) }}</span></div>
                <div class="chat-bubble" :class="messageStatusClass(message.status)">
                  <template v-if="message.role === 'ASSISTANT' && message.status === 'PENDING' && !message.content">
                    <span class="chat-thinking"><i></i><i></i><i></i>{{ chatRunActivity || messageStatusLabel(message.status) }}</span>
                  </template>
                  <template v-else>
                    <div v-if="message.content" class="chat-markdown" v-html="renderMarkdown(chatMessagePresentations.get(message.id)?.content || message.content)" @click="handleChatMarkdownClick"></div>
                    <section v-if="message.role === 'ASSISTANT' && chatMessagePresentations.get(message.id)?.sources?.length" class="chat-source-section" aria-label="课程证据来源">
                      <div class="chat-source-heading"><span>课程证据来源</span><small>{{ chatMessagePresentations.get(message.id).sources.length }} 个</small></div>
                      <div class="chat-source-list">
                        <article v-for="source in chatMessagePresentations.get(message.id).sources" :key="`${message.id}-${source.key}`" class="chat-source-card">
                          <div class="chat-source-card-heading">
                            <span class="chat-source-kind" :class="{ 'is-runtime': source.runtimeEvidence }">{{ source.provenanceLabel }}</span>
                            <strong>{{ source.title }}</strong>
                          </div>
                          <code v-if="source.citation">{{ source.citation }}</code>
                          <p v-if="source.excerpt">{{ source.excerpt }}</p>
                          <small v-if="source.runtimeEvidence">Agent 已从该来源读取本轮摘录{{ source.stepName ? ` · ${source.stepName}` : '' }}</small>
                          <small v-else-if="source.updatedAt">更新于 {{ formatDate(source.updatedAt) }}</small>
                        </article>
                      </div>
                    </section>
                    <p v-if="!message.content">{{ messageStatusLabel(message.status) }}</p>
                    <small v-if="message.role === 'ASSISTANT' && message.status !== 'COMPLETED'">{{ messageStatusLabel(message.status) }}</small>
                    <div v-if="message.role === 'ASSISTANT' && message.status === 'FAILED'" class="chat-failure-guide" role="status">
                      <strong>{{ chatFailureTitle(message) }}</strong>
                      <span v-if="message.content">原因：{{ message.content }}</span>
                      <small>{{ chatFailureGuidance(message) }}</small>
                    </div>
                  </template>
                  <div v-if="message.attachments?.length" class="chat-attachment-list" aria-label="已导入的工作区文件">
                    <span v-for="attachment in message.attachments" :key="attachment.id" :title="attachment.workspacePath">
                      <i>{{ attachment.directory ? '▣' : '⌁' }}</i><strong>{{ attachment.originalName }}</strong><em v-if="attachment.directory">{{ attachment.fileCount }} 文件</em><code>{{ attachment.workspacePath }}</code>
                    </span>
                  </div>
                </div>
                <section v-if="message.role === 'ASSISTANT' && message.runId && chatAssessmentsForRun(message.runId).length" class="chat-assessment-evidence" aria-label="本轮形成性学习证据">
                  <div class="chat-assessment-evidence-heading">
                    <span>FORMATION EVIDENCE</span>
                    <small>本轮学习证据已回写</small>
                  </div>
                  <article v-for="attempt in chatAssessmentsForRun(message.runId)" :key="attempt.id" class="chat-assessment-evidence-card">
                    <header>
                      <span :class="attempt.correct ? 'assessment-correct' : 'assessment-wrong'">{{ attempt.correct ? '已掌握' : '需补强' }}</span>
                      <strong>{{ learningGoalTitleForAssessment(attempt) }}</strong>
                      <small>{{ assessmentObservationLabel(attempt) }}</small>
                    </header>
                    <div class="chat-assessment-mastery">
                      <span>掌握度 <b>{{ formatRate(attempt.masteryBefore) }} → {{ formatRate(attempt.masteryAfter) }}</b></span>
                      <em :class="{ 'is-positive': Number(attempt.masteryAfter) >= Number(attempt.masteryBefore) }">{{ formatMasteryDelta(attempt) }}</em>
                    </div>
                    <blockquote v-if="attempt.learnerEvidenceQuote" class="chat-assessment-learner-quote"><span>学习者本轮原话</span>{{ attempt.learnerEvidenceQuote }}</blockquote>
                    <p v-if="attempt.evidenceText"><b>Agent 观察：</b>{{ attempt.evidenceText }}</p>
                    <p v-else-if="attempt.feedback">{{ attempt.feedback }}</p>
                    <footer>
                      <span v-if="assessmentRetrievalEvidenceLabel(attempt)"><BookOpen :size="12" />知识源：{{ assessmentRetrievalEvidenceLabel(attempt) }}</span>
                      <span v-if="attempt.learningAssignmentId"><ListChecks :size="12" />已回写课程作业</span>
                      <span v-if="attempt.feedback && attempt.evidenceText"><CircleDot :size="12" />{{ attempt.feedback }}</span>
                    </footer>
                  </article>
                </section>
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
            :class="{ 'chat-composer-dragging': chatDragActive, 'chat-composer-blocked': Boolean(educationSendBlockReason) }"
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
            <div v-if="showChatAgentSettings && activeConversationId" class="chat-agent-settings" aria-label="本轮学习设置">
              <div class="chat-agent-settings-heading"><div><strong>调整本轮学习计划</strong><small>这些设置会确定课程检索范围和教学策略；本轮回答会继续回写到同一学习目标。</small></div><button type="button" aria-label="关闭本轮学习设置" @click="showChatAgentSettings = false"><X :size="14" /></button></div>
              <div class="chat-education-settings">
                <div class="chat-education-toggle" role="status">
                  <span class="chat-education-toggle-state"><ShieldCheck :size="13" /></span>
                  <span><strong>教育知识库 Agent 已启用</strong><small>学习对话始终按课程版本、课程来源和学习者掌握度组织回答，并在结束后形成测评证据。</small></span>
                </div>
                <div class="chat-education-grid">
                  <label><span>学习者画像</span><select v-model="chatEducation.learnerProfileId" :disabled="chatSending || chatUploading" @change="selectChatLearnerProfile"><option value="">请选择画像</option><option v-for="profile in learnerProfiles" :key="profile.id" :value="profile.id">{{ profile.subject }} · {{ profile.gradeLevel }}</option></select></label>
                  <label><span>当前课程</span><select v-model="chatEducation.courseId" :disabled="chatSending || chatUploading || !chatEducation.learnerProfileId" @change="selectChatCourse"><option value="">仅使用画像课程约束</option><option v-for="course in availableChatCourses" :key="course.id" :value="course.id">{{ course.code }} · {{ course.title }}</option></select></label>
                  <label><span>学习目标</span><select v-model="chatEducation.learningGoalId" :disabled="chatSending || chatUploading" @change="selectLearningGoal(learningGoals.find((goal) => goal.id === chatEducation.learningGoalId), false)"><option value="">不绑定目标</option><option v-for="goal in learningGoals.filter((item) => item.status === 'ACTIVE')" :key="goal.id" :value="goal.id">{{ goal.title }} · {{ goal.conceptKey }}</option></select></label>
                  <label><span>教学策略</span><select v-model="chatEducation.pedagogicalMode" :disabled="chatSending || chatUploading"><option value="AUTO">自动选择</option><option value="EXPLAIN">概念讲解</option><option value="SOCRATIC">启发式引导</option><option value="PRACTICE">练习优先</option><option value="DIAGNOSE">错误诊断</option></select></label>
                  <label><span>目标知识点</span><input v-model="chatEducation.conceptKey" maxlength="255" placeholder="例如：函数定义域" :disabled="chatSending || chatUploading" /></label>
                  <label><span>难度范围</span><div class="chat-education-difficulty"><input v-model.number="chatEducation.minDifficulty" type="number" min="1" max="5" placeholder="1" :disabled="chatSending || chatUploading" /><span>—</span><input v-model.number="chatEducation.maxDifficulty" type="number" min="1" max="5" placeholder="5" :disabled="chatSending || chatUploading" /></div></label>
                  <small class="chat-education-context">{{ activeChatCourse ? `已锁定 ${activeChatCourse.code} · ${activeChatCourse.title}` : '课程实例未绑定；将按画像与知识源范围运行' }} · {{ currentEducationRetrievalScope.subject || '未选择学科' }} · {{ currentEducationRetrievalScope.gradeLevel || '未选择年级' }} · {{ currentEducationRetrievalScope.curriculumVersion || '未选择课程版本' }}</small>
                </div>
              </div>
            </div>
            <section v-if="educationSendBlockReason" class="education-source-gate" role="status" aria-live="polite">
              <span class="education-source-gate-icon"><ShieldCheck :size="16" /></span>
              <div>
                <strong>本轮教学尚不能启动</strong>
                <p>{{ educationSendBlockReason }}</p>
              </div>
              <button type="button" class="secondary-button" :disabled="chatSending || chatUploading" @click="openEducationAgentSetup">{{ educationSetupActionLabel }} <ArrowUp :size="13" /></button>
            </section>
            <textarea
              ref="chatInputRef"
              v-model="chatInput"
              rows="3"
              :disabled="chatSending || chatUploading || !activeConversationId || Boolean(educationSendBlockReason)"
              :placeholder="educationComposerPlaceholder"
              aria-label="输入消息"
              @input="handleChatInput"
              @keydown="handleChatKeydown"
            ></textarea>
            <div class="chat-composer-footer">
              <span class="chat-composer-hint">
                <span class="chat-composer-hint-primary"><kbd>Enter</kbd> 发送 · <kbd>Shift</kbd> + <kbd>Enter</kbd> 换行<span v-if="canCancelChat"> · <kbd>Esc</kbd> 停止</span></span>
                <span class="chat-composer-hint-context">{{ desktopWorkspaceDropping ? '正在导入知识材料…' : (educationSendBlockReason || (activeLearningGoal ? `本轮证据将归入「${activeLearningGoal.conceptKey}」；只有作答、推理或教师评分会改变掌握度` : (activeChatCourse ? `已锁定课程「${activeChatCourse.title}」；设定目标后可开始累积学习证据` : '已应用画像课程约束；设定目标后可开始累积学习证据'))) }}</span>
              </span>
              <div class="chat-composer-actions">
                <button class="secondary-button chat-agent-settings-button" type="button" :disabled="chatSending || chatUploading || !activeConversationId" @click="showChatAgentSettings = !showChatAgentSettings"><Settings2 :size="14" /><span>调整学习计划</span></button>
                <button class="secondary-button chat-attachment-button" type="button" :disabled="chatSending || chatUploading || !activeConversationId || Boolean(educationSendBlockReason)" @click="openChatAttachmentPicker"><Paperclip :size="14" /><span>上传作答材料</span></button>
                <button v-if="canCancelChat" class="secondary-button chat-stop-button" type="button" :disabled="chatCancellingRunId === pendingChatMessage?.runId" @click="cancelChatRun"><Square :size="14" /><span>{{ chatCancellingRunId === pendingChatMessage?.runId ? '处理中…' : (chatRunStatus === 'WAITING_APPROVAL' ? '撤回审批' : '停止') }}</span></button>
                <button class="primary-button chat-send-button" type="submit" :disabled="!canSendChat"><span class="chat-send-label">{{ chatUploading ? '导入中…' : chatSending ? '提交中…' : '发送' }}</span><Send :size="14" /></button>
              </div>
            </div>
          </form>
        </main>

        <aside v-if="showLearningTrace && !showChatWorkspace && !showChatRun" class="chat-learning-trace-panel" aria-label="教育 Agent 决策依据">
          <div class="chat-run-panel-heading">
            <div><p class="eyebrow">AGENT CONTEXT</p><h2>Agent 依据</h2></div>
            <button class="icon-button" type="button" aria-label="关闭 Agent 依据" @click="showLearningTrace = false"><X :size="15" /></button>
          </div>
          <div class="learning-trace-intro" :class="{ ready: educationAgentReady }">
            <div class="learning-trace-intro-icon"><Sparkles :size="16" /></div>
            <div>
              <strong>{{ educationAgentReady ? '教育 Agent 已就绪' : '先补齐课程资料与学习状态' }}</strong>
              <p>{{ educationAgentReady ? '本轮回答将受课程知识来源、课程约束与学习者状态共同限制。' : (educationSendBlockReason || '配置学习者画像和课程资料后，回答才会进入教育 Agent 路径。') }}</p>
            </div>
          </div>
          <section class="learning-trace-section" aria-label="Agent 上下文链路">
            <div class="learning-trace-section-heading"><span>DECISION PIPELINE</span><small>{{ educationAgentTrace.filter((item) => item.state === 'ready').length }} / {{ educationAgentTrace.length }} 已就绪</small></div>
            <div class="learning-trace-list">
              <article v-for="(item, index) in educationAgentTrace" :key="item.id" class="learning-trace-row" :class="`is-${item.state}`">
                <div class="learning-trace-index"><span>{{ index + 1 }}</span><i v-if="index < educationAgentTrace.length - 1"></i></div>
                <div class="learning-trace-copy">
                  <div class="learning-trace-label"><component :is="item.icon" :size="13" /><strong>{{ item.label }}</strong><em>{{ item.state === 'ready' ? '已应用' : '待配置' }}</em></div>
                  <b>{{ item.value }}</b>
                  <small>{{ item.detail }}</small>
                </div>
              </article>
            </div>
          </section>
          <section class="learning-trace-next" aria-label="下一步学习动作">
            <div class="learning-trace-section-heading"><span>NEXT LEARNING ACTION</span></div>
            <strong>{{ activeLearningTask?.title || activeLearningRecommendation?.nextActionTitle || '绑定学习目标后生成' }}</strong>
            <p>{{ activeLearningTask?.prompt || activeLearningRecommendation?.rationale || 'Agent 会根据掌握度和测评证据，给出下一步练习、诊断或复习。' }}</p>
            <button class="secondary-button" type="button" @click="chatMode = false; navigateConsoleSection('education')">{{ activeLearnerProfile ? '调整课程与学习目标' : '配置学习者画像' }} <ArrowUp :size="13" /></button>
          </section>
        </aside>

        <aside v-if="showChatWorkspace" class="chat-workspace-panel" :class="{ 'workspace-panel-expanded': workspaceFilePreview || workspaceFilePreviewLoading }">
          <div class="chat-run-panel-heading">
            <div><p class="eyebrow">KNOWLEDGE MATERIALS</p><h2>知识材料</h2></div>
            <button class="icon-button" type="button" aria-label="关闭知识材料" @click="showChatWorkspace = false"><X :size="15" /></button>
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
          <span>Education Agent Operations</span>
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
          <div v-if="currentUser && localDemoSessionActive" class="current-user-chip" :title="`${currentUser.userId} · ${currentUser.tenantId}`">
            <span>{{ roleLabel(currentUser.primaryRole) }}</span><strong>{{ currentUser.userId }}</strong>
            <select v-if="currentUser.localDemo" v-model="demoRole" aria-label="切换本地演示角色" :disabled="demoRoleSwitching" @change="switchDemoRole(demoRole)">
              <option value="STUDENT">学生</option>
              <option value="TEACHER">老师</option>
              <option value="ADMIN">管理员</option>
            </select>
            <button v-if="currentUser.localDemo" class="current-user-logout" type="button" :disabled="localDemoLoginBusy" @click="endLocalDemoSession">退出</button>
          </div>
          <button v-if="isAdminRole" class="secondary-button top-config-button" type="button" title="配置大语言模型" @click="showModelSettings = true"><Settings2 :size="15" />大语言模型</button>
          <button v-if="isAdminRole" class="secondary-button top-config-button" type="button" title="配置向量模型" @click="showEmbeddingSettings = true"><Settings2 :size="15" />向量模型</button>
          <button v-if="isLearnerOnlyRole" class="secondary-button" type="button" title="打开学习对话" @click="chatMode = true"><MessageSquarePlus :size="15" />学习对话</button>
        </div>
      </div>
    </header>

    <div class="console-layout">
      <aside class="sidebar">
      <nav class="side-nav" aria-label="主导航">
        <a v-if="isAdminRole" class="nav-item" :class="{ active: activeConsoleSection === 'runtime' }" href="#runtime" :aria-current="activeConsoleSection === 'runtime' ? 'page' : undefined" @click.prevent="navigateConsoleSection('runtime')"><span class="nav-icon"><CircleDot :size="16" /></span>运行追踪</a>
        <a v-if="canViewEducationConsole" class="nav-item" :class="{ active: activeConsoleSection === 'education' }" href="#education" :aria-current="activeConsoleSection === 'education' ? 'page' : undefined" @click.prevent="navigateConsoleSection('education')"><span class="nav-icon"><Sparkles :size="16" /></span>{{ isAdminRole ? '教育概览' : (isTeacherRole ? '课程管理' : '我的学习') }}</a>
        <a v-if="isAdminRole" class="nav-item" :class="{ active: activeConsoleSection === 'audit' }" href="#audit" :aria-current="activeConsoleSection === 'audit' ? 'page' : undefined" @click.prevent="navigateConsoleSection('audit')"><span class="nav-icon"><Check :size="16" /></span>审计与证据</a>
      </nav>

      <div class="sidebar-foot">
        <div class="system-state"><span class="pulse" :class="{ offline: !infraOnline }"></span><span>{{ infraLabel }}</span></div>
      </div>
    </aside>

    <main class="main-content" id="runtime">
      <div v-if="errorMessage" :key="`error-${errorMessage}`" class="message error-message console-message-banner">{{ errorMessage }}</div>
      <div v-if="noticeMessage" :key="`notice-${noticeMessage}`" class="message notice-message console-message-banner">{{ noticeMessage }}</div>
      <div v-if="educationRuntimeDiagnostic" class="message education-runtime-message console-message-banner" role="alert">{{ educationRuntimeDiagnostic }}</div>

      <section class="role-welcome panel" :class="`role-welcome-${currentPrimaryRole.toLowerCase()}`" aria-label="当前角色工作台">
        <div class="role-welcome-copy">
          <p class="eyebrow">{{ currentPrimaryRole }} WORKSPACE</p>
          <h1>{{ roleWorkspaceTitle }}</h1>
          <p>{{ roleWorkspaceDetail }}</p>
        </div>
        <ol class="role-welcome-steps">
          <li v-for="(step, index) in roleWorkspaceSteps" :key="step.title">
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
            <div><strong>{{ step.title }}</strong><small>{{ step.detail }}</small></div>
          </li>
        </ol>
        <div class="role-welcome-action">
          <div><strong>现在就开始</strong><small>{{ roleQuickStartAction.detail }}</small></div>
          <button class="primary-button" type="button" @click="runRoleQuickStartAction">{{ roleQuickStartAction.label }} <ArrowRight :size="13" /></button>
        </div>
      </section>

      <section v-if="isAdminRole" class="infra-strip panel" aria-label="基础设施状态">
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

      <section v-if="isAdminRole" class="stats-grid" aria-label="运行统计">
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

      <section v-if="isAdminRole" class="create-panel" :class="{ 'create-panel-collapsed': !showCreateForm }">
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
                <span>启用多轮教学推理</span>
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
              <label class="field"><span>学习者画像</span><select v-model="form.education.learnerProfileId" @change="syncEducationRunProfile"><option value="">请选择画像</option><option v-for="profile in learnerProfiles" :key="profile.id" :value="profile.id">{{ profile.subject }} · {{ profile.gradeLevel }}</option></select></label>
              <label class="field"><span>学习目标</span><select v-model="form.education.learningGoalId" @change="selectLearningGoal(learningGoals.find((goal) => goal.id === form.education.learningGoalId), false)"><option value="">不绑定目标</option><option v-for="goal in learningGoals.filter((item) => item.status === 'ACTIVE')" :key="goal.id" :value="goal.id">{{ goal.title }} · {{ goal.conceptKey }}</option></select></label>
              <label class="field"><span>学科</span><input v-model="form.education.subject" required :readonly="Boolean(form.education.learnerProfileId)" :title="form.education.learnerProfileId ? '由学习者画像锁定' : ''" /></label>
              <label class="field"><span>年级</span><input v-model="form.education.gradeLevel" required :readonly="Boolean(form.education.learnerProfileId)" :title="form.education.learnerProfileId ? '由学习者画像锁定' : ''" /></label>
              <label class="field"><span>课程版本</span><input v-model="form.education.curriculumVersion" required :readonly="Boolean(form.education.learnerProfileId)" :title="form.education.learnerProfileId ? '由学习者画像锁定' : ''" /></label>
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

      <section v-if="isAdminRole" class="workspace-grid">
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

      <section v-if="isAdminRole" class="tool-section panel" id="tools">
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
          <div><p class="eyebrow">{{ isAdminRole ? 'ADVANCED GOVERNANCE' : 'EDUCATION WORKSPACE' }}</p><h2>{{ isAdminRole ? '高级治理设置' : roleWorkspaceTitle }}</h2><p class="panel-heading-help">{{ isAdminRole ? '知识源、索引、组织策略和凭证设置只在这里维护。' : roleWorkspaceDetail }}</p></div>
          <button v-if="isAdminRole" class="secondary-button" type="button" @click="showGovernance = !showGovernance">{{ showGovernance ? '收起高级设置' : '展开高级设置' }}</button>
        </div>
        <div v-if="showGovernance" class="governance-grid">
          <section v-if="isAdminRole" class="governance-card context-workbench-card">
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
          <section v-if="isAdminRole" class="governance-card governance-fixed-card context-index-card">
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
          <section v-if="isAdminRole" class="governance-card governance-fixed-card context-config-card">
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
          <form v-if="isTeacherOnlyRole" id="education-document-upload" class="governance-card governance-fixed-card" @submit.prevent="createDocument">
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
              <div><p class="eyebrow">EDUCATION AGENT</p><h3>{{ isAdminWorkspace ? '教育概览与 Agent 状态' : (educationWorkspaceMode === 'teacher' ? '课程运营与 Agent 状态' : '学习契约与 Agent 状态') }}</h3></div>
              <span class="context-mode-chip">{{ educationWorkspaceModeLabel }}</span>
            </div>
            <p class="context-workbench-help">{{ educationWorkspaceModeDetail }}<template v-if="isAdminWorkspace">教育数据用于治理观察，不改变教师课程所有权或学生学习状态。</template><template v-else-if="isTeacherOnlyRole">课程元数据决定 Agent 的知识边界，学生提交物和测评证据决定后续复核动作。</template><template v-else>课程元数据决定检索范围，学习者状态和形成性证据决定 Agent 的教学动作。</template></p>
            <p v-if="educationError" class="policy-error">{{ educationError }}</p>
            <section v-if="isTeacherOnlyRole || (isLearnerOnlyRole && activeEducationCourse && !currentEducationSourceCount)" class="education-knowledge-base-bridge" :class="{ ready: currentEducationSourceCount }" aria-label="课程知识库入口">
              <div class="education-knowledge-base-bridge-icon"><BookOpen :size="16" /></div>
              <div class="education-knowledge-base-bridge-copy">
                <p class="eyebrow">COURSE KNOWLEDGE BASE</p>
                <strong>{{ currentEducationSourceCount ? (isTeacherOnlyRole ? `${currentEducationSourceCount} 个课程来源已配置` : `${currentEducationSourceCount} 个来源已进入当前课程约束`) : (educationWorkspaceMode === 'teacher' ? '先把课程资料接入知识库' : '当前课程还没有可检索的知识来源') }}</strong>
                <span>{{ currentEducationSourceCount ? 'Agent 会只从匹配学科、年级、课程版本、知识点和难度的来源中检索。' : (educationWorkspaceMode === 'teacher' ? '上传 PDF/DOCX 后，继续补充章节、知识点、前置知识和难度元数据，课程才可以启动教学 Run。' : '请联系课程负责人补充课程资料；没有授权来源时，Agent 不会退化成通用问答。') }}</span>
              </div>
              <button v-if="isTeacherOnlyRole && !manageableEducationDocuments.length" class="secondary-button" type="button" @click="openEducationDocumentUpload">上传课程资料 <ArrowUp :size="12" /></button>
              <button v-else-if="isTeacherOnlyRole" class="secondary-button" type="button" @click="openEducationAgentSetup">维护课程元数据 <ArrowUp :size="12" /></button>
            </section>
            <section v-if="isTeacherOnlyRole" class="education-agent-state-card education-teacher-state-card" :class="{ ready: teacherAgentReady }" aria-label="教师课程运营状态">
              <div class="education-agent-state-heading">
                <div><p class="eyebrow">CURRENT TEACHING STATE</p><h4>课程运营状态与下一步</h4><span>教师只需要关注课程资料、学生名单、作业证据和复核结果；学生画像由学生本人维护。</span></div>
                <span class="education-agent-state-pill"><i></i>{{ teacherAgentReady ? '课程资料已接入' : '等待课程配置' }}</span>
              </div>
              <div class="education-agent-state-grid">
                <article class="education-agent-state-item">
                  <small>01 · 课程资料</small><strong>{{ manageableEducationSources.length ? `${manageableEducationSources.length} 个来源已配置` : '尚未配置课程资料' }}</strong><p>{{ manageableEducationSources.length ? '资料已经具备课程边界，可继续绑定课程和学生。' : '先上传 PDF/DOCX，再维护课程元数据。' }}</p>
                </article>
                <article class="education-agent-state-item">
                  <small>02 · 课程与学生</small><strong>{{ teacherEducationCourses.length }} 门课程 · {{ teacherActiveLearnerCount }} 名学生</strong><p>{{ teacherEducationCourses.length ? (teacherActiveLearnerCount ? '课程名单已建立，可继续布置课程作业。' : '课程已创建，但还没有活跃学生名单。') : '创建课程实例，把课程资料变成可运营的教学单元。' }}</p>
                </article>
                <article class="education-agent-state-item">
                  <small>03 · 教师下一动作</small><strong>{{ teacherNextAction.label }}</strong><p>{{ teacherNextAction.detail }}</p>
                </article>
                <article class="education-agent-state-item">
                  <small>04 · 待处理证据</small><strong>{{ teacherCoursePendingCount }} 项课程待办</strong><p>{{ teacherCoursePendingCount ? '请查看提交物、测评证据和反馈，再确认或退回作业。' : '当前没有待处理复核；可以继续布置或查看课程进度。' }}</p>
                </article>
              </div>
              <footer class="education-agent-state-footer">
                <span>{{ teacherNextAction.detail }}</span>
                <button class="secondary-button" type="button" @click="runTeacherNextAction">{{ teacherNextAction.label }} <ArrowUp :size="12" /></button>
              </footer>
            </section>
            <section v-else-if="isLearnerOnlyRole" class="education-agent-state-card" :class="{ ready: educationAgentReady }" aria-label="教育 Agent 当前状态">
              <div class="education-agent-state-heading">
                <div><p class="eyebrow">CURRENT AGENT STATE</p><h4>当前学习状态与下一步</h4><span>Agent 将课程边界、学习者证据和教学动作串成一条可追踪的学习回路。</span></div>
                <span class="education-agent-state-pill"><i></i>{{ educationAgentReady ? '课程与学情已接入' : '等待配置' }}</span>
              </div>
              <div class="education-agent-state-grid">
                <article class="education-agent-state-item">
                  <small>01 · 课程边界</small><strong>{{ currentEducationSourceLabel }}</strong><p>{{ currentEducationRetrievalDetail }}</p><small v-if="educationVersionRepairHint" class="education-version-repair-hint">{{ educationVersionRepairHint }}</small>
                </article>
                <article class="education-agent-state-item">
                  <small>02 · 学习者状态</small><strong>{{ learnerStateDiagnosis.title }}</strong><p>{{ learnerStateDiagnosis.detail }}</p>
                </article>
                <article class="education-agent-state-item">
                  <small>03 · Agent 下一动作</small><strong>{{ agentTeachingAction.title }}</strong><p>{{ agentTeachingAction.detail }}</p>
                </article>
                <article class="education-agent-state-item">
                  <small>04 · 需要的证据</small><strong>{{ agentEvidenceRequest.title }}</strong><p>{{ agentEvidenceRequest.detail }}</p>
                </article>
              </div>
              <footer class="education-agent-state-footer">
                <span>{{ activeLearningGoal ? `当前目标：${activeLearningGoal.title} · ${learningEvidenceSummary}` : '尚未绑定学习目标；聊天内容不会被直接当作掌握度证据。' }}</span>
                <button class="secondary-button" type="button" @click="educationAgentReady ? (chatMode = true) : openEducationAgentSetup()">{{ educationAgentReady ? '进入学习对话' : '配置并开始' }} <ArrowUp :size="12" /></button>
              </footer>
            </section>
            <ol class="education-agent-loop" aria-label="教育 Agent 学习闭环">
              <li v-for="(trace, index) in (isAdminWorkspace ? adminOperationsTrace : (isTeacherOnlyRole ? teacherOperationsTrace : educationAgentTrace))" :key="trace.id" :class="`is-${trace.state}`">
                <span class="education-agent-loop-index">{{ String(index + 1).padStart(2, '0') }}</span>
                <div><strong>{{ trace.label }}</strong><small>{{ trace.value }}</small></div>
                <ArrowDown v-if="index < (isAdminWorkspace ? adminOperationsTrace.length : (isTeacherOnlyRole ? teacherOperationsTrace.length : educationAgentTrace.length)) - 1" class="education-agent-loop-arrow" :size="13" />
              </li>
            </ol>
            <details v-if="isTeacherOnlyRole && educationMetrics && teacherEducationCourses.length" class="education-operations-metrics">
              <summary><span>教师运营指标</span><small>完成率 · 证据覆盖 · 复核与干预</small></summary>
              <div class="education-metrics" aria-label="教育业务闭环指标">
              <div><span>作业完成率</span><strong>{{ formatRate(educationMetrics.assignmentCompletionRate) }}</strong><small>{{ educationMetrics.assignmentCompleted }} / {{ educationMetrics.assignmentTotal }}</small></div>
              <div><span>提交物覆盖</span><strong>{{ formatRate(educationMetrics.assignmentSubmissionCoverageRate) }}</strong><small>{{ educationMetrics.assignmentSubmissionCovered }} / {{ educationMetrics.assignmentTotal }}</small></div>
              <div><span>任务启动率</span><strong>{{ formatRate(educationMetrics.taskStartRate) }}</strong><small>{{ educationMetrics.taskStarted }} / {{ educationMetrics.taskTotal }}</small></div>
              <div><span>任务完成率</span><strong>{{ formatRate(educationMetrics.taskCompletionRate) }}</strong><small>{{ educationMetrics.taskCompleted }} / {{ educationMetrics.taskTotal }}</small></div>
              <div><span>测评证据覆盖</span><strong>{{ formatRate(educationMetrics.taskEvidenceCoverageRate) }}</strong><small>{{ educationMetrics.taskEvidenceCovered }} / {{ educationMetrics.taskStarted }}</small></div>
              <div><span>通知读取率</span><strong>{{ formatRate(educationMetrics.notificationReadRate) }}</strong><small>{{ educationMetrics.notificationRead }} / {{ educationMetrics.notificationTotal }}</small></div>
              <div><span>测评正确率</span><strong>{{ formatRate(educationMetrics.assessmentAccuracyRate) }}</strong><small>{{ educationMetrics.correctAssessmentTotal }} / {{ educationMetrics.assessmentTotal }}</small></div>
              <div><span>教师确认率</span><strong>{{ formatRate(educationMetrics.assignmentReviewVerificationRate) }}</strong><small>{{ educationMetrics.assignmentReviewVerified }} / {{ educationMetrics.assignmentReviewPending + educationMetrics.assignmentReviewRevisionRequired + educationMetrics.assignmentReviewVerified }}</small></div>
              <div><span>教师量规覆盖</span><strong>{{ formatRate(educationMetrics.teacherEvaluationCoverageRate) }}</strong><small>{{ educationMetrics.teacherEvaluationCoveredAssignmentTotal }} / {{ educationMetrics.assignmentTotal }} 作业</small></div>
              <div><span>教师量规均分</span><strong>{{ formatScore(educationMetrics.averageTeacherContentCorrectnessScore) }}</strong><small>内容 {{ formatScore(educationMetrics.averageTeacherContentCorrectnessScore) }} · 证据 {{ formatScore(educationMetrics.averageTeacherEvidenceQualityScore) }} · 迁移 {{ formatScore(educationMetrics.averageTeacherTransferReadinessScore) }}</small></div>
              <div><span>反馈确认率</span><strong>{{ formatRate(educationMetrics.feedbackAcknowledgementRate) }}</strong><small>{{ educationMetrics.feedbackAcknowledged }} / {{ educationMetrics.feedbackTotal }}</small></div>
              <div><span>反馈执行率</span><strong>{{ formatRate(educationMetrics.feedbackResolutionRate) }}</strong><small>{{ educationMetrics.feedbackResolved }} / {{ educationMetrics.feedbackTotal }}</small></div>
              <div><span>重试成功率</span><strong>{{ formatRate(educationMetrics.retrySuccessRate) }}</strong><small>{{ educationMetrics.retriedTaskCompleted }} / {{ educationMetrics.retriedTaskTotal }}</small></div>
              <div><span>作业待重试</span><strong>{{ educationMetrics.assignmentRetryRequired }}</strong><small>失败/超时/取消后待处理</small></div>
              <div><span>作业待返工</span><strong>{{ educationMetrics.assignmentReviewRevisionRequired }}</strong><small>教师退回后待重新提交</small></div>
              <div><span>保持度正确率</span><strong>{{ formatRate(educationMetrics.reviewAssessmentAccuracyRate) }}</strong><small>平均掌握度提升 {{ formatRate(educationMetrics.averageMasteryGain) }}</small></div>
              </div>
            </details>
            <details v-if="isAdminWorkspace && educationMetrics" class="education-operations-metrics" open>
              <summary><span>组织教育指标</span><small>课程规模 · 作业闭环 · 证据质量</small></summary>
              <p class="learning-task-help">管理员查看同租户聚合结果；课程资料、名单、布置和教师复核仍由课程负责人操作。</p>
              <div class="education-metrics" aria-label="管理员组织教育指标">
                <div><span>课程知识源</span><strong>{{ educationSources.length }}</strong><small>已接入课程元数据</small></div>
                <div><span>课程实例</span><strong>{{ educationCourses.length }}</strong><small>组织内课程</small></div>
                <div><span>课程作业</span><strong>{{ educationMetrics.assignmentTotal }}</strong><small>教师发布的作业</small></div>
                <div><span>作业完成率</span><strong>{{ formatRate(educationMetrics.assignmentCompletionRate) }}</strong><small>{{ educationMetrics.assignmentCompleted }} / {{ educationMetrics.assignmentTotal }}</small></div>
                <div><span>提交物覆盖</span><strong>{{ formatRate(educationMetrics.assignmentSubmissionCoverageRate) }}</strong><small>{{ educationMetrics.assignmentSubmissionCovered }} / {{ educationMetrics.assignmentTotal }}</small></div>
                <div><span>教师确认率</span><strong>{{ formatRate(educationMetrics.assignmentReviewVerificationRate) }}</strong><small>{{ educationMetrics.assignmentReviewVerified }} 份已确认</small></div>
                <div><span>测评证据</span><strong>{{ educationMetrics.assessmentTotal }}</strong><small>形成性 {{ educationMetrics.formativeAssessmentTotal }} · 复习 {{ educationMetrics.reviewAssessmentTotal }}</small></div>
                <div><span>保持度正确率</span><strong>{{ formatRate(educationMetrics.reviewAssessmentAccuracyRate) }}</strong><small>平均掌握度提升 {{ formatRate(educationMetrics.averageMasteryGain) }}</small></div>
              </div>
            </details>
            <section v-if="isAdminWorkspace" class="education-source-overview" aria-label="组织课程知识源概览">
              <div class="subsection-title">
                <div><h4>课程知识源概览</h4><span>只读元数据；课程资料维护由教师负责</span></div>
                <span class="context-mode-chip">{{ educationSources.length }} 个来源</span>
              </div>
              <p class="learning-task-help">这里显示资料名称、课程边界和所有者，帮助管理员确认 Agent 的知识来源；不会展示课程正文，也不能在此修改资料。</p>
              <div v-if="educationSources.length" class="education-source-overview-list">
                <article v-for="source in educationSources" :key="source.id" class="education-source-overview-row">
                  <div class="education-source-overview-main">
                    <strong>{{ educationSourceLabel(source) }}</strong>
                    <small>{{ source.documentId }}<template v-if="source.documentOwnerUserId"> · 资料所有者：{{ source.documentOwnerUserId }}</template></small>
                  </div>
                  <div class="education-source-overview-scope">
                    <span>{{ source.subject }} · {{ source.gradeLevel }} · {{ source.curriculumVersion }}</span>
                    <small>{{ source.chapter || '未标注章节' }} · {{ source.conceptTags || '未标注知识点' }} · 难度 {{ source.difficultyLevel }}</small>
                  </div>
                </article>
              </div>
              <div v-else class="context-preview-empty">组织内还没有配置课程知识源；请让教师上传资料并补充课程元数据。</div>
            </section>
            <details v-if="isLearnerOnlyRole" class="education-profile-setup" :open="!activeLearnerProfile">
              <summary><span><strong>学习者画像与目标</strong><small>{{ activeLearnerProfile ? `${activeLearnerProfile.subject} · ${activeLearnerProfile.gradeLevel} · ${activeLearnerProfile.curriculumVersion}` : '建立 Agent 可持续读取的学习上下文' }}</small></span><em>{{ activeLearnerProfile ? '已绑定' : '待建立' }}</em></summary>
              <div class="education-profile-setup-content">
                <form class="education-profile-form" @submit.prevent="saveLearnerProfile">
                  <label class="field"><span>学科</span><input v-model="learnerProfileForm.subject" required maxlength="128" /></label>
                  <label class="field"><span>年级</span><input v-model="learnerProfileForm.gradeLevel" required maxlength="128" /></label>
                  <label class="field"><span>课程版本</span><input v-model="learnerProfileForm.curriculumVersion" required maxlength="128" /></label>
                  <label class="field"><span>学习目标</span><input v-model="learnerProfileForm.learningGoal" maxlength="512" placeholder="例如：掌握函数基础并能独立完成练习" /></label>
                  <button class="secondary-button" type="submit" :disabled="educationLoading">{{ educationLoading ? '保存中…' : '保存学习者画像' }}</button>
                </form>
                <div v-if="learnerProfiles.length" class="education-profile-list">
                  <div v-for="profile in learnerProfiles" :key="profile.id" class="education-profile-chip" :class="{ active: profile.id === activeLearnerProfile?.id }">
                    <button type="button" class="education-profile-select" @click="selectLearnerProfile(profile)">
                      <strong>{{ profile.subject }} · {{ profile.gradeLevel }}</strong><small>{{ profile.curriculumVersion }} · {{ profile.learningGoal || '未设置学习目标' }}</small>
                    </button>
                    <button type="button" class="education-profile-delete" :disabled="learnerProfileDeletingId === profile.id" title="删除学习画像" @click.stop="deleteLearnerProfile(profile)"><Trash2 :size="13" /></button>
                  </div>
                </div>
              </div>
            </details>
            <section class="education-course-workbench" aria-label="课程工作台">
              <div class="subsection-title education-course-heading">
                <div><h4>{{ isAdminWorkspace ? '课程实例概览' : (educationWorkspaceMode === 'teacher' ? '课程运营工作台' : '我的课程与学习路径') }}</h4><span>{{ isAdminWorkspace ? `${educationCourses.length} 门组织课程 · ${learningAssignments.length} 份课程作业` : `${teacherEducationCourses.length} 个我创建 · ${enrolledEducationCourses.length} 个已加入` }}</span></div>
                <span v-if="activeEducationCourse" class="context-mode-chip">{{ educationCourseStatusLabel(activeEducationCourse.status) }}</span>
              </div>
              <p class="learning-task-help">{{ isAdminWorkspace ? '管理员在这里查看组织课程和作业规模；课程资料、名单、布置与复核由教师负责。' : '课程约束决定学习范围；Agent 会结合每次作业、提交物和对话证据更新学习状态。' }}{{ educationWorkspaceMode === 'teacher' ? '班级进度、名单和布置动作只在课程负责人入口中展开；学生画像由学生本人维护。' : (!isAdminWorkspace ? '你只需要关注自己的课程行动、证据和结课结果。' : '') }}</p>
              <details v-if="canManageEducationOperations" class="education-teacher-entry" :open="educationWorkspaceMode === 'teacher'">
                <summary><span><strong>课程负责人入口</strong><small>创建课程、维护名单、批量布置作业</small></span><em>{{ educationWorkspaceMode === 'teacher' ? '管理模式' : '需要教师 / 组织权限' }}</em></summary>
                <p class="education-teacher-entry-help">这是课程管理操作，不会改变学习者的 Agent 状态；提交后仍由 Runtime 做最终权限校验。</p>
                <form class="education-course-form" @submit.prevent="createEducationCourse">
                  <label class="field"><span>课程代码</span><input v-model="educationCourseForm.code" required maxlength="128" placeholder="例如：MATH-G1-2026" /></label>
                  <label class="field"><span>课程名称</span><input v-model="educationCourseForm.title" required maxlength="255" placeholder="例如：高中数学函数基础" /></label>
                  <label class="field"><span>学科</span><input v-model="educationCourseForm.subject" required maxlength="128" /></label>
                  <label class="field"><span>年级</span><input v-model="educationCourseForm.gradeLevel" required maxlength="128" /></label>
                  <label class="field"><span>课程版本</span><input v-model="educationCourseForm.curriculumVersion" required maxlength="128" /></label>
                  <button class="secondary-button" type="submit" :disabled="educationCourseSaving">{{ educationCourseSaving ? '创建中…' : '创建课程实例' }}</button>
                </form>
              </details>
              <div v-if="educationCourses.length" class="education-course-list">
                <button v-for="course in educationCourses" :key="course.id" type="button" class="education-course-chip" :class="{ active: course.id === activeEducationCourseId }" @click="selectEducationCourse(course)">
                  <span><strong>{{ course.title }}</strong><small>{{ course.code }} · {{ course.subject }} · {{ course.gradeLevel }} · {{ course.curriculumVersion }}</small></span>
                  <em>{{ isAdminWorkspace ? `组织课程 · ${course.activeEnrollmentCount} 人` : (course.ownerUserId === form.userId ? `我的课程 · ${course.activeEnrollmentCount} 人` : '已加入') }}</em>
                </button>
              </div>
              <div v-else class="context-preview-empty">{{ isAdminWorkspace ? '当前组织还没有课程实例；课程由教师创建并运营。' : (educationWorkspaceMode === 'learner' ? '还没有加入课程；课程负责人发布后，课程约束和学习路径会出现在这里。' : '还没有可访问的课程；如需开课，请展开课程负责人入口。') }}</div>
              <div v-if="activeEducationCourse && (activeEducationCourseIsOwner || isAdminWorkspace)" class="education-course-detail">
                <div class="education-course-detail-heading">
                  <div><strong>{{ activeEducationCourse.title }}</strong><small>{{ activeEducationCourse.code }} · 课程负责人 {{ activeEducationCourse.ownerUserId }}</small></div>
                  <span v-if="isAdminWorkspace" class="context-mode-chip">管理员只读</span>
                  <div class="education-course-detail-actions">
                    <button v-if="activeEducationCourseIsOwner && activeEducationCourse.status === 'ACTIVE'" class="secondary-button" type="button" :title="educationCourseProgress?.readyToComplete ? '结课结果会固化当前课程证据快照' : '请先处理下方结课阻塞清单；Runtime 仍会在提交时做最终校验'" :disabled="educationCourseActionId === activeEducationCourse.id || !educationCourseProgress?.readyToComplete" @click="completeEducationCourse(activeEducationCourse)">{{ educationCourseActionId === activeEducationCourse.id ? '结课中…' : '完成结课' }}</button>
                    <button v-if="activeEducationCourseIsOwner && ['ACTIVE', 'COMPLETED'].includes(activeEducationCourse.status)" class="text-button" type="button" :disabled="educationCourseActionId === activeEducationCourse.id" @click="archiveEducationCourse(activeEducationCourse)">{{ educationCourseActionId === activeEducationCourse.id ? '处理中…' : '归档课程' }}</button>
                  </div>
                </div>
                <div class="education-course-columns">
                  <div id="education-course-roster" class="education-course-roster">
                    <div class="subsection-title"><div><h4>活跃名单</h4><span>{{ educationCourseEnrollments.filter((item) => item.status === 'ACTIVE').length }} 人</span></div></div>
                    <form v-if="activeEducationCourseIsOwner" class="education-course-enrollment-form" @submit.prevent="enrollEducationLearner">
                      <label class="field"><span>学习者 ID</span><input v-model="educationCourseEnrollmentForm.learnerUserId" required maxlength="255" placeholder="例如：student-1" /></label>
                      <button class="secondary-button" type="submit" :disabled="educationCourseRosterSaving || activeEducationCourse.status !== 'ACTIVE'">{{ educationCourseRosterSaving ? '加入中…' : '加入名单' }}</button>
                    </form>
                    <p v-else class="learning-task-help">管理员只读查看名单；加入或移除学习者由课程教师执行。</p>
                    <div v-if="educationCourseEnrollments.length" class="education-course-roster-list">
                      <div v-for="enrollment in educationCourseEnrollments" :key="enrollment.id" class="education-course-roster-row" :class="{ inactive: enrollment.status !== 'ACTIVE' }">
                        <span><strong>{{ enrollment.learnerUserId }}</strong><small>{{ enrollment.status === 'ACTIVE' ? '活跃成员' : '已移除' }} · {{ formatDate(enrollment.enrolledAt) }}</small></span>
                        <button v-if="activeEducationCourseIsOwner && enrollment.status === 'ACTIVE' && activeEducationCourse.status === 'ACTIVE'" class="text-button" type="button" :disabled="educationCourseActionId === enrollment.learnerUserId" @click="removeEducationLearner(enrollment)">{{ educationCourseActionId === enrollment.learnerUserId ? '处理中…' : '移除' }}</button>
                      </div>
                    </div>
                    <div v-else class="context-preview-empty">名单为空；请先加入学习者。</div>
                  </div>
                  <div id="education-course-assignment" class="education-course-assignment">
                    <div class="subsection-title"><div><h4>批量布置作业</h4><span>一次提交，逐人追踪</span></div></div>
                    <form v-if="activeEducationCourseIsOwner" class="education-course-assignment-form" @submit.prevent="assignEducationCourse">
                      <label class="field"><span>作业标题</span><input v-model="educationCourseAssignmentForm.title" required maxlength="255" placeholder="例如：函数定义域练习" /></label>
                      <label class="field"><span>目标知识点</span><input v-model="educationCourseAssignmentForm.conceptKey" required maxlength="255" placeholder="函数定义域" /></label>
                      <label class="field"><span>目标掌握度</span><input v-model="educationCourseAssignmentForm.targetMastery" type="number" min="0.01" max="1" step="0.05" required /></label>
                      <label class="field"><span>截止时间（可选）</span><input v-model="educationCourseAssignmentForm.dueAt" type="datetime-local" /></label>
                      <label class="field education-course-wide"><span>作业说明</span><textarea v-model="educationCourseAssignmentForm.instructions" required maxlength="4000" rows="2" placeholder="说明作答范围、提交要求或迁移任务"></textarea></label>
                      <button class="secondary-button" type="submit" :disabled="educationCourseAssignmentSaving || !educationCourseEnrollments.some((item) => item.status === 'ACTIVE')">{{ educationCourseAssignmentSaving ? '布置中…' : '向活跃名单布置' }}</button>
                    </form>
                    <p v-else class="learning-task-help">管理员只读查看作业规模与证据覆盖；布置作业由课程教师执行。</p>
                  </div>
                </div>
                <div v-if="educationCourseProgress" class="education-course-progress">
                  <div class="subsection-title"><div><h4>课程进度与干预队列</h4><span>{{ educationCourseProgress.truncated ? '仅展示最近 500 份作业' : '覆盖全部课程作业' }}</span></div><button class="text-button" type="button" :disabled="educationCourseLoading" @click="loadEducationCourseWorkspace(activeEducationCourse.id)">{{ educationCourseLoading ? '刷新中…' : '刷新进度' }}</button></div>
                  <div class="education-course-summary-grid">
                    <div><span>名单覆盖</span><strong>{{ formatRate(educationCourseProgress.rosterCoverageRate) }}</strong><small>{{ educationCourseProgress.learnersWithAssignments }} / {{ educationCourseProgress.activeLearnerTotal }} 名活跃学习者</small></div>
                    <div><span>作业完成</span><strong>{{ formatRate(educationCourseProgress.assignmentCompletionRate) }}</strong><small>{{ educationCourseProgress.completed }} / {{ educationCourseProgress.assignmentTotal }}</small></div>
                    <div><span>教师确认</span><strong>{{ formatRate(educationCourseProgress.teacherVerificationRate) }}</strong><small>{{ educationCourseProgress.reviewVerified }} / {{ educationCourseProgress.reviewPending + educationCourseProgress.reviewVerified + educationCourseProgress.revisionRequired }}</small></div>
                    <div><span>待证据</span><strong>{{ educationCourseProgress.awaitingEvidence }}</strong><small>Run 已结束但证据未回写</small></div>
                    <div><span>待重试</span><strong>{{ educationCourseProgress.retryRequired }}</strong><small>失败、超时或返工</small></div>
                    <div><span>开放干预</span><strong>{{ educationCourseProgress.openInterventionCount }}</strong><small>补证据或建议重试</small></div>
                    <div><span>结课判定</span><strong>{{ educationCourseProgress.readyToComplete ? '可结课' : '未就绪' }}</strong><small>{{ educationCourseProgress.readyToComplete ? '名单、确认与提交物齐全' : `作业待处理 ${educationCourseProgress.completionBlockerCount} · 缺提交物 ${educationCourseProgress.submissionBlockerCount} · 名单缺口 ${educationCourseProgress.rosterCoverageBlockerCount}` }}</small></div>
                  </div>
                  <section v-if="!educationCourseProgress.readyToComplete" class="education-course-completion-blockers" aria-label="结课阻塞清单">
                    <header>
                      <div><strong>结课阻塞清单</strong><span>Agent 不会绕过未完成的作业、证据或名单覆盖直接结课。</span></div>
                      <em>{{ Number(educationCourseProgress.completionBlockerCount || 0) + Number(educationCourseProgress.submissionBlockerCount || 0) + Number(educationCourseProgress.rosterCoverageBlockerCount || 0) }} 项待处理</em>
                    </header>
                    <div class="education-course-completion-blocker-list">
                      <button v-if="Number(educationCourseProgress.activeLearnerTotal || 0) === 0 || Number(educationCourseProgress.rosterCoverageBlockerCount || 0)" type="button" class="education-course-completion-blocker is-roster" @click="focusEducationCourseRoster">
                        <span><BookOpen :size="14" /></span><strong>补齐活跃名单覆盖</strong><small>{{ Number(educationCourseProgress.activeLearnerTotal || 0) === 0 ? '还没有活跃学习者' : `${educationCourseProgress.rosterCoverageBlockerCount} 名学习者尚未覆盖作业` }}</small><b>去名单</b>
                      </button>
                      <button v-if="Number(educationCourseProgress.assignmentTotal || 0) === 0" type="button" class="education-course-completion-blocker" @click="focusEducationCourseAssignment">
                        <span><ListChecks :size="14" /></span><strong>先布置课程作业</strong><small>没有有效作业，Agent 无法形成课程结课证据。</small><b>去布置</b>
                      </button>
                      <button v-if="Number(educationCourseProgress.completionBlockerCount || 0)" type="button" class="education-course-completion-blocker" @click="focusCourseBlocker('completion')">
                        <span><CircleAlert :size="14" /></span><strong>处理未完成作业</strong><small>{{ educationCourseProgress.completionBlockerCount }} 份作业尚未完成或完成复核。</small><b>看作业</b>
                      </button>
                      <button v-if="Number(educationCourseProgress.awaitingEvidence || 0)" type="button" class="education-course-completion-blocker" @click="focusCourseBlocker('evidence')">
                        <span><ShieldCheck :size="14" /></span><strong>补回形成性证据</strong><small>{{ educationCourseProgress.awaitingEvidence }} 份作业 Run 已结束，但还没有可验证测评证据。</small><b>补证据</b>
                      </button>
                      <button v-if="Number(educationCourseProgress.retryRequired || 0)" type="button" class="education-course-completion-blocker" @click="focusCourseBlocker('retry')">
                        <span><RefreshCw :size="14" /></span><strong>安排失败作业重试</strong><small>{{ educationCourseProgress.retryRequired }} 份作业需要重新执行或重新学习。</small><b>看重试</b>
                      </button>
                      <button v-if="Number(educationCourseProgress.reviewPending || 0)" type="button" class="education-course-completion-blocker" @click="focusCourseBlocker('review')">
                        <span><Check :size="14" /></span><strong>完成教师确认</strong><small>{{ educationCourseProgress.reviewPending }} 份作业等待教师依据提交物与证据确认。</small><b>去确认</b>
                      </button>
                      <button v-if="Number(educationCourseProgress.revisionRequired || 0)" type="button" class="education-course-completion-blocker" @click="focusCourseBlocker('revision')">
                        <span><PenLine :size="14" /></span><strong>跟进返工作业</strong><small>{{ educationCourseProgress.revisionRequired }} 份作业已退回，需要学习者重新提交。</small><b>看返工</b>
                      </button>
                      <button v-if="Number(educationCourseProgress.submissionBlockerCount || 0)" type="button" class="education-course-completion-blocker" @click="focusCourseBlocker('submission')">
                        <span><Paperclip :size="14" /></span><strong>补齐提交物</strong><small>{{ educationCourseProgress.submissionBlockerCount }} 份有效作业还没有可追溯的学习者提交物。</small><b>看提交物</b>
                      </button>
                    </div>
                  </section>
                  <div v-if="educationCourseProgress.learners?.length" class="education-course-progress-list">
                    <div class="education-course-progress-header"><span>学习者</span><span>作业状态</span><span>掌握度进度</span><span>下一步</span></div>
                    <div v-for="learner in educationCourseProgress.learners" :key="learner.learnerUserId" class="education-course-progress-row">
                      <span><strong>{{ learner.learnerUserId }}</strong><small>{{ learner.lastActivityAt ? `最近 ${formatDate(learner.lastActivityAt)}` : '尚无作业活动' }}</small></span>
                      <span class="education-course-status-copy">{{ learner.assigned }} 待接受 · {{ learner.completed }} 完成 · {{ learner.awaitingEvidence }} 待证据 · {{ learner.retryRequired }} 待重试 · {{ learner.reviewPending }} 待确认 · {{ learner.submissionMissing }} 缺提交物</span>
                      <span><strong>{{ formatRate(learner.averageMasteryProgress) }}</strong><small>提升 {{ learner.averageMasteryGain >= 0 ? '+' : '' }}{{ formatRate(learner.averageMasteryGain) }}</small></span>
                      <button class="text-button education-course-next-action" type="button" @click="focusCourseLearnerAction(learner)">{{ courseLearnerNextAction(learner).label }}<small v-if="courseLearnerAttentionCount(learner)">{{ courseLearnerAttentionCount(learner) }} 项待处理</small></button>
                    </div>
                  </div>
                  <div v-else class="context-preview-empty">名单中的学习者还没有作业；布置作业后，这里会显示每人的业务状态。</div>
                  <div v-if="educationCourseResult" class="education-course-progress education-course-result">
                    <div class="subsection-title"><div><h4>结课结果快照</h4><span>{{ formatDate(educationCourseResult.completedAt) }}</span></div><div><span class="context-mode-chip">不可被后续复习改写</span><button v-if="activeEducationCourseIsOwner" class="text-button" type="button" :disabled="educationCourseActionId === activeEducationCourse.id" @click="exportEducationCourseResult(activeEducationCourse)">{{ educationCourseActionId === activeEducationCourse.id ? '导出中…' : '导出报告' }}</button></div></div>
                    <div class="education-course-summary-grid">
                      <div><span>有效作业</span><strong>{{ educationCourseResult.assignmentCompleted }} / {{ educationCourseResult.effectiveAssignmentTotal }}</strong><small>教师确认 {{ educationCourseResult.assignmentVerified }}</small></div>
                      <div><span>提交物覆盖</span><strong>{{ formatRate(educationCourseResult.effectiveAssignmentTotal ? educationCourseResult.submissionCovered / educationCourseResult.effectiveAssignmentTotal : 0) }}</strong><small>{{ educationCourseResult.submissionCovered }} 份</small></div>
                      <div><span>平均目标进度</span><strong>{{ formatRate(educationCourseResult.averageMasteryProgress) }}</strong><small>平均提升 {{ formatRate(educationCourseResult.averageMasteryGain) }}</small></div>
                      <div><span>学习者结果</span><strong>{{ educationCourseResult.learners?.length || 0 }} / {{ educationCourseResult.activeLearnerTotal }}</strong><small>覆盖 {{ educationCourseResult.learnersWithAssignments }} 人</small></div>
                    </div>
                    <div v-if="educationCourseResult.learners?.length" class="education-course-progress-list">
                      <div class="education-course-progress-header"><span>学习者</span><span>有效作业</span><span>掌握度</span><span>提交物</span></div>
                      <div v-for="learner in educationCourseResult.learners" :key="learner.learnerUserId" class="education-course-progress-row">
                        <span><strong>{{ learner.learnerUserId }}</strong><small>{{ learner.lastActivityAt ? `最近 ${formatDate(learner.lastActivityAt)}` : '无活动时间' }}</small></span>
                        <span>{{ learner.assignmentCompleted }} / {{ learner.effectiveAssignmentTotal }}<small>确认 {{ learner.assignmentVerified }}</small></span>
                        <span>{{ formatRate(learner.averageMasteryProgress) }}<small>提升 {{ formatRate(learner.averageMasteryGain) }}</small></span>
                        <span>{{ learner.submissionCovered }} / {{ learner.effectiveAssignmentTotal }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div v-else-if="activeEducationCourse && isLearnerOnlyRole" class="education-course-detail education-course-learner-detail">
                <div class="education-course-detail-heading">
                  <div><strong>{{ activeEducationCourse.title }}</strong><small>{{ activeEducationCourse.code }} · 课程负责人 {{ activeEducationCourse.ownerUserId }}</small></div>
                  <span class="context-mode-chip">我的学习状态</span>
                </div>
                <div v-if="educationCourseResult" class="education-course-progress education-course-result education-course-learner-result">
                  <div class="subsection-title"><div><h4>我的结课结果</h4><span>{{ formatDate(educationCourseResult.completedAt) }}</span></div><span class="context-mode-chip">不可被后续复习改写</span></div>
                  <template v-if="activeEducationCourseLearnerResult">
                    <p class="learning-task-help">这是 Agent 在结课时固化的个人学习事实，后续复习会更新当前状态，但不会改写这份结果。</p>
                    <div class="education-course-summary-grid education-course-learner-summary-grid">
                      <div><span>有效作业</span><strong>{{ activeEducationCourseLearnerResult.assignmentCompleted }} / {{ activeEducationCourseLearnerResult.effectiveAssignmentTotal }}</strong><small>已完成的课程作业</small></div>
                      <div><span>教师确认</span><strong>{{ activeEducationCourseLearnerResult.assignmentVerified }} / {{ activeEducationCourseLearnerResult.effectiveAssignmentTotal }}</strong><small>通过业务复核</small></div>
                      <div><span>提交物覆盖</span><strong>{{ activeEducationCourseLearnerResult.submissionCovered }} / {{ activeEducationCourseLearnerResult.effectiveAssignmentTotal }}</strong><small>有可追溯的作答</small></div>
                      <div><span>目标进度</span><strong>{{ formatRate(activeEducationCourseLearnerResult.averageMasteryProgress) }}</strong><small>掌握度提升 {{ activeEducationCourseLearnerResult.averageMasteryGain >= 0 ? '+' : '' }}{{ formatRate(activeEducationCourseLearnerResult.averageMasteryGain) }}</small></div>
                    </div>
                    <small class="education-course-learner-activity">{{ activeEducationCourseLearnerResult.lastActivityAt ? `最后学习活动：${formatDate(activeEducationCourseLearnerResult.lastActivityAt)}` : '结课时没有记录到个人学习活动。' }}</small>
                  </template>
                  <div v-else class="context-preview-empty">课程已形成结课快照，但其中没有当前账号的个人学习记录。</div>
                </div>
                <div v-else class="education-course-learner-state">
                  <div><strong>{{ activeEducationCourse.status === 'ACTIVE' ? '课程进行中' : '结课结果尚未读取' }}</strong><small>{{ activeEducationCourse.status === 'ACTIVE' ? 'Agent 正在依据你的作业、提交物与对话证据更新学习状态；结课后这里会出现个人结果。' : '请刷新课程工作台；若仍不可用，请联系课程负责人确认结课快照。' }}</small></div>
                  <div v-if="activeEducationCourse.status === 'ACTIVE' && activeEducationCourseLearnerProgress" class="education-course-learner-live">
                    <div><small>课程作业</small><strong>{{ activeEducationCourseLearnerProgress.completed }} / {{ activeEducationCourseLearnerProgress.total }} 已完成</strong></div>
                    <div><small>待处理</small><strong>{{ activeEducationCourseLearnerProgress.attention }} 项</strong><span v-if="activeEducationCourseLearnerProgress.nextAction?.detail">{{ activeEducationCourseLearnerProgress.nextAction.detail }}</span></div>
                    <div><small>目标进度</small><strong>{{ activeEducationCourseLearnerProgress.averageMasteryProgress === null ? '待测评' : formatRate(activeEducationCourseLearnerProgress.averageMasteryProgress) }}</strong></div>
                  </div>
                  <button v-if="activeEducationCourse.status === 'ACTIVE'" class="secondary-button" type="button" @click="takeLearnerCourseNextAction">{{ activeEducationCourseLearnerProgress?.nextAction?.label || '查看我的作业' }} <ArrowUp :size="12" /></button>
                  <button v-else class="text-button" type="button" :disabled="educationCourseLoading" @click="loadEducationCourseWorkspace(activeEducationCourse.id)">{{ educationCourseLoading ? '刷新中…' : '刷新结果' }}</button>
                </div>
              </div>
            </section>
            <section v-if="isTeacherOnlyRole && learningEvaluationQueue.length" class="learning-assignment-workbench" aria-label="独立评价队列">
              <div class="subsection-title"><div><h4>独立评价队列</h4><span>{{ learningEvaluationQueue.length }} 份已完成作业待第二评分者评价</span></div><span class="context-mode-chip">education.evaluate</span></div>
              <p class="learning-task-help">独立评价不会改变教师确认状态；系统会将两个评分者的三维分数用于共识判定和实验审计。</p>
              <div class="learning-assignment-list">
                <article v-for="assignment in learningEvaluationQueue" :key="assignment.id" class="learning-assignment-row">
                  <div class="learning-assignment-main"><div class="learning-assignment-meta"><strong>{{ assignment.title }}</strong><span>待独立评价</span></div><small>{{ assignment.teacherUserId }} → {{ assignment.learnerUserId }} · {{ assignment.subject }} · {{ assignment.gradeLevel }} · {{ assignment.curriculumVersion }}</small><p>{{ assignment.instructions }}</p></div>
                  <div class="learning-assignment-actions"><button class="secondary-button" type="button" :disabled="learningIndependentEvaluationSavingId === assignment.id" @click="submitIndependentLearningEvaluation(assignment)">{{ learningIndependentEvaluationSavingId === assignment.id ? '提交中…' : '提交独立评价' }}</button></div>
                </article>
              </div>
            </section>
            <section class="learning-assignment-workbench" aria-label="课程作业入口">
              <div class="subsection-title"><h4>{{ isAdminWorkspace ? '课程作业概览' : (educationWorkspaceMode === 'teacher' ? '课程作业与复核' : '我的课程作业与反馈') }}</h4><div><span v-if="learningAssignmentCourseFilter || learningAssignmentLearnerFilter || learningAssignmentIssueFilter">{{ learningAssignmentIssueFilter ? `正在处理：${learningAssignmentIssueLabel}` : '当前已筛选' }}</span><button v-if="learningAssignmentCourseFilter || learningAssignmentLearnerFilter || learningAssignmentIssueFilter" class="text-button" type="button" @click="clearLearningAssignmentFilter">清除筛选</button><span v-else>{{ educationAssignmentsForView.length }} 个作业</span></div></div>
              <p class="learning-task-help">{{ isAdminWorkspace ? '管理员只读查看作业状态与证据覆盖；确认、返工和反馈由课程教师执行。' : (educationWorkspaceMode === 'teacher' ? '围绕课程约束布置作业，并根据提交物、测评证据和反馈决定确认、返工或重试。' : '接受课程作业后，Agent 会把课程边界、目标知识点和当前掌握度汇总成下一步行动。') }}</p>
              <div v-if="!isAdminWorkspace" class="subsection-title learning-task-heading"><div><h4>作业通知</h4><span>{{ learningAssignmentNotificationsForView.length }} 条</span></div><div class="learning-notification-heading-actions"><span>{{ learningAssignmentNotificationUnreadCountForView }} 条未读</span><button v-if="learningAssignmentNotificationUnreadCountForView" class="text-button" type="button" @click="markAllLearningAssignmentNotificationsRead">全部已读</button></div></div>
              <div v-if="!isAdminWorkspace && learningAssignmentNotificationsForView.length" class="learning-notification-list" aria-label="课程作业通知">
                <article v-for="notification in learningAssignmentNotificationsForView.slice(0, 5)" :key="notification.id" class="learning-notification-row" :class="{ unread: notification.unread }">
                  <div class="learning-notification-main"><div class="learning-notification-meta"><strong>{{ notification.title }}</strong><small>{{ formatDate(notification.createdAt) }}</small></div><p>{{ notification.body }}</p></div>
                  <div class="learning-notification-actions"><button class="secondary-button" type="button" @click="openLearningAssignmentNotification(notification)">{{ learningAssignmentNotificationActionLabel(notification) }}</button><button v-if="notification.unread" class="text-button" type="button" @click="markLearningAssignmentNotificationRead(notification)">标记已读</button></div>
                </article>
              </div>
              <details v-if="canManageEducationOperations" class="education-teacher-entry education-assignment-entry" :open="educationWorkspaceMode === 'teacher'">
                <summary><span><strong>教师布置入口</strong><small>把课程约束和目标知识点下发给指定学习者</small></span><em>{{ educationWorkspaceMode === 'teacher' ? '管理模式' : '需要教师 / 组织权限' }}</em></summary>
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
              </details>
              <div v-if="visibleLearningAssignments.length" id="learning-assignment-list" class="learning-assignment-list">
                <article v-for="assignment in visibleLearningAssignments" :id="`learning-assignment-${assignment.id}`" :key="assignment.id" class="learning-assignment-row">
                  <div class="learning-assignment-main">
                    <div class="learning-assignment-meta"><strong>{{ assignment.title }}</strong><span>{{ learningAssignmentStatusLabel(assignment.status) }}</span></div>
                    <small>{{ assignment.teacherUserId }} → {{ assignment.learnerUserId }} · {{ assignment.subject }} · {{ assignment.gradeLevel }} · {{ assignment.curriculumVersion }}</small>
                    <small v-if="assignment.reviewStatus !== 'NOT_REQUIRED'" class="learning-assignment-progress">业务结果：{{ learningAssignmentReviewStatusLabel(assignment.reviewStatus) }}<span v-if="assignment.teacherReviewedAt"> · {{ formatDate(assignment.teacherReviewedAt) }}</span></small>
                    <p>{{ assignment.instructions }}</p>
                    <small v-if="learningAssignmentActionHint(assignment)" class="learning-assignment-action-hint"><ArrowRight :size="12" />{{ learningAssignmentActionHint(assignment) }}</small>
                    <div v-if="assignment.teacherReviewNote" class="learning-assignment-review-note" :class="{ revision: assignment.reviewStatus === 'REVISION_REQUIRED' }"><CircleAlert :size="13" /><div><strong>{{ learningAssignmentReviewNoteLabel(assignment) }}</strong><span>{{ assignment.teacherReviewNote }}</span></div></div>
                    <small v-if="!learningAssignmentDetailsLoaded(assignment.id)" class="learning-assignment-progress">正在补齐提交物、测评和反馈证据…</small>
                    <small v-if="learningAssignmentProgressMap[assignment.id]" class="learning-assignment-progress">掌握度 {{ formatRate(learningAssignmentProgressMap[assignment.id].currentMastery) }} / {{ formatRate(learningAssignmentProgressMap[assignment.id].targetMastery) }} · 提升 {{ learningAssignmentProgressMap[assignment.id].masteryGain >= 0 ? '+' : '' }}{{ formatRate(learningAssignmentProgressMap[assignment.id].masteryGain) }} · 目标进度 {{ formatRate(learningAssignmentProgressMap[assignment.id].masteryProgress) }} · 测评 {{ learningAssignmentProgressMap[assignment.id].assessmentTotal }} 次 · 任务 {{ learningAssignmentProgressMap[assignment.id].taskCompleted }} / {{ learningAssignmentProgressMap[assignment.id].taskTotal }}</small>
                    <small v-if="learningAssignmentProgressMap[assignment.id]" class="learning-assignment-progress">Run 证据覆盖 {{ formatRate(learningAssignmentProgressMap[assignment.id].runEvidenceCoverageRate) }}（{{ learningAssignmentProgressMap[assignment.id].runWithAssessmentEvidence }} / {{ learningAssignmentProgressMap[assignment.id].runTotal }}） · 教师反馈确认 {{ formatRate(learningAssignmentProgressMap[assignment.id].feedbackAcknowledgementRate) }}（{{ learningAssignmentProgressMap[assignment.id].feedbackAcknowledged }} / {{ learningAssignmentProgressMap[assignment.id].feedbackTotal }}）</small>
                    <details v-if="learningAssignmentEvidenceMap[assignment.id]?.length" class="learning-assessment-history"><summary>查看测评证据（{{ learningAssignmentEvidenceMap[assignment.id].length }}）</summary><div v-for="attempt in learningAssignmentEvidenceMap[assignment.id].slice().reverse().slice(0, 5)" :key="attempt.id"><span :class="attempt.correct ? 'assessment-correct' : 'assessment-wrong'">{{ attempt.correct ? '正确' : '错误' }}</span><span><b v-if="attempt.learnerEvidenceQuote">学习者原话：{{ attempt.learnerEvidenceQuote }} · </b>{{ attempt.evidenceText || '未填写证据文本' }}<small v-if="attempt.feedback"> · {{ attempt.feedback }}</small></span><small>{{ attempt.evidenceSource === 'MANUAL_REVIEW' ? '人工复核' : (attempt.assessmentType === 'REVIEW' ? '保持度复习' : 'Agent观察') }} · {{ formatDate(attempt.createdAt) }}</small><small v-if="assessmentRetrievalEvidenceLabel(attempt)">知识源：{{ assessmentRetrievalEvidenceLabel(attempt) }}</small></div></details>
                    <details v-if="learningAssignmentSubmissionMap[assignment.id]?.length" class="learning-assessment-history"><summary>学习者提交物（{{ learningAssignmentSubmissionMap[assignment.id].length }}）</summary><div v-for="submission in learningAssignmentSubmissionMap[assignment.id].slice(0, 5)" :key="submission.id"><span>原始作答</span><span>{{ submission.content }}</span><small>Run {{ submission.runId.slice(0, 8) }} · {{ formatDate(submission.submittedAt) }}</small></div></details>
                    <details v-if="learningAssignmentFeedbackMap[assignment.id]?.length" class="learning-assessment-history"><summary>教师反馈（{{ learningAssignmentFeedbackMap[assignment.id].length }}）</summary><div v-for="feedback in learningAssignmentFeedbackMap[assignment.id].slice(0, 5)" :key="feedback.id"><span>{{ learningAssignmentFeedbackActionLabel(feedback.action) }}</span><span>{{ feedback.message }}<small v-if="feedback.suggestedDueAt"> · 截止 {{ formatDate(feedback.suggestedDueAt) }}</small></span><small>{{ learningAssignmentFeedbackStatusLabel(feedback) }} · {{ formatDate(feedback.createdAt) }}<button v-if="assignment.learnerUserId === form.userId && ['OPEN', 'ACKNOWLEDGED'].includes(feedback.status)" class="text-button" type="button" :disabled="learningAssignmentFeedbackAcknowledgingId === feedback.id || learningAssignmentAcceptingId === assignment.id" @click="acknowledgeLearningAssignmentFeedback(assignment, feedback)">{{ learningAssignmentFeedbackContinueLabel(feedback) }}</button></small></div></details>
                    <details v-if="learningAssignmentEvaluationMap[assignment.id]?.length" class="learning-assessment-history"><summary>教师量规评价（{{ learningAssignmentEvaluationMap[assignment.id].length }}）</summary><div v-for="evaluation in learningAssignmentEvaluationMap[assignment.id].slice(0, 5)" :key="evaluation.id"><span>{{ evaluation.decision === 'VERIFY' ? '确认' : '退回' }} · {{ evaluation.rubricVersion }}</span><span>内容 {{ evaluation.contentCorrectnessScore }} / 5 · 证据 {{ evaluation.evidenceQualityScore }} / 5 · 迁移 {{ evaluation.transferReadinessScore }} / 5</span><small>{{ evaluation.evaluatorUserId }} · {{ formatDate(evaluation.createdAt) }}<span v-if="evaluation.note"> · {{ evaluation.note }}</span></small></div></details>
                  </div>
                  <div class="learning-assignment-actions">
                    <button v-if="assignment.learnerUserId === form.userId && (assignment.status === 'ASSIGNED' || assignment.status === 'AWAITING_EVIDENCE' || assignment.status === 'RETRY_REQUIRED' || (['ACCEPTED', 'OVERDUE'].includes(assignment.status) && learningAssignmentHasOpenIntervention(assignment))) && !learningAssignmentHasExecutableFeedback(assignment)" class="secondary-button" type="button" :title="learningAssignmentSourceBlockReason(assignment)" :disabled="learningAssignmentAcceptingId === assignment.id || Boolean(learningAssignmentSourceBlockReason(assignment))" @click="startLearningAssignment(assignment)">{{ learningAssignmentSourceBlockReason(assignment) ? '需课程资料' : learningAssignmentStartLabel(assignment, learningAssignmentAcceptingId === assignment.id) }}</button>
                    <button v-if="assignment.learnerUserId === form.userId && learningAssignmentSourceBlockReason(assignment)" class="text-button" type="button" @click="openEducationAgentSetup">{{ educationSetupActionLabel }}</button>
                    <button v-if="learningAssignmentSubmissionOpen(assignment) && !learningAssignmentHasExecutableFeedback(assignment)" class="secondary-button" type="button" @click="startLearningAssignmentSubmission(assignment)">提交作业内容</button>
                    <button v-if="educationWorkspaceMode === 'teacher' && assignment.teacherUserId === form.userId && assignment.status === 'COMPLETED' && assignment.reviewStatus === 'PENDING'" class="secondary-button" type="button" :disabled="learningAssignmentReviewSavingId === assignment.id" @click="verifyLearningAssignment(assignment)">{{ learningAssignmentReviewSavingId === assignment.id ? '确认中…' : '确认作业结果' }}</button>
                    <button v-if="educationWorkspaceMode === 'teacher' && assignment.teacherUserId === form.userId && assignment.status === 'COMPLETED' && assignment.reviewStatus === 'PENDING'" class="text-button" type="button" :disabled="learningAssignmentReviewSavingId === assignment.id" @click="returnLearningAssignmentForRevision(assignment)">{{ learningAssignmentReviewSavingId === assignment.id ? '处理中…' : '退回返工' }}</button>
                    <button v-if="educationWorkspaceMode === 'teacher' && assignment.teacherUserId === form.userId && assignment.status !== 'CANCELLED'" class="text-button" type="button" @click="startLearningAssignmentFeedback(assignment)">写教师反馈</button>
                    <button v-if="educationWorkspaceMode === 'teacher' && assignment.teacherUserId === form.userId && ['ASSIGNED', 'ACCEPTED', 'RETRY_REQUIRED', 'OVERDUE'].includes(assignment.status)" class="text-button" type="button" @click="cancelLearningAssignment(assignment)">取消作业</button>
                  </div>
                </article>
              </div>
              <div v-else class="context-preview-empty">{{ educationAssignmentsForView.length ? '当前筛选范围没有作业。' : (isAdminWorkspace ? '暂无课程作业；教师发布作业后，组织概览会在这里显示状态。' : (educationWorkspaceMode === 'teacher' ? '还没有课程作业；可在教师布置入口创建。' : '暂无课程作业；接受课程作业后，Agent 会在这里展示行动与证据。')) }}</div>
              <form v-if="learningAssignmentFeedbackForm.assignmentId" class="learning-assignment-feedback-form" @submit.prevent="submitLearningAssignmentFeedback">
                <div class="subsection-title"><h4>教师反馈</h4><button class="text-button" type="button" @click="closeLearningAssignmentFeedback">关闭</button></div>
                <label class="field"><span>反馈动作</span><select v-model="learningAssignmentFeedbackForm.action"><option value="COMMENT">教师反馈</option><option value="REQUEST_EVIDENCE">要求补充证据</option><option value="RECOMMEND_RETRY">建议重新学习</option><option value="RESCHEDULE">重新安排截止时间</option></select></label>
                <label v-if="learningAssignmentFeedbackForm.action === 'RESCHEDULE'" class="field"><span>新的截止时间</span><input v-model="learningAssignmentFeedbackForm.suggestedDueAt" type="datetime-local" required /></label>
                <label class="field learning-assignment-wide"><span>反馈内容</span><textarea v-model="learningAssignmentFeedbackForm.message" required maxlength="4000" rows="2" placeholder="写明证据判断和下一步行动"></textarea></label>
                <button class="secondary-button" type="submit" :disabled="learningAssignmentFeedbackSavingId === learningAssignmentFeedbackForm.assignmentId">{{ learningAssignmentFeedbackSavingId ? '发送中…' : '发送反馈' }}</button>
              </form>
              <form v-if="learningAssignmentSubmissionForm.assignmentId" class="learning-assignment-feedback-form learning-assignment-submission-form" @submit.prevent="submitLearningAssignmentSubmission">
                <div class="subsection-title"><h4>提交作业内容</h4><button class="text-button" type="button" @click="closeLearningAssignmentSubmission">关闭</button></div>
                <p class="learning-task-help">提交物会绑定最近一次成功的教育 Run，教师确认时可以同时查看原始作答和测评证据。</p>
                <label class="field learning-assignment-wide"><span>作答内容</span><textarea v-model="learningAssignmentSubmissionForm.content" required maxlength="8000" rows="4" placeholder="填写你的解题过程、答案或实践结果"></textarea></label>
                <button class="secondary-button" type="submit" :disabled="learningAssignmentSubmissionSavingId === learningAssignmentSubmissionForm.assignmentId">{{ learningAssignmentSubmissionSavingId ? '提交中…' : '保存提交物' }}</button>
              </form>
            </section>
            <div v-if="isLearnerOnlyRole" class="learning-goal-workbench">
              <div class="learning-task-workbench">
                <div class="subsection-title learning-task-heading"><div><h4>待处理学习任务</h4><span>{{ learningTasks.filter((task) => ['OPEN', 'IN_PROGRESS', 'AWAITING_EVIDENCE', 'DEFERRED', 'FAILED'].includes(task.status)).length }} 条</span></div><div class="learning-notification-heading-actions"><span>{{ learningNotificationUnreadCount }} 条未读</span><button v-if="learningNotificationUnreadCount" class="text-button" type="button" @click="markAllLearningNotificationsRead">全部已读</button></div></div>
                <p class="learning-task-help">复习计划到期后会自动生成任务；Run 失败会进入可重试，Run 成功但没有测评证据会进入待补证据。</p>
                <div v-if="learningNotifications.length" class="learning-notification-list" aria-label="学习任务通知">
                  <article v-for="notification in learningNotifications.slice(0, 5)" :key="notification.id" class="learning-notification-row" :class="{ unread: notification.unread }">
                    <div class="learning-notification-main"><div class="learning-notification-meta"><strong>{{ notification.title }}</strong><small>{{ formatDate(notification.createdAt) }}</small></div><p>{{ learningNotificationBody(notification) }}</p></div>
                    <div class="learning-notification-actions"><button class="secondary-button" type="button" @click="openLearningNotification(notification)">{{ notification.notificationType === 'EVIDENCE_REQUIRED' ? '补充证据' : (notification.notificationType === 'FAILED' ? '重试任务' : (notification.taskStatus === 'DEFERRED' ? '查看复习安排' : '打开任务')) }}</button><button v-if="notification.unread" class="text-button" type="button" @click="markLearningNotificationRead(notification)">标记已读</button></div>
                  </article>
                </div>
                <div v-if="learningTasks.length" class="learning-task-list">
                  <article v-for="task in learningTasks.filter((item) => ['OPEN', 'IN_PROGRESS', 'AWAITING_EVIDENCE', 'DEFERRED', 'FAILED'].includes(item.status)).slice(0, 8)" :id="`learning-task-${task.id}`" :key="task.id" class="learning-task-row">
                    <div class="learning-task-main"><strong>{{ task.title }}</strong><small>{{ task.status === 'IN_PROGRESS' ? '进行中' : (task.status === 'AWAITING_EVIDENCE' ? '待补测评证据' : (task.status === 'FAILED' ? `执行失败${task.failureReason ? `：${task.failureReason}` : ''}` : (task.status === 'DEFERRED' ? `延期至 ${formatDate(task.scheduledAt)}` : `到期 ${formatDate(task.scheduledAt)}`))) }} · 第 {{ task.reviewSequence + 1 }} 次复习</small><p>{{ task.prompt }}</p></div>
                    <div class="learning-task-actions">
                    <button class="secondary-button" type="button" :title="learningTaskIsScheduled(task) ? `任务将在 ${formatDate(task.scheduledAt)} 开放` : learningTaskSourceBlockReason(task)" :disabled="learningTaskStartingId === task.id || learningTaskDeferringId === task.id || Boolean(learningTaskSourceBlockReason(task))" @click="learningTaskIsScheduled(task) ? focusLearningTask(task) : startLearningTask(task)">{{ learningTaskSourceBlockReason(task) ? '需课程资料' : learningTaskActionLabel(task, learningTaskStartingId === task.id) }}</button>
                    <button v-if="learningTaskSourceBlockReason(task)" class="text-button" type="button" @click="openEducationAgentSetup">{{ educationSetupActionLabel }}</button>
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
                <div class="learning-recommendation-heading"><div><span>下一步学习动作</span><strong>{{ learningRecommendation.nextActionTitle }}</strong></div><div><button class="secondary-button" type="button" :title="learningGoalSourceBlockReason(learningRecommendation.learningGoalId)" :disabled="Boolean(learningGoalSourceBlockReason(learningRecommendation.learningGoalId))" @click="useLearningRecommendation">{{ learningGoalSourceBlockReason(learningRecommendation.learningGoalId) ? educationSetupActionLabel : '带着建议开始' }}</button><button v-if="learningGoalSourceBlockReason(learningRecommendation.learningGoalId)" class="text-button" type="button" @click="openEducationAgentSetup">{{ educationSetupActionLabel }}</button></div></div>
                <p>{{ learningRecommendation.rationale }}</p>
                <small>掌握度 {{ Math.round(learningRecommendation.currentMastery * 100) }}% / 目标 {{ Math.round(learningRecommendation.targetMastery * 100) }}% · 测评 {{ learningRecommendation.attemptCount }} 次 · 正确 {{ learningRecommendation.correctAttemptCount }} 次</small>
                <small v-if="learningRecommendation.reviewPlanId">保持度复习 {{ learningRecommendation.reviewCount }} 次 · 成功 {{ learningRecommendation.successfulReviewCount }} 次 · 下次 {{ formatDate(learningRecommendation.nextReviewAt) }}</small>
                <details v-if="learningGoalAssessments.length" class="learning-assessment-history"><summary>查看测评历史（{{ learningGoalAssessments.length }}）</summary><div v-for="attempt in learningGoalAssessments.slice().reverse().slice(0, 5)" :key="attempt.id"><span :class="attempt.correct ? 'assessment-correct' : 'assessment-wrong'">{{ attempt.correct ? '正确' : '错误' }}</span><span>{{ Math.round(attempt.masteryBefore * 100) }}% → {{ Math.round(attempt.masteryAfter * 100) }}%</span><small>{{ attempt.assessmentType === 'REVIEW' ? '保持度复习' : (attempt.evidenceSource === 'MANUAL_REVIEW' ? '人工复核' : 'Agent观察') }} · {{ formatDate(attempt.createdAt) }}</small></div></details>
              </div>
            </div>
            <details v-if="canManageEducationOperations" class="education-source-editor education-teacher-entry" :open="educationWorkspaceMode === 'teacher'">
              <summary><span><strong>课程资料维护入口</strong><small>为可见知识文档补充学科、版本、章节和知识点边界</small></span><em>{{ educationSources.length }} 个课程来源</em></summary>
              <form v-if="manageableEducationDocuments.length" class="education-source-form" @submit.prevent="saveEducationSource">
                <label class="field field-wide"><span>知识文档</span><select v-model="educationSourceForm.documentId" required><option value="">选择可见的知识文档</option><option v-for="document in manageableEducationDocuments" :key="document.id" :value="document.id">{{ document.title }}{{ document.ownerUserId !== form.userId ? ' · 组织共享' : '' }}</option></select></label>
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
              <div v-else class="context-preview-empty">当前没有可配置的知识文档；可以先上传课程资料，或请管理员授权课程资料。</div>
              <div v-if="educationSources.length" class="education-source-list">
                  <div v-for="source in educationSources" :key="source.id" class="education-source-row">
                  <div><strong>{{ source.documentTitle || documents.find((document) => document.id === source.documentId)?.title || source.documentId }}</strong><small>{{ source.subject }} · {{ source.gradeLevel }} · {{ source.curriculumVersion }} · 难度 {{ source.difficultyLevel }}<template v-if="isAdminWorkspace && educationSourceOwnerLabel(source)"> · {{ educationSourceOwnerLabel(source) }}</template></small></div>
                  <button v-if="canEditEducationSource(source)" class="text-button" type="button" @click="educationSourceForm.documentId = source.documentId; educationSourceForm.subject = source.subject; educationSourceForm.gradeLevel = source.gradeLevel; educationSourceForm.curriculumVersion = source.curriculumVersion; educationSourceForm.chapter = source.chapter || ''; educationSourceForm.conceptTags = source.conceptTags || ''; educationSourceForm.prerequisiteConcepts = source.prerequisiteConcepts || ''; educationSourceForm.learningObjectives = source.learningObjectives || ''; educationSourceForm.difficultyLevel = source.difficultyLevel">编辑</button>
                  <small v-else class="document-owner-hint">仅资料所有者可删除正文</small>
                </div>
              </div>
            </details>
          </section>
          <form v-if="isAdminRole" class="governance-card governance-fixed-card memory-card" @submit.prevent="createMemory">
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
          <form v-if="isAdminRole" class="governance-card policy-card" @submit.prevent="saveTenantPolicy">
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
          <form v-if="isAdminRole" class="governance-card api-key-card" @submit.prevent="createManagedApiKey">
            <div class="subsection-title">
              <div><h3>登录凭证管理</h3><span>API Key 生命周期</span></div>
              <button class="refresh-button" type="button" :disabled="loading" aria-label="刷新 API Key" @click="loadApiKeys">⟳</button>
            </div>
            <p class="api-key-card-intro">为教师或学生创建一把登录凭证。角色模板会自动填充最小权限；正式环境建议把生成的密钥交给学校统一认证或安全的密码管理器保存。</p>
            <p v-if="apiKeyError" class="policy-error">{{ apiKeyError }}</p>
            <div class="api-key-create-grid">
              <label class="field"><span>组织 ID</span><input v-model="apiKeyForm.tenantId" required maxlength="128" /></label>
              <label class="field"><span>用户 ID（登录名）</span><input v-model="apiKeyForm.userId" required maxlength="128" placeholder="例如：teacher-zhang" /></label>
              <label class="field api-key-role-field"><span>用户角色</span>
                <select :value="selectedApiKeyRoleTemplate" @change="applyApiKeyRoleTemplate($event.target.value)">
                  <option v-for="template in apiKeyRoleTemplates" :key="template.value" :value="template.value">{{ template.label }}</option>
                  <option value="CUSTOM">自定义权限</option>
                </select>
                <small class="form-hint">{{ apiKeyRoleTemplates.find((template) => template.value === selectedApiKeyRoleTemplate)?.description || '已手动调整权限，请确认范围。' }}</small>
              </label>
              <label class="field api-key-expiry-field"><span>过期时间（可选）</span><input v-model="apiKeyForm.expiresAt" type="datetime-local" /></label>
              <div class="field api-key-permissions-field">
                <span>权限（模板可自动填充，也可手动调整）</span>
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
              <button class="secondary-button" type="submit" :disabled="loading">{{ loading ? '创建中…' : `创建${selectedApiKeyRoleTemplate === 'CUSTOM' ? '' : apiKeyRoleTemplates.find((template) => template.value === selectedApiKeyRoleTemplate)?.label || ''}登录凭证` }}</button>
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
                <div class="api-key-row-main"><strong>{{ key.keyPrefix }}…</strong><small>{{ key.userId }} · {{ apiKeyRoleFromPermissions(key.permissions) }} · 创建于 {{ formatDate(key.createdAt) }}</small></div>
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
</template>
