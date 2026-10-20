import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

const h = vi.hoisted(() => ({
  inst: null,
  create: vi.fn(),
  post: vi.fn(),
  handleApiError: vi.fn(),
}))

vi.mock('axios', () => {
  const inst = vi.fn()
  inst.interceptors = { response: { use: vi.fn() } }
  h.inst = inst
  h.create.mockImplementation(() => inst)
  return { default: { create: h.create, post: (...a) => h.post(...a) } }
})
vi.mock('@/utils/error', () => ({ handleApiError: (...a) => h.handleApiError(...a) }))
vi.mock('@/stores/toast', () => ({ useToastStore: () => ({}) }))

import api from '@/api/client'
import { useAuthStore } from '@/stores/auth'

// Capture the interceptor + axios.create() call at import time: beforeEach calls
// clearAllMocks(), which would otherwise wipe mock.calls on `use`.
const [ON_OK, ON_REJECT] = h.inst.interceptors.response.use.mock.calls[0]
const CREATE_CFG = h.create.mock.calls[0][0]

const RETRIED = Symbol('retried')
const err = (status, url = '/api/flights/list', config = {}) => ({
  response: { status },
  config: { url, method: 'get', ...config },
})

const storedSession = (o) => localStorage.setItem('aircargo_auth', JSON.stringify(o))

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  localStorage.clear()
  sessionStorage.clear() // sin esto, aircargo_expired_route se filtra entre tests
  h.inst.mockResolvedValue(RETRIED)
  h.post.mockResolvedValue({ data: {} })
  // default to "logged out, sitting on /login" so clearLocalSession() never navigates
  window.history.replaceState({}, '', '/login')
})

describe('api client — non-401 passthrough', () => {
  it('leaves successful responses untouched', async () => {
    const res = { status: 200, data: { ok: true } }
    const out = await ON_REJECT(res).catch(() => 'threw')
    expect(out).toBe('threw') // a fulfilled response never reaches the error branch
  })

  it('routes other errors to the shared toast handler', async () => {
    const e = err(500)
    await expect(ON_REJECT(e)).rejects.toBe(e)
    expect(h.handleApiError).toHaveBeenCalledTimes(1)
    expect(h.post).not.toHaveBeenCalled()
  })

  it('passes 428 through WITHOUT a toast (LoginView renders it inline)', async () => {
    const e = err(428, '/api/auth/login')
    await expect(ON_REJECT(e)).rejects.toBe(e)
    expect(h.handleApiError).not.toHaveBeenCalled()
    expect(h.post).not.toHaveBeenCalled()
  })

  it('warns on 403 without a toast storm', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const e = err(403, '/api/users')
    await expect(ON_REJECT(e)).rejects.toBe(e)
    expect(warn).toHaveBeenCalled()
    expect(h.handleApiError).not.toHaveBeenCalled()
  })
})

describe('api client — refresh single-flight', () => {
  it('refreshes once, then replays the original request', async () => {
    const cfg = { url: '/api/mawbs', method: 'get' }
    const e = err(401, '/api/mawbs', cfg)

    await expect(ON_REJECT(e)).resolves.toBe(RETRIED)

    expect(h.post).toHaveBeenCalledTimes(1)
    expect(h.post).toHaveBeenCalledWith('/api/auth/refresh', {}, { withCredentials: true })
    expect(h.inst).toHaveBeenCalledWith(expect.objectContaining({ _retry: true }))
  })

  it('collapses concurrent 401s into ONE refresh and replays each request', async () => {
    const gate = {}
    gate.promise = new Promise((res) => { gate.resolve = res })
    h.post.mockReturnValue(gate.promise)

    const handler = ON_REJECT
    const a = handler(err(401, '/api/a'))
    const b = handler(err(401, '/api/b'))
    const c = handler(err(401, '/api/c'))

    // all three are parked on the in-flight refresh
    expect(h.post).toHaveBeenCalledTimes(1)
    expect(h.inst).not.toHaveBeenCalled()

    gate.resolve({ data: {} })
    const results = await Promise.all([a, b, c])

    expect(results).toEqual([RETRIED, RETRIED, RETRIED])
    expect(h.post).toHaveBeenCalledTimes(1)
    expect(h.inst).toHaveBeenCalledTimes(3)
  })

  it('never refreshes the refresh endpoint itself (no loop)', async () => {
    const e = err(401, '/api/auth/refresh')
    await expect(ON_REJECT(e)).rejects.toBe(e)
    expect(h.post).not.toHaveBeenCalled()
  })

  it('does not retry a request twice — the _retry guard holds', async () => {
    const e = err(401, '/api/mawbs', { _retry: true })
    await expect(ON_REJECT(e)).rejects.toBe(e)
    expect(h.post).not.toHaveBeenCalled()
  })

  it('recovers the single-flight latch after a failure (later 401 can refresh again)', async () => {
    h.post.mockRejectedValueOnce(new Error('refresh dead'))
    await expect(ON_REJECT(err(401, '/api/a'))).rejects.toThrow('refresh dead')

    h.post.mockResolvedValue({ data: {} })
    await expect(ON_REJECT(err(401, '/api/b'))).resolves.toBe(RETRIED)
    expect(h.post).toHaveBeenCalledTimes(2)
  })
})

