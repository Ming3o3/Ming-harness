// Electron 桌面版从 preload 获取本机 Runtime 地址；浏览器仍沿用 Vite 的 /api 代理。
const desktopBridge = typeof window !== 'undefined' ? window.harnessDesktop : null
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || desktopBridge?.apiBaseUrl || '/api'
const configuredApiKey = import.meta.env.VITE_HARNESS_API_KEY || ''
// 本地聊天工作台默认开放工作区读写权限；写入和命令执行仍由后端策略要求人工审批。
const defaultChatPermissions = import.meta.env.VITE_HARNESS_CHAT_PERMISSIONS
  || 'workspace.read,workspace.write,workspace.exec,workspace.manage,education.read,education.write,education.assign'

/** API Key/OIDC 与本地请求头共用同一身份组装逻辑，SSE fetch 也能安全携带认证信息。 */
function identityHeaders(requestHeaders = {}) {
  return {
    ...(configuredApiKey
      ? { Authorization: `Bearer ${configuredApiKey}` }
      : {
          'X-Tenant-Id': localStorage.getItem('harnessTenantId') || 'tenant-demo',
          'X-User-Id': localStorage.getItem('harnessUserId') || 'operator',
        }),
    ...(requestHeaders || {}),
  }
}

/** 桌面主进程只接收当前身份所需的最小字段，桥接令牌不会进入 Vue 运行时。 */
function desktopIdentityPayload(payload = {}) {
  return {
    displayName: payload.displayName || '',
    apiKey: configuredApiKey,
    tenantId: localStorage.getItem('harnessTenantId') || 'tenant-demo',
    userId: localStorage.getItem('harnessUserId') || 'operator',
    permissions: localStorage.getItem('harnessChatPermissions') || defaultChatPermissions,
  }
}

async function request(path, options = {}) {
  const { headers: requestHeaders, ...requestOptions } = options
  // multipart 的 boundary 必须由浏览器生成，不能手动设置 JSON Content-Type。
  const isFormData = typeof FormData !== 'undefined' && requestOptions.body instanceof FormData
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...requestOptions,
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...identityHeaders(requestHeaders),
    },
  })

  if (!response.ok) {
    const payload = await response.json().catch(() => ({}))
    const error = new Error(payload.message || `请求失败（${response.status}）`)
    error.code = payload.code
    error.traceId = payload.traceId || response.headers.get('X-Trace-Id')
    throw error
  }

  if (response.status === 204) {
    return null
  }
  return response.json()
}

/**
 * 使用 fetch 解析 SSE，避免原生 EventSource 无法携带 Authorization/X-Api-Key 请求头。
 * 调用方负责 AbortController 和断线重连；服务端只推送脱敏后的既有 Run 详情结构。
 */
async function streamRunEvents(runId, { signal, onEvent } = {}) {
  const response = await fetch(`${apiBaseUrl}/runs/${encodeURIComponent(runId)}/events`, {
    method: 'GET',
    signal,
    headers: identityHeaders({ Accept: 'text/event-stream' }),
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}))
    const error = new Error(payload.message || `实时执行连接失败（${response.status}）`)
    error.code = payload.code
    error.traceId = payload.traceId || response.headers.get('X-Trace-Id')
    throw error
  }
  if (!response.body) throw new Error('当前浏览器不支持实时执行流')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const records = buffer.split(/\r?\n\r?\n/)
      buffer = records.pop() || ''
      records.forEach((record) => {
        const lines = record.split(/\r?\n/)
        const event = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
        const data = lines.filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5).trimStart()).join('\n')
        if (!data) return
        try {
          onEvent?.({ event, data: JSON.parse(data) })
        } catch {
          // 单条 SSE 损坏时忽略，后续快照或 HTTP 轮询会恢复客户端状态。
        }
      })
    }
  } finally {
    reader.releaseLock()
  }
}

