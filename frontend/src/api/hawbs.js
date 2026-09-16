import api from './client'

export const hawbsApi = {
  getByMawb: (mawbId) => api.get(`/hawbs/mawb/${mawbId}`),
  create: (dto) => api.post('/hawbs', dto),
  update: (id, dto) => api.put(`/hawbs/${id}`, dto),
}