describe('api client — refresh failure', () => {
  it('rejects every queued request with the refresh error', async () => {
    const boom = new Error('refresh dead')
    const gate = {}
    gate.promise = new Promise((_, rej) => { gate.reject = rej })
    h.post.mockReturnValue(gate.promise)

    const handler = ON_REJECT
    const a = handler(err(401, '/api/a'))
    const b = handler(err(401, '/api/b'))
    gate.reject(boom)

    await expect(a).rejects.toBe(boom)
    await expect(b).rejects.toBe(boom)
    expect(h.inst).not.toHaveBeenCalled()
  })

  it('clears a fully-established session so the app bounces to /login', async () => {
    storedSession({ userId: 'u1', selectedSiteId: 's1' })
    window.history.replaceState({}, '', '/mawbs')
    h.post.mockRejectedValue(new Error('refresh dead'))

    await expect(ON_REJECT(err(401, '/api/a'))).rejects.toThrow()

    expect(localStorage.getItem('aircargo_auth')).toBeNull()
  })

  it('leaves a half-finished login intact (no bounce back to the password step)', async () => {
    // user is mid multi-step login: profile exists but no site confirmed yet
    storedSession({ userId: 'u1' })
    h.post.mockRejectedValue(new Error('refresh dead'))

    await expect(ON_REJECT(err(401, '/api/a'))).rejects.toThrow()

    expect(localStorage.getItem('aircargo_auth')).not.toBeNull()
  })

  // El criterio de "sesión xxx" debe ser userId AND selectedSiteId, nunca userId
  // solo: un perfil sin sitio seleccionado es un login multi-paso en curso, y
  // borrarlo devolvería al operador al paso de contraseña con MFA por re-empezar.
  it('keeps a mid-MFA login (no userId yet) intact too', async () => {
    // paso MFA/contraseña: se guardan flags, todavía no hay userId
    storedSession({ email: 'a@b.c', hasPasswordSet: true, mfaEnabled: true })
    h.post.mockRejectedValue(new Error('refresh dead'))

    await expect(ON_REJECT(err(401, '/api/a'))).rejects.toThrow()

    expect(localStorage.getItem('aircargo_auth')).not.toBeNull()
  })

  // REGRESIÓN del bucle infinito de 401: `clearLocalSession` borraba
  // `aircargo_auth` de localStorage, pero el store Pinia se hidrata de esa clave
  // UNA sola vez al arrancar y nunca la relee. `auth.isAuthenticated` seguía en
  // true → el watch de App.vue no detenía el heartbeat → cada 30 s volvían el
  // heartbeat 401 y el refresh 401 en bucle. El interceptor debe apagar el store.
  it('clears the Pinia auth store too, not just localStorage', async () => {
    storedSession({ userId: 'u1', selectedSiteId: 's1' })
    window.history.replaceState({}, '', '/mawbs')
    h.post.mockRejectedValue(new Error('refresh dead'))

    // El store arranca autenticado desde la sesión persistida.
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(true)

    await expect(ON_REJECT(err(401, '/api/auth/heartbeat'))).rejects.toThrow()

    // Sin esto, isAuthenticated seguía true y el intervalo nunca se detenía.
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.userId).toBeNull()
    expect(auth.selectedSiteId).toBeNull()
  })

  it('does not clear the store for a half-finished login (mid-MFA)', async () => {
    const auth = useAuthStore()
    auth.userId = null
    auth.hasPasswordSet = true
    auth.mfaEnabled = true
    storedSession({ email: 'a@b.c', hasPasswordSet: true, mfaEnabled: true })
    h.post.mockRejectedValue(new Error('refresh dead'))

    await expect(ON_REJECT(err(401, '/api/a'))).rejects.toThrow()

    // El perfil del paso a medias se conserva: no hay userId/site que limpiar y
    // borrarlo devolvería al operador al paso de contraseña.
    expect(auth.hasPasswordSet).toBe(true)
    expect(localStorage.getItem('aircargo_auth')).not.toBeNull()
  })

  it('returns the user to the view where the session died', async () => {
    storedSession({ userId: 'u1', selectedSiteId: 's1' })
    window.history.replaceState({}, '', '/receipts?flightId=f1')
    h.post.mockRejectedValue(new Error('refresh dead'))

    await expect(ON_REJECT(err(401, '/api/a'))).rejects.toThrow()

    expect(localStorage.getItem('aircargo_auth')).toBeNull()
    // La ruta queda registrada para devolver al usuario a su vista tras
    // re-autenticar. Los borradores de recibos no se copian: viven en
    // localStorage, que el logout no borra, y la vista los restaura sola.
    expect(sessionStorage.getItem('aircargo_expired_route')).toBe('/receipts?flightId=f1')
    expect(sessionStorage.getItem('aircargo_pending_drafts')).toBeNull()
  })

  it('does not record a route when the session dies on the login page', async () => {
    storedSession({ userId: 'u1', selectedSiteId: 's1' })
    window.history.replaceState({}, '', '/login')
    h.post.mockRejectedValue(new Error('refresh dead'))

    await expect(ON_REJECT(err(401, '/api/a'))).rejects.toThrow()

    expect(sessionStorage.getItem('aircargo_expired_route')).toBeNull()
  })

  it('leaves receipt drafts in localStorage untouched', async () => {
    storedSession({ userId: 'u1', selectedSiteId: 's1' })
    localStorage.setItem('warehouse_draft_AWB-1', '{"pieces":3}')
    window.history.replaceState({}, '', '/receipts')
    h.post.mockRejectedValue(new Error('refresh dead'))

    await expect(ON_REJECT(err(401, '/api/a'))).rejects.toThrow()

    expect(localStorage.getItem('warehouse_draft_AWB-1')).toBe('{"pieces":3}')
  })

  it('survives a corrupt aircargo_auth payload', async () => {
    localStorage.setItem('aircargo_auth', '{not json')
    h.post.mockRejectedValue(new Error('refresh dead'))
    await expect(ON_REJECT(err(401, '/api/a'))).rejects.toThrow()
  })
})