export const api = {
  // 控制台使用受 ops.read 保护的摘要接口，避免直接暴露 Actuator 组件详情。
  health: () => request('/health'),
  // 模型密钥只在保存时提交给后端；读取接口仅返回是否配置和掩码。
  getModelConfig: () => request('/model-config'),
  updateModelConfig: (payload) => request('/model-config', {
    method: 'PUT',
    body: JSON.stringify(payload),
  }),
  testModelConfig: (payload) => request('/model-config/test', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  resetModelConfig: () => request('/model-config', { method: 'DELETE' }),
  // Embedding 密钥只在保存时提交，读取接口仅返回组织级配置和掩码。
  getEmbeddingConfig: () => request('/context/embedding-config'),
  updateEmbeddingConfig: (payload) => request('/context/embedding-config', {
    method: 'PUT',
    body: JSON.stringify(payload),
  }),
  testEmbeddingConfig: (payload) => request('/context/embedding-config/test', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  resetEmbeddingConfig: () => request('/context/embedding-config', { method: 'DELETE' }),
  // 仅返回工作区名称和能力摘要，绝对路径始终只保留在本地后端进程。
  workspace: () => request('/workspace'),
  // 工作区浏览接口只接受相对路径；后端会按当前组织、用户和 workspaceId 再次解析根目录。
  browseWorkspaceFiles: ({ workspaceId = '', path = '.' } = {}) => {
    const params = new URLSearchParams({ path })
    if (workspaceId) params.set('workspaceId', workspaceId)
    return request(`/workspace/files?${params.toString()}`)
  },
  readWorkspaceFile: ({ workspaceId = '', path } = {}) => {
    const params = new URLSearchParams({ path: path || '' })
    if (workspaceId) params.set('workspaceId', workspaceId)
    return request(`/workspace/files/content?${params.toString()}`)
  },
  // Monaco 编辑器使用拥有 workspace.write 权限的专用接口，避免把脱敏预览误当成可保存正文。
  readWorkspaceEditorFile: ({ workspaceId = '', path } = {}) => {
    const params = new URLSearchParams({ path: path || '' })
    if (workspaceId) params.set('workspaceId', workspaceId)
    return request(`/workspace/files/editor-content?${params.toString()}`, {
      headers: configuredApiKey ? {} : {
        'X-Permissions': localStorage.getItem('harnessChatPermissions') || defaultChatPermissions,
      },
    })
  },
  saveWorkspaceEditorFile: ({ workspaceId = '', path, content, expectedSha256 } = {}) => {
    const params = new URLSearchParams()
    if (workspaceId) params.set('workspaceId', workspaceId)
    return request(`/workspace/files/editor-content?${params.toString()}`, {
      method: 'PUT',
      headers: configuredApiKey ? {} : {
        'X-Permissions': localStorage.getItem('harnessChatPermissions') || defaultChatPermissions,
      },
      body: JSON.stringify({ path, content, expectedSha256 }),
    })
  },
  // Git 审阅同样只通过 workspaceId 定位本机目录，页面不会接触绝对路径或任意 Git 参数。
  workspaceGitStatus: ({ workspaceId = '' } = {}) => {
    const params = new URLSearchParams()
    if (workspaceId) params.set('workspaceId', workspaceId)
    return request(`/workspace/git/status?${params.toString()}`)
  },
  workspaceGitDiff: ({ workspaceId = '', path = '.', staged = false, contextLines = 3 } = {}) => {
    const params = new URLSearchParams({ path, staged: String(staged), contextLines: String(contextLines) })
    if (workspaceId) params.set('workspaceId', workspaceId)
    return request(`/workspace/git/diff?${params.toString()}`)
  },
  listLocalWorkspaces: () => request('/workspaces'),
  /**
   * 目录选择和绝对路径登记都在 Electron 主进程完成；此处只传递当前用户身份。
   * 浏览器环境不存在该桥接能力，会明确报错而不是伪造路径输入框。
   */
  pickDesktopWorkspace: (payload = {}) => {
    if (!desktopBridge?.pickWorkspace) {
      throw new Error('当前为浏览器模式，请使用桌面版选择本地项目')
    }
    return desktopBridge.pickWorkspace(desktopIdentityPayload(payload))
  },
  /** 配置后由 preload 捕获原生目录拖拽；页面回调只接收工作区摘要或错误消息。 */
  configureDesktopWorkspaceDrop: () => {
    desktopBridge?.configureWorkspaceDrop?.(desktopIdentityPayload())
  },
  onDesktopWorkspaceDropped: (listener) => {
    desktopBridge?.onWorkspaceDropped?.(listener)
  },
  clearDesktopWorkspaceDropListener: () => {
    desktopBridge?.clearWorkspaceDropListener?.()
  },
  isDesktop: () => Boolean(desktopBridge?.isDesktop),
  listRuns: () => request('/runs'),
  // 分页接口不改变旧的数组接口，供历史列表按需增量加载。
  listRunsPage: ({ page = 0, size = 20, status } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (status) params.set('status', status)
    return request(`/runs/page?${params.toString()}`)
  },
  dashboardSummary: () => request('/dashboard/summary'),
  getRun: (runId) => request(`/runs/${runId}`),
  streamRunEvents,
  createRun: (payload) => request('/runs', { method: 'POST', body: JSON.stringify(payload) }),
  startRun: (runId) => request(`/runs/${runId}/start`, { method: 'POST' }),
  approveRun: (runId) => request(`/runs/${runId}/approve`, { method: 'POST' }),
  rejectRun: (runId, reason) => request(`/runs/${runId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  }),
  retryRun: (runId) => request(`/runs/${runId}/retry`, { method: 'POST' }),
  cancelRun: (runId) => request(`/runs/${runId}`, { method: 'DELETE' }),
  getRunFeedback: (runId) => request(`/runs/${encodeURIComponent(runId)}/feedback`),
  saveRunFeedback: (runId, payload) => request(`/runs/${encodeURIComponent(runId)}/feedback`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  listConversations: () => request('/conversations'),
  createConversation: (payload = {}) => request('/conversations', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  renameConversation: (conversationId, title) => request(`/conversations/${encodeURIComponent(conversationId)}`, {
    method: 'PATCH',
    body: JSON.stringify({ title }),
  }),
  getConversation: (conversationId) => request(`/conversations/${encodeURIComponent(conversationId)}`),
  // entries 的 path 是浏览器可提供的相对路径，后端会再次校验，绝不接受本机绝对路径。
  uploadConversationAttachments: (conversationId, entries) => {
    const body = new FormData()
    Array.from(entries || []).forEach(({ file, path }) => {
      body.append('files', file)
      body.append('paths', path)
    })
    return request(`/conversations/${encodeURIComponent(conversationId)}/attachments`, {
      method: 'POST',
      body,
    })
  },
  deleteConversationAttachment: (conversationId, attachmentId) => request(
    `/conversations/${encodeURIComponent(conversationId)}/attachments/${encodeURIComponent(attachmentId)}`,
    { method: 'DELETE' },
  ),
  sendConversationMessage: (conversationId, payload, idempotencyKey) => request(
    `/conversations/${encodeURIComponent(conversationId)}/messages`, {
      method: 'POST',
      headers: {
        ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
        // api-key/OIDC 模式下后端会忽略该请求头，使用认证身份中的可信权限。
        ...(configuredApiKey ? {} : { 'X-Permissions': localStorage.getItem('harnessChatPermissions') || defaultChatPermissions }),
      },
      body: JSON.stringify(payload),
    },
  ),
  listTools: () => request('/tools'),
  listAuditEvents: (runId) => request(`/runs/${runId}/audit-events`),
  listDocuments: () => request('/context/documents'),
  createDocument: (payload) => request('/context/documents', { method: 'POST', body: JSON.stringify(payload) }),
  uploadDocument: ({ file, title = '', sensitivity = '', allowedUsers = '' } = {}) => {
    const body = new FormData()
    body.append('file', file)
    if (title) body.append('title', title)
    if (sensitivity) body.append('sensitivity', sensitivity)
    if (allowedUsers) body.append('allowedUsers', allowedUsers)
    return request('/context/documents/upload', { method: 'POST', body })
  },
  deleteDocument: (documentId) => request(`/context/documents/${documentId}`, { method: 'DELETE' }),
  previewContext: (query, maxChars = 4000) => {
    const params = new URLSearchParams({ query, maxChars: String(maxChars) })
    return request(`/context/preview?${params.toString()}`)
  },
  contextConfiguration: () => request('/context/configuration'),
  reindexContext: (payload = {}) => request('/context/reindex', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  listMemories: () => request('/context/memories'),
  createMemory: (payload) => request('/context/memories', { method: 'POST', body: JSON.stringify(payload) }),
  deleteMemory: (memoryId) => request(`/context/memories/${encodeURIComponent(memoryId)}`, { method: 'DELETE' }),
  listEducationSources: () => request('/education/sources'),
  saveEducationSource: (payload) => request('/education/sources', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  deleteEducationSource: (documentId) => request(`/education/sources/${encodeURIComponent(documentId)}`, {
    method: 'DELETE',
  }),
  listLearnerProfiles: () => request('/education/profiles'),
  saveLearnerProfile: (payload) => request('/education/profiles', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  activeLearnerProfile: () => request('/education/profiles/active'),
  listLearnerMastery: (profileId) => request(`/education/profiles/${encodeURIComponent(profileId)}/mastery`),
  updateLearnerMastery: (profileId, payload) => request(`/education/profiles/${encodeURIComponent(profileId)}/mastery`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  listLearningGoals: () => request('/education/goals'),
  createLearningGoal: (payload) => request('/education/goals', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  updateLearningGoalStatus: (goalId, status) => request(`/education/goals/${encodeURIComponent(goalId)}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }),
  listGoalAssessments: (goalId) => request(`/education/goals/${encodeURIComponent(goalId)}/assessments`),
  submitGoalAssessment: (goalId, payload) => request(`/education/goals/${encodeURIComponent(goalId)}/assessments`, {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  getGoalRecommendation: (goalId) => request(`/education/goals/${encodeURIComponent(goalId)}/recommendation`),
  getGoalReviewPlan: (goalId) => request(`/education/goals/${encodeURIComponent(goalId)}/review-plan`),
  listLearningTasks: (status) => {
    const query = status ? `?status=${encodeURIComponent(status)}` : ''
    return request(`/education/tasks${query}`)
  },
  getEducationMetrics: () => request('/education/metrics'),
  listEducationCourses: () => request('/education/courses'),
  createEducationCourse: (payload) => request('/education/courses', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  getEducationCourse: (courseId) => request(`/education/courses/${encodeURIComponent(courseId)}`),
  listEducationCourseEnrollments: (courseId) => request(
    `/education/courses/${encodeURIComponent(courseId)}/enrollments`),
  enrollEducationLearner: (courseId, payload) => request(
    `/education/courses/${encodeURIComponent(courseId)}/enrollments`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  removeEducationLearner: (courseId, learnerUserId) => request(
    `/education/courses/${encodeURIComponent(courseId)}/enrollments/${encodeURIComponent(learnerUserId)}/remove`, {
      method: 'POST',
    }),
  archiveEducationCourse: (courseId) => request(
    `/education/courses/${encodeURIComponent(courseId)}/archive`, {
      method: 'POST',
    }),
  completeEducationCourse: (courseId, payload = {}) => request(
    `/education/courses/${encodeURIComponent(courseId)}/complete`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  assignEducationCourse: (courseId, payload, idempotencyKey) => request(
    `/education/courses/${encodeURIComponent(courseId)}/assignments`, {
      method: 'POST',
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
      body: JSON.stringify(payload),
    }),
  getEducationCourseProgress: (courseId, limit = 500) => request(
    `/education/courses/${encodeURIComponent(courseId)}/progress?limit=${encodeURIComponent(limit)}`),
  startLearningTask: (taskId, payload = {}, idempotencyKey) => request(
    `/education/tasks/${encodeURIComponent(taskId)}/start`, {
      method: 'POST',
      headers: {
        ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
        ...(configuredApiKey ? {} : {
          'X-Permissions': localStorage.getItem('harnessChatPermissions') || defaultChatPermissions,
        }),
      },
      body: JSON.stringify(payload),
    },
  ),
  deferLearningTask: (taskId, days = 1) => request(
    `/education/tasks/${encodeURIComponent(taskId)}/defer`, {
      method: 'POST',
      body: JSON.stringify({ days }),
    },
  ),
  listLearningNotifications: (unreadOnly = false, limit = 50) => {
    const params = new URLSearchParams({ unreadOnly: String(Boolean(unreadOnly)), limit: String(limit) })
    return request(`/education/notifications?${params.toString()}`)
  },
  markLearningNotificationRead: (notificationId) => request(
    `/education/notifications/${encodeURIComponent(notificationId)}/read`, {
      method: 'POST',
    }),
  markAllLearningNotificationsRead: () => request('/education/notifications/read-all', {
    method: 'POST',
  }),
  listLearningAssignments: () => request('/education/assignments'),
  createLearningAssignment: (payload) => request('/education/assignments', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  acceptLearningAssignment: (assignmentId) => request(
    `/education/assignments/${encodeURIComponent(assignmentId)}/accept`, {
      method: 'POST',
    }),
  startLearningAssignment: (assignmentId, payload = {}, idempotencyKey) => request(
    `/education/assignments/${encodeURIComponent(assignmentId)}/start`, {
      method: 'POST',
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
      body: JSON.stringify(payload),
    }),
  reviewLearningAssignment: (assignmentId, payload) => request(
    `/education/assignments/${encodeURIComponent(assignmentId)}/review`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  cancelLearningAssignment: (assignmentId) => request(
    `/education/assignments/${encodeURIComponent(assignmentId)}/cancel`, {
      method: 'POST',
    }),
  getLearningAssignmentProgress: (assignmentId) => request(
    `/education/assignments/${encodeURIComponent(assignmentId)}/progress`),
  getLearningAssignmentEvidence: (assignmentId) => request(
    `/education/assignments/${encodeURIComponent(assignmentId)}/evidence`),
  listLearningAssignmentFeedback: (assignmentId) => request(
    `/education/assignments/${encodeURIComponent(assignmentId)}/feedback`),
  createLearningAssignmentFeedback: (assignmentId, payload) => request(
    `/education/assignments/${encodeURIComponent(assignmentId)}/feedback`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  acknowledgeLearningAssignmentFeedback: (assignmentId, feedbackId) => request(
    `/education/assignments/${encodeURIComponent(assignmentId)}/feedback/${encodeURIComponent(feedbackId)}/acknowledge`, {
      method: 'POST',
    }),
  listLearningAssignmentNotifications: (unreadOnly = false, limit = 50) => {
    const params = new URLSearchParams({ unreadOnly: String(Boolean(unreadOnly)), limit: String(limit) })
    return request(`/education/assignment-notifications?${params.toString()}`)
  },
  markLearningAssignmentNotificationRead: (notificationId) => request(
    `/education/assignment-notifications/${encodeURIComponent(notificationId)}/read`, {
      method: 'POST',
    }),
  markAllLearningAssignmentNotificationsRead: () => request('/education/assignment-notifications/read-all', {
    method: 'POST',
  }),
  executeLearningGoalNextAction: (goalId, payload = {}, idempotencyKey) => request(
    `/education/goals/${encodeURIComponent(goalId)}/next-action`, {
      method: 'POST',
      headers: {
        ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
        ...(configuredApiKey ? {} : {
          'X-Permissions': localStorage.getItem('harnessChatPermissions') || defaultChatPermissions,
        }),
      },
      body: JSON.stringify(payload),
    },
  ),
  getTenantPolicy: (tenantId) => request(`/admin/tenants/${encodeURIComponent(tenantId)}/policy`),
  updateTenantPolicy: (tenantId, payload) => request(`/admin/tenants/${encodeURIComponent(tenantId)}/policy`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  }),
  resetTenantPolicy: (tenantId) => request(`/admin/tenants/${encodeURIComponent(tenantId)}/policy`, { method: 'DELETE' }),
  listTenantPolicyAudits: (tenantId) => request(`/admin/tenants/${encodeURIComponent(tenantId)}/policy/audits`),
  // API Key 只在创建响应中返回一次明文，列表和审计接口不会泄露完整密钥。
  listApiKeys: (tenantId) => request(`/admin/api-keys${tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ''}`),
  createApiKey: (payload) => request('/admin/api-keys', { method: 'POST', body: JSON.stringify(payload) }),
  rotateApiKey: (keyId, payload = null) => request(`/admin/api-keys/${encodeURIComponent(keyId)}/rotate`, {
    method: 'POST',
    body: JSON.stringify(payload || {}),
  }),
  revokeApiKey: (keyId) => request(`/admin/api-keys/${encodeURIComponent(keyId)}`, { method: 'DELETE' }),
  listApiKeyAudits: (tenantId) => request(`/admin/api-keys/audits${tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : ''}`),
}
