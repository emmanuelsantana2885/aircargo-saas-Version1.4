import { ref, onMounted, onUnmounted } from 'vue'

export function useOnlineStatus() {
  const online = ref(typeof navigator !== 'undefined' ? navigator.onLine : true)

  function goOnline() { online.value = true }
  function goOffline() { online.value = false }

  onMounted(() => {
    window.addEventListener('online', goOnline)
    window.addEventListener('offline', goOffline)
  })

  onUnmounted(() => {
    window.removeEventListener('online', goOnline)
    window.removeEventListener('offline', goOffline)
  })

  return { online }
}