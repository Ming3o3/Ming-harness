const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
const configuredApiKey = import.meta.env.VITE_HARNESS_API_KEY || ''
// 本地聊天工作台默认开放工作区读写权限；写入和命令执行仍由后端策略要求人工审批。
const defaultChatPermissions = import.meta.env.VITE_HARNESS_CHAT_PERMISSIONS
  || 'workspace.read,workspace.write,workspace.exec'

async function request(path, options = {}) {
  const { headers: requestHeaders, ...requestOptions } = options
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...requestOptions,
    headers: {
      'Content-Type': 'application/json',
      ...(configuredApiKey
        ? { Authorization: `Bearer ${configuredApiKey}` }
        : {
            'X-Tenant-Id': localStorage.getItem('harnessTenantId') || 'tenant-demo',
            'X-User-Id': localStorage.getItem('harnessUserId') || 'operator',
          }),
      ...(requestHeaders || {}),
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

export const api = {
  // 控制台使用受 ops.read 保护的摘要接口，避免直接暴露 Actuator 组件详情。
  health: () => request('/health'),
  listRuns: () => request('/runs'),
  // 分页接口不改变旧的数组接口，供历史列表按需增量加载。
  listRunsPage: ({ page = 0, size = 20, status } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (status) params.set('status', status)
    return request(`/runs/page?${params.toString()}`)
  },
  dashboardSummary: () => request('/dashboard/summary'),
  getRun: (runId) => request(`/runs/${runId}`),
  createRun: (payload) => request('/runs', { method: 'POST', body: JSON.stringify(payload) }),
  startRun: (runId) => request(`/runs/${runId}/start`, { method: 'POST' }),
  approveRun: (runId) => request(`/runs/${runId}/approve`, { method: 'POST' }),
  rejectRun: (runId, reason) => request(`/runs/${runId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  }),
  retryRun: (runId) => request(`/runs/${runId}/retry`, { method: 'POST' }),
  cancelRun: (runId) => request(`/runs/${runId}`, { method: 'DELETE' }),
  listConversations: () => request('/conversations'),
  createConversation: (payload = {}) => request('/conversations', {
    method: 'POST',
    body: JSON.stringify(payload),
  }),
  getConversation: (conversationId) => request(`/conversations/${encodeURIComponent(conversationId)}`),
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
  deleteDocument: (documentId) => request(`/context/documents/${documentId}`, { method: 'DELETE' }),
  previewContext: (query) => request(`/context/preview?query=${encodeURIComponent(query)}`),
  listMemories: () => request('/context/memories'),
  createMemory: (payload) => request('/context/memories', { method: 'POST', body: JSON.stringify(payload) }),
  listEvaluations: () => request('/evaluations'),
  runEvaluation: (payload) => request('/evaluations', { method: 'POST', body: JSON.stringify(payload) }),
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
