import axios from 'axios'
import { useToastStore } from '../stores/toast'
import { useAuthStore } from '../stores/auth'
import { handleApiError } from '../utils/error'

// Los tokens viajan en cookies httpOnly: el navegador los envía solo.
// Este cliente solo gestiona el refresh transparente ante un 401.
const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
})

let isRefreshing = false
let failedQueue = []

// Callback global que la app registra al montar (App.vue) para manejar expiración de sesión
let onSessionExpired = null

export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler
}

function processQueue(error) {
  failedQueue.forEach(p => (error ? p.reject(error) : p.resolve()))
  failedQueue = []
}

function readStoredSession() {
  try {
    const raw = localStorage.getItem('aircargo_auth')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

/* Clasificación de la sesión persistida en localStorage.
 *
 * `aircargo_auth` no guarda tokens (viven en cookies httpOnly): sólo el perfil no
 * sensible. El store `auth` define una sesión operativa como
 * `isAuthenticated = userId && selectedSiteId`, y el guard de `router/index.js`
 * usa exactamente ese mismo par. Hay que ser coherentes con esa definición,
 * porque son los únicos dos datos que separan un estado del otro:
 *
 *   1) SIN SESIÓN        → no hay `aircargo_auth`, o está corrupto.
 *      No hay nada que limpiar y no hay nada que preservar.
 *
 *   2) SESIÓN PLENA     → `userId` Y `selectedSiteId`.
 *      Es el estado normal de quien está operando: se autenticó, eligió sitio y
 *      ya puede entrar a las vistas. Si el refresh falla, la sesión está muerta
 *      de verdad (revocada, expirada, navegador cerrado), así que se limpia
 *      entera y la app vuelve a /login. Perder el perfil aquí es correcto: ya
 *      no describe nada válido.
 *
 *   3) LOGIN A MEDIAS    → `userId` sin `selectedSiteId` (paso de sitio aún
 *      pendiente) o sin `userId` (aún en MFA/contraseña, con `hasPasswordSet`,
 *      `mfaEnabled` o `mustChangePassword` escritos).
 *      El login multi-paso ya validó credenciales pero la sesión aún no es
 *      operativa, así que un fallo de refresh NO debe borrarla: LoginView
 *      necesita ese perfil para retomar el paso exacto donde se quedó, y el
 *      `enrollToken`/TOTP del flujo sigue siendo válido.
 *
 * Usar sólo `!!userId` (sin mirar `selectedSiteId`) mezcla los casos 2 y 3:
 * durante un login a medias destruye el perfil y deja al operador de vuelta en
 * el paso de contraseña, con MFA por re-empezar. `sessionIsFullyEstablished()`
 * mantiene la separación explícita. */
function sessionIsFullyEstablished(stored) {
  return !!stored?.userId && !!stored?.selectedSiteId
}

// Endpoints donde un 401 es una RESPUESTA LEGÍTIMA del negocio, no el aviso de
// una sesión caducada. Sin esta lista, un login con credenciales incorrectas
// (401 "Email y/o contraseña incorrectos") entraba al flujo de refresh: se pedía
// un refresh que tampoco puede funcionar, y el error que llegaba a `LoginView`
// era el del refresh fallido, no el de las credenciales — el usuario nunca veía
// por qué su contraseña era rechazada.
const NO_REFRESH_ON_401 = [
  '/auth/login',
  '/auth/refresh',
  '/auth/logout',
  '/auth/set-password',
  '/auth/set-password-token',
  '/auth/change-password',
  '/auth/reset-password/validate',
  '/auth/mfa/enroll/',
  // Recuperación de MFA: son endpoints públicos sin sesión. Un 401 aquí es la
  // respuesta real (p. ej. gateway de un despliegue aún sin la ruta pública) y
  // NO debe provocar un refresh —que también fallaría— ni limpiar la sesión:
  // eso convertía un error visible y accionable en un bucle silencioso de 401.
  '/auth/mfa/recovery/',
]

// Limpia y notifica al handler global (SPA navigation, no hard reload).
// Sólo procede cuando la sesión estaba PLENA (caso 2): un login a medias
// (caso 3) se deja intacto a propósito, y sin sesión (caso 1) no hay nada que
// hacer. El `onSessionExpired` global mantiene el retorno a /login, y antes se
// preservan los borradores de recibos para no perder trabajo en curso.
function clearLocalSession() {
  const stored = readStoredSession()
  if (!sessionIsFullyEstablished(stored)) return

  localStorage.removeItem('aircargo_auth')

  // Limpiar TAMBIÉN el store Pinia. Es la pieza que faltaba y la causa del bucle
  // infinito de 401: el store se hidrata de `aircargo_auth` una sola vez al
  // arrancar y NUNCA vuelve a leer localStorage, así que borrar la clave no lo
  // enteraba. `auth.isAuthenticated` seguía en true → el watch de App.vue no
  // llamaba a stopHeartbeat() → el intervalo seguía golpeando los endpoints
  // cada 30 s (heartbeat 401 → refresh 401 → clearLocalSession ya no-op → ...).
  // El store es la única fuente de verdad de `isAuthenticated`, así que el
  // interceptor —que es quien detecta la sesión muerta— debe apagarlo aquí y no
  // depender de que un handler externo lo recuerde.
  try {
    useAuthStore().clearProfile()
  } catch {
    // Pinia aún no inicializada (p. ej. un import temprano en tests): la clave de
    // localStorage ya quedó limpia, que es lo mínimo imprescindible.
  }

  // Recordar dónde estaba el usuario para devolverlo allí tras re-autenticar.
  // Antes este bloque copiaba las claves `warehouse_draft_*` de localStorage a
  // `sessionStorage.aircargo_pending_drafts`: era redundante (el logout sólo
  // borra `aircargo_auth`, así que esos borradores nunca salían de localStorage)
  // y además inalcanzable, porque `?expired=1` se perdía al navegar tras el
  // login. Los borradores de recibos los restaura la propia vista de recepción
  // al inicializar cada formulario, así que aquí sólo hace falta la ruta.
  try {
    const path = window.location.pathname + (window.location.search || '')
    if (path && path !== '/login') {
      sessionStorage.setItem('aircargo_expired_route', path)
    }
  } catch {}

  // Notificar al handler global (App.vue) para navegar vía router.push
  if (onSessionExpired) {
    onSessionExpired()
  } else {
    // Fallback: hard reload solo si no hay handler
    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  }
}

api.interceptors.response.use(
  res => res,
  async err => {
    const originalRequest = err.config
    const url = originalRequest?.url || ''
    if (err.response?.status === 401 && !originalRequest?._retry
        && !NO_REFRESH_ON_401.some(p => url.includes(p))) {
      if (isRefreshing) {
// Se marca `_retry` ANTES de encolar. Antes sólo se marcaba la petición
        // que lanzaba el refresh; las que esperaban en la cola se reintentaban
        // con `_retry` intacto, así que un segundo 401 disparaba OTRO refresh.
        // Con la rotación de refresh tokens del backend eso se convierte en
        // reutilización de token → revocación de toda la familia de sesiones
        // ante un simple aluvión de peticiones concurrentes con el token
        // caducado.
        originalRequest._retry = true
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(() => api(originalRequest))
      }
      originalRequest._retry = true
      isRefreshing = true

      try {
        // el refresh token viaja en su cookie httpOnly; no se envía nada en el body
        await axios.post('/api/auth/refresh', {}, { withCredentials: true })
        processQueue(null)
        return api(originalRequest)
      } catch (refreshErr) {
        processQueue(refreshErr)
        // El refresh falló. clearLocalSession() decide internamente si la sesión
        // estaba PLENA (limpia y vuelve a /login) o si era un login a medias /
        // no había sesión (no toca nada). Ver la clasificación arriba.
        clearLocalSession()
        return Promise.reject(refreshErr)
      } finally {
        isRefreshing = false
      }
    }
    // 428 = flujo esperado del login (falta contraseña / MFA): lo maneja LoginView
    if (err.response?.status === 428) return Promise.reject(err)
    if (err.response?.status === 403) {
      const url = err.config?.url || ''
      console.warn('[API 403] Sin permiso para:', err.config?.method?.toUpperCase(), url)
      return Promise.reject(err)
    }
    handleApiError(err, useToastStore())
    return Promise.reject(err)
  }
)

export default api
