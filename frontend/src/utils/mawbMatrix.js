/* ══════════════════════════════════════════════════════════════════
   Pure matrix logic for MawbsView (tab "matriz").

   Everything here is side-effect free: no store, no api, no DOM.
   MawbsView keeps the fetching and the reactive state; this module owns
   the indexing + row/column projection so it can be unit-tested.
   ══════════════════════════════════════════════════════════════════ */

import { mawbStatusDot, mawbStatusCell, mawbStatusChalkCell } from './mawbStatus'

export const DEFAULT_COL_WIDTHS = [180, 200, 90, 90, 90, 90, 80]
const FALLBACK_FLIGHT_COL_WIDTH = 100
const MIN_COL_WIDTH = 50
const KG_TO_LBS = 2.20462

/* ══════════════════════════════════════════════════════════════════
   Column visibility.
   The 7 fixed columns used to be hardcoded in the template, so hiding
   one of them had no safe implementation (the frozen left offsets and
   the frozen-edge shadow are both derived from the column list).
   Here the list is data, and the offsets/shadow follow it.
   ══════════════════════════════════════════════════════════════════ */

/** The fixed columns, in render order. `frozen` = sticks to the left edge. */
export const MATRIX_COLUMNS = [
  { key: 'mawb', labelKey: 'mawbs.columns.mawb', frozen: true, required: true },
  { key: 'parties', labelKey: 'mawbs.columns.shipperConsignee', frozen: true },
  { key: 'reserved', labelKey: 'mawbs.columns.pcsReserved', frozen: false },
  { key: 'received', labelKey: 'mawbs.columns.pcsReceived', frozen: false },
  { key: 'kg', labelKey: 'mawbs.columns.kg', frozen: false },
  { key: 'lbs', labelKey: 'mawbs.columns.lbs', frozen: false },
  { key: 'dispatched', labelKey: 'mawbs.columns.pcsDispatched', frozen: false },
]

const COLUMN_KEYS = new Set(MATRIX_COLUMNS.map(c => c.key))
const REQUIRED_KEYS = new Set(MATRIX_COLUMNS.filter(c => c.required).map(c => c.key))

/**
 * Accept whatever the caller has (Set, array, localStorage string) and
 * return a clean Set of *hideable* column keys. Unknown keys are dropped
 * and `required` columns are always kept: the MAWB is the row identity,
 * without it the matrix cannot be read at all.
 */
export function normalizeHidden(hidden) {
  let list = hidden
  if (typeof list === 'string') {
    try { list = JSON.parse(list) } catch { return new Set() }
  }
  if (list instanceof Set) list = [...list]
  if (!Array.isArray(list)) return new Set()
  return new Set(list.filter(k => COLUMN_KEYS.has(k) && !REQUIRED_KEYS.has(k)))
}

/** The fixed columns actually rendered, in order. */
export function visibleMatrixColumns(hidden) {
  const h = normalizeHidden(hidden)
  return MATRIX_COLUMNS.filter(c => !h.has(c.key))
}

/**
 * Key of the last visible frozen column: it carries the shadow that
 * separates the frozen block from the horizontally scrolling flight
 * columns. Falls back to the required column when the others are hidden.
 */
export function frozenEndKey(hidden) {
  const vis = visibleMatrixColumns(hidden).filter(c => c.frozen)
  return (vis[vis.length - 1] || MATRIX_COLUMNS[0]).key
}

/** Column sizing for the resizable matrix. Prefers the user override. */
export function colStyle(colIdx, colWidths = {}, defaults = DEFAULT_COL_WIDTHS) {
  const w = colWidths[colIdx]
  if (w) return { width: w + 'px', minWidth: w + 'px' }
  if (colIdx < defaults.length) {
    const d = defaults[colIdx]
    return { width: d + 'px', minWidth: d + 'px' }
  }
  return { width: FALLBACK_FLIGHT_COL_WIDTH + 'px', minWidth: FALLBACK_FLIGHT_COL_WIDTH + 'px' }
}

/** Clamp used while dragging a column border. */
export function clampColWidth(width) {
  return Math.max(MIN_COL_WIDTH, width)
}

/**
 * Sticky left offsets for the 7 fixed columns, recomputed after a resize.
 * Returns an 8-length array so colStyle(7 + fi) lines up with stickyOffsets[7].
 *
 * Hidden columns contribute no width, so hiding "Shipper / Consignee"
 * makes the next frozen column sit at 0 instead of 180px.
 * Only the `frozen` entries are actually painted (the rest are
 * position:static); the tail is kept cumulative for backward compatibility.
 */
export function stickyOffsetsFor(colWidths = {}, defaults = DEFAULT_COL_WIDTHS, frozen = 7, hidden) {
  const hiddenSet = normalizeHidden(hidden)
  // The result is indexed by ORIGINAL column index (the template looks up
  // stickyOffsets[2] for the `kg` column whatever is hidden), and each
  // entry is the width of the visible columns before it. Index 7 is the
  // left edge of the scrolling flight columns.
  const offsets = [0]
  let acc = 0
  for (let i = 0; i < frozen; i++) {
    const hiddenCol = hiddenSet.has(MATRIX_COLUMNS[i] && MATRIX_COLUMNS[i].key)
    if (!hiddenCol) acc += (colWidths[i] || defaults[i] || FALLBACK_FLIGHT_COL_WIDTH)
    offsets.push(acc)
  }
  return offsets
}

/**
 * Min-width of the matrix table: the visible fixed columns plus the flight
 * columns. A hardcoded 820px kept a phantom horizontal scrollbar once
 * columns were hidden, because the table reserved room for columns that
 * were no longer rendered.
 */
export function matrixMinWidth(hidden, colWidths = {}, defaults = DEFAULT_COL_WIDTHS, flightCount = 0) {
  const h = normalizeHidden(hidden)
  let fixed = 0
  MATRIX_COLUMNS.forEach((c, i) => {
    if (h.has(c.key)) return
    fixed += colWidths[i] || defaults[i] || 0
  })
  return fixed + flightCount * FALLBACK_FLIGHT_COL_WIDTH
}

/* ── Indexing ─────────────────────────────────────────────────── */

/**
 * Build every lookup the matrix needs. `receipts` are summed per MAWB
 * (each receipt holds a distinct set of pieces), `bookings` grouped per MAWB.
 */
export function indexMatrixData({ ulds = [], links = [], receipts = [], bookings = [] } = {}) {
  const uldFlightMap = {}
  const uldNumberMap = {}
  for (const u of ulds) {
    uldFlightMap[u.id] = u.flightId
    uldNumberMap[u.id] = u.uldNumber || u.id.slice(0, 8)
  }

  const receivedByMawb = {}
  for (const r of receipts) {
    const mawbId = r.mawbId
    if (!mawbId) continue
    receivedByMawb[mawbId] = (receivedByMawb[mawbId] || 0) + (r.pieceCount || 0)
  }

  const bookingByMawb = {}
  for (const b of bookings) {
    if (!b.mawbId) continue
    if (!bookingByMawb[b.mawbId]) bookingByMawb[b.mawbId] = []
    bookingByMawb[b.mawbId].push(b)
  }

  // mawbId -> flightId -> { pieces, ulds:[{uldNumber,pieces}] }
  const mawbPcsByFlight = new Map()
  const mawbUldsByFlight = new Map()
  for (const link of links) {
    const mawbId = link.mawbId
    const flightId = uldFlightMap[link.uldId]
    const pcs = link.pieces || 0
    if (!mawbId || !flightId) continue
    if (!mawbPcsByFlight.has(mawbId)) mawbPcsByFlight.set(mawbId, new Map())
    const fm = mawbPcsByFlight.get(mawbId)
    fm.set(flightId, (fm.get(flightId) || 0) + pcs)
    if (!mawbUldsByFlight.has(mawbId)) mawbUldsByFlight.set(mawbId, new Map())
    const um = mawbUldsByFlight.get(mawbId)
    if (!um.has(flightId)) um.set(flightId, [])
    um.get(flightId).push({ uldNumber: uldNumberMap[link.uldId] || 'ULD?', pieces: pcs })
  }

  const flightIds = new Set()
  for (const fm of mawbPcsByFlight.values()) {
    for (const fid of fm.keys()) flightIds.add(fid)
  }

  return { uldFlightMap, uldNumberMap, receivedByMawb, bookingByMawb, mawbPcsByFlight, mawbUldsByFlight, flightIds }
}

/**
 * Narrow rows + columns to a single flight when the view has one selected.
 * Returns new arrays; the caller's Set is never mutated.
 */
export function selectFlightScope({ mawbs = [], flights = [], flightIds, mawbPcsByFlight, flightIdFilter } = {}) {
  if (!flightIdFilter) return { mawbs, flights: flights.filter(f => flightIds.has(f.id)) }

  const scopedMawbs = mawbs.filter(m => m.flight?.id === flightIdFilter || m.flightId === flightIdFilter)
  const scopedIds = new Set([flightIdFilter])
  for (const m of scopedMawbs) {
    const fm = mawbPcsByFlight.get(m.id)
    if (fm) for (const fid of fm.keys()) scopedIds.add(fid)
  }
  return { mawbs: scopedMawbs, flights: flights.filter(f => scopedIds.has(f.id)) }
}

/* ── Row projection ──────────────────────────────────────────── */

/**
 * One row per MAWB, with per-flight piece cells, ULD counts and the
 * received/reserved/excess arithmetic used by the status column.
 */
export function buildMatrixRows({ mawbs = [], flights = [], bookingByMawb = {}, receivedByMawb = {}, mawbPcsByFlight, mawbUldsByFlight } = {}) {
  return mawbs.map(m => {
    const fm = (mawbPcsByFlight && mawbPcsByFlight.get(m.id)) || new Map()
    const um = (mawbUldsByFlight && mawbUldsByFlight.get(m.id)) || new Map()
    const cells = {}
    const uldCountByFlight = {}
    const breakdown = {}
    let pcsDispatched = 0
    for (const f of flights) {
      const pcs = fm.get(f.id) || 0
      cells[f.id] = pcs
      pcsDispatched += pcs
      uldCountByFlight[f.id] = (um.get(f.id) || []).length
      breakdown[f.id] = um.get(f.id) || []
    }
    const receivedPieces = receivedByMawb[m.id] || 0
    const bookingList = bookingByMawb[m.id]
    const reservedPieces = bookingList && bookingList.length > 0
      ? Math.max(...bookingList.map(b => b.skids || 0))
      : (m.pieces || 0)
    return {
      mawbId: m.id,
      awbNumber: m.awbNumber,
      shipperName: m.shipperName,
      consigneeName: m.consigneeName,
      destination: m.destination,
      commodityType: m.commodityType || null,
      totalPieces: receivedPieces || pcsDispatched || m.pieces || 0,
      totalWeightKg: m.reportedWeightKg || m.chargeableWeightKg || null,
      physicalWeightLbs: m.reportedWeightKg
        ? (Number(m.reportedWeightKg) * KG_TO_LBS)
        : (m.chargeableWeightKg ? (Number(m.chargeableWeightKg) * KG_TO_LBS) : null),
      status: m.status,
      reservedPieces,
      receivedPieces,
      pieceDiff: receivedPieces - reservedPieces,
      hasDispatchedExcess: receivedPieces > 0 && pcsDispatched > receivedPieces,
      pcsDispatched,
      cells,
      uldCountByFlight,
      breakdownByFlight: breakdown,
    }
  })
}

/* ── Presentation helpers ────────────────────────────────────── */

export function getPieces(row, flight) {
  return (row.cells && row.cells[flight.id]) || 0
}

/* Solo devuelve el realce de columna. El fondo y la tinta de la celda los pone
 * la pizarra (`.chalk td`), que va después en la cascada: antes esta función
 * devolvía `bg-white text-slate-900`, clases anuladas por el verde de fondo. */
export function cellClasses(pcs, hoverFlightId = null, flightId = null) {
  if (!pcs) return ''
  const colGlow = hoverFlightId != null && hoverFlightId === flightId
    ? 'ring-1 ring-inset ring-slate-300'
    : ''
  return colGlow
}

export const ARC_RADIUS = 5.5
export const ARC_CIRCUM = 2 * Math.PI * ARC_RADIUS

export function arcOffsetFor(pcs, totalPieces) {
  const total = totalPieces || 1
  const pct = Math.min(pcs / total, 1)
  return ARC_CIRCUM * (1 - pct)
}

/* El color del estado lo decide utils/mawbStatus.js (paleta canónica) para
 * que la matriz, la tabla de estados y el panel de info no diverjan entre sí
 * ni de Bookings/ULDs. Aquí solo se aplica. */
export function mawbStatusClassRaw(status) {
  return mawbStatusDot(status)
}

export function mawbStatusClass(row) {
  return mawbStatusCell(row.status, { excess: !!row.hasDispatchedExcess })
}

/* La celda de MAWB dentro de la matriz se pinta sobre la pizarra, así que usa
 * la variante oscura de la paleta. El panel de info (superficie clara) sigue
 * con mawbStatusClass. */
export function mawbStatusChalkClass(row) {
  return mawbStatusChalkCell(row.status, { excess: !!row.hasDispatchedExcess })
}
