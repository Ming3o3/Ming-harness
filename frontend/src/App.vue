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
  permissions: 'run.read, run.create, run.execute, run.approve, run.cancel, audit.read, context.read, context.write, evaluation.read, evaluation.run, tool.read, ops.read, tenant.policy.read, tenant.policy.write, auth.key.read, auth.key.manage',
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
const runPageLabel = computed(() => {
  if (!runPage.totalElements) return '0 条记录'
  return `第 ${runPage.page + 1} / ${runPage.totalPages} 页 · 共 ${runPage.totalElements} 条`
})
const canPreviousRunPage = computed(() => runPage.page > 0)
const canNextRunPage = computed(() => runPage.hasNext)

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
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    if (showLoading) detailLoading.value = false
  }
}

async function pollSelectedRun() {
  if (!selectedRun.value || isTerminal(selectedStatus.value)) return
  if (runsLoading.value) return
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
  } catch (error) {
    errorMessage.value = errorText(error)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadDashboard(), loadHealth(), loadTenantPolicy(), loadApiKeys()])
  runPollTimer = window.setInterval(pollSelectedRun, 1500)
  healthPollTimer = window.setInterval(loadHealth, 10000)
})

onBeforeUnmount(() => {
  window.clearInterval(runPollTimer)
  window.clearInterval(healthPollTimer)
})
</script>

<template>
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
        <a class="nav-item active" href="#runtime"><span class="nav-icon">◈</span>运行中心</a>
        <a class="nav-item" href="#tools"><span class="nav-icon">⌘</span>工具注册</a>
        <a class="nav-item" href="#audit"><span class="nav-icon">↯</span>审计追踪</a>
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
