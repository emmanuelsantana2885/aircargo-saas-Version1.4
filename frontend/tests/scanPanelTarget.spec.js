import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ScanPanel from '@/components/ScanPanel.vue'
import { scanApi } from '@/api/scan.js'
import es from '@/i18n/es'
import en from '@/i18n/en'

/* El setup del componente resuelve el catálogo ULD al montarse; sin mock,
 * happy-dom intenta una conexión real y ensucia la salida (lo mismo que hace
 * cameraI18n.spec.js). Lista vacía => fallback legacy PMC/PAH/... */
vi.mock('@/api/uldTypeCatalog', () => ({
  uldTypeCatalogApi: { getAll: vi.fn().mockResolvedValue({ data: [] }) },
}))

vi.mock('@/api/scan.js', () => ({
  scanApi: {
    lookup: vi.fn(),
    piece: vi.fn(),
    undoLast: vi.fn(),
  },
}))

const i18n = createI18n({
  legacy: false,
  locale: 'es',
  fallbackLocale: 'en',
  messages: { es, en },
})

function mountPanel(props = {}) {
  return mount(ScanPanel, {
    props: { active: true, uldNumber: 'NUEVO', ...props },
    global: { plugins: [i18n] },
  })
}

async function typeAndEnter(w, code) {
  const input = w.find('input')
  await input.setValue(code)
  await input.trigger('keydown', { key: 'Enter' })
  await flushPromises()
}

function buttonByText(w, text) {
  return w.findAll('button').find((b) => b.text().includes(text))
}

describe('ScanPanel — selector de destino', () => {
  afterEach(() => vi.clearAllMocks())

  it('en modo sello el código se emite como seal-scanned y NO registra pieza', async () => {
    const w = mountPanel()
    await buttonByText(w, 'Sello').trigger('click')

    await typeAndEnter(w, 'sc-1234abcd')

    expect(w.emitted('seal-scanned')).toEqual([['SC-1234ABCD']])
    expect(scanApi.piece).not.toHaveBeenCalled()
    expect(w.emitted('uld-number-scanned')).toBeUndefined()
    expect(w.text()).toContain('Sello SC-1234ABCD registrado')
    w.unmount()
  })

  it('el placeholder cambia según el destino seleccionado', async () => {
    // Sin número de ULD ('' es el contrato desde UldsView) → primer paso de la secuencia
    const w = mountPanel({ uldNumber: '' })
    // Híbrido: por defecto Auto con secuencia guiada → primer paso ULD
    expect(w.find('input').attributes('placeholder')).toBe(i18n.global.t('scan.placeholderUld'))

    await buttonByText(w, 'Sello').trigger('click')
    expect(w.find('input').attributes('placeholder')).toBe(i18n.global.t('scan.placeholderSeal'))

    await buttonByText(w, 'Pieza').trigger('click')
    expect(w.find('input').attributes('placeholder')).toBe(i18n.global.t('scan.inputPlaceholder'))
    w.unmount()
  })

  it('tras un scan de sello el botón deshabilita deshacer (no es pieza)', async () => {
    const w = mountPanel()
    await buttonByText(w, 'Sello').trigger('click')
    await typeAndEnter(w, 'SC-9999')

    const undo = buttonByText(w, 'Deshacer')
    expect(undo.attributes('disabled')).toBeDefined()
    w.unmount()
  })
})

describe('ScanPanel — autodetección de códigos ULD', () => {
  afterEach(() => vi.clearAllMocks())

  it('un código ULD en modo pieza emite uld-number-scanned aunque no haya ULD guardado', async () => {
    const w = mountPanel({ uldId: '', uldNumber: 'NUEVO' })

    await typeAndEnter(w, 'pmc-12345')

    expect(w.emitted('uld-number-scanned')).toEqual([['PMC-12345']])
    expect(scanApi.piece).not.toHaveBeenCalled()
    expect(w.text()).toContain('ULD PMC-12345 asignado')
    w.unmount()
  })

  it('si el código coincide con el ULD actual no re-emite (evita re-guardar)', async () => {
    const w = mountPanel({ uldId: 'u1', uldNumber: 'PMC12345UP' })

    await typeAndEnter(w, 'PMC-12345UP')

    expect(w.emitted('uld-number-scanned')).toBeUndefined()
    expect(w.text()).toContain('ya estaba asignado')
    expect(scanApi.piece).not.toHaveBeenCalled()
    w.unmount()
  })
})

describe('ScanPanel — modo Auto (secuencia guiada ULD → Sello → MAWB)', () => {
  afterEach(() => vi.clearAllMocks())

  it('tras escanear un ULD avanza el foco al sello (scan-focus: seal)', async () => {
    const w = mountPanel({ uldId: '', uldNumber: '' })

    await typeAndEnter(w, 'pmc-12345')

    expect(w.emitted('uld-number-scanned')).toEqual([['PMC-12345']])
    const focuses = (w.emitted('scan-focus') || []).map(e => e[0])
    expect(focuses).toContain('seal')
    w.unmount()
  })

  it('en Auto un código SC-… se detecta como sello sin tocar el selector', async () => {
    const w = mountPanel({ uldId: 'u1', uldNumber: 'PMC12345UP' })

    await typeAndEnter(w, 'sc-1234abcd')

    expect(w.emitted('seal-scanned')).toEqual([['SC-1234ABCD']])
    expect(scanApi.piece).not.toHaveBeenCalled()
    const focuses = (w.emitted('scan-focus') || []).map(e => e[0])
    expect(focuses).toContain('mawb')
    w.unmount()
  })

  it('un código no reconocido emite unknown-code, conserva el input y marca el selector', async () => {
    const w = mountPanel({ uldId: 'u1', uldNumber: 'PMC12345UP' })

    await typeAndEnter(w, '!!??')

    expect(w.emitted('unknown-code')).toEqual([['!!??']])
    expect(scanApi.piece).not.toHaveBeenCalled()
    expect(w.find('input').element.value).toBe('!!??')
    expect(w.text()).toContain(i18n.global.t('scan.message.unknown'))
    w.unmount()
  })

  it('el input bloquea el teclado virtual (inputmode=none) en modo scan', async () => {
    const w = mountPanel()
    expect(w.find('input').attributes('inputmode')).toBe('none')
    w.unmount()
  })
})

