import { describe, it, expect } from 'vitest'
import {
  DEFAULT_COL_WIDTHS, colStyle, clampColWidth, stickyOffsetsFor,
  indexMatrixData, selectFlightScope, buildMatrixRows,
  getPieces, cellClasses, arcOffsetFor, ARC_CIRCUM,
  mawbStatusClass, mawbStatusChalkClass, mawbStatusClassRaw,
  MATRIX_COLUMNS, normalizeHidden, visibleMatrixColumns, frozenEndKey, matrixMinWidth,
} from '@/utils/mawbMatrix'
import { readFileSync } from 'node:fs'

/* ── fixtures ────────────────────────────────────────────────── */
const ulds = [
  { id: 'u1', flightId: 'f1', uldNumber: 'ULDAAA1111' },
  { id: 'u2', flightId: 'f1', uldNumber: 'AAABBB2222' },
  { id: 'u3', flightId: 'f2', uldNumber: 'AADDCC3333' },
  { id: 'u4', flightId: null, uldNumber: 'AADEEE4444' }, // not assigned to a flight
]
const links = [
  { uldId: 'u1', mawbId: 'm1', pieces: 3 },
  { uldId: 'u2', mawbId: 'm1', pieces: 4 },
  { uldId: 'u3', mawbId: 'm1', pieces: 2 },
  { uldId: 'u2', mawbId: 'm2', pieces: 5 },
  { uldId: 'u4', mawbId: 'm3', pieces: 9 }, // orphan link: no flightId → ignored
  { uldId: 'u1', mawbId: null, pieces: 7 }, // no mawbId → ignored
]
const flights = [
  { id: 'f1', flightDate: '2026-09-10', flightNumber: '0401' },
  { id: 'f2', flightDate: '2026-09-11', flightNumber: '0402' },
  { id: 'f9', flightDate: '2026-09-12', flightNumber: '0409' }, // no ULDs → no column
]
const mawbs = [
  { id: 'm1', awbNumber: '406-1', pieces: 10, status: 'RECEIVED', reportedWeightKg: 100, shipperName: 'S1', consigneeName: 'C1', destination: 'MIA', commodityType: 'DRY_CARGO' },
  { id: 'm2', awbNumber: '406-2', pieces: 5, status: 'BOOKED' },
  { id: 'm3', awbNumber: '406-3', pieces: 1, status: 'DEPARTED' },
]

const base = () => indexMatrixData({ ulds, links, receipts: [], bookings: [] })

describe('colStyle', () => {
  it('uses the default width for a frozen column', () => {
    expect(colStyle(0)).toEqual({ width: '180px', minWidth: '180px' })
    expect(colStyle(2)).toEqual({ width: '90px', minWidth: '90px' })
  })

  it('prefers a user override over the default', () => {
    expect(colStyle(0, { 0: 420 })).toEqual({ width: '420px', minWidth: '420px' })
  })

  it('falls back to 100px for flight columns past the frozen set', () => {
    expect(colStyle(7)).toEqual({ width: '100px', minWidth: '100px' })
    expect(colStyle(42, { 7: 300 })).toEqual({ width: '100px', minWidth: '100px' })
  })

  it('always pins width and minWidth together (no shrinkable columns)', () => {
    for (const i of [0, 3, 6, 7, 11]) {
      const s = colStyle(i, { 0: 55, 3: 61 })
      expect(s.width).toBe(s.minWidth)
    }
  })

  it('clamps a dragged width at 50px', () => {
    expect(clampColWidth(10)).toBe(50)
    expect(clampColWidth(240)).toBe(240)
  })

  it('computes 8 sticky offsets from the frozen columns', () => {
    const off = stickyOffsetsFor()
    expect(off).toHaveLength(8)
    expect(off[0]).toBe(0)
    // 180 + 200 + 90 + 90 + 90 + 90 + 80
    expect(off[7]).toBe(180 + 200 + 90 + 90 + 90 + 90 + 80)
  })

  it('recomputes sticky offsets after a resize', () => {
    const off = stickyOffsetsFor({ 0: 300 })
    expect(off[1]).toBe(300)
    expect(off[2]).toBe(300 + 200)
  })
})

describe('indexMatrixData', () => {
  it('maps each ULD to its flight and label', () => {
    const idx = base()
    expect(idx.uldFlightMap).toEqual({ u1: 'f1', u2: 'f1', u3: 'f2', u4: null })
    expect(idx.uldNumberMap.u1).toBe('ULDAAA1111')
  })

  it('synthesises a ULD label from the id when the number is missing', () => {
    const idx = indexMatrixData({ ulds: [{ id: 'abcdef123456', flightId: 'f1' }], links: [] })
    expect(idx.uldNumberMap.abcdef123456).toBe('abcdef12')
  })

  it('sums pieces per (mawb, flight) across ULDs', () => {
    const { mawbPcsByFlight } = base()
    expect(mawbPcsByFlight.get('m1').get('f1')).toBe(7) // 3 + 4
    expect(mawbPcsByFlight.get('m1').get('f2')).toBe(2)
    expect(mawbPcsByFlight.get('m2').get('f1')).toBe(5)
  })

  it('ignores links without a mawbId or without a resolvable flight', () => {
    const { mawbPcsByFlight, flightIds } = base()
    expect(mawbPcsByFlight.has(null)).toBe(false)
    expect(mawbPcsByFlight.get('m3')).toBeUndefined() // u4 has flightId null
    expect(flightIds.has(null)).toBe(false)
  })

  it('sums receipt pieceCount per MAWB', () => {
    const idx = indexMatrixData({
      receipts: [{ mawbId: 'm1', pieceCount: 4 }, { mawbId: 'm1', pieceCount: 6 }, { mawbId: 'm2', pieceCount: 1 }, { pieceCount: 99 }],
      bookings: [],
    })
    expect(idx.receivedByMawb).toEqual({ m1: 10, m2: 1 })
  })

  it('groups bookings per MAWB and skips unlinked ones', () => {
    const idx = indexMatrixData({ bookings: [{ mawbId: 'm1', skids: 5 }, { mawbId: 'm1', skids: 2 }, { skids: 1 }] })
    expect(idx.bookingByMawb.m1).toHaveLength(2)
  })

  it('collects the ULD breakdown per (mawb, flight)', () => {
    const { mawbUldsByFlight } = base()
    expect(mawbUldsByFlight.get('m1').get('f1')).toEqual([
      { uldNumber: 'ULDAAA1111', pieces: 3 },
      { uldNumber: 'AAABBB2222', pieces: 4 },
    ])
  })

  it('tolerates being called with no arguments', () => {
    const idx = indexMatrixData()
    expect(idx.flightIds.size).toBe(0)
    expect(idx.receivedByMawb).toEqual({})
  })
})

describe('selectFlightScope', () => {
  it('keeps only flights that have at least one dispatched link', () => {
    const idx = base()
    const { flights: cols } = selectFlightScope({ mawbs, flights, ...idx })
    expect(cols.map(f => f.id)).toEqual(['f1', 'f2']) // f9 dropped
  })

  it('keeps every MAWB when no flight is selected', () => {
    const idx = base()
    const out = selectFlightScope({ mawbs, flights, ...idx })
    expect(out.mawbs).toHaveLength(3)
  })

  it('scopes rows to the selected flight (nested + flat id both match)', () => {
    const idx = base()
    const scoped = [
      { id: 'm1', flightId: 'f1' },
      { id: 'm2', flight: { id: 'f2' } },
      { id: 'm3', flightId: 'f1' },
    ]
    const { mawbs: rows } = selectFlightScope({ mawbs: scoped, flights, ...idx, flightIdFilter: 'f1' })
    expect(rows.map(m => m.id)).toEqual(['m1', 'm3'])
  })

  it('keeps the selected flight column plus extra flights reached via ULDs', () => {
    const idx = base()
    const scoped = [{ id: 'm1', flightId: 'f1' }] // m1 is dispatched to f1 AND f2
    const { flights: cols } = selectFlightScope({ mawbs: scoped, flights, ...idx, flightIdFilter: 'f1' })
    expect(cols.map(f => f.id)).toEqual(['f1', 'f2'])
  })

  it('does not mutate the caller flightIds Set', () => {
    const idx = base()
    const before = [...idx.flightIds].sort()
    selectFlightScope({ mawbs: [{ id: 'm1', flightId: 'f1' }], flights, ...idx, flightIdFilter: 'f1' })
    expect([...idx.flightIds].sort()).toEqual(before)
  })
})

describe('buildMatrixRows', () => {
  it('projects one row per MAWB with per-flight cells', () => {
    const idx = base()
    const { flights: cols } = selectFlightScope({ mawbs, flights, ...idx })
    const rows = buildMatrixRows({ mawbs, flights: cols, ...idx })
    expect(rows).toHaveLength(3)
    expect(rows[0].cells).toEqual({ f1: 7, f2: 2 })
    expect(rows[0].pcsDispatched).toBe(9)
  })

  it('counts distinct ULDs per flight', () => {
    const idx = base()
    const { flights: cols } = selectFlightScope({ mawbs, flights, ...idx })
    const rows = buildMatrixRows({ mawbs, flights: cols, ...idx })
    expect(rows[0].uldCountByFlight).toEqual({ f1: 2, f2: 1 })
    expect(rows[0].breakdownByFlight.f1).toHaveLength(2)
  })

  it('uses the MAX skids across bookings as reserved pieces', () => {
    const idx = indexMatrixData({ bookings: [{ mawbId: 'm1', skids: 5 }, { mawbId: 'm1', skids: 12 }, { mawbId: 'm1', skids: 8 }] })
    const rows = buildMatrixRows({ mawbs: [mawbs[0]], flights: [], ...idx })
    expect(rows[0].reservedPieces).toBe(12)
  })

  it('falls back to mawb.pieces when there is no booking', () => {
    const idx = base()
    const rows = buildMatrixRows({ mawbs: [mawbs[0]], flights: [], ...idx })
    expect(rows[0].reservedPieces).toBe(10)
  })

  it('computes pieceDiff and flags dispatched excess', () => {
    // 9 dispatched across f1+f2 but only 5 received
    const idx = indexMatrixData({ ulds, links, receipts: [{ mawbId: 'm1', pieceCount: 5 }] })
    const { flights: cols } = selectFlightScope({ mawbs, flights, ...idx })
    const rows = buildMatrixRows({ mawbs, flights: cols, ...idx })
    expect(rows[0].receivedPieces).toBe(5)
    expect(rows[0].pcsDispatched).toBe(9)
    expect(rows[0].pieceDiff).toBe(5 - 10)
    expect(rows[0].hasDispatchedExcess).toBe(true)
  })

  it('never flags excess when nothing has been received', () => {
    const rows = buildMatrixRows({ mawbs: [mawbs[0]], flights: [], ...base() })
    expect(rows[0].receivedPieces).toBe(0)
    expect(rows[0].hasDispatchedExcess).toBe(false)
  })

  it('prefers received > dispatched > declared for totalPieces', () => {
    const withReceipt = buildMatrixRows({
      mawbs: [mawbs[0]], flights: [],
      ...indexMatrixData({ receipts: [{ mawbId: 'm1', pieceCount: 77 }] }),
    })
    expect(withReceipt[0].totalPieces).toBe(77)

    const dispatched = buildMatrixRows({ mawbs: [mawbs[0]], flights: [], ...base() })
    expect(dispatched[0].totalPieces).toBe(10) // falls through to mawb.pieces
  })

  it('converts kg to lbs and prefers physical weight over chargeable', () => {
    const rows = buildMatrixRows({ mawbs: [mawbs[0]], flights: [], ...base() })
    expect(rows[0].totalWeightKg).toBe(100)
    expect(rows[0].physicalWeightLbs).toBeCloseTo(220.462, 2)

    const chargeableOnly = buildMatrixRows({
      mawbs: [{ id: 'z', chargeableWeightKg: 50 }], flights: [], ...base(),
    })
    expect(chargeableOnly[0].physicalWeightLbs).toBeCloseTo(110.231, 2)

    const none = buildMatrixRows({ mawbs: [{ id: 'z' }], flights: [], ...base() })
    expect(none[0].physicalWeightLbs).toBeNull()
  })

  it('emits a zero cell for flights the MAWB was not dispatched on', () => {
    const rows = buildMatrixRows({ mawbs: [mawbs[1]], flights, ...base() })
    expect(rows[0].cells).toEqual({ f1: 5, f2: 0, f9: 0 })
  })

  it('returns an empty array for no MAWBs', () => {
    expect(buildMatrixRows()).toEqual([])
  })
})

describe('presentation helpers', () => {
  it('getPieces reads a cell, defaulting to 0', () => {
    const row = { cells: { f1: 4 } }
    expect(getPieces(row, { id: 'f1' })).toBe(4)
    expect(getPieces(row, { id: 'f2' })).toBe(0)
    expect(getPieces({}, { id: 'f1' })).toBe(0)
  })

  it('cellClasses returns empty for an empty cell', () => {
    expect(cellClasses(0)).toBe('')
  })

  it('cellClasses highlights only the hovered flight column', () => {
    const hot = cellClasses(3, 'f2', 'f2')
    expect(hot).toContain('ring-1')
    const cold = cellClasses(3, 'f2', 'f1')
    expect(cold).not.toContain('ring-1')
    expect(cellClasses(3, null, 'f1')).not.toContain('ring-1')
  })

  it('arcOffset is full circle at zero and empty at total', () => {
    expect(arcOffsetFor(0, 10)).toBeCloseTo(ARC_CIRCUM, 5)
    expect(arcOffsetFor(10, 10)).toBeCloseTo(0, 5)
  })

  it('arcOffset never goes negative on over-shipment', () => {
    expect(arcOffsetFor(25, 10)).toBeCloseTo(0, 5)
  })

  it('arcOffset tolerates a missing total', () => {
    expect(Number.isFinite(arcOffsetFor(3, 0))).toBe(true)
  })

  it('status dot classes follow the canonical MAWB palette', () => {
    expect(mawbStatusClassRaw('BOOKED')).toBe('bg-slate-400')
    expect(mawbStatusClassRaw('RECEIVED')).toBe('bg-amber-400')
    expect(mawbStatusClassRaw('MANIFESTED')).toBe('bg-emerald-500')
    expect(mawbStatusClassRaw('DEPARTED')).toBe('bg-blue-500')
    expect(mawbStatusClassRaw('ARRIVED')).toBe('bg-blue-500')
    expect(mawbStatusClassRaw(null)).toBe('bg-slate-400')
    expect(mawbStatusClassRaw('WAT')).toBe('bg-slate-300')
  })

  it('every MAWB state is visually distinct from the others', () => {
    /* Regresión: la paleta era toda gris pizarra y BOOKED y MANIFESTED
     * compartían `border-l-slate-400`, así que el operador no podía
     * distinguirlos al vistazo. Cada estado debe tener color propio. */
    const barOf = (status) => mawbStatusClass({ status }).split(/\s+/)
      .filter((c) => c.startsWith('border-l-') && c !== 'border-l-4')
      .join('')
    const states = ['BOOKED', 'RECEIVED', 'MANIFESTED', 'DEPARTED']
    const bars = states.map(barOf)
    expect(bars.every(Boolean)).toBe(true)
    expect(new Set(bars).size).toBe(states.length)
  })

  it('excess always wins over the status colouring', () => {
    expect(mawbStatusClass({ hasDispatchedExcess: true, status: 'DEPARTED' }))
      .toContain('border-l-rose-500')
    expect(mawbStatusClass({ hasDispatchedExcess: false, status: 'DEPARTED' }))
      .toContain('border-l-blue-500')
  })

  it('unknown status falls back to a neutral cell', () => {
    expect(mawbStatusClass({ status: 'WAT' })).toBe('bg-white hover:bg-slate-50')
  })

  /* ── Variante pizarra (matriz) ────────────────────────────── */
  it('chalk status cell keeps every state distinct on the dark board', () => {
    const barOf = (status) => mawbStatusChalkClass({ status }).split(/\s+/)
      .filter((c) => c.startsWith('border-l-') && c !== 'border-l-4')
      .join('')
    const states = ['BOOKED', 'RECEIVED', 'MANIFESTED', 'DEPARTED']
    const bars = states.map(barOf)
    expect(bars.every(Boolean)).toBe(true)
    expect(new Set(bars).size).toBe(states.length)
  })

  it('chalk status cell paints ink and bar, never a background', () => {
    /* Regresión: la variante clara devolvía `bg-slate-50 text-slate-600`, que
     * sobre el verde de la pizarra quedaba ilegible y además anulada por
     * `.chalk td`. Aquí el fondo es de la pizarra, la clase solo fija tinta y
     * barra (y sin `bg-*` para no competir con el `!important` de `.fz`). */
    const cls = mawbStatusChalkClass({ status: 'RECEIVED' })
    expect(cls).toContain('text-amber-300')
    expect(cls).toContain('border-l-amber-400')
    expect(cls).not.toMatch(/\bbg-/)
  })

  it('chalk excess still wins over the status colour', () => {
    expect(mawbStatusChalkClass({ hasDispatchedExcess: true, status: 'DEPARTED' }))
      .toContain('border-l-rose-400')
    expect(mawbStatusChalkClass({ hasDispatchedExcess: false, status: 'DEPARTED' }))
      .toContain('border-l-sky-400')
  })

  it('chalk unknown status is neutral but not empty', () => {
    expect(mawbStatusChalkClass({ status: 'WAT' })).toBe('text-slate-300')
  })
})

