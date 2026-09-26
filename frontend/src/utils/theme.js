/* ══════════════════════════════════════════════════════════════════
   Theme engine v4 — 12 themes × 10 tones (Motores de tema de
   MejoraPropuesta4, adaptado: claves aircargo_theme/aircargo_tone,
   `data-theme` = modo claro/oscuro, aliases de variables del app).
   ══════════════════════════════════════════════════════════════════ */

/* La diferencia perceptual entre matices NO la fija el L% de HSL: a L=52% el
 * azul sale #104ef9 (6.04:1 sobre blanco, azul profundo) y el verde sale
 * #1eeba0 (1.56:1, menta fosforito). Por eso el "verde chillón" se corrige
 * BAJANDO SATURACION y no moviendo lightness: bajar la saturacion calma el
 * croma, y un offset de lightness por encima de unos puntos aplana la rampa
 * contra su suelo (los tonos altos se vuelven indistinguibles), lo que hace
 * inutil el selector de tonos. El techo de croma lo fija un guard del spec. */
const THEMES = {
  azul:      { mode: 'light', name: 'Azul',    h: 224, s: 95, bg: '#eef2fb', surface: '#ffffff', surface2: '#f6f8fd', text: '#0f172a', muted: '#5b6b85', border: '#dbe4f2', borderS: '#9fb0cd', violet: '#8b5cf6', cyan: '#06b6d4' },
  indigo:    { mode: 'light', name: 'Índigo',  h: 239, s: 84, bg: '#eef0fc', surface: '#ffffff', surface2: '#f5f6fd', text: '#10132a', muted: '#5c6288', border: '#dadef5', borderS: '#9aa0d4', violet: '#a78bfa', cyan: '#22d3ee' },
  esmeralda: { mode: 'light', name: 'Esmeralda', h: 158, s: 58, bg: '#ecf7f1', surface: '#ffffff', surface2: '#f4fbf7', text: '#06281b', muted: '#4f6b5c', border: '#d3ebe0', borderS: '#8fbca6', violet: '#0ea5e9', cyan: '#14b8a6' },
  ambar:     { mode: 'light', name: 'Ámbar',   h: 38,  s: 78, bg: '#fbf6ee', surface: '#fffdf8', surface2: '#f7f1e6', text: '#2a1e0b', muted: '#8a6f45', border: '#efe3ca', borderS: '#c4a878', violet: '#7c3aed', cyan: '#0284c7' },
  rosa:      { mode: 'light', name: 'Rosa',    h: 345, s: 84, bg: '#fbf0f4', surface: '#ffffff', surface2: '#fdf6f9', text: '#2a0f19', muted: '#8a5f6c', border: '#f0dce3', borderS: '#c9a3b0', violet: '#9333ea', cyan: '#0891b2' },
  pizarra:   { mode: 'light', name: 'Gris',    h: 220, s: 10, bg: '#f2f3f6', surface: '#ffffff', surface2: '#f7f7f9', text: '#16181d', muted: '#59606b', border: '#dfe1e6', borderS: '#aab0ba', violet: '#8b5cf6', cyan: '#0891b2' },
  tokyo:     { mode: 'dark',  name: 'Tokio',   h: 223, s: 93, bg: '#0b1220', surface: '#111827', surface2: '#0f1626', text: '#e2e8f0', muted: '#94a3b8', border: '#334155', borderS: '#64748b', violet: '#a78bfa', cyan: '#22d3ee' },
  medianoche:{ mode: 'dark',  name: 'Medianoche', h: 240, s: 82, bg: '#0a0f22', surface: '#101830', surface2: '#0d1430', text: '#e8eaf6', muted: '#9aa3c7', border: '#2e3756', borderS: '#5a6694', violet: '#a78bfa', cyan: '#22d3ee' },
   esmernoche:{ mode: 'dark',  name: 'Esmeralda noche', h: 158, s: 60, bg: '#08180f', surface: '#0e2117', surface2: '#0c1c13', text: '#d9f2e4', muted: '#82ad97', border: '#24402f', borderS: '#4b7a60', violet: '#22d3ee', cyan: '#34d399' },
   lava:      { mode: 'dark',  name: 'Lava',    h: 353, s: 90, bg: '#190a0d', surface: '#221014', surface2: '#1d0c10', text: '#fbe9eb', muted: '#b99299', border: '#462931', borderS: '#80484f', violet: '#f59e0b', cyan: '#f87171' },
   grafito:   { mode: 'dark',  name: 'Grafito', h: 220, s: 10, bg: '#0c0e12', surface: '#15181f', surface2: '#101318', text: '#e6e8ee', muted: '#9aa1ac', border: '#2a2e37', borderS: '#4c525e', violet: '#8b5cf6', cyan: '#38bdf8' },
   marino:    { mode: 'dark',  name: 'Marino',  h: 228, s: 55, bg: '#0a0f1e', surface: '#111830', surface2: '#0d1326', text: '#e2e8f5', muted: '#93a1c2', border: '#212c4a', borderS: '#3d4c78', violet: '#818cf8', cyan: '#22d3ee' },
}

