const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
const configuredApiKey = import.meta.env.VITE_HARNESS_API_KEY || ''

async function request(path, options = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(configuredApiKey
        ? { Authorization: `Bearer ${configuredApiKey}` }
        : {
            'X-Tenant-Id': localStorage.getItem('harnessTenantId') || 'tenant-demo',
            'X-User-Id': localStorage.getItem('harnessUserId') || 'operator',
          }),
      ...(options.headers || {}),
    },
    ...options,
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
}
