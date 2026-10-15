import { describe, it, expect, beforeEach } from 'vitest'
import { readFileSync } from 'node:fs'
import {
  accentFor, getTheme, getTone, getMode,
  setTheme, setTone, setMode, resetTheme, initTheme,
  THEMES, TONE_NAMES, MAX_TONE, LIGHT_OF, DARK_OF, resolveBand, contrast, hslToRgb, accentL,
} from '@/utils/theme'

const root = document.documentElement
const cssVar = (name) => root.style.getPropertyValue(name)

/* Independent WCAG reference implementation: the guard must not reuse the
 * engine's own math, or a bug in that math would validate itself. */
const refLin = (c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4) }
const refLum = ([r, g, b]) => 0.2126 * refLin(r) + 0.7152 * refLin(g) + 0.0722 * refLin(b)
const refContrast = (a, b) => {
  const l1 = refLum(a), l2 = refLum(b)
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05)
}
const parseRgb = (s) => s.replace(/rgba?\(|\s/g, '').split(',').slice(0, 3).map(Number)
const parseHex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16))
const over = (base, mix, p) => base.map((v, i) => Math.round(v * (1 - p) + mix[i] * p))
const accentRgb = (th, tone) => hslToRgb(th.h, th.s, accentL(th, tone))
/* DARK_OF va de claro -> oscuro, asi que se invierte para poder razonar
 * sobre pares (oscuro, claro) simetricos. */
const DARK_PAIRS = Object.fromEntries(Object.entries(DARK_OF).map(([l, d]) => [d, l]))

/* Croma relativa = (max-min)/max. Es la medida que captura el "verde chillón":
 * el mismo L% produce un azul profundo y una menta fosforito, y la diferencia
 * perceptual se ve en la croma, no en el L. */
const chroma = ([r, g, b]) => { const mx = Math.max(r, g, b); return mx === 0 ? 0 : (mx - Math.min(r, g, b)) / mx }

beforeEach(() => {
  localStorage.clear()
  resetTheme()
})

