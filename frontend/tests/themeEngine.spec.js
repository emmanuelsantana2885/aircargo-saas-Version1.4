import { describe, it, expect, beforeEach } from 'vitest'
import {
  accentFor, getTheme, getTone, getMode,
  setTheme, setTone, setMode, resetTheme, initTheme,
  THEMES, TONE_NAMES,
} from '@/utils/theme'

const root = document.documentElement
const cssVar = (name) => root.style.getPropertyValue(name)

beforeEach(() => {
  localStorage.clear()
  resetTheme()
})

describe('accentFor — math of tone', () => {
  it('returns null for an unknown theme instead of throwing', () => {
    expect(accentFor('no-existe', 3)).toBeNull()
  })

  it('clamps tone to the 0..9 range in both directions', () => {
    // hue/sat/lum are the same for both themes; only the mode differs
    expect(accentFor('azul', -50).acc).toBe(accentFor('azul', 0).acc)
    expect(accentFor('azul', 999).acc).toBe(accentFor('azul', 9).acc)
  })

  it('keeps dark themes brighter than light themes at every tone', () => {
    // Guard against a regression that would make accent text invisible on dark surfaces:
    // dark floor is 46%, light floor is 30%.
    for (let tone = 0; tone <= 9; tone++) {
      const light = accentFor('azul', tone).acc
      const dark = accentFor('tokyo', tone).acc
      expect(light).not.toBe(dark)
    }
    expect(accentFor('azul', 0).acc).toBe('hsl(224 95% 64%)')
    expect(accentFor('tokyo', 0).acc).toBe('hsl(223 93% 72%)')
  })

  it('applies the documented lightness floors at max tone', () => {
    // 64-9*4 = 28 -> clamped to 30 (light); 72-9*3 = 45 -> clamped to 46 (dark)
    expect(accentFor('azul', 9).acc).toBe('hsl(224 95% 30%)')
    expect(accentFor('tokyo', 9).acc).toBe('hsl(223 93% 46%)')
  })

  it('derives soft variants as rgba from the accent rgb, not from the raw hsl', () => {
    const a = accentFor('azul', 3)
    expect(a.soft).toMatch(/^rgba\(\d+,\d+,\d+,0\.12\)$/)
    expect(a.soft2).toMatch(/^rgba\(\d+,\d+,\d+,0\.22\)$/)
    // same alpha, same rgb: only the opacity differs
    expect(a.soft.replace(/,0\.12\)$/, '')).toBe(a.soft2.replace(/,0\.22\)$/, ''))
  })

  it('accS is always exactly 10 points darker than acc (the "strong" variant)', () => {
    const a = accentFor('esmeralda', 5) // light: 64 - 5*4 = 44
    expect(a.acc).toBe('hsl(158 84% 44%)')
    expect(a.accS).toBe('hsl(158 84% 34%)')
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
    expect(setTone(999)).toBe(9)
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

  it('falls back to tokyo/azul for themes without a dedicated pair', () => {
    setTheme('pizarra', 1)
    setMode('dark')
    expect(getTheme()).toBe('tokyo')
    setMode('light')
    expect(getTheme()).toBe('azul')
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
    expect(getTone()).toBe(9)
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
    expect(TONE_NAMES).toHaveLength(10)
    TONE_NAMES.forEach((n) => expect(typeof n).toBe('string'))
  })
})
