<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from './api'

const runs = ref([])
const tools = ref([])
const selectedRun = ref(null)
const auditEvents = ref([])
const loading = ref(false)
const detailLoading = ref(false)
const errorMessage = ref('')
const noticeMessage = ref('')
const showCreateForm = ref(true)

const form = reactive({
  tenantId: 'tenant-demo',
  userId: 'operator',
  title: '订单状态分析',
  input: '请分析这条任务并返回可追溯结果',
  toolName: 'demo.echo',
  modelName: '',
  promptVersion: 'prompt-v1',
  policyVersion: 'policy-v1',
  budget: 1,
})

const stats = computed(() => ({
  total: runs.value.length,
  queued: runs.value.filter((run) => run.status === 'QUEUED').length,
  running: runs.value.filter((run) => run.status === 'RUNNING').length,
  succeeded: runs.value.filter((run) => run.status === 'SUCCEEDED').length,
  failed: runs.value.filter((run) => run.status === 'FAILED').length,
}))

const selectedStatus = computed(() => selectedRun.value?.run?.status || 'NONE')
const canStart = computed(() => selectedStatus.value === 'QUEUED')
const canCancel = computed(() => ['QUEUED', 'RUNNING'].includes(selectedStatus.value))

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

function stepLabel(type) {
  return { MODEL: '模型', TOOL: '工具', APPROVAL: '审批' }[type] || type
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

async function loadDashboard() {
  clearMessages()
  try {
    const [runData, toolData] = await Promise.all([api.listRuns(), api.listTools()])
    runs.value = runData
    tools.value = toolData
    if (selectedRun.value) {
      await selectRun(selectedRun.value.run.id, false)
    } else if (runs.value.length) {
      await selectRun(runs.value[0].id, false)
    }
  } catch (error) {
    errorMessage.value = error.message
  }
}

async function selectRun(runId, announce = true) {
  detailLoading.value = true
  if (announce) clearMessages()
  try {
    const [detail, events] = await Promise.all([api.getRun(runId), api.listAuditEvents(runId)])
    selectedRun.value = detail
    auditEvents.value = events
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    detailLoading.value = false
  }
}

async function createAndStartRun() {
  clearMessages()
  loading.value = true
  try {
    const created = await api.createRun({
      ...form,
      budget: Number(form.budget),
      modelName: form.modelName || null,
    })
    await api.startRun(created.id)
    noticeMessage.value = 'Run 已创建并完成执行'
    showCreateForm.value = false
    await loadDashboard()
    await selectRun(created.id, false)
  } catch (error) {
    errorMessage.value = error.message
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
    errorMessage.value = error.message
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
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
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
        <div class="system-state"><span class="pulse"></span><span>演示网关在线</span></div>
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
          <span class="date-chip">本地演示环境</span>
          <button class="primary-button" type="button" @click="showCreateForm = !showCreateForm">
            <span>＋</span> 新建 Run
          </button>
        </div>
      </header>

      <div v-if="errorMessage" class="message error-message">{{ errorMessage }}</div>
      <div v-if="noticeMessage" class="message notice-message">{{ noticeMessage }}</div>

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
            <select v-model="form.toolName">
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
          <div class="form-actions field-wide">
            <span class="form-hint">创建后会依次执行模型步骤和工具步骤，并记录完整审计链。</span>
            <button class="primary-button" type="submit" :disabled="loading">{{ loading ? '执行中…' : '创建并执行' }}</button>
          </div>
        </form>
      </section>

      <section class="workspace-grid">
        <div class="runs-panel panel">
          <div class="panel-heading">
            <div><p class="eyebrow">RECENT RUNS</p><h2>最近执行</h2></div>
            <button class="refresh-button" type="button" @click="loadDashboard" aria-label="刷新列表">⟳</button>
          </div>
          <div v-if="!runs.length" class="empty-state">
            <div class="empty-orb">◈</div>
            <strong>还没有执行记录</strong>
            <span>创建第一个 Run，开始观察执行链。</span>
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
              <span class="run-row-meta"><em :class="statusClass(run.status)">{{ statusLabel(run.status) }}</em><small>{{ run.stepCount }} steps</small></span>
            </button>
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
                <button v-if="canCancel" class="danger-button" type="button" :disabled="loading" @click="cancelSelectedRun">取消</button>
              </div>
            </div>

            <div class="run-meta-grid">
              <div><span>租户 / 用户</span><strong>{{ selectedRun.run.tenantId }} / {{ selectedRun.run.userId }}</strong></div>
              <div><span>模型</span><strong>{{ selectedRun.run.modelName }}</strong></div>
              <div><span>Prompt / 策略</span><strong>{{ selectedRun.run.promptVersion }} · {{ selectedRun.run.policyVersion }}</strong></div>
              <div><span>创建时间</span><strong>{{ formatDate(selectedRun.run.createdAt) }}</strong></div>
            </div>

            <div class="input-preview"><span>任务输入</span><p>{{ selectedRun.run.input }}</p></div>

            <div class="subsection">
              <div class="subsection-title"><h3>执行步骤</h3><span>{{ selectedRun.steps.length }} steps</span></div>
              <div class="step-timeline">
                <div v-for="step in selectedRun.steps" :key="step.id" class="step-item">
                  <div class="step-rail"><span class="step-marker" :class="statusClass(step.status)">{{ step.sequence }}</span><span class="rail-line"></span></div>
                  <div class="step-body">
                    <div class="step-title-row"><div><span class="step-type">{{ stepLabel(step.type) }}</span><strong>{{ step.name }}</strong></div><span class="step-status" :class="statusClass(step.status)">{{ statusLabel(step.status) }}</span></div>
                    <p v-if="step.output" class="step-output">{{ step.output }}</p>
                    <p v-if="step.error" class="step-error">{{ step.error }}</p>
                    <small>尝试 {{ step.attempt }} 次 · {{ formatDate(step.finishedAt || step.startedAt) }}<span v-if="step.inputTokens"> · {{ step.inputTokens + step.outputTokens }} tokens</span></small>
                  </div>
                </div>
              </div>
            </div>

            <div class="subsection audit-subsection">
              <div class="subsection-title"><h3>审计事件</h3><span>{{ auditEvents.length }} events</span></div>
              <div class="audit-list">
                <div v-for="event in auditEvents" :key="event.id" class="audit-row">
                  <span class="audit-time">{{ formatDate(event.createdAt) }}</span><strong>{{ event.eventType }}</strong><span>{{ event.message }}</span>
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
            <small>{{ tool.readOnly ? '只读工具' : '有副作用' }} · Schema 已注册</small>
          </div>
        </div>
      </section>

      <footer class="footer">Ming Harness · 每次执行都可恢复、可解释、可审计、可限制</footer>
    </main>
  </div>
</template>
