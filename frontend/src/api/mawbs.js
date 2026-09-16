import api from './client'

export const mawbsApi = {
  getAll: (params = {}) => api.get('/mawbs', { params }),
  getByFlight: (flightId, params = {}) => api.get(`/mawbs/flight/${flightId}`, { params }),
  create: (dto) => api.post('/mawbs', dto),
  getByAwbNumber: (awbNumber) => api.get(`/mawbs/awb/${encodeURIComponent(awbNumber)}`),
  update: (mawbId, dto) => api.put(`/mawbs/${mawbId}`, dto),
  updateStatus: (mawbId, status) => api.patch(`/mawbs/${mawbId}/status`, JSON.stringify(status), { headers: { 'Content-Type': 'application/json' } }),
  getSupportingDocs: (mawbId) => api.get(`/mawbs/${mawbId}/supporting-docs`),
  updateSupportingDocs: (mawbId, docs) => api.put(`/mawbs/${mawbId}/supporting-docs`, docs),
  getSupportingDocsPdf: (mawbId) => api.get(`/mawbs/${mawbId}/supporting-docs/pdf`, { responseType: 'blob' }),
}
