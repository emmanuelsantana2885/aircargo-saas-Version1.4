import { describe, it, expect, beforeEach, vi } from 'vitest'

// El composable mantiene un caché a NIVEL DE MÓDULO (`loaded`), por lo que cada
// test parte de un estado limpio invalidándolo explícitamente.
const getAll = vi.fn()

vi.mock('@/api/commodityTypes', () => ({
  commodityTypesApi: {
    getAll: (...args) => getAll(...args),
  },
}))

import { useCommodities } from '@/composables/useCommodities'

const CATALOG = [
  { code: 'DRY_CARGO', label: 'Dry Cargo', description: 'General non-hazardous goods', color: '#2563eb', isActive: true },
  { code: 'FCC', label: 'Full Container', description: 'Full Container Load (FCL)', color: '#7c3aed', isActive: true },
  { code: 'PERISHABLE', label: 'Perishable', description: '', color: '#16a34a', isActive: true },
]

describe('useCommodities — texto del catálogo', () => {
  beforeEach(() => {
    getAll.mockReset()
    getAll.mockResolvedValue({ data: CATALOG })
    useCommodities().invalidate()
  })

  it('carga solo los commodities activos y pide activeOnly=true', async () => {
    const { loadCommodities, commodities } = useCommodities()
    await loadCommodities(true)
    expect(getAll).toHaveBeenCalledWith(true)
    expect(commodities.value.map(c => c.code)).toEqual(['DRY_CARGO', 'FCC', 'PERISHABLE'])
  })

  it('labelOf devuelve el label del catálogo y cae al código si no existe', async () => {
    const { loadCommodities, labelOf } = useCommodities()
    await loadCommodities(true)
    expect(labelOf('FCC')).toBe('Full Container')
    expect(labelOf('NO_EXISTE')).toBe('NO_EXISTE')
    expect(labelOf(null)).toBe('')
  })

  it('descriptionOf expone la descripción editable', async () => {
    const { loadCommodities, descriptionOf } = useCommodities()
    await loadCommodities(true)
    expect(descriptionOf('FCC')).toBe('Full Container Load (FCL)')
    // Sin description en BD se devuelve cadena vacía, no el código.
    expect(descriptionOf('PERISHABLE')).toBe('')
  })

  it('fullLabelOf combina label y description para superficies sin tooltip', async () => {
    const { loadCommodities, fullLabelOf } = useCommodities()
    await loadCommodities(true)
    expect(fullLabelOf('FCC')).toBe('Full Container — Full Container Load (FCL)')
    // Sin description no duplica texto ni añade separador.
    expect(fullLabelOf('PERISHABLE')).toBe('Perishable')
  })

  it('tooltipOf prioriza la description y cae a label/código', async () => {
    const { loadCommodities, tooltipOf } = useCommodities()
    await loadCommodities(true)
    expect(tooltipOf('FCC')).toBe('Full Container Load (FCL)')
    expect(tooltipOf('PERISHABLE')).toBe('Perishable')
    expect(tooltipOf('NO_EXISTE')).toBe('NO_EXISTE')
  })

  it('resolveCommodity mantiene short/color para los headers densos', async () => {
    const { loadCommodities, resolveCommodity } = useCommodities()
    await loadCommodities(true)
    const c = resolveCommodity('DRY_CARGO')
    expect(c.short).toBe('DRY_')
    expect(c.color).toBe('#2563eb')
    expect(c.isLegacy).toBe(false)
  })

  it('marca como legacy los códigos que no están en el catálogo', async () => {
    const { loadCommodities, resolveCommodity } = useCommodities()
    await loadCommodities(true)
    expect(resolveCommodity('COMAT').isLegacy).toBe(true)
    expect(isKnownFalse('COMAT')).toBe(true)
  })

  it('isKnown distingue catálogo de valores arbitrarios', async () => {
    const { loadCommodities, isKnown } = useCommodities()
    await loadCommodities(true)
    expect(isKnown('FCC')).toBe(true)
    expect(isKnown('COMAT')).toBe(false)
  })

  it('invalidate() fuerza una recarga posterior del catálogo', async () => {
    const { loadCommodities, commodities, invalidate } = useCommodities()
    await loadCommodities(true)
    expect(commodities.value.find(c => c.code === 'FCC').label).toBe('Full Container')

    getAll.mockResolvedValue({ data: [{ code: 'FCC', label: 'RENAMED', description: 'nueva' }] })
    await loadCommodities()          // caché: no vuelve a pedir
    expect(commodities.value.find(c => c.code === 'FCC').label).toBe('Full Container')

    invalidate()
    await loadCommodities()          // tras invalidar sí recarga
    expect(commodities.value.find(c => c.code === 'FCC').label).toBe('RENAMED')
  })
})

// helper local para no arrastrar `isKnown` al test de legacy
function isKnownFalse(code) {
  return !useCommodities().isKnown(code)
}