describe('api client — configuration', () => {
  it('is the mocked instance with a captured single response interceptor', () => {
    expect(api).toBe(h.inst)
    expect(ON_OK).toBeTypeOf('function')
    expect(ON_REJECT).toBeTypeOf('function')
  })

  it('is configured for cookie auth, not bearer headers', () => {
    // tokens live in httpOnly cookies; the client must never attach Authorization
    expect(CREATE_CFG.baseURL).toBe('/api')
    expect(CREATE_CFG.withCredentials).toBe(true)
    expect(JSON.stringify(CREATE_CFG.headers || {}).toLowerCase())
      .not.toContain('authorization')
  })
})

describe('api client — 401 that is a business answer, not an expired session', () => {
  it('does not attempt a refresh when the login itself is rejected', async () => {
    // Credenciales incorrectas → 401. Es la respuesta esperada del login, no el
    // aviso de sesión caducada: refrescar no puede funcionar y el error que
    // llegaría a LoginView sería el del refresh, no "email y/o contraseña
    // incorrectos".
    const e = err(401, '/api/auth/login')
    await expect(ON_REJECT(e)).rejects.toBe(e)
    expect(h.post).not.toHaveBeenCalled()
  })

  it.each([
    '/api/auth/reset-password/validate',
    '/api/auth/set-password-token',
    '/api/auth/mfa/enroll/setup',
    '/api/auth/logout',
  ])('does not attempt a refresh for %s', async (url) => {
    const e = err(401, url)
    await expect(ON_REJECT(e)).rejects.toBe(e)
    expect(h.post).not.toHaveBeenCalled()
  })
})

describe('api client — concurrent 401s queue behind one refresh', () => {
  it('marks queued requests as retried so they cannot trigger a second refresh', async () => {
    storedSession({ userId: 'u1', selectedSiteId: 's1' })
    // refresh lento: la segunda petición entra mientras la primera lo espera
    let releaseRefresh
    h.post.mockImplementation(() => new Promise(res => { releaseRefresh = res }))

    const cfgA = { url: '/api/a', method: 'get' }
    const cfgB = { url: '/api/b', method: 'get' }

    const first = ON_REJECT(err(401, '/api/a', cfgA))
    const second = ON_REJECT(err(401, '/api/b', cfgB))

    releaseRefresh({ data: {} })
    await Promise.all([first, second])

    // Ambas peticiones se reintentan, pero el refresh ocurrió UNA sola vez. Sin
    // `_retry` en la cola, el reintento de B habría entrado de nuevo al
    // interceptor con otro 401 → segundo refresh → reutilización del refresh
    // token → revocación de la sesión.
    expect(h.post).toHaveBeenCalledTimes(1)
    expect(h.inst).toHaveBeenCalledTimes(2)
    // todo lo que se reinyecta llega YA marcado como reintentado
    const retried = h.inst.mock.calls.map(c => c[0])
    expect(retried.map(c => c._retry)).toEqual([true, true])
  })
})
