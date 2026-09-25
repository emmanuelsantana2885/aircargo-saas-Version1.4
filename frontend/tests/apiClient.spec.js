import { describe, it, expect, vi, beforeEach } from 'vitest'

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
  localStorage.clear()
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