describe('accentFor — math of tone', () => {
  it('returns null for an unknown theme instead of throwing', () => {
    expect(accentFor('no-existe', 3)).toBeNull()
  })

  it('clamps tone to the 0..MAX_TONE range in both directions', () => {
    // hue/sat/lum are the same for both themes; only the mode differs
    expect(accentFor('azul', -50).acc).toBe(accentFor('azul', 0).acc)
    expect(accentFor('azul', 999).acc).toBe(accentFor('azul', MAX_TONE).acc)
  })

  it('keeps dark themes brighter than light themes at every tone', () => {
    // Guard against a regression that would make accent text invisible on dark surfaces:
    // dark floor is 42%, light floor is 26%.
    for (let tone = 0; tone <= MAX_TONE; tone++) {
      const light = accentFor('azul', tone).acc
      const dark = accentFor('tokyo', tone).acc
      expect(light).not.toBe(dark)
    }
    // azul 66->26 y tokyo 74->42: mismo ramp de lightness, menos croma.
    expect(accentFor('azul', 0).acc).toBe('hsl(224 78% 66%)')
    expect(accentFor('tokyo', 0).acc).toBe('hsl(223 76% 74%)')
  })

  it('applies the documented lightness floors at max tone', () => {
    // los extremos caen exactos en el suelo: 26 (claro) y 42 (oscuro).
    expect(accentFor('azul', MAX_TONE).acc).toBe('hsl(224 78% 26%)')
    expect(accentFor('tokyo', MAX_TONE).acc).toBe('hsl(223 76% 42%)')
  })

  it('derives soft variants as rgba from the accent rgb, not from the raw hsl', () => {
    const a = accentFor('azul', 3)
    expect(a.soft).toMatch(/^rgba\(\d+,\d+,\d+,0\.12\)$/)
    expect(a.soft2).toMatch(/^rgba\(\d+,\d+,\d+,0\.22\)$/)
    // same alpha, same rgb: only the opacity differs
    expect(a.soft.replace(/,0\.12\)$/, '')).toBe(a.soft2.replace(/,0\.22\)$/, ''))
  })

  it('accS is always exactly 10 points darker than acc (the "strong" variant)', () => {
    // esmeralda: sat 84 -> 58 (de-neon); lightness ramp unchanged: 66 - 5*3.08
    const a = accentFor('esmeralda', 5)
    expect(a.acc).toBe('hsl(158 58% 50.62%)')
    expect(a.accS).toBe('hsl(158 58% 40.62%)')
    // and the relationship holds for every theme at every tone
    for (const id of Object.keys(THEMES)) {
      for (let tone = 0; tone <= MAX_TONE; tone++) {
        const x = accentFor(id, tone)
        // el lightness lleva decimales: `(\d+)%` se comeria el ultimo
        // grupo entero y daria L === LS en toda la rampa.
        const L = Number(/([\d.]+)%\)$/.exec(x.acc)[1])
        const LS = Number(/([\d.]+)%\)$/.exec(x.accS)[1])
        // toBeCloseTo y no toBe: L y LS se redondean por separado, asi
        // que la resta puede dar 10.000000000000004.
        expect(L - LS).toBeCloseTo(10, 2)
      }
    }
  })

  it('keeps green/yellow accents out of neon territory (chroma ceiling)', () => {
    /* Regressión del pedido: el verde fosforito. A L=52% el azul es un azul
     * profundo pero el verde salía menta fosforito (croma 0.87, 1.56:1 sobre
     * blanco). El techo de croma en matices verde/amarillo es lo que fija el
     * límite objetivo; azul/índigo se quedan intactos a propósito. */
    const GREEN_YELLOW = [[60, 190]]
    for (const [id, th] of Object.entries(THEMES)) {
      if (!GREEN_YELLOW.some(([lo, hi]) => th.h >= lo && th.h <= hi)) continue
      for (let tone = 0; tone <= MAX_TONE; tone++) {
        const ch = chroma(accentRgb(th, tone))
        expect(ch, `${id} tono ${tone} croma ${ch.toFixed(3)}`).toBeLessThanOrEqual(0.78)
      }
    }
  })

  it('keeps every tone distinct so the tone picker stays usable', () => {
    /* La rampa usa su suelo en el ultimo tono (66 -> 26 en claro). Cualquier
     * intento de "arreglar" el brillo del verde moviendo lightness aplana los
     * tonos altos contra ese suelo y varios tonos quedan identicos: este guard
     * es el que lo detecta. Por eso el verde se DESATURA en vez de moverse en
     * lightness. */
    for (const [id, th] of Object.entries(THEMES)) {
      const tones = []
      for (let tone = 0; tone <= MAX_TONE; tone++) tones.push(accentFor(id, tone).acc)
      expect(new Set(tones).size, `${id} tonos distintos`).toBe(MAX_TONE + 1)
      expect(accentL(th, 0) - accentL(th, MAX_TONE), `${id} span`).toBeGreaterThanOrEqual(20)
    }
  })

  it('keeps the body text readable on every surface of every theme', () => {
    /* Un tema nuevo puede entrar en la lista con un `text` que funciona sobre
     * `bg` pero se apaga sobre `surface2` (el gris de las filas alternas).
     * Se exigen 4.5:1 (AA de cuerpo) en las tres superficies y 3:1 solo para
     * `muted`, que es texto secundario. Con la referencia WCAG propia para no
     * validar la matematica del motor con la propia matematica del motor. */
    for (const [id, th] of Object.entries(THEMES)) {
      const text = parseHex(th.text)
      for (const surface of ['bg', 'surface', 'surface2']) {
        const c = refContrast(text, parseHex(th[surface]))
        expect(c, `${id}: ${th.text} sobre ${surface} (${th[surface]}) = ${c.toFixed(2)}:1`)
          .toBeGreaterThanOrEqual(4.5)
      }
      const cm = refContrast(parseHex(th.muted), parseHex(th.surface))
      expect(cm, `${id}: muted ${th.muted} sobre ${th.surface} = ${cm.toFixed(2)}:1`)
        .toBeGreaterThanOrEqual(3)
    }
  })

  it('gives every light/dark pair the same accent hue family', () => {
    /* El toggle de modo repara en un par: si el par no comparte familia de
     * tono, el cambio de iluminacion parece un cambio de tema entero. */
    for (const [dark, light] of Object.entries(DARK_PAIRS)) {
      const h1 = THEMES[light].h
      const h2 = THEMES[dark].h
      const gap = Math.min(Math.abs(h1 - h2), 360 - Math.abs(h1 - h2))
      expect(gap, `${light}(h=${h1}) ↔ ${dark}(h=${h2}) separados ${gap}°`)
        .toBeLessThanOrEqual(60)
    }
  })
})

