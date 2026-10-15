import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const pushMock = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({
    currentRoute: { value: { fullPath: '/receipts?mawbId=123' } },
    push: pushMock,
  }),
}))

import { useIdleLogout } from '@/composables/useIdleLogout'
import { loadDraft, popReturnTo } from '@/utils/formDraft'

describe('useIdleLogout', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    pushMock.mockClear()
    sessionStorage.clear()
    document.body.innerHTML = '<input id="campo" type="text" />'
    document.getElementById('campo').value = 'dato importante sin guardar'
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('no avisa antes de los 15 min', () => {
    const { start, stop, warningSeconds } = useIdleLogout()
    start()
    vi.advanceTimersByTime(55 * 60 * 1000 - 1000)
    expect(warningSeconds.value).toBeNull()
    stop()
  })

  it('a los 55 min activa la cuenta regresiva; la actividad la cancela y evita el logout', () => {
    const { start, stop, warningSeconds } = useIdleLogout()
    start()
    vi.advanceTimersByTime(55 * 60 * 1000 + 500)
    expect(warningSeconds.value).not.toBeNull()

    document.dispatchEvent(new Event('pointerdown')) // "Seguir trabajar" / interacción (listeners en document, captura)
    expect(warningSeconds.value).toBeNull()

    for (let i = 0; i < 5; i++) { // 5 min más con actividad cada minuto
      vi.advanceTimersByTime(60 * 1000)
      document.dispatchEvent(new Event('keydown'))
    }
    expect(warningSeconds.value).toBeNull()
    expect(pushMock).not.toHaveBeenCalled()
    stop()
  })

  it('a los 60 min: preserva borrador + returnTo y delega el logout al host', async () => {
    let expired = false
    const { start, stop } = useIdleLogout(async () => { expired = true })
    start()

    await vi.advanceTimersByTimeAsync(60 * 60 * 1000 + 500)

    expect(expired).toBe(true)
    expect(loadDraft().route).toBe('/receipts?mawbId=123')
    expect(loadDraft().forms.length).toBeGreaterThan(0)
    expect(loadDraft().forms[0].v).toBe('dato importante sin guardar')
    expect(popReturnTo()).toBe('/receipts?mawbId=123')
    // el composable NO navega: `onExpire` (App.vue) ya hace logout + push a
    // /login?idle=1. Navegar aquí además causaba doble logout y doble redirect
    // en cada expiración por inactividad.
    expect(pushMock).not.toHaveBeenCalled()
    stop()
  })

  it('el logout manual no duplica el trabajo con doble expiración', async () => {
    let calls = 0
    const { start, stop, expire } = useIdleLogout(async () => { calls++ })
    start()

    await Promise.all([expire(), expire()])
    expect(calls).toBe(1)
    stop()
  })
})
