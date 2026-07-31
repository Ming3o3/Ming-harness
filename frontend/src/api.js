const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'

async function request(path, options = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-Id': localStorage.getItem('harnessTenantId') || 'tenant-demo',
      'X-User-Id': localStorage.getItem('harnessUserId') || 'operator',
      ...(options.headers || {}),
    },
    ...options,
  })

  if (!response.ok) {
    const payload = await response.json().catch(() => ({}))
    throw new Error(payload.message || `请求失败（${response.status}）`)
  }

  if (response.status === 204) {
    return null
  }
  return response.json()
}

export const api = {
  health: async () => {
    const response = await fetch('/actuator/health')
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new Error(payload.message || `基础设施健康检查失败（${response.status}）`)
    }
    return payload
  },
  listRuns: () => request('/runs'),
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