const KEY = 'aircargo_theme'
const TONE_KEY = 'aircargo_tone'
const DEFAULT_THEME = 'azul'
const DEFAULT_TONE = 3
const MAX_TONE = 9

const TONE_NAMES = ['Suave', 'Tenue', 'Tímido', 'Vivo', 'Fuerte', 'Saturado', 'Profundo', 'Nocturno', 'Carbón', 'Abisal']
const ST_L = { pend: ['#475569', '#eef2f7'], rec: ['#b45309', '#fef3c7'], carg: ['#047857', '#d1fae5'], pro: ['#6d28d9', '#ede9fe'], man: ['#1d4ed8', '#dbeafe'], disp: ['#0e7490', '#cffafe'] }
const ST_D = { pend: ['#94a3b8', '#1e293b'], rec: ['#fbbf24', '#421500'], carg: ['#6ee7b7', '#053a2a'], pro: ['#c4b5fd', '#2e1065'], man: ['#93c5fd', '#172554'], disp: ['#67e8f9', '#083344'] }

let current = DEFAULT_THEME
let tone = DEFAULT_TONE

function lsGet(k) { try { return localStorage.getItem(k) } catch { return null } }
function lsSet(k, v) { try { localStorage.setItem(k, v) } catch {} }

function hsl(h, s, l) { return 'hsl(' + h + ' ' + s + '% ' + l + '%)' }

function hslToRgb(h, s, l) {
  s /= 100; l /= 100
  const c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs(((h / 60) % 2) - 1)), m = l - c / 2
  let r = 0, g = 0, b = 0
  if (h < 60) { r = c; g = x } else if (h < 120) { r = x; g = c } else if (h < 180) { g = c; b = x } else if (h < 240) { g = x; b = c } else if (h < 300) { r = x; b = c } else { r = c; b = x }
  return [Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255)]
}

function rgba2(r, g, b, a) { return 'rgba(' + r + ',' + g + ',' + b + ',' + a + ')' }

/* Lightness del acento para un tono dado. Fuente UNICA de la rampa: la usan
 * tanto `accentFor` como la banda del header en `applyTheme`, de modo que el
 * accent y su banda no pueden divergir por una copia desincronizada de la
 * formula (el bug que hacia que la barra no siguiera al acento). */
function accentL(th, ti) {
  return th.mode === 'dark' ? Math.max(46, 72 - ti * 3) : Math.max(30, 64 - ti * 4)
}

function accentFor(t, tn) {
  const th = THEMES[t]
  if (!th) return null
  const ti = tn === undefined || tn === null ? tone : Math.max(0, Math.min(MAX_TONE, +tn))
  const L = accentL(th, ti)
  const La = L - 10
  const acc = hsl(th.h, th.s, L)
  const accS = hsl(th.h, th.s, La)
  const [r, g, b] = hslToRgb(th.h, th.s, L)
  return { th, acc, accS, soft: rgba2(r, g, b, 0.12), soft2: rgba2(r, g, b, 0.22) }
}

/* Contraste WCAG sobre un color plano (sRGB relativo). Se usa solo para
 * resolver la barra del header, no para el resto del sistema. */
function relLum(r, g, b) {
  const f = (c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4) }
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
}
function contrast(r1, g1, b1, r2, g2, b2) {
  const l1 = relLum(r1, g1, b1), l2 = relLum(r2, g2, b2)
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
}

