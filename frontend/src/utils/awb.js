/**
 * Normalización de números AWB (Air Waybill) — única fuente de verdad.
 *
 * Los AWB viajan por el sistema con formatos inconsistentes: `406-05857585`,
 * `40605857585`, `406_05857585`, `406/05857585`, ` 406-05857585 ` y, en
 * `uld_awb.mawb_label`, a veces como UUID de MAWB o vacío. Cualquier
 * comparación por igualdad directa falla en cuanto un registro histórico usa
 * otro separador, y el síntoma es silencioso: la fila simplemente no aparece
 * (pesos despachados en 0, piezas en 0, ULD vacío).
 *
 * Por eso TODA comparación de AWB —en vistas, stores y componentes— debe pasar
 * por aquí en ambos lados: `normalizeAwb(a) === normalizeAwb(b)`.
 *
 * Forma canónica: mayúsculas, sin separadores y con guion en el dígito 4
 * (`406-05857585`). La canonicalización a 11 dígitos cubre MAWBs numéricas
 * grabadas sin guion; las alfanuméricas (p.ej. `AAD99387UPS`) sólo se
 * normalizan a mayúsculas y sin separadores.
 */
export function normalizeAwb(raw) {
  let s = String(raw ?? '').trim().toUpperCase().replace(/[\s\-_/]/g, '')
  if (/^\d{11}$/.test(s)) {
    s = s.slice(0, 3) + '-' + s.slice(3)
  }
  return s
}

/**
 * ¿Son estos dos AWB el mismo documento?
 * Envuelve el caso más frecuente de uso y deja la intención explícita en el
 * call site. Un valor vacío NUNCA iguala a otro valor vacío: así se evita que
 * los `uld_awb` sin etiqueta (mawb_label vacío) se emparejen entre sí.
 */
export function sameAwb(a, b) {
  const na = normalizeAwb(a)
  if (!na) return false
  return na === normalizeAwb(b)
}