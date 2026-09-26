import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Regresión del bug reportado: editar `description` en Settings no se veía en el
 * dropdown de MAWB del formulario de ULDs (mostraba el código crudo) porque:
 *   1. el texto venía hardcodeado en `specialItems`, y
 *   2. la caché de módulo `useCommodities` no se invalidaba tras el CRUD.
 *
 * Estos tests fijan el CONTRATO de las superficies afectadas: el texto visible
 * es el `label` del catálogo y el `description` viaja en el `title` (tooltip).
 * Si alguien reintroduce el código crudo o el texto hardcodeado, fallan.
 * El panel de Settings está detrás de TOTP y los selects son nativos, así que
 * se verifica el wiring sobre el source en vez de montar componentes pesados.
 */

const src = p => readFileSync(resolve(process.cwd(), 'src', p), 'utf8')

const CATALOG = [
  { code: 'FCC', label: 'Full Container', description: 'Full Container Load (FCL)', color: '#7c3aed' },
  { code: 'EMPTY_ULD', label: 'Empty ULD', description: 'Empty unit load device', color: '#64748b' },
]

/* ── 1. Contrato del par (label, description) ──────────────────────────────── */

describe('texto de commodity: label visible + description en tooltip', () => {
  it('el texto visible es el label y el tooltip la description', () => {
    const c = CATALOG[0]
    expect(`FCC — ${c.label}`).toBe('FCC — Full Container')
    expect(`FCC — ${c.label}`).not.toContain('FCL)')
    expect(c.description).toBe('Full Container Load (FCL)')
  })
})

/* ── 2. Settings: toda mutación propaga al catálogo compartido ─────────────── */

describe('SettingsView — catálogo de commodities', () => {
  const source = src('views/SettingsView.vue')

  it('usa el composable compartido y lo invalida', () => {
    expect(source).toContain('invalidate: invalidateCommodityCache')
    expect(source).toContain('loadCommodities: refreshCommodityCache')
  })

  it('propaga tras las 4 mutaciones y en el montaje', () => {
    const calls = (source.match(/await refreshCommodityCatalog\(\)/g) || []).length
    expect(calls).toBeGreaterThanOrEqual(5) // edit + create + delete + restore + onMounted
  })

  it('conserva su propia carga con activeOnly=false (editar inactivos)', () => {
    expect(source).toContain('commodityTypesApi.getAll(false)')
  })
})

/* ── 3. ULDs: specialItems y dropdown sin texto hardcodeado ────────────────── */

describe('UldsView — specialItems y dropdown derivados del catálogo', () => {
  const source = src('views/UldsView.vue')

  it('deriva el texto descriptivo desde el catálogo', () => {
    expect(source).toContain('descriptionOf(it.commodityType) || labelOf(it.commodityType)')
  })

  it('elimina los textos de commodity hardcodeados', () => {
    expect(source).not.toContain("shipperName: 'Full Container Load'")
    expect(source).not.toContain("shipperName: 'Cargo Nets'")
    expect(source).not.toContain("shipperName: 'Empty ULD'")
    expect(source).not.toContain("shipperName: 'Empty Bags'")
  })

  it('mawbOptionLabel usa el label del catálogo, no el código crudo', () => {
    expect(source).toContain('labelOf(o.commodityType)')
    expect(source).not.toContain('[${o.commodityType}]')
  })

  it('expone la description como ayuda/tooltip bajo el select', () => {
    expect(source).toContain('commodityHintFor')
    expect(source).toContain('tooltipOf(mawb.commodityType)')
  })
})

/* ── 4. LoadPlanning: description resuelta contra el catálogo ──────────────── */

describe('LoadPlanningView — description del commodity', () => {
  const source = src('views/LoadPlanningView.vue')

  it('resuelve el label desde el catálogo con fallback al valor congelado', () => {
    expect(source).toContain('labelOf(m.commodityType) || m.description')
  })

  it('aplica tooltip en la tabla y label+description en el XLSX', () => {
    expect(source).toContain('tooltipOf(item.commodityCode)')
    expect(source).toContain('fullLabelOf(item.commodityCode)')
  })
})

/* ── 5. Resto de vistas: sin código crudo ni label duplicado a mano ────────── */

describe('resto de vistas — label con tooltip', () => {
  it('BookingsView muestra label en el select y en la vista previa', () => {
    const s = src('views/BookingsView.vue')
    expect(s).toContain('labelOf(c)')
    expect(s).toContain('labelOf(row.commodityType)')
    expect(s).toContain('tooltipOf(form.commodityType)')
  })

  it('DashboardView y MawbsView muestran label en el badge de commodity', () => {
    for (const f of ['views/DashboardView.vue', 'views/MawbsView.vue']) {
      const s = src(f)
      expect(s, f).toContain('labelOf(row.commodityType)')
      expect(s, f).toContain('tooltipOf(row.commodityType)')
      expect(s, f).not.toContain('{{ row.commodityType }}')
    }
  })

  it('FlightDetail usa la description en el tooltip del chip', () => {
    const s = src('components/FlightDetail.vue')
    expect(s).toContain('tooltipOf(m.commodityType)')
    expect(s).toContain('loadCommodities()')
  })
})
