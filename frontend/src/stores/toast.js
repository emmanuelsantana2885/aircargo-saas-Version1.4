import { defineStore } from 'pinia'
import { ref } from 'vue'

// Duraciones por severidad (ms)
const TOAST_DURATIONS = {
  success: 4000,
  warning: 5000,
  error: 6000,
  info: 4000,
}

export const useToastStore = defineStore('toast', () => {
  const toasts = ref([])
  let nextId = 0

  function add(type, message, duration, html = false) {
    const id = ++nextId
    const d = duration ?? TOAST_DURATIONS[type] ?? 4000
    toasts.value.push({ id, type, message, html })
    if (d > 0) {
      setTimeout(() => remove(id), d)
    }
  }

  function remove(id) {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx >= 0) toasts.value.splice(idx, 1)
  }

  function success(msg, duration) { add('success', msg, duration) }
  function error(msg, duration) { add('error', msg, duration) }
  function warning(msg, duration) { add('warning', msg, duration) }
  function info(msg, duration) { add('info', msg, duration) }
  function rich(type, htmlMsg, duration = 6000) { add(type, htmlMsg, duration, true) }

  return { toasts, add, remove, success, error, warning, info, rich, durations: TOAST_DURATIONS }
})
