import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notificationsApi } from '../api/notifications'
import { useToastStore } from './toast'

const MAX_ITEMS = 30

export const useNotificationsStore = defineStore('notifications', () => {
  const items = ref([])
  const unread = ref(0)
  const connected = ref(false)
  const loading = ref(false)

  let source = null

  async function load() {
    loading.value = true
    try {
      const [all, count] = await Promise.all([
        notificationsApi.getAll(),
        notificationsApi.getUnreadCount(),
      ])
      items.value = Array.isArray(all.data) ? all.data.slice(0, MAX_ITEMS) : []
      unread.value = Number(count.data) || 0
    } catch {
      // silencioso: el badge simplemente no se refresca
    } finally {
      loading.value = false
    }
  }

  function start() {
    if (source) return
    const toast = useToastStore()
    load()

    if (typeof window === 'undefined' || typeof EventSource === 'undefined') return

    try {
      source = new EventSource(notificationsApi.streamUrl())
    } catch {
      source = null
      return
    }

    source.onopen = () => { connected.value = true }
    source.onerror = () => { connected.value = false }

    source.addEventListener('notification', (e) => {
      let n = null
      try { n = JSON.parse(e.data) } catch { return }
      if (!n) return
      items.value = [n, ...items.value].slice(0, MAX_ITEMS)
      if (!n.isRead) unread.value += 1
      try { toast.info(n.title, 6000) } catch { /* noop */ }
    })
  }

  function stop() {
    if (source) {
      source.close()
      source = null
    }
    connected.value = false
  }

  async function markRead(id) {
    const n = items.value.find(x => x.id === id)
    if (!n || n.isRead) return
    n.isRead = true
    unread.value = Math.max(0, unread.value - 1)
    try {
      await notificationsApi.markRead(id)
    } catch {
      load()
    }
  }

  async function markAllRead() {
    const pending = items.value.filter(n => !n.isRead)
    if (!pending.length) return
    pending.forEach(n => { n.isRead = true })
    unread.value = 0
    try {
      await Promise.all(pending.map(n => notificationsApi.markRead(n.id)))
    } catch {
      load()
    }
  }

  async function remove(id) {
    const n = items.value.find(x => x.id === id)
    items.value = items.value.filter(x => x.id !== id)
    if (n && !n.isRead) unread.value = Math.max(0, unread.value - 1)
    try {
      await notificationsApi.remove(id)
    } catch {
      load()
    }
  }

  return { items, unread, connected, loading, load, start, stop, markRead, markAllRead, remove }
})
