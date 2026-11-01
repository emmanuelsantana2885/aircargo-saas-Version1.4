import api from './client'

// Global receipt-form settings: which reserved fields (seal number, dock bay)
// are enabled for capture. Only a SUPER_USER can change them.
export const receiptFormConfigApi = {
  get: () => api.get('/receipt-form-config'),
  update: (payload) => api.put('/receipt-form-config', payload),
}