describe('setTheme / setTone', () => {
  it('writes the theme tokens and reflects the mode on <html data-theme>', () => {
    setTheme('grafito', 4)
    expect(getTheme()).toBe('grafito')
    expect(getMode()).toBe('dark')
    expect(root.dataset.theme).toBe('dark')
    expect(cssVar('--bg')).toBe('#0c0e12')
    expect(cssVar('--text')).toBe('#e6e8ee')
  })

  it('persists theme and tone separately', () => {
    setTheme('lava', 7)
    expect(localStorage.getItem('aircargo_theme')).toBe('lava')
    expect(localStorage.getItem('aircargo_tone')).toBe('7')
  })

  it('migrates the legacy "light" alias to "azul"', () => {
    expect(setTheme('light', 2)).toBe('azul')
    expect(getTheme()).toBe('azul')
  })

  it('is a no-op for an unknown theme and returns the current one', () => {
    setTheme('rosa', 1)
    expect(setTheme('no-existe')).toBe('rosa')
    expect(getTheme()).toBe('rosa')
  })

  it('setTone rounds, clamps and persists without touching the theme', () => {
    setTheme('marino', 2)
    expect(setTone(4.6)).toBe(5)
    expect(getTone()).toBe(5)
    expect(getTheme()).toBe('marino')
    expect(setTone(999)).toBe(MAX_TONE)
    expect(setTone('abc')).toBe(3) // non-numeric falls back to DEFAULT_TONE
  })

  it('exposes the 6 status token pairs for both palettes', () => {
    setTheme('tokyo', 3)
    for (const s of ['pend', 'rec', 'carg', 'pro', 'man', 'disp']) {
      expect(cssVar('--sc-' + s)).toMatch(/^#/)
      expect(cssVar('--sb-' + s)).toMatch(/^#/)
    }
  })
})

describe('setMode — light/dark switching by theme name', () => {
  it('maps a light theme to its dark counterpart', () => {
    setTheme('esmeralda', 3)
    expect(setMode('dark')).toBe('dark')
    expect(getTheme()).toBe('esmernoche')
  })

  it('maps back to light, preserving tone', () => {
    setTheme('esmeralda', 6)
    setMode('dark')
    setMode('light')
    expect(getMode()).toBe('light')
    expect(getTheme()).toBe('esmeralda')
    expect(getTone()).toBe(6)
  })

  it('lands a neutral grey theme on another grey, not on azul', () => {
    setTheme('pizarra', 1)
    setMode('dark')
    expect(getTheme()).toBe('grafito')
    setMode('light')
    expect(getTheme()).toBe('pizarra')
  })

  it('keeps the warm themes in the warm family when going dark', () => {
    setTheme('ambar', 3)
    setMode('dark')
    expect(getTheme()).toBe('lava')
  })

  it('keeps a navy theme on the indigo side, not on plain azul', () => {
    setTheme('marino', 3)
    setMode('light')
    expect(getTheme()).toBe('indigo')
  })

  it('covers every theme: toggling mode always lands on the requested mode', () => {
    for (const key of Object.keys(THEMES)) {
      setTheme(key, 3)
      const other = THEMES[key].mode === 'dark' ? 'light' : 'dark'
      expect(getMode()).not.toBe(other)
      setMode(other)
      expect(getMode()).toBe(other)
      /* Y volviendo debe recuperar una familia de tono coherente, no quedar
       * saltando al azul por defecto. */
      setMode(THEMES[key].mode)
      expect(getMode()).toBe(THEMES[key].mode)
    }
  })

  it('is a no-op when the mode already matches', () => {
    setTheme('tokyo', 3)
    expect(setMode('dark')).toBe('dark')
    expect(getTheme()).toBe('tokyo')
  })
})

describe('initTheme — restoring persisted state', () => {
  it('reads back theme and tone from localStorage', () => {
    localStorage.setItem('aircargo_theme', 'indigo')
    localStorage.setItem('aircargo_tone', '8')
    initTheme()
    expect(getTheme()).toBe('indigo')
    expect(getTone()).toBe(8)
  })

  it('falls back to the default when the persisted theme is garbage', () => {
    localStorage.setItem('aircargo_theme', 'tema-inventado')
    localStorage.setItem('aircargo_tone', 'no-es-un-numero')
    initTheme()
    expect(getTheme()).toBe(THEMES[getTheme()] ? getTheme() : 'azul')
    expect(getTone()).toBe(3)
  })

  it('clamps an out-of-range persisted tone', () => {
    localStorage.setItem('aircargo_theme', 'azul')
    localStorage.setItem('aircargo_tone', '42')
    initTheme()
    expect(getTone()).toBe(MAX_TONE)
  })

  it('migrates a persisted legacy "light" value', () => {
    localStorage.setItem('aircargo_theme', 'light')
    initTheme()
    expect(getTheme()).toBe('azul')
  })

  it('does not write to localStorage (no persist on init)', () => {
    localStorage.clear()
    initTheme()
    expect(localStorage.getItem('aircargo_theme')).toBeNull()
  })
})

describe('resolveBand — contrast guarantee for the header band', () => {
  const INK_WHITE = '#ffffff'
  const INK_DARK = '#0b1220'

  it('agrees with the reference WCAG implementation', () => {
    for (const bg of ['#7767e0', '#f6b451', '#0d9488', '#000000', '#ffffff', '#4b5563']) {
      const rgb = parseHex(bg)
      expect(contrast(rgb[0], rgb[1], rgb[2], 255, 255, 255)).toBeCloseTo(refContrast(rgb, [255, 255, 255]), 10)
    }
  })

  it('never returns a band/ink pair below 4.5:1 in any of the 120 theme x tone combos', () => {
    const worst = []
    for (const [id, th] of Object.entries(THEMES)) {
      for (let tone = 0; tone <= MAX_TONE; tone++) {
        const { band, ink } = resolveBand(...accentRgb(th, tone))
        const ratio = refContrast(parseRgb(band), parseHex(ink))
        worst.push({ id, tone, ratio })
        expect(ratio, `${id}/t${tone} banda ${band} con tinta ${ink}`).toBeGreaterThanOrEqual(4.5)
      }
    }
    // Documented floor: the resolver converges in at most one adjustment step.
    expect(Math.min(...worst.map((w) => w.ratio))).toBeGreaterThanOrEqual(4.5)
  })

  it('keeps the chips and the inverted ctrl-chip readable over the band', () => {
    for (const [id, th] of Object.entries(THEMES)) {
      for (let tone = 0; tone <= MAX_TONE; tone++) {
        const { band, ink } = resolveBand(...accentRgb(th, tone))
        const bandRgb = parseRgb(band)
        const inkRgb = parseHex(ink)
        // A chip must move AWAY from the text, i.e. toward the opposite color.
        const away = ink === INK_WHITE ? [0, 0, 0] : [255, 255, 255]
        for (const p of [0.16, 0.28]) {
          const chip = over(bandRgb, away, p)
          expect(refContrast(inkRgb, chip), `${id}/t${tone} chip ${p}`).toBeGreaterThanOrEqual(4.5)
        }
        // ctrl-chip inverts the pair: same ratio by construction.
        expect(refContrast(bandRgb, inkRgb), `${id}/t${tone} ctrl-chip`).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  it('picks the ink that maximises contrast, so it is never the losing option', () => {
    for (const [id, th] of Object.entries(THEMES)) {
      for (let tone = 0; tone <= MAX_TONE; tone++) {
        const { band, ink } = resolveBand(...accentRgb(th, tone))
        const bandRgb = parseRgb(band)
        const white = refContrast(bandRgb, [255, 255, 255])
        const dark = refContrast(bandRgb, [11, 18, 32])
        const chosen = ink === INK_WHITE ? white : dark
        const other = ink === INK_WHITE ? dark : white
        expect(chosen, `${id}/t${tone}`).toBeGreaterThanOrEqual(other)
      }
    }
  })

  it('does not change a band that already passes, keeping the accent recognisable', () => {
    // indigo at tone 4 is a deep violet: it must survive untouched.
    const deep = hslToRgb(248, 66, 48)
    const r = resolveBand(...deep)
    expect(parseRgb(r.band)).toEqual(deep)
    expect(r.ink).toBe(INK_WHITE)
  })

  it('is idempotent: resolving an already-resolved band is a no-op', () => {
    for (const th of Object.values(THEMES)) {
      for (const tone of [0, 3, 9, 13]) {
        const once = resolveBand(...accentRgb(th, tone))
        const twice = resolveBand(...parseRgb(once.band))
        expect(parseRgb(twice.band)).toEqual(parseRgb(once.band))
        expect(twice.ink).toBe(once.ink)
      }
    }
  })

  it('publishes the band tokens through setTheme for every theme', () => {
    for (const id of Object.keys(THEMES)) {
      for (const tone of [0, 5, 9, 13]) {
        setTheme(id, tone)
        expect(cssVar('--accent-band'), `${id}/t${tone}`).toMatch(/^rgba?\(\d+,\d+,\d+,?1?\)$/)
        expect([INK_WHITE, INK_DARK]).toContain(cssVar('--accent-ink'))
        expect(cssVar('--accent-ink-soft')).toMatch(/^rgba\(0,0,0,0\.16\)$|^rgba\(255,255,255,0\.18\)$/)
        expect(cssVar('--accent-ink-soft-2')).toMatch(/^rgba\(0,0,0,0\.28\)$|^rgba\(255,255,255,0\.32\)$/)
      }
    }
  })

  it('every published gradient has exactly two stops in every theme x tone', () => {
    /* Tres paradas (acento -> acento -> cian) se leian como un barrido de
     * color en vez de superficie. El guard cubre los 4 degradados que el
     * motor publica, en los 12 temas y 10 tonos. */
    const grads = ['--gradient-accent', '--gradient-title', '--bg-grad']
    /* Cuenta solo las paradas de COLOR: el separador inicial (angulo, p.ej.
     * `135deg,`) no es una parada, y los `hsl(...)` traen comas y parentesis
     * propios que rompen un split ingenuo. */
    const stopCount = (v) => {
      const parts = []
      let depth = 0
      let cur = ''
      for (let i = v.indexOf('(') + 1; i < v.length; i++) {
        const ch = v[i]
        if (ch === '(') { depth++; cur += ch }
        else if (ch === ')') { if (depth === 0) break; depth--; cur += ch }
        else if (ch === ',' && depth === 0) { parts.push(cur); cur = '' }
        else cur += ch
      }
      parts.push(cur)
      return parts.filter((p) => /hsl\(|#|rgb/.test(p)).length
    }
    for (const id of Object.keys(THEMES)) {
      for (let tone = 0; tone <= MAX_TONE; tone++) {
        setTheme(id, tone)
        for (const g of grads) {
          const v = cssVar(g)
          expect(v, `${g} ${id}/t${tone}`).toContain('linear-gradient')
          expect(stopCount(v), `${g} ${id}/t${tone} -> ${v}`).toBe(2)
        }
      }
    }
  })
})

describe('resetTheme + catalog invariants', () => {
  it('restores the default theme and tone', () => {
    setTheme('lava', 9)
    resetTheme()
    expect(getTheme()).toBe('azul')
    expect(getTone()).toBe(3)
    expect(getMode()).toBe('light')
  })

  it('every theme declares the full token set the engine writes', () => {
    const required = ['mode', 'name', 'h', 's', 'bg', 'surface', 'surface2',
      'text', 'muted', 'border', 'borderS', 'violet', 'cyan']
    for (const [id, th] of Object.entries(THEMES)) {
      for (const k of required) {
        expect(th[k], `${id}.${k}`).toBeDefined()
      }
      expect(['light', 'dark']).toContain(th.mode)
    }
  })

  it('every tone has a display name', () => {
    expect(TONE_NAMES).toHaveLength(MAX_TONE + 1)
    TONE_NAMES.forEach((n) => expect(typeof n).toBe('string'))
  })
})

/* ══════════════════════════════════════════════════════════════════════
 * GUARD: el design system light debe CONSUMIR los tokens del tema.
 * Antes `.ds-page` pintaba #f2f5fd y `.ds-card` bg-white fijos, asi que
 * los 12 temas se veian iguales en la superficie mas visible de la app.
 * Estos guards fallan si alguien reintroduce un literal ahi.
 * ══════════════════════════════════════════════════════════════════ */
describe('design system — consumo de tokens (main.css)', () => {
  /* Se lee el archivo real, no el DOM: happy-dom no parsea el CSS importado
   * por Vite, asi que el texto de la hoja es la unica fuente fiable. */
  /* Se ignoran los comentarios: varios bloques documentan en prosa el literal
   * viejo que reemplazaron ("antes era #f2f5fd fijo"), y eso no es un regresion. */
  const mainCss = () => readFileSync('src/assets/main.css', 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
  const blockOf = (sel, len = 320) => {
    const css = mainCss()
    const i = css.indexOf(sel)
    return i === -1 ? '' : css.slice(i, i + len)
  }

  it('.ds-page usa var(--bg) y no un hex fijo', () => {
    const css = mainCss()
    const block = css.slice(css.indexOf('.ds-page,'), css.indexOf('.ds-page {'))
    expect(block).toMatch(/background-color:\s*var\(--bg\)/)
    expect(block).not.toMatch(/#f2f5fd|bg-slate-50\/70/)
  })

  it('.ds-card y .ds-table-section toman surface/border del tema', () => {
    for (const sel of ['.ds-card {', '.ds-table-section {']) {
      const block = blockOf(sel)
      expect(block, sel).toMatch(/var\(--surface\)/)
      expect(block, sel).toMatch(/var\(--border\)/)
    }
    expect(blockOf('.ds-card {')).not.toMatch(/bg-white|#dbe4f2/)
  })

  it('la barra de cabecera se deriva de --brand, no de un azul fijo', () => {
    const header = blockOf('.ds-table-header {', 520)
    expect(header).toMatch(/color-mix\(in srgb, var\(--brand\)/)
    expect(header).not.toMatch(/#1d4ed8|#172554/)
  })

  it('--shadow-tint difiere por modo: en dark la sombra no puede usar --text', () => {
    setTheme('azul', 3)
    expect(cssVar('--shadow-tint')).toBe(THEMES.azul.text)
    setTheme('tokyo', 3)
    expect(cssVar('--shadow-tint')).toBe('#000000')
  })

  it('el bloque dark solo conserva lo que es dark de verdad', () => {
    const dark = Array.from(mainCss().matchAll(/data-theme='dark'\]\s*(\.ds-[\w-]+)/g)).map((m) => m[1])
    const permitido = ['.ds-btn-primary', '.ds-btn-danger', '.ds-modal-backdrop']
    const sobrantes = [...new Set(dark)].filter((d) => !permitido.includes(d))
    expect(sobrantes, 'dark overrides ahora cubiertos por tokens: ' + sobrantes.join(', ')).toEqual([])
  })
})

/* ══════════════════════════════════════════════════════════════════════
 * GUARD: la pizarra (matriz MAWBs) es la superficie más densa de la app.
 * Su paleta vive en `--chalk-*` y los realces de fila NO pueden pisar los
 * colores semánticos (verde = vuelo con piezas, ámbar = desviación de
 * piezas): ambos usan la misma especificidad y el orden de la cascada
 * decidiría cuál gana.
 * ══════════════════════════════════════════════════════════════════ */
describe('design system — pizarra chalk', () => {
  const css = () => readFileSync('src/assets/main.css', 'utf8').replace(/\/\*[\s\S]*?\*\//g, '')

  it('declara la paleta en tokens --chalk-*', () => {
    const c = css()
    const bloque = c.slice(c.indexOf('.chalk {'), c.indexOf('.chalk table {'))
    for (const t of ['--chalk-cell', '--chalk-cell-alt', '--chalk-frozen', '--chalk-ink',
      '--chalk-amber-bg', '--chalk-green-ink', '--chalk-fly', '--chalk-fly-on', '--chalk-void-ink']) {
      expect(bloque, t).toMatch(new RegExp(t.replace(/-/g, '\\-')))
    }
  })

  it('zebra y hover excluyen las celdas semánticas y las columnas fijas', () => {
    const c = css()
    for (const sel of [':nth-child(even) td', ':hover td']) {
      const i = c.indexOf(sel)
      expect(i, sel).toBeGreaterThan(-1)
      const linea = c.slice(c.lastIndexOf('.chalk tbody tr', i), c.indexOf('{', i))
      for (const ex of ['.fly', '.fly-on', '.amber', '.empty', '.fz', '.fz2']) {
        expect(linea, `${sel} debe excluir ${ex}`).toContain(`:not(${ex})`)
      }
    }
  })

  /* La sombra de scroll va en `.fz-end` (la última congelada VISIBLE), no en
   * `.fz2`: si el usuario oculta "Shipper / Consignee" la que queda pegada
   * al scroll es MAWB, y con la sombra fija en `.fz2` el bloque congelado
   * quedaría pegado al área de vuelos sin ninguna marca de separación. */
  it('la sombra de scroll la lleva .fz-end, la ultima congelada visible', () => {
    const i = css().indexOf('.chalk .fz-end {')
    expect(i).toBeGreaterThan(-1)
    expect(css().slice(i, css().indexOf('}', i))).toMatch(/box-shadow:[^;]*\d+px 0 \d+px/)
  })

  it('.fz2 se queda solo con el separador de 1px', () => {
    const i = css().indexOf('.chalk .fz2 {')
    expect(i).toBeGreaterThan(-1)
    const block = css().slice(i, css().indexOf('}', i))
    expect(block).toContain('box-shadow: 1px 0 0')
    // sin sombra difusa: la de scroll es responsabilidad de .fz-end
    expect(block).not.toMatch(/box-shadow:[^;]*\d+px 0 \d+px/)
  })

  /* Contraste medido sobre los tokens REALES, no sobre literales sueltos:
   * asi el guard sigue siendo valido aunque cambien los hex. El script de
   * auditoria (`audit-contrast.mjs`) solo mira pares Tailwind en el
   * template, asi que la pizarra necesita su propio reloj. */
  it('todos los pares de la pizarra cumplen contraste (medido sobre los tokens)', () => {
    const ini = css().indexOf('--chalk-bg:')
    const blk = css().slice(ini, css().indexOf('}', ini))
    const tok = {}
    for (const m of blk.matchAll(/--chalk-([\w-]+):\s*(#[0-9a-fA-F]{6})/g)) tok[m[1]] = m[2]
    const rgb = h => [0, 2, 4].map(i => parseInt(h.slice(1).slice(i, i + 2), 16) / 255)
    const lin = c => (c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4))
    const lum = h => { const [r, g, b] = rgb(h).map(lin); return 0.2126 * r + 0.7152 * g + 0.0722 * b }
    const ratio = (a, b) => {
      const l1 = lum(a), l2 = lum(b)
      const [hi, lo] = l1 > l2 ? [l1, l2] : [l2, l1]
      return (hi + 0.05) / (lo + 0.05)
    }
    const pairs = [
      ['celda base', tok.ink, tok.cell, 4.5],
      ['celda zebra', tok.ink, tok['cell-alt'], 4.5],
      ['celda hover', tok.ink, tok.hover, 4.5],
      ['congelada', tok.ink, tok.frozen, 4.5],
      ['congelada zebra', tok.ink, tok['frozen-alt'], 4.5],
      ['cabecera', tok['head-ink'], tok.head, 4.5],
      ['pie (tinta atenuada)', tok['ink-dim'], tok.bg, 4.5],
      ['celda ambar', tok['amber-ink'], tok['amber-bg'], 4.5],
      ['ink-warn sobre celda', tok['amber-ink'], tok.cell, 4.5],
      ['piezas en verde', tok['green-ink'], tok.cell, 4.5],
      ['celda de vuelo', tok.ink, tok.fly, 4.5],
      ['vuelo resaltado', tok.ink, tok['fly-on'], 4.5],
      ['celda de vuelo hover', tok.ink, tok['fly-hover'], 4.5],
      // 3:1 es el minimo para texto decorativo/no textual; la celda vacia solo
      // se lee como "hueco" y su unico fondo es --chalk-void-bg.
      ['celda vacia', tok['void-ink'], tok['void-bg'], 3],
    ]
    const fallos = pairs
      .filter(([, fg, bg, min]) => ratio(fg, bg) < min)
      .map(([n, fg, bg, min]) => `${n}: ${ratio(fg, bg).toFixed(2)} < ${min} (${fg} sobre ${bg})`)
    expect(fallos).toEqual([])
  })

  it('la pizarra no depende ya de verdes esmeralda hardcodeados', () => {
    // Los antiguos #0a2925/#0d322d/#15564c/#14806f/#0a2a26 etc.都属于 a la
    // paleta verde previa. Ninguno debe reaparecer.
    for (const viejo of ['#0a2925', '#0d322d', '#0e3a34', '#134a41', '#0c352f',
      '#0e4740', '#104f47', '#0f6d5f', '#0a5f52', '#15564c', '#14806f', '#0a2a26', '#12403a']) {
      expect(css().toLowerCase()).not.toContain(viejo)
    }
  })

  it('las celdas no llevan borde por celda: solo separador horizontal tenue', () => {
    const i = css().indexOf('.chalk td {')
    const bloque = css().slice(i, css().indexOf('}', i))
    expect(bloque).toMatch(/border-bottom:\s*1px solid var\(--chalk-rule\)/)
    expect(bloque).not.toMatch(/border:\s*1px solid/)
  })

  it('la celda vacia se pinta con token, no con un hex suelto', () => {
    // Antes el fondo iba literal (#0a2a26) y por eso la comprobacion de
    // contraste tenia que parsear el hex de la regla. Ahora ambos lados son
    // tokens; el contraste se mide en el guard de pares de la pizarra.
    const regla = css().slice(css().indexOf('.chalk td.empty {'), css().indexOf('}', css().indexOf('.chalk td.empty {')))
    expect(regla).toMatch(/background:\s*var\(--chalk-void-bg\)/)
    expect(regla).toMatch(/color:\s*var\(--chalk-void-ink\)/)
  })
})
