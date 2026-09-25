/* ══════════════════════════════════════════════════════════════════
   Pure matrix logic for MawbsView (tab "matriz").

   Everything here is side-effect free: no store, no api, no DOM.
   MawbsView keeps the fetching and the reactive state; this module owns
   the indexing + row/column projection so it can be unit-tested.
   ══════════════════════════════════════════════════════════════════ */

export const DEFAULT_COL_WIDTHS = [180, 200, 90, 90, 90, 90, 80]
const FALLBACK_FLIGHT_COL_WIDTH = 100
const MIN_COL_WIDTH = 50
const KG_TO_LBS = 2.20462

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
 * Sticky left offsets for the 7 frozen columns, recomputed after a resize.
 * Returns an 8-length array so colStyle(7 + fi) lines up with stickyOffsets[7].
 */
export function stickyOffsetsFor(colWidths = {}, defaults = DEFAULT_COL_WIDTHS, frozen = 7) {
  const offsets = [0]
  for (let i = 0; i < frozen; i++) {
    offsets.push(offsets[i] + (colWidths[i] || defaults[i] || FALLBACK_FLIGHT_COL_WIDTH))
  }
  return offsets
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

export function cellClasses(pcs, hoverFlightId = null, flightId = null) {
  if (!pcs) return ''
  const colGlow = hoverFlightId != null && hoverFlightId === flightId
    ? 'ring-1 ring-inset ring-slate-300'
    : ''
  return `relative bg-white text-slate-900 ${colGlow}`.trim()
}

export const ARC_RADIUS = 5.5
export const ARC_CIRCUM = 2 * Math.PI * ARC_RADIUS

export function arcOffsetFor(pcs, totalPieces) {
  const total = totalPieces || 1
  const pct = Math.min(pcs / total, 1)
  return ARC_CIRCUM * (1 - pct)
}

export function mawbStatusClassRaw(status) {
  if (!status || status === 'BOOKED') return 'bg-slate-500'
  if (status === 'RECEIVED') return 'bg-slate-600'
  if (status === 'MANIFESTED') return 'bg-slate-500'
  if (status === 'DEPARTED') return 'bg-slate-700'
  return 'bg-slate-400'
}

export function mawbStatusClass(row) {
  if (row.hasDispatchedExcess) return 'text-slate-700 bg-slate-50/80 hover:bg-slate-100 border-l-4 border-l-slate-500'
  const s = row.status
  if (!s || s === 'BOOKED') return 'text-slate-600 bg-slate-50 hover:bg-slate-100 border-l-4 border-l-slate-400'
  if (s === 'RECEIVED') return 'text-slate-700 bg-slate-50/60 hover:bg-slate-100 border-l-4 border-l-slate-500'
  if (s === 'MANIFESTED') return 'text-slate-600 bg-slate-50/60 hover:bg-slate-100 border-l-4 border-l-slate-400'
  if (s === 'DEPARTED') return 'text-slate-800 bg-slate-50/60 hover:bg-slate-100 border-l-4 border-l-slate-600'
  return 'bg-white hover:bg-slate-50'
}