describe('ScanPanel — barra de estado y stepper (mockup)', () => {
  afterEach(() => vi.clearAllMocks())

  function pillByText(w, text) {
    return w.findAll('span').find(s => s.text().includes(text) && s.classes().includes('rounded-full'))
  }

  it('muestra el título contextual y marca el paso ULD como activo (azul)', () => {
    const w = mountPanel({ uldNumber: '', sealNumber: '' })
    expect(w.text()).toContain(i18n.global.t('scan.banner.uld'))
    const pill = pillByText(w, '1 · ULD')
    expect(pill).toBeTruthy()
    expect(pill.classes()).toContain('bg-blue-600')
    w.unmount()
  })

  it('tras escanear un ULD: paso 1 completado (verde) y banner en Sello', async () => {
    const w = mountPanel({ uldId: '', uldNumber: '', sealNumber: '' })

    await typeAndEnter(w, 'pmc-12345')
    // el padre (UldsView) persiste el número escaneado y lo refleja en la prop
    await w.setProps({ uldNumber: 'PMC-12345' })

    expect(w.text()).toContain(i18n.global.t('scan.banner.seal'))
    const pill = pillByText(w, '1 · ULD')
    expect(pill.classes()).toContain('bg-emerald-50')
    w.unmount()
  })

  it('con sello ya presente el stepper arranca en el paso 2', () => {
    const w = mountPanel({ uldNumber: 'PMC12345UP', sealNumber: 'SC-1234' })
    expect(w.text()).toContain(i18n.global.t('scan.banner.seal'))
    const sealPill = pillByText(w, '2 · Sello')
    expect(sealPill.classes()).toContain('bg-blue-600')
    w.unmount()
  })

  it('muestra el contador x/y de piezas tras registrar una pieza', async () => {
    scanApi.piece.mockResolvedValue({
      data: { success: true, pieceNumber: 1, awbNumber: '406-05399391', mawbId: 'm1', totalOnUld: 3, availablePieces: 9, warning: null },
    })
    const w = mountPanel({ uldId: 'u1', uldNumber: 'PMC12345UP', sealNumber: 'SC-1234' })

    await typeAndEnter(w, '406-05399391')

    expect(w.text()).toContain('3/12 pcs')
    w.unmount()
  })
})

describe('ScanPanel — registro de piezas', () => {
  afterEach(() => vi.clearAllMocks())

  it('sin ULD guardado un MAWB no llama al backend y muestra el aviso', async () => {
    const w = mountPanel({ uldId: '', uldNumber: 'NUEVO' })

    await typeAndEnter(w, '406-05399391')

    expect(scanApi.piece).not.toHaveBeenCalled()
    expect(w.text()).toContain(i18n.global.t('scan.message.uldFirst'))
    w.unmount()
  })

  it('con ULD guardado registra la pieza, emite piece-added y habilita deshacer', async () => {
    scanApi.piece.mockResolvedValue({
      data: {
        success: true,
        pieceNumber: 1,
        awbNumber: '406-05399391',
        mawbId: 'm1',
        totalOnUld: 1,
        availablePieces: 4,
        warning: null,
      },
    })
    const w = mountPanel({ uldId: 'u1', uldNumber: 'PMC12345UP' })

    await typeAndEnter(w, '406-05399391')

    expect(scanApi.piece).toHaveBeenCalledWith({
      uldId: 'u1',
      awbNumber: '406-05399391',
      source: 'BARCODE',
    })
    expect(w.emitted('piece-added')).toHaveLength(1)
    const undo = buttonByText(w, 'Deshacer')
    expect(undo.attributes('disabled')).toBeUndefined()
    w.unmount()
  })

  it('regresión: Enter desde el teclado (evento, no string) usa el valor del input', async () => {
    // processScan recibía el KeyboardEvent como codeOverride y serializaba {}
    // al backend. Ahora solo acepta strings explícitos y cae al input.
    scanApi.piece.mockResolvedValue({
      data: {
        success: true,
        pieceNumber: 2,
        awbNumber: '406-05399391',
        mawbId: 'm1',
        totalOnUld: 2,
        availablePieces: 3,
        warning: null,
      },
    })
    const w = mountPanel({ uldId: 'u1', uldNumber: 'PMC12345UP' })

    await typeAndEnter(w, '406-05399391')

    expect(scanApi.piece).toHaveBeenCalledTimes(1)
    expect(scanApi.piece.mock.calls[0][0].awbNumber).toBe('406-05399391')
    w.unmount()
  })
})