/* Resuelve una banda de color + su tinta para que el par siempre supere 4.5:1.
 * El accent crudo no sirve como fondo grande en los tonos medios (p.ej. indigo
 * tono 0 daba 4.34:1 tanto con blanco como con tinta oscura), asi que se
 * acerca a negro o blanco hasta que la mejor tinta pasa el umbral. */
function resolveBand(r, g, b) {
  let c = [r, g, b]
  for (let i = 0; i < 12; i++) {
    const cw = contrast(255, 255, 255, c[0], c[1], c[2])
    const cd = contrast(11, 18, 32, c[0], c[1], c[2])
    if (Math.max(cw, cd) >= 4.5) break
    const target = cw >= cd ? [0, 0, 0] : [255, 255, 255]
    c = c.map((v, i) => Math.round(v * 0.92 + target[i] * 0.08))
  }
  const cw = contrast(255, 255, 255, c[0], c[1], c[2])
  const cd = contrast(11, 18, 32, c[0], c[1], c[2])
  const lightInk = cw >= cd
  return {
    band: rgba2(c[0], c[1], c[2], 1),
    ink: lightInk ? '#ffffff' : '#0b1220',
    /* Mezclar hacia la tinta BAJA el contraste del texto, asi que los chips y
     * bordes de la barra usan el color opuesto: oscurecen una barra de tinta
     * clara y aclaran una de tinta oscura. */
    inkSoft: lightInk ? 'rgba(0,0,0,0.16)' : 'rgba(255,255,255,0.18)',
    inkSoft2: lightInk ? 'rgba(0,0,0,0.28)' : 'rgba(255,255,255,0.32)',
  }
}

function applyTheme(t, tn, persist) {
  if (!THEMES[t]) return
  current = t
  tone = Math.max(0, Math.min(MAX_TONE, Number.isFinite(tn) ? Math.round(tn) : DEFAULT_TONE))
  const A = accentFor(t, tone)
  const th = A.th
  const st = th.mode === 'dark' ? ST_D : ST_L
  const root = document.documentElement
  root.dataset.theme = th.mode
  const set = (k, v) => { if (v !== undefined) root.style.setProperty(k, v) }
  set('--bg', th.bg)
  set('--surface', th.surface)
  set('--surface-2', th.surface2)
  /* Tokens derivados que main.css solo declara como fallback estático (slate).
   * Sin esto seguían congelados en la paleta anterior: en los temas cálidos
   * (ámbar, rosa) un hover gris-azulado se veía descolgado sobre la crema. */
  set('--surface-3', 'color-mix(in srgb, ' + th.surface + ' 94%, ' + th.text + ')')
  set('--text', th.text)
  set('--muted', th.muted)
  /* --text-2 es texto secundario de 10-13px (sidebar, badges, botones), así
   * que necesita 4.5:1. El muted pelado se queda en 4.21:1 sobre surface-2 en
   * el tema ámbar; mezclar un 15% hacia --text sube el peor caso a 4.97:1 y en
   * dark aclara hacia el blanco, igual que hace --muted a la inversa. */
  set('--text-2', 'color-mix(in srgb, ' + th.muted + ' 85%, ' + th.text + ')')
  set('--border', th.border)
  set('--border-2', th.border)
  set('--border-str', th.borderS)
  set('--border-strong', th.borderS)
  set('--accent', A.acc)
  set('--accent-strong', A.accS)
  set('--accent-soft', A.soft)
  set('--accent-soft-2', A.soft2)
  set('--accent-soft-strong', A.soft2)
  /* Barra del header: banda + tinta garantizadas >= 4.5:1 en las 120
   * combinaciones tema x tono. Antes era un teal fijo (#0d9488) que ignoraba
   * los 12 temas y daba 3.74:1 con sus propios chips de 11.5px. */
  const band = resolveBand(...hslToRgb(th.h, th.s, accentL(th, tone)))
  set('--accent-band', band.band)
  set('--accent-ink', band.ink)
  set('--accent-ink-soft', band.inkSoft)
  set('--accent-ink-soft-2', band.inkSoft2)
  set('--accent-violet', th.violet)
  set('--accent-cyan', th.cyan)
  set('--grad-accent', 'linear-gradient(135deg, ' + A.accS + ' 0%, ' + A.acc + ' 55%, ' + th.cyan + ' 130%)')
  set('--grad-title', 'linear-gradient(120deg, ' + A.accS + ' 0%, ' + A.acc + ' 60%, ' + th.violet + ' 135%)')
  set('--gradient-accent', 'linear-gradient(135deg, ' + A.accS + ' 0%, ' + A.acc + ' 55%, ' + th.cyan + ' 130%)')
  set('--gradient-title', 'linear-gradient(120deg, ' + A.accS + ' 0%, ' + A.acc + ' 60%, ' + th.violet + ' 135%)')
  set('--bg-grad', th.mode === 'dark'
    ? 'linear-gradient(135deg,' + th.bg + ', ' + th.surface2 + ' 55%, ' + th.bg + ')'
    : 'linear-gradient(135deg,' + th.bg + ', ' + (th.surface2 || th.surface) + ' 55%, ' + th.bg + ')')
  set('--warn', th.mode === 'dark' ? '#fbbf24' : '#b45309')
  set('--warn-bg', th.mode === 'dark' ? 'rgba(120,53,15,.28)' : '#fffbeb')
  set('--danger', th.mode === 'dark' ? '#f87171' : '#dc2626')
  set('--danger-soft', th.mode === 'dark' ? 'rgba(127,29,29,.3)' : '#fef2f2')
  set('--ok', th.mode === 'dark' ? '#34d399' : '#059669')
  set('--ok-soft', th.mode === 'dark' ? 'rgba(6,78,59,.35)' : '#ecfdf5')
  set('--shadow-lg', th.mode === 'dark' ? '0 24px 60px -24px rgba(0,0,0,.65)' : '0 24px 60px -24px rgba(15,23,42,.35)')
  /* Las sombras proyectan tinta, no texto: en dark --text es un color CLARO, asi
   * que mezclar las sombras hacia --text las volvia luminosas (halo gris claro
   * flotando sobre la superficie oscura). --shadow-tint es el color con el que
   * hay que difuminar la sombra: el propio texto en claro, negro en dark. */
  set('--shadow-tint', th.mode === 'dark' ? '#000000' : th.text)
  ;['pend', 'rec', 'carg', 'pro', 'man', 'disp'].forEach((s) => {
    set('--sc-' + s, st[s][0])
    set('--sb-' + s, st[s][1])
  })
  if (persist) { lsSet(KEY, current); lsSet(TONE_KEY, String(tone)) }
}

