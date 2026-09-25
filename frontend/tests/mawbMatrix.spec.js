import { describe, it, expect } from 'vitest'
import {
  DEFAULT_COL_WIDTHS, colStyle, clampColWidth, stickyOffsetsFor,
  indexMatrixData, selectFlightScope, buildMatrixRows,
  getPieces, cellClasses, arcOffsetFor, ARC_CIRCUM,
  mawbStatusClass, mawbStatusClassRaw,
} from '@/utils/mawbMatrix'

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

  it('status dot classes map the four MAWB states', () => {
    expect(mawbStatusClassRaw('BOOKED')).toBe('bg-slate-500')
    expect(mawbStatusClassRaw('RECEIVED')).toBe('bg-slate-600')
    expect(mawbStatusClassRaw('DEPARTED')).toBe('bg-slate-700')
    expect(mawbStatusClassRaw(null)).toBe('bg-slate-500')
    expect(mawbStatusClassRaw('WAT')).toBe('bg-slate-400')
  })

  it('excess always wins over the status colouring', () => {
    expect(mawbStatusClass({ hasDispatchedExcess: true, status: 'DEPARTED' }))
      .toContain('border-l-slate-500')
    expect(mawbStatusClass({ hasDispatchedExcess: false, status: 'DEPARTED' }))
      .toContain('border-l-slate-600')
  })

  it('unknown status falls back to a neutral cell', () => {
    expect(mawbStatusClass({ status: 'WAT' })).toBe('bg-white hover:bg-slate-50')
  })
})