/* ── columnas configurables ─────────────────────────────────────
 * La matriz es muy densa: en pantallas angostas las columnas de peso y
 * piezas son las primeras que estorban. Se pueden ocultar, pero el MAWB es
 * la identidad de la fila y no se oculta nunca. Los offsets sticky siguen
 * indexándose por columna LÓGICA: si `kg` es el índice 4, `stickyOffsets[4]`
 * es `kg` aunque haya columnas ocultas delante. */
describe('columnas configurables de la matriz', () => {
  const view = () => readFileSync('src/views/MawbsView.vue', 'utf8')
  const keys = () => MATRIX_COLUMNS.map(c => c.key)

  it('el catálogo cubre las 7 columnas fijas en orden estable', () => {
    expect(keys()).toEqual(['mawb', 'parties', 'reserved', 'received', 'kg', 'lbs', 'dispatched'])
    for (const c of MATRIX_COLUMNS) {
      expect(c.key, c.key).toMatch(/^[a-z]+$/)
      expect(c.labelKey, c.key).toMatch(/^mawbs\.columns\.[A-Za-z]+$/)
    }
  })

  it('solo MAWB es obligatoria y congelada; las demas se pueden ocultar', () => {
    expect(MATRIX_COLUMNS.filter(c => c.required).map(c => c.key)).toEqual(['mawb'])
    expect(MATRIX_COLUMNS.filter(c => c.frozen).map(c => c.key)).toEqual(['mawb', 'parties'])
  })

  it('normalizeHidden descarta claves desconocidas y nunca oculta el MAWB', () => {
    expect([...normalizeHidden([])]).toEqual([])
    expect([...normalizeHidden(['kg', 'lbs'])]).toEqual(['kg', 'lbs'])
    expect([...normalizeHidden(['kg', 'inventada', 'mawb'])]).toEqual(['kg'])
    expect([...normalizeHidden(new Set(['dispatched']))]).toEqual(['dispatched'])
  })

  it('normalizeHidden tolera basura de localStorage sin lanzar', () => {
    expect([...normalizeHidden(null)]).toEqual([])
    expect([...normalizeHidden('')]).toEqual([])
    expect([...normalizeHidden('{no es json')]).toEqual([])
    expect([...normalizeHidden('[]')]).toEqual([])
    expect([...normalizeHidden('["kg"]')]).toEqual(['kg'])
  })

  it('visibleMatrixColumns conserva el orden del catalogo', () => {
    const vis = visibleMatrixColumns(new Set(['kg', 'parties'])).map(c => c.key)
    expect(vis).toEqual(['mawb', 'reserved', 'received', 'lbs', 'dispatched'])
  })

  it('visibleMatrixColumns con todo oculto deja al menos el MAWB', () => {
    const vis = visibleMatrixColumns(new Set(['parties', 'reserved', 'received', 'kg', 'lbs', 'dispatched']))
    expect(vis.map(c => c.key)).toEqual(['mawb'])
  })

  it('frozenEndKey es la ultima congelada visible', () => {
    expect(frozenEndKey(new Set())).toBe('parties')
    expect(frozenEndKey(new Set(['parties']))).toBe('mawb')
  })

  it('ocultar columnas mueve los offsets pero respeta el indice logico', () => {
    const todas = stickyOffsetsFor({}, DEFAULT_COL_WIDTHS, 7)
    const sinKg = stickyOffsetsFor({}, DEFAULT_COL_WIDTHS, 7, new Set(['kg']))
    const sinLbs = stickyOffsetsFor({}, DEFAULT_COL_WIDTHS, 7, new Set(['lbs']))

    expect(todas).toHaveLength(8)
    expect(sinKg).toHaveLength(8)
    // `dispatched` (6) arranca antes porque `kg` (4) ya no ocupa ancho
    expect(sinKg[6]).toBe(todas[6] - 90)
    expect(sinLbs[6]).toBe(todas[6] - 90)
    // lo anterior a la oculta no se mueve
    expect(sinKg[0]).toBe(todas[0])
    expect(sinKg[3]).toBe(todas[3])
    // el borde inicial de los vuelos tambien se adelanta
    expect(sinKg[7]).toBe(todas[7] - 90)
  })

  it('sin cambios de ancho, ocultar varias columnas suma sus anchos', () => {
    const todas = stickyOffsetsFor({}, DEFAULT_COL_WIDTHS, 7)
    const ocultas = stickyOffsetsFor({}, DEFAULT_COL_WIDTHS, 7, new Set(['reserved', 'received']))
    // reserved(90) + received(90) = 180
    expect(ocultas[7]).toBe(todas[7] - 180)
    expect(ocultas[2]).toBe(todas[2])
  })

  it('la vista oculta cada columna de forma pareja en th y td', () => {
    const v = view()
    for (const key of keys()) {
      const enTh = v.split(`v-if="colVisible('${key}')"`).length - 1
      expect(enTh, `${key} debe aparecer en un th`).toBeGreaterThanOrEqual(1)
    }
    // 7 th + 7 td = 14 usos exactos
    const total = (v.match(/v-if="colVisible\('/g) || []).length
    expect(total).toBe(14)
  })

  it('la vista guarda la preferencia y ofrece volver a mostrarlas', () => {
    const v = view()
    expect(v).toMatch(/localStorage\.setItem\(HIDDEN_COLS_KEY/)
    expect(v).toMatch(/function showAllCols\(\)/)
    // la lectura inicial pasa por normalizeHidden
    expect(v).toMatch(/ref\(normalizeHidden\(readHiddenCols\(\)\)\)/)
  })

  it('la vista calcula los offsets con las columnas ocultas', () => {
    const v = view()
    expect(v).toMatch(/stickyOffsetsFor\(colWidths, defaultColWidths, MATRIX_COLUMNS\.length, hiddenCols\.value\)/)
  })

  it('el ancho minimo reserva solo las columnas que se pintan', () => {
    // 7 fijas = 820; con 3 vuelos de 100 = 1120
    expect(matrixMinWidth([], {}, DEFAULT_COL_WIDTHS, 3)).toBe(1120)
    // kg (90) oculta -> 820 - 90 + 300
    expect(matrixMinWidth(['kg'], {}, DEFAULT_COL_WIDTHS, 3)).toBe(1030)
    // las seis conmutables ocultas -> solo el MAWB (180) + 300
    expect(matrixMinWidth(
      ['parties', 'reserved', 'received', 'kg', 'lbs', 'dispatched'],
      {}, DEFAULT_COL_WIDTHS, 3)).toBe(480)
  })

  it('el ancho minimo respeta las columnas redimensionadas', () => {
    expect(matrixMinWidth([], { 0: 300 }, DEFAULT_COL_WIDTHS, 0)).toBe(300 + 640)
  })

  it('el ancho minimo nunca queda por debajo del MAWB', () => {
    const w = matrixMinWidth(['parties', 'reserved', 'received', 'kg', 'lbs', 'dispatched'], {}, DEFAULT_COL_WIDTHS, 0)
    expect(w).toBe(180)
  })

  it('la vista no deja un min-width fijo que anule la preferencia', () => {
    const v = view()
    expect(v).not.toMatch(/style="min-width: 820px"/)
    expect(v).toMatch(/:style="\{ minWidth: matrixMinWidth \+ 'px' \}"/)
  })

  it('la sombra de scroll la lleva la ultima congelada, no una clase fija', () => {
    const v = view()
    expect(v).toMatch(/frozenEnd === 'mawb'/)
    expect(v).toMatch(/frozenEnd === 'parties'/)
  })
})
