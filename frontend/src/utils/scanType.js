/* Detección contextual del tipo de código escaneado (SDD §5 "flujo de usuario"):
 *   'uld'  → prefijos del catálogo de tipos ULD (PMC, PAH, ...) + sufijo numérico
 *   'seal' → prefijo de sello (SC-… / SEAL-… / SC1234…)
 *   'mawb' → todo lo demás (MAWB numérico IATA o HAWB alfanumérico)
 *
 * El prefijo de sello no es infalible (un HAWB raro podría empezar por "SC"):
 * por eso el panel mantiene el selector manual como override (modo Pieza/Sello).
 */

export const SEAL_RE = /^(SC|SEAL)[-\s]?[A-Z0-9]{3,}$/i

export function isUldCode(code, uldCodes = []) {
  const c = (code || '').trim()
  const codes = (uldCodes || []).filter(Boolean)
  if (!c || !codes.length) return false
  return new RegExp(`^(${codes.join('|')})[-\\s]?[A-Z0-9]{4,}`, 'i').test(c)
}

export function isSealCode(code) {
  const c = (code || '').trim()
  return !!c && SEAL_RE.test(c)
}

/** @returns {'uld'|'seal'|'mawb'|null} null cuando no hay código */
export function detectScanType(code, uldCodes = []) {
  const c = (code || '').trim()
  if (!c) return null
  if (isUldCode(c, uldCodes)) return 'uld'
  if (isSealCode(c)) return 'seal'
  return 'mawb'
}
