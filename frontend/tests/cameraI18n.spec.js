import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import EvidenceCaptureModal from '@/components/EvidenceCaptureModal.vue'
import ScanPanel from '@/components/ScanPanel.vue'
import es from '@/i18n/es'
import en from '@/i18n/en'

/* ScanPanel resuelve el catalogo ULD al montarse (uldTypeCatalogApi.getAll en el
 * setup). Sin mock, happy-dom intenta conectar a su origen por defecto
 * (http://localhost:3000) y el run escupe "ECONNREFUSED" (el .catch(() => {})
 * del componente lo traga, por eso los tests igual pasan, pero ensucia la
 * salida). Lo mockeamos a una lista vacia para que el fallback legacy siga
 * cubierto por los tests. */
vi.mock('@/api/uldTypeCatalog', () => ({
  uldTypeCatalogApi: { getAll: vi.fn().mockResolvedValue({ data: [] }) },
}))

/* Regresion de los crashes que arranco `check-sfc-refs.mjs` a detectar:
 * ambos componentes usaban `t('common.close')` en el template sin declarar
 * `t`. El guard lo daba por bueno porque existia un `t => t.stop()` anidado
 * (param de arrow de un forEach), y en runtime Vue tiraba
 * `TypeError: _ctx.t is not a function`. Montar de verdad es la unica forma de
 * fijar el contrato template<->script.
 *
 * Nota: `EvidenceCaptureModal` sustituyo a `CameraCapture`; el contrato que
 * fija esta suite es el nuevo (3 vias: captura nativa, archivo, camara viva). */
const i18n = createI18n({
  legacy: false,
  locale: 'es',
  fallbackLocale: 'en',
  messages: { es, en },
})

function stubMediaDevices(stream) {
  Object.defineProperty(globalThis.navigator, 'mediaDevices', {
    configurable: true,
    writable: true,
    value: { getUserMedia: vi.fn().mockResolvedValue(stream) },
  })
}

const fakeStream = () => ({
  getTracks: () => [{ stop: vi.fn() }],
  getVideoTracks: () => [],
})

describe('EvidenceCaptureModal i18n', () => {
  beforeEach(() => stubMediaDevices(fakeStream()))
  afterEach(() => vi.restoreAllMocks())

  // El modal usa <Teleport to="body">, asi que su DOM vive en document.body
  // y no dentro del wrapper devuelto por mount().
  it('renderiza el modal sin ReferenceError de t', async () => {
    const w = mount(EvidenceCaptureModal, { props: { show: true }, global: { plugins: [i18n] } })
    await w.vm.$nextTick()
    expect(document.body.textContent).toContain('Capturar Foto')
    w.unmount()
  })

  it('el boton de cerrar tiene aria-label resuelto, no la clave en crudo', async () => {
    const w = mount(EvidenceCaptureModal, { props: { show: true }, global: { plugins: [i18n] } })
    await w.vm.$nextTick()
    const btn = document.body.querySelector('button[aria-label]')
    expect(btn).not.toBeNull()
    expect(btn.getAttribute('aria-label')).toBe('Cerrar')
    expect(document.body.textContent).not.toContain('common.close')
    w.unmount()
  })

  it('funciona tambien en ingles', async () => {
    i18n.global.locale.value = 'en'
    const w = mount(EvidenceCaptureModal, { props: { show: true }, global: { plugins: [i18n] } })
    await w.vm.$nextTick()
    expect(document.body.textContent).toContain('Capture Photo')
    w.unmount()
    i18n.global.locale.value = 'es'
  })

  it('ofrece siempre la via nativa "Tomar foto" aunque no haya camara en vivo', async () => {
    // Sin mediaDevices getUserMedia emula HTTP sin contexto seguro: la via
    // nativa (input[capture]) debe seguir presente y el aviso explicar el caso.
    Object.defineProperty(globalThis.navigator, 'mediaDevices', {
      configurable: true, writable: true, value: undefined,
    })
    const w = mount(EvidenceCaptureModal, { props: { show: true }, global: { plugins: [i18n] } })
    await w.vm.$nextTick()
    const body = document.body.textContent
    expect(body).toContain('Tomar foto')
    expect(body).toContain('Elegir imagen')
    w.unmount()
  })

  it('el overlay se monta con el z-index recibido (debe superar al modal huesped)', async () => {
    const w = mount(EvidenceCaptureModal, {
      props: { show: true, zIndex: 95 },
      global: { plugins: [i18n] },
    })
    await w.vm.$nextTick()
    const overlay = document.body.querySelector('.fixed.inset-0')
    expect(overlay).not.toBeNull()
    expect(overlay.style.zIndex).toBe('95')
    w.unmount()
  })
})

describe('ScanPanel i18n', () => {
  afterEach(() => vi.restoreAllMocks())

  it('ningun boton deja una clave i18n sin resolver', async () => {
    const w = mount(ScanPanel, {
      props: { active: false, uldNumber: 'PMC12345UP' },
      global: { plugins: [i18n] },
      stubs: { ScanPanel: false },
    })
    await w.vm.$nextTick()
    const close = w.findAll('button').find((b) => b.attributes('aria-label'))
    // Ningun boton puede quedar con la clave i18n literal sin resolver.
    for (const b of w.findAll('button')) {
      const l = b.attributes('aria-label')
      if (l) expect(l).not.toMatch(/^[a-z]+\.[A-Za-z.]+$/)
    }
    expect(close === undefined || close.attributes('aria-label') === 'Cerrar').toBe(true)
    w.unmount()
  })
})