function normalizeLegacy(id) {
  if (id === 'light') return 'azul'
  if (id === 'tokyo') return 'tokyo'
  return id
}

export function getTheme() { return current }
export function getTone() { return tone }
export function getMode() { return THEMES[current] ? THEMES[current].mode : 'light' }

export function setTheme(id, tn) {
  id = normalizeLegacy(id)
  if (!THEMES[id]) return current
  applyTheme(id, tn === undefined ? tone : tn, true)
  return current
}

export function setTone(tn) {
  applyTheme(current, Number.isFinite(+tn) ? +tn : DEFAULT_TONE, true)
  return tone
}

export function setMode(mode) {
  const th = THEMES[current]
  if (!th || th.mode === mode) return getMode()
  if (mode === 'dark') {
    const next = th.name === 'Esmeralda' ? 'esmernoche' : th.name === 'Rosa' ? 'lava' : 'tokyo'
    applyTheme(next, tone, true)
  } else if (mode === 'light') {
    const next = th.name === 'Medianoche' ? 'indigo' : th.name === 'Esmeralda noche' ? 'esmeralda' : th.name === 'Lava' ? 'rosa' : 'azul'
    applyTheme(next, tone, true)
  }
  return getMode()
}

export function resetTheme() {
  applyTheme(DEFAULT_THEME, DEFAULT_TONE, true)
  return current
}

export function initTheme() {
  let saved = lsGet(KEY)
  saved = normalizeLegacy(saved)
  let t = saved && THEMES[saved] ? saved : DEFAULT_THEME
  let tn = DEFAULT_TONE
  const savedTone = lsGet(TONE_KEY)
  if (savedTone !== null && !isNaN(+savedTone)) tn = Math.max(0, Math.min(MAX_TONE, +savedTone))
  applyTheme(t, tn, false)
}

export { THEMES, TONE_NAMES, accentFor, accentL, resolveBand, contrast, relLum, hslToRgb }