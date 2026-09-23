<template>
  <div v-if="auth.isAuthenticated" class="flex h-screen overflow-hidden app-layout">
    <Sidebar ref="sidebarRef" />
    <div class="flex flex-col flex-1 min-w-0 overflow-hidden" :style="{ marginLeft: sidebarMarginLeft }">
      <Header @toggle-sidebar="toggleSidebar" :collapsed="sidebarCollapsed" />
      <main class="flex-1 overflow-auto relative">
        <ErrorBoundary>
          <router-view v-slot="{ Component }">
            <transition name="page">
              <component :is="Component" :key="$route.path" />
            </transition>
          </router-view>
        </ErrorBoundary>
      </main>
      <BottomNav />
    </div>
  </div>
  <router-view v-else />
  <IdleWarningModal
    v-if="idleWarningSeconds !== null && idleWarningSeconds >= 0"
    :seconds-left="idleWarningSeconds ?? 0"
    @continue="continueWorkingSafe"
    @logout-now="expireIdleSession"
  />
  <ToastNotifications />
  <ConfirmDialog />
  <CommandPalette :open="paletteOpen" @close="paletteOpen = false" />
</template>

<script setup>
// Desarrollado por Emmanuel Santana Solano
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useToastStore } from './stores/toast'
import { useNotificationsStore } from './stores/notifications'
import ErrorBoundary from './components/ErrorBoundary.vue'
import Sidebar from './components/layout/Sidebar.vue'
import Header from './components/layout/Header.vue'
import BottomNav from './components/layout/BottomNav.vue'
import ToastNotifications from './components/ToastNotifications.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
import IdleWarningModal from './components/IdleWarningModal.vue'
import CommandPalette from './components/CommandPalette.vue'
import { useIdleLogout } from './composables/useIdleLogout'
import { usersApi } from './api/users'
import api from './api/client'
import { initTheme } from './utils/theme'
import { initFont } from './utils/font'
import { initDensity } from './utils/density'

initTheme()
initFont()
initDensity()


const auth = useAuthStore()
const toast = useToastStore()
const notif = useNotificationsStore()
const router = useRouter()
const sidebarRef = ref(null)
const paletteOpen = ref(false)
let heartbeatInterval
const prevReceiptCount = ref(0)
const NOTIFY_ROLES = ['TRAFFIC', 'OPERATIONS', 'SUPER_USER', 'ADMIN']

const sidebarCollapsed = computed(() => sidebarRef.value?.collapsed ?? false)

const sidebarMarginLeft = computed(() => {
  const s = sidebarRef.value
  if (!s) return '210px'
  // móvil: el sidebar es un overlay fijo (fuera del flujo) → sin margen
  if (s.isMobile) return '0px'
  return s.collapsed ? '60px' : '210px'
})

async function expireIdleSession() {
  auth.logout()
  window.location.href = '/login?idle=1'   // recarga limpia: garantiza estado fresco
}

const {
  start: startIdleLogout,
  stop: stopIdleLogout,
  continueWorking: continueWorkingRaw,
  warningSeconds: idleWarningSeconds,
} = useIdleLogout(expireIdleSession)

function continueWorkingSafe() {
  continueWorkingRaw()
}

// IDLE LOGOUT — cierre por inactividad 50 min (seguridad)
import { watch } from 'vue'
watch(() => auth.isAuthenticated, (authed) => {
  if (authed) startIdleLogout(); else stopIdleLogout()
}, { immediate: true })

// HEARTBEAT — presencia del navegador vivo. Arranca con el login real (no solo al
// montar App): si la app se monta en /login y autentica por SPA (router.push sin
// reload), el onMounted ya no alcanza a ver `isAuthenticated`. El watch con
// immediate:true cubre ambos caminos (montaje logueado y login posterior).
function startHeartbeat() {
  if (heartbeatInterval) return
  auth.refreshProfile()
  usersApi.heartbeat().catch(() => {})
  checkNewReceipts()
  heartbeatInterval = setInterval(() => {
    usersApi.heartbeat().catch(() => {})
    checkNewReceipts()
  }, 30000)
}

function stopHeartbeat() {
  if (heartbeatInterval) {
    clearInterval(heartbeatInterval)
    heartbeatInterval = null
  }
}

watch(() => auth.isAuthenticated, (authed) => {
  if (authed) { startHeartbeat(); notif.start() } else { stopHeartbeat(); notif.stop() }
}, { immediate: true })


function toggleSidebar() {
  const s = sidebarRef.value
  if (!s) return
  if (s.isMobile) {
    s.mobileOpen = !s.mobileOpen
  } else {
    s.collapsed = !s.collapsed
  }
}

// Global keyboard shortcuts
function onKeydown(e) {
  if (!auth.isAuthenticated || paletteOpen.value) return
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    paletteOpen.value = true
    return
  }
  const tag = document.activeElement?.tagName || ''
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  if (e.ctrlKey && e.key === 'g') { e.preventDefault(); router.push('/') }
  if (e.ctrlKey && e.key === 'b') { e.preventDefault(); router.push('/bookings') }
  if (e.ctrlKey && e.key === 'f') { e.preventDefault(); router.push('/flights') }
  if (e.ctrlKey && e.key === 'l') { e.preventDefault(); router.push('/load-planning') }
  if (e.ctrlKey && e.key === 'u') { e.preventDefault(); router.push('/ulds') }
  if (e.ctrlKey && e.key === 'r') { e.preventDefault(); router.push('/receipts') }
  if (e.ctrlKey && e.key === 'm') { e.preventDefault(); router.push('/mawbs') }
}

async function checkNewReceipts() {
  try {
    const res = await api.get('/receipts')
    const current = Array.isArray(res.data) ? res.data : []
    if (prevReceiptCount.value > 0 && current.length > prevReceiptCount.value && NOTIFY_ROLES.includes(auth.role)) {
      const newCount = current.length - prevReceiptCount.value
      toast.info(`Nuevo${newCount > 1 ? 's' : ''} recibo${newCount > 1 ? 's' : ''} de bodega disponible${newCount > 1 ? 's' : ''} (${newCount})`, 6000)
    }
    prevReceiptCount.value = current.length
  } catch {
    // silent
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  stopHeartbeat()
  notif.stop()
})
</script>
