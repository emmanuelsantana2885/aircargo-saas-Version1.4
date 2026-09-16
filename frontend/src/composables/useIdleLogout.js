import { ref, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { captureForms, saveDraft, setReturnTo } from '@/utils/formDraft'

/**
 * Cierre de sesión automático por inactividad (10 minutos).
 *
 * · A los 8 min muestra un aviso con cuenta regresiva y opción de continuar.
 * · A los 10 min: snapshot de formularios en edición → logout limpio
 *   (revoca cookies en el servidor) → /login con aviso y retorno a la vista.
 * · Cualquier interacción del usuario (click, tecla, scroll, movimiento, foco, navegación)
 *   reinicia el contador de inactividad.
 * · Detección robusta: throttle en pointermove, eventos táctiles, input, focus.
 */
const IDLE_MS = 10 * 60 * 1000
const WARN_MS = 8 * 60 * 1000
const TICK_MS = 1000

const warningSeconds = ref(null) // null = sin aviso activo
let lastActivity = Date.now()
let timer = null
let bound = false
let running = false
let pointerMoveThrottle = null

function touch() {
  lastActivity = Date.now()
  if (warningSeconds.value !== null) warningSeconds.value = null // actividad cancela el aviso
}

function throttleTouch() {
  if (pointerMoveThrottle) return
  pointerMoveThrottle = setTimeout(() => {
    touch()
    pointerMoveThrottle = null
  }, 500) // throttle a 500ms para pointermove
}

function bindListeners() {
  if (bound) return
  bound = true
  const opts = { passive: true }
  // Eventos de interacción del usuario - click, teclas, rueda, touch
  window.addEventListener('pointerdown', touch, opts)
  window.addEventListener('pointermove', throttleTouch, opts)
  window.addEventListener('keydown', touch, opts)
  window.addEventListener('wheel', touch, opts)
  window.addEventListener('touchstart', touch, opts)
  window.addEventListener('touchmove', throttleTouch, opts)
  window.addEventListener('scroll', touch, opts)
  window.addEventListener('input', touch, opts) // inputs, textareas, selects
  window.addEventListener('change', touch, opts) // selects, checkboxes
  // Cambios de foco/visibilidad (cambio de pestaña, ventana)
  window.addEventListener('focus', touch, opts)
  window.addEventListener('visibilitychange', () => {
    if (!document.hidden) touch()
  }, opts)
  // Navegación SPA (vue-router)
  window.addEventListener('popstate', touch, opts)
  window.addEventListener('hashchange', touch, opts)
}

function unbindListeners() {
  if (!bound) return
  bound = false
  const opts = { passive: true }
  window.removeEventListener('pointerdown', touch, opts)
  window.removeEventListener('pointermove', throttleTouch, opts)
  window.removeEventListener('keydown', touch, opts)
  window.removeEventListener('wheel', touch, opts)
  window.removeEventListener('touchstart', touch, opts)
  window.removeEventListener('touchmove', throttleTouch, opts)
  window.removeEventListener('scroll', touch, opts)
  window.removeEventListener('input', touch, opts)
  window.removeEventListener('change', touch, opts)
  window.removeEventListener('focus', touch, opts)
  window.removeEventListener('visibilitychange', () => {}, opts)
  window.removeEventListener('popstate', touch, opts)
  window.removeEventListener('hashchange', touch, opts)
  if (pointerMoveThrottle) {
    clearTimeout(pointerMoveThrottle)
    pointerMoveThrottle = null
  }
}

export function useIdleLogout(onExpire) {
  const router = useRouter()

  const secondsLeft = computed(() =>
    warningSeconds.value === null ? null : Math.max(0, Math.ceil(warningSeconds.value / TICK_MS))
  )

  function tick() {
    const idle = Date.now() - lastActivity
    if (idle >= IDLE_MS) {
      expire()
    } else if (idle >= WARN_MS) {
      warningSeconds.value = IDLE_MS - idle
    }
  }

  async function expire() {
    stop()
    try {
      // 1. preservar trabajo no guardado + vista de retorno
      saveDraft({ route: router.currentRoute.value.fullPath, forms: captureForms() })
      setReturnTo(router.currentRoute.value.fullPath)
    } catch {}
    try {
      // 2. logout limpio: revoca cookies y sesión en el servidor
      await onExpire?.()
    } catch {}
    // 3. al volver a entrar se ofrece restaurar el borrador
    router.push('/login?idle=1')
  }

  function start() {
    if (running) return
    running = true
    touch() // inicializa lastActivity al inicio
    bindListeners()
    timer = setInterval(tick, TICK_MS)
  }

  function stop() {
    running = false
    warningSeconds.value = null
    if (timer) clearInterval(timer)
    timer = null
    unbindListeners()
  }

  function continueWorking() {
    touch()
    fetch('/api/auth/heartbeat').catch(() => {}) // mantiene la sesión viva en el servidor
  }

  onBeforeUnmount(stop)

  return { start, stop, continueWorking, warningSeconds: secondsLeft }
}
