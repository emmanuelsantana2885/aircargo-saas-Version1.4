import api from './client'

export const calcConfigApi = {
  getAll:   () => api.get('/receipt-calc-config'),
  resolve:  (airlineId) => api.get('/receipt-calc-config/resolve', { params: { airlineId } }),
  getDefault:  () => api.get('/receipt-calc-config/default'),
  saveDefault: (payload) => api.put('/receipt-calc-config/default', payload),
  getForAirline: (airlineId) => api.get(`/receipt-calc-config/airline/${airlineId}`),
  saveForAirline: (airlineId, payload) => api.put(`/receipt-calc-config/airline/${airlineId}`, payload),
  deleteForAirline: (airlineId) => api.delete(`/receipt-calc-config/airline/${airlineId}`),
}