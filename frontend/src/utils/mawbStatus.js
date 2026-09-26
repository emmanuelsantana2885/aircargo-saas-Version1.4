/* Paleta canónica del estado de una MAWB.
 *
 * Antes cada vista decidía el color por su cuenta y divergieron:
 *   - BookingsView y UldsView us ámbar/verde/azul (semánticos).
 *   - MawbsView us 100% gris pizarra, donde BOOKED y MANIFESTEDliteralmente
 *     compartían `border-l-slate-400`: indistinguibles al vistazo.
 * Este módulo es la única fuente de verdad. Los valores son los mismos que
 * ya usaba Bookings/Ulds, así que esas vistas no cambian de aspecto: lo que
 * cambia es Mawbs, que pasa a ser legible.
 *
 * Significado de cada estado (ciclo de vida del vuelo):
 *   BOOKED    → gris      reservado, aún sin movimiento en rampa
 *   RECEIVED  → ámbar     recibida en bodega, en proceso
 *   MANIFESTED→ verde     manifestada y lista
 *   DEPARTED  → azul      despachada / en vuelo
 *   ARRIVED   → azul      misma familia que DEPARTED
 */

const DOT = {
  BOOKED: 'bg-slate-400',
  RECEIVED: 'bg-amber-400',
  MANIFESTED: 'bg-emerald-500',
  DEPARTED: 'bg-blue-500',
  ARRIVED: 'bg-blue-500',
  _DEFAULT: 'bg-slate-300',
}

const TEXT = {
  BOOKED: 'text-slate-500',
  RECEIVED: 'text-amber-600',
  MANIFESTED: 'text-emerald-600',
  DEPARTED: 'text-blue-600',
  ARRIVED: 'text-blue-600',
  _DEFAULT: 'text-slate-400',
}

/* Celda de la matriz: fondo tenue + barra de estado a la izquierda.
 * El fondo se mantiene casi neutro para no competir con el chalkboard de las
 * celdas de piezas; el color vive en la barra y en el texto, que es donde el
 * operador lee el estado de un vistazo. */
const CELL = {
  BOOKED: 'text-slate-600 bg-slate-50 hover:bg-slate-100 border-l-4 border-l-slate-400',
  RECEIVED: 'text-amber-700 bg-amber-50/60 hover:bg-amber-50 border-l-4 border-l-amber-400',
  MANIFESTED: 'text-emerald-700 bg-emerald-50/60 hover:bg-emerald-50 border-l-4 border-l-emerald-500',
  DEPARTED: 'text-blue-700 bg-blue-50/60 hover:bg-blue-50 border-l-4 border-l-blue-500',
  ARRIVED: 'text-blue-700 bg-blue-50/60 hover:bg-blue-50 border-l-4 border-l-blue-500',
  _DEFAULT: 'bg-white hover:bg-slate-50',
}

/* Aviso de exceso de piezas despachadas: prima sobre el color de estado
 * porque es una anomalía operativa, no una etapa del ciclo. */
const CELL_EXCESS =
  'text-rose-700 bg-rose-50/80 hover:bg-rose-50 border-l-4 border-l-rose-500'

/* Variante para superficies oscuras: la celda de MAWB de la matriz, que vive
 * sobre el verde profundo de la pizarra (`.chalk`).
 *
 * La paleta clara de arriba no sirve aquí por dos motivos: sus tintas 600-700
 * son indistinguibles sobre el verde, y su `bg-*`tenue queda anulado igual por
 * la pizarra (`.chalk td` y `.chalk .fz` van después en la cascada y ganan), de
 * modo que el operador perdía la única señal de color que le quedaba.
 *
 * Aquí el fondo lo pone la pizarra, no la clase: estas solo fijan la tinta del
 * texto y la barra de estado, manteniendo las mismas familias de color.
 * Deliberadamente NO declaran `background`: la celda de MAWB de la matriz es
 * una columna fija (`.chalk .fz`) y su fondo lo pone la pizarra. Estos
 * marcadores solo aportan tinta + filo de estado. */
const CELL_CHALK = {
  BOOKED: 'text-slate-300 border-l-4 border-l-slate-400',
  RECEIVED: 'text-amber-300 border-l-4 border-l-amber-400',
  MANIFESTED: 'text-emerald-300 border-l-4 border-l-emerald-400',
  DEPARTED: 'text-sky-300 border-l-4 border-l-sky-400',
  ARRIVED: 'text-sky-300 border-l-4 border-l-sky-400',
  _DEFAULT: 'text-slate-300',
}

const CELL_CHALK_EXCESS = 'text-rose-300 border-l-4 border-l-rose-400'

/* Badge compacto para la tabla de estados. Fondo + tinta con contraste AA
 * medido por scripts/audit-contrast.mjs en light y dark. */
const BADGE = {
  BOOKED: 'bg-slate-100 text-slate-600 border-slate-300',
  RECEIVED: 'bg-amber-100 text-amber-800 border-amber-300',
  MANIFESTED: 'bg-emerald-100 text-emerald-800 border-emerald-300',
  DEPARTED: 'bg-blue-100 text-blue-800 border-blue-300',
  ARRIVED: 'bg-blue-100 text-blue-800 border-blue-300',
  _DEFAULT: 'bg-slate-50 text-slate-700 border-slate-300',
}

/* Variante de badge con estilos inline (la que se aplica con :style y por eso
 * no puede vivir como clase). Mismos tonos que BADGE pero con la tinta
 * oscurecida para mantener AA sobre el fondo. */
const BADGE_STYLE = {
  BOOKED: { background: '#f1f5f9', color: '#475569' },
  RECEIVED: { background: '#fef3c7', color: '#92400e' },
  MANIFESTED: { background: '#d1fae5', color: '#065f46' },
  DEPARTED: { background: '#dbeafe', color: '#1e40af' },
  ARRIVED: { background: '#dbeafe', color: '#1e40af' },
  _DEFAULT: { background: 'var(--bg, #f1f5f9)', color: 'var(--text, #475569)' },
}

function norm(status) {
  if (!status || status === '—' || status === '-') return 'BOOKED'
  const s = String(status).trim().toUpperCase()
  return Object.prototype.hasOwnProperty.call(DOT, s) ? s : '_DEFAULT'
}

export function mawbStatusDot(status) {
  return DOT[norm(status)]
}

export function mawbStatusText(status) {
  return TEXT[norm(status)]
}

export function mawbStatusCell(status, { excess = false } = {}) {
  if (excess) return CELL_EXCESS
  return CELL[norm(status)]
}

/* Igual que mawbStatusCell pero para la matriz (pizarra). */
export function mawbStatusChalkCell(status, { excess = false } = {}) {
  if (excess) return CELL_CHALK_EXCESS
  return CELL_CHALK[norm(status)]
}

export function mawbStatusBadge(status) {
  return BADGE[norm(status)]
}

export function mawbStatusBadgeStyle(status) {
  return BADGE_STYLE[norm(status)]
}

/* Las 4 etapas que la app modela, para iterar (filtros, cabeceras, export).
 * El orden es el del ciclo de vida, no el alfabético. */


/* Variante para superficies CLARAS (chalk-light theme).
 * Usa tintas oscuras (600-700) con fondos tenues para contraste AA sobre fondo claro. */
const CELL_CHALK_LIGHT = {
  BOOKED: 'text-slate-700 bg-slate-50 hover:bg-slate-100 border-l-4 border-l-slate-400',
  RECEIVED: 'text-amber-700 bg-amber-50/60 hover:bg-amber-50 border-l-4 border-l-amber-400',
  MANIFESTED: 'text-emerald-700 bg-emerald-50/60 hover:bg-emerald-50 border-l-4 border-l-emerald-500',
  DEPARTED: 'text-blue-700 bg-blue-50/60 hover:bg-blue-50 border-l-4 border-l-blue-500',
  ARRIVED: 'text-blue-700 bg-blue-50/60 hover:bg-blue-50 border-l-4 border-l-blue-500',
  _DEFAULT: 'bg-white hover:bg-slate-50',
}

const CELL_CHALK_LIGHT_EXCESS =
  'text-rose-700 bg-rose-50/80 hover:bg-rose-50 border-l-4 border-l-rose-500'

/* Badge para el tema claro - usa tintas 800 para contraste AA. */
const BADGE_LIGHT = {
  BOOKED: 'bg-slate-100 text-slate-700 border-slate-300',
  RECEIVED: 'bg-amber-100 text-amber-800 border-amber-300',
  MANIFESTED: 'bg-emerald-100 text-emerald-800 border-emerald-300',
  DEPARTED: 'bg-blue-100 text-blue-800 border-blue-300',
  ARRIVED: 'bg-blue-100 text-blue-800 border-blue-300',
  _DEFAULT: 'bg-slate-50 text-slate-700 border-slate-300',
}

const BADGE_STYLE_LIGHT = {
  BOOKED: { background: '#f1f5f9', color: '#334155' },
  RECEIVED: { background: '#fef3c7', color: '#92400e' },
  MANIFESTED: { background: '#d1fae5', color: '#065f46' },
  DEPARTED: { background: '#dbeafe', color: '#1e40af' },
  ARRIVED: { background: '#dbeafe', color: '#1e40af' },
  _DEFAULT: { background: 'var(--bg, #f1f5f9)', color: 'var(--text, #334155)' },
}

export { CELL_CHALK_LIGHT, CELL_CHALK_LIGHT_EXCESS, BADGE_LIGHT, BADGE_STYLE_LIGHT }

export const MAWB_STATUS_ORDER = ['BOOKED', 'RECEIVED', 'MANIFESTED', 'DEPARTED']

/* Variante para la pizarra clara (chalk-light theme).
 * Igual que mawbStatusChalkCell pero usa CELL_CHALK_LIGHT. */
export function mawbStatusChalkLightCell(status, { excess = false } = {}) {
  if (excess) return CELL_CHALK_LIGHT_EXCESS
  return CELL_CHALK_LIGHT[norm(status)]
}
