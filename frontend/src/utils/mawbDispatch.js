import { normalizeAwb } from './awb'

/**
 * Peso y piezas despachadas por MAWB — única fuente de verdad.
 *
 * Esta lógica se duplicó entre `DashboardView` y `FlightDetail` y DIVERGIÓ: el
 * panel de vuelo nunca consultaba los recibos y prefería `chargeableWeightKg`
 * (peso declarado) sobre `reportedWeightKg`, mientras el Dashboard prefería el
 * peso real del recibo. Para la misma MAWB los dos paneles mostraban pesos
 * distintos en el caso normal (recibo emitido). Además el panel de vuelo
 * comparaba `mawbLabel` por igualdad exacta y no topeaba las piezas
 * despachadas a las recibidas. Las tres diferencias están corregidas aquí y
 * ambos paneles consumen estas funciones.
 *
 * Fórmula: (pesoRecebido / piezasRecibidas) × piezasDespachadas
 *
 * Base del peso recibido: el peso FÍSICO real del recibo (`actualWeightLbs`)
 * cuando existe un recibo vigente (no superseded); si no, cae a los kg
 * declarados de la MAWB — que son sólo un proxy mientras no haya recibo.
 *
 * Conteo de piezas: únicamente links `uld_awb` cuyo ULD pertenece al vuelo, y
 * topeadas a las piezas recibidas (no se puede despachar más de lo recibido).
 */

/** Recibo vigente (no superseded) de una MAWB, por `mawbId` o por AWB normalizado. */
export function activeReceiptForMawb(mawb, receipts) {
  const awb = normalizeAwb(mawb?.awbNumber)
  if (!awb && !mawb?.id) return null
  return (receipts || []).find(r =>
    !r.superseded &&
    ((r.mawbId && mawb?.id && r.mawbId === mawb.id) ||
      (r.mawbNumber && normalizeAwb(r.mawbNumber) === awb))
  ) || null
}

/**
 * Piezas despachadas de una MAWB entre los ULDs indicados, topeadas a las
 * piezas recibidas.
 *
 * `uldIds` es OBLIGATORIO y debe ser un conjunto no vacío: es el conjunto de
 * ULDs del vuelo que se está mirando. Sin ese alcance se sumarían piezas de
 * ULDs de otros vuelos (inflación cruzada entre flights, bug ya corregido), así
 * que antes que devolver un número sin sentido devuelve 0. Para el total
 * histórico de la MAWB hay que pasar todos los ULDs en el conjunto.
 */
export function dispatchedPiecesForMawb(mawb, uldAwbs, uldIds, receivedPcs) {
  const awb = normalizeAwb(mawb?.awbNumber)
  if (!awb || !uldIds?.size) return 0
  const sum = (uldAwbs || [])
    .filter(l => uldIds.has(l.uldId) && normalizeAwb(l.mawbLabel) === awb)
    .reduce((s, l) => s + (Number(l.pieces) || 0), 0)
  const cap = Number(receivedPcs || 0)
  return cap > 0 ? Math.min(sum, cap) : sum
}

/**
 * @param {object} mawb        MAWB
 * @param {object[]} uldAwbs   links `uld_awb`
 * @param {Set<string>} uldIds ULDs del vuelo en cuestión
 * @param {object[]} [receipts] recibos — si se omite, no se usa peso físico
 * @returns {number} libras despachadas (0 si no hay datos suficientes)
 */
export function mawbDispatchedWeightLbs(mawb, uldAwbs, uldIds, receipts) {
  const rec = activeReceiptForMawb(mawb, receipts)
  let receivedLbs = 0
  let receivedPcs = Number(mawb?.pieces || 0)

  if (rec && Number(rec.actualWeightLbs) > 0) {
    receivedLbs = Number(rec.actualWeightLbs)
    receivedPcs = Number(rec.pieceCount || receivedPcs)
  } else {
    const receivedKg = Number(mawb?.reportedWeightKg || mawb?.chargeableWeightKg || 0)
    receivedLbs = receivedKg ? receivedKg * 2.20462 : 0
  }
  if (!receivedLbs || !receivedPcs) return 0

  const dispatchedPcs = dispatchedPiecesForMawb(mawb, uldAwbs, uldIds, receivedPcs)
  if (!dispatchedPcs) return 0

  return (receivedLbs / receivedPcs) * dispatchedPcs
}

