import { ref, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { captureForms, saveDraft, setReturnTo } from '@/utils/formDraft'

/**
 * Cierre de sesión automático por inactividad (50 minutos).
 *
 * · A los 40 min muestra un aviso con cuenta regresiva y opción de continuar.
 * · A los 50 min: snapshot de formularios en edición → logout limpio
 *   (revoca cookies en el servidor) → /login con aviso y retorno a la vista.
 * · Cualquier interacción del usuario (click, tecla, scroll, movimiento, arrastre,
 *   input, cambio de pestaña, foco, navegación) reinicia el contador de inactividad.
 * · Detección robusta: listeners en fase de captura sobre `document` (sobreviven al
 *   `stopPropagation` de hijos), throttle en pointermove/touchmove, eventos de drag.
 * · Guard de suspensión: un salto de reloj entre ticks (laptop dormida) no cuenta
 *   como inactividad.
 */
const IDLE_MS = 50 * 60 * 1000
const WARN_MS = 40 * 60 * 1000
const TICK_MS = 1000

const warningSeconds = ref(null) // null = sin aviso activo
let lastActivity = Date.now()
let lastTick = Date.now() // marca del tick anterior para detectar suspensiones del timer
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

// Handler NOMBRADO para visibilitychange — permite el unbind exacto (antes se
// des-ligaba un listener anónimo distinto y el real quedaba fugado de por vida).
function onVisibilityChange() {
  if (!document.hidden) touch()
}

// Eventos escuchados en FASE DE CAPTURA sobre document: se disparan antes que
// cualquier handler del componente, así un `stopPropagation` en un hijo jamás
// suprime la detección de actividad (click en modales, selects, drag & drop).
const ACTIVITY_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart', 'touchmove', 'scroll', 'input', 'change']
const DRAG_EVENTS = ['dragstart', 'drag', 'dragover', 'drop', 'dragend'] // arrastrar ULDs/columnas

function eventHandler(ev) {
  if (ev === 'pointermove' || ev === 'touchmove') throttleTouch()
  else touch()
}

function bindListeners() {
  if (bound) return
  bound = true
  const captureOpts = { passive: true, capture: true }
  for (const ev of ACTIVITY_EVENTS) document.addEventListener(ev, eventHandler, captureOpts)
  for (const ev of DRAG_EVENTS) document.addEventListener(ev, touch, captureOpts)
  // Cambios de foco/visibilidad (cambio de pestaña, ventana)
  window.addEventListener('focus', touch, { passive: true })
  document.addEventListener('visibilitychange', onVisibilityChange)
  // Navegación SPA (vue-router)
  window.addEventListener('popstate', touch, { passive: true })
  window.addEventListener('hashchange', touch, { passive: true })
}

function unbindListeners() {
  if (!bound) return
  bound = false
  const captureOpts = { passive: true, capture: true }
  for (const ev of ACTIVITY_EVENTS) document.removeEventListener(ev, eventHandler, captureOpts)
  for (const ev of DRAG_EVENTS) document.removeEventListener(ev, touch, captureOpts)
  window.removeEventListener('focus', touch, { passive: true })
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.removeEventListener('popstate', touch, { passive: true })
  window.removeEventListener('hashchange', touch, { passive: true })
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
    const now = Date.now()
    // Guard de suspensión/salto de reloj: si un solo tick tardó más que IDLE_MS
    // (laptop dormida, tabs en background throttled, cambio de hora), ese lapso
    // NO cuenta como inactividad real → se re-ancla lastActivity y sigue. La
    // inactividad real (timers en curso, ticks ~1s) sigue expirando con normalidad.
    if (now - lastTick > IDLE_MS) {
      lastTick = now
      touch()
      return
    }
    lastTick = now

    const idle = now - lastActivity
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
    if (running) stop() // arranque idempotente: reinicia el ciclo en vez de ignorarlo
    running = true
    lastTick = Date.now() // re-ancla el guard de suspensión por sesión
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
