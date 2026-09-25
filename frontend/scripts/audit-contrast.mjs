/* ══════════════════════════════════════════════════════════════════
   Contrast audit — deterministic WCAG check of Tailwind color pairs
   actually used in the SFC templates, under BOTH theme modes.

   Why this exists: the project remaps ~160 color utilities in
   `:root[data-theme='dark']` (main.css) so dark themes stay readable.
   That remap is global and unconditional, so a light chip keeps a
   light background while its dark text flips to light (or vice versa).
   This script measures the result instead of trusting the intent.

   Usage:  node scripts/audit-contrast.mjs [--json] [--strict]
   Exit 0 = no failures at AA, 1 = failures found.
   ══════════════════════════════════════════════════════════════════ */

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const ROOT = new URL('..', import.meta.url).pathname
const SRC = join(ROOT, 'src')
const CSS = join(SRC, 'assets/main.css')

const args = process.argv.slice(2)
const asJson = args.includes('--json')
const strict = args.includes('--strict')

/* ── Tailwind v3 default palette (only the families this app uses) ── */
const P = {
  slate: { 50: '#f8fafc', 100: '#f1f5f9', 200: '#e2e8f0', 300: '#cbd5e1', 400: '#94a3b8', 500: '#64748b', 600: '#475569', 700: '#334155', 800: '#1e293b', 900: '#0f172a', 950: '#020617' },
  gray: { 50: '#f9fafb', 100: '#f3f4f6', 200: '#e5e7eb', 300: '#d1d5db', 400: '#9ca3af', 500: '#6b7280', 600: '#4b5563', 700: '#374151', 800: '#1f2937', 900: '#111827', 950: '#030712' },
  red: { 50: '#fef2f2', 100: '#fee2e2', 200: '#fecaca', 300: '#fca5a5', 400: '#f87171', 500: '#ef4444', 600: '#dc2626', 700: '#b91c1c', 800: '#991b1b', 900: '#7f1d1d' },
  green: { 50: '#f0fdf4', 100: '#dcfce7', 200: '#bbf7d0', 300: '#86efac', 400: '#4ade80', 500: '#22c55e', 600: '#16a34a', 700: '#15803d', 800: '#166534', 900: '#14532d' },
  emerald: { 50: '#ecfdf5', 100: '#d1fae5', 200: '#a7f3d0', 300: '#6ee7b7', 400: '#34d399', 500: '#10b981', 600: '#059669', 700: '#047857', 800: '#065f46', 900: '#064e3b' },
  blue: { 50: '#eff6ff', 100: '#dbeafe', 200: '#bfdbfe', 300: '#93c5fd', 400: '#60a5fa', 500: '#3b82f6', 600: '#2563eb', 700: '#1d4ed8', 800: '#1e40af', 900: '#1e3a8a' },
  amber: { 50: '#fffbeb', 100: '#fef3c7', 200: '#fde68a', 300: '#fcd34d', 400: '#fbbf24', 500: '#f59e0b', 600: '#d97706', 700: '#b45309', 800: '#92400e', 900: '#78350f' },
  yellow: { 50: '#fefce8', 100: '#fef9c3', 200: '#fef08a', 300: '#fde047', 400: '#facc15', 500: '#eab308', 600: '#ca8a04', 700: '#a16207', 800: '#854d0e', 900: '#713f12' },
  orange: { 50: '#fff7ed', 100: '#ffedd5', 200: '#fed7aa', 300: '#fdba74', 400: '#fb923c', 500: '#f97316', 600: '#ea580c', 700: '#c2410c', 800: '#9a3412', 900: '#7c2d12' },
  purple: { 50: '#faf5ff', 100: '#f3e8ff', 200: '#e9d5ff', 300: '#d8b4fe', 400: '#c084fc', 500: '#a855f7', 600: '#9333ea', 700: '#7e22ce', 800: '#6b21a8', 900: '#581c87' },
  violet: { 50: '#f5f3ff', 100: '#ede9fe', 200: '#ddd6fe', 300: '#c4b5fd', 400: '#a78bfa', 500: '#8b5cf6', 600: '#7c3aed', 700: '#6d28d9', 800: '#5b21b6', 900: '#4c1d95' },
  indigo: { 50: '#eef2ff', 100: '#e0e7ff', 200: '#c7d2fe', 300: '#a5b4fc', 400: '#818cf8', 500: '#6366f1', 600: '#4f46e5', 700: '#4338ca', 800: '#3730a3', 900: '#312e81' },
  sky: { 50: '#f0f9ff', 100: '#e0f2fe', 200: '#bae6fd', 300: '#7dd3fc', 400: '#38bdf8', 500: '#0ea5e9', 600: '#0284c7', 700: '#0369a1', 800: '#075985', 900: '#0c4a6e' },
  cyan: { 50: '#ecfeff', 100: '#cffafe', 200: '#a5f3fc', 300: '#67e8f9', 400: '#22d3ee', 500: '#06b6d4', 600: '#0891b2', 700: '#0e7490', 800: '#155e75', 900: '#164e63' },
  teal: { 50: '#f0fdfa', 100: '#ccfbf1', 200: '#99f6e4', 300: '#5eead4', 400: '#2dd4bf', 500: '#14b8a6', 600: '#0d9488', 700: '#0f766e', 800: '#115e59', 900: '#134e4a' },
  rose: { 50: '#fff1f2', 100: '#ffe4e6', 200: '#fecdd3', 300: '#fda4af', 400: '#fb7185', 500: '#f43f5e', 600: '#e11d48', 700: '#be123c', 800: '#9f1239', 900: '#881337' },
  zinc: { 50: '#fafafa', 100: '#f4f4f5', 200: '#e4e4e7', 300: '#d4d4d8', 400: '#a1a1aa', 500: '#71717a', 600: '#52525b', 700: '#3f3f46', 800: '#27272a', 900: '#18181b', 950: '#09090b' },
  neutral: { 50: '#fafafa', 100: '#f5f5f5', 200: '#e5e5e5', 300: '#d4d4d4', 400: '#a3a3a3', 500: '#737373', 600: '#525252', 700: '#404040', 800: '#262626', 900: '#171717', 950: '#0a0a0a' },
  stone: { 50: '#fafaf9', 100: '#f5f5f4', 200: '#e7e5e4', 300: '#d6d3d1', 400: '#a8a29e', 500: '#78716c', 600: '#57534e', 700: '#44403c', 800: '#292524', 900: '#1c1917', 950: '#0c0a09' },
}
P.white = '#ffffff'
P.black = '#000000'

/* ── color math ─────────────────────────────────────────────────── */
function hexToRgb(hex) {
  let h = hex.trim().replace('#', '')
  if (h.length === 3) h = h.split('').map((c) => c + c).join('')
  const n = parseInt(h, 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}
const srgb = (c) => {
  const s = c / 255
  return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4)
}
function luminance(hex) {
  const [r, g, b] = hexToRgb(hex)
  return 0.2126 * srgb(r) + 0.7152 * srgb(g) + 0.0722 * srgb(b)
}
function contrast(fg, bg) {
  const a = luminance(fg)
  const b = luminance(bg)
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}
function parseCssColor(v) {
  if (!v) return null
  v = v.trim()
  const m = v.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/i)
  if (m) return m[0]
  // Modern space syntax with an alpha channel: `rgb(20 83 45 / 0.2)`. The dark
  // chips are translucent and sit on the dark card surface, so the alpha is
  // composited over that surface — treating it as opaque overstated the chip
  // lightness and understated how bad the dark-on-chip contrast really was.
  const rgb = v.match(/rgba?\(\s*(\d+)\s+(\d+)\s+(\d+)\s*(?:\/\s*([\d.]+))?\s*\)/)
  if (rgb) {
    const [r, g, b] = [+rgb[1], +rgb[2], +rgb[3]]
    const a = rgb[4] === undefined ? 1 : Number(rgb[4])
    if (a >= 1) return '#' + [r, g, b].map((n) => n.toString(16).padStart(2, '0')).join('')
    const over = DARK_CHIP_SURFACE
    const mix = [r, g, b].map((n, i) => Math.round(n * a + over[i] * (1 - a)))
    return '#' + mix.map((n) => Math.min(255, n).toString(16).padStart(2, '0')).join('')
  }
  if (v.startsWith('hsl')) return null // unresolvable without a full HSL solver
  return null
}
/* ── dark-mode remap table, read from main.css (single source of truth) ── */

// Surface the dark chips are painted on top of: `bg-white` remaps to #1e293b in
// the dark scope, so that is the card colour a translucent tint composites over.
const DARK_CHIP_SURFACE = hexToRgb('#1e293b')

function readDarkRemaps() {
  const css = readFileSync(CSS, 'utf8')
  const text = {}
  const bg = {}
  // Only rules inside the dark scope. A rule header may be a comma-separated
  // selector list (`:root[…dark] .a, :root[…dark] .b { … }`), so the header is
  // captured as a whole and split — matching only the first selector silently
  // dropped every class in the remaining arms.
  const re = /:root\[data-theme='dark'\]([^{}]*)\{([^{}]*)\}/gi
  let m
  while ((m = re.exec(css))) {
    const body = m[2]
    const cm = body.match(/(?:^|[;{\s])color\s*:\s*([^;]+)/i)
    const bm = body.match(/background(?:-color)?\s*:\s*([^;]+)/i)
    if (!cm && !bm) continue
    for (const sel of m[1].split(',')) {
      const sm = sel.match(/\.([a-z0-9-]+)/i)
      if (!sm) continue
      if (cm) text[sm[1]] = parseCssColor(cm[1])
      if (bm) bg[sm[1]] = parseCssColor(bm[1])
    }
  }
  return { text, bg }
}

/* ── template scanning ──────────────────────────────────────────── */
function walk(dir, out = []) {
  for (const e of readdirSync(dir)) {
    const p = join(dir, e)
    if (statSync(p).isDirectory()) walk(p, out)
    else if (p.endsWith('.vue')) out.push(p)
  }
  return out
}

const UTIL = /(?:^|\s)((?:hover|focus|active|group-hover|sm|md|lg|xl):)*((?:text|bg)-[a-z]+-\d{2,3}|(?:text|bg)-(?:white|black))(?=\s|$)/g

function findings() {
  const remap = readDarkRemaps()
  const files = walk(SRC)
  const out = []

  for (const file of files) {
    const raw = readFileSync(file, 'utf8')
    const rel = relative(ROOT, file)
    // template only: up to the first <script setup>/<script>
    const cut = raw.search(/<script\b/)
    const tpl = cut === -1 ? raw : raw.slice(0, cut)
    const tplOffset = 0

    // element-wise: class="…"
    const els = [...tpl.matchAll(/<([a-zA-Z][\w-]*)([^>]*?)>/g)]
    for (const el of els) {
      const attrs = el[2] || ''
      // Static `class="…"` plus dynamic `:class` / `v-bind:class` bindings. The
      // dynamic case carries the same literals inside ternary arms, and those
      // are exactly the pairs that hide real bugs — e.g. bg-amber-50 paired with
      // a text colour the dark remap flips to light, which is unreadable.
      const staticCls = attrs.match(/(?<!:)\bclass\s*=\s*"([^"]*)"/)
      const dynCls = [...attrs.matchAll(/(?:\b:?class|v-bind:class)\s*=\s*"([^"]*)"/g)]
        .filter((m) => m[0] !== staticCls?.[0])
        .flatMap((m) => m[1].match(/'([^']*)'|"([^"]*)"/g) || [])
        .map((lit) => lit.replace(/^['"]|['"]$/g, ''))
      const cand = [staticCls?.[1], ...dynCls].filter(Boolean)
      if (!cand.length) continue
      const line = tpl.slice(0, el.index).split('\n').length + tplOffset

      for (const cls of cand) {
        const tokens = cls.split(/\s+/).filter(Boolean)

        // Only resting-state utilities describe the rendered pair. A
        // `hover:bg-*` / `focus:text-*` token is a different state entirely and
        // comparing against it produced false positives, so variants are dropped
        // before any pairing happens.
        const resting = tokens.filter((t) => !t.includes(':'))
        const textClasses = resting.filter((t) => t.startsWith('text-'))
        const bgClasses = resting.filter((t) => t.startsWith('bg-'))
        if (!textClasses.length) continue
        // `text-[10px]`, font sizes and text transforms are not colors
        const isColor = (c) => /^text-([a-z]+-\d{2,3}|white|black)$/.test(c)
        const colors = textClasses.filter(isColor)
        if (!colors.length) continue
        // opacity variants (bg-slate-50/70) need alpha compositing to judge
        const bgs = bgClasses.filter((b) => /^bg-([a-z]+-\d{2,3}|white|black)$/.test(b))
        if (!bgs.length) continue

        for (const mode of ['light', 'dark']) {
          for (const tc of colors) {
            for (const bc of bgs) {
              const fg = mode === 'dark' ? remap.text[tc] || paletteOf(tc) : paletteOf(tc)
              const bg = mode === 'dark' ? remap.bg[bc] || paletteOf(bc) : paletteOf(bc)
              if (!fg || !bg) continue
              const ratio = contrast(fg, bg)
              // AA: 4.5 normal text, 3.0 for large (>=18.66px bold / >=24px)
              const large = tokens.some((t) => /^text-(xl|2xl|3xl|4xl|5xl)$/.test(t)) ||
                (tokens.some((t) => t === 'font-bold' || t === 'font-black') && tokens.some((t) => /^text-(lg|xl)$/.test(t)))
              const min = large ? 3 : strict ? 4.5 : 4.5
              if (ratio < min) {
                out.push({
                  file: rel,
                  line,
                  mode,
                  text: tc,
                  bg: bc,
                  fg,
                  bgHex: bg,
                  ratio: Math.round(ratio * 100) / 100,
                  min,
                  large,
                })
              }
            }
          }
        }
      }
    }
  }
  return out
}

function paletteOf(cls) {
  // strip the utility prefix generically — "text-" is 5 chars, "bg-" is 3, so a
  // hardcoded slice silently produced null for every class and skipped the mode
  const body = cls.replace(/^(?:text|bg)-/, '')
  if (body === 'white') return P.white
  if (body === 'black') return P.black
  const m = body.match(/^([a-z]+)-(\d{2,3})$/)
  if (!m) return null
  return P[m[1]]?.[m[2]] || null
}

const res = findings()

if (asJson) {
  console.log(JSON.stringify(res, null, 2))
  process.exit(res.length ? 1 : 0)
}

if (!res.length) {
  console.log('audit-contrast ✓ no same-element color pair below WCAG AA in light or dark mode.')
  process.exit(0)
}

const byFile = new Map()
for (const f of res) {
  if (!byFile.has(f.file)) byFile.set(f.file, [])
  byFile.get(f.file).push(f)
}
console.log(`audit-contrast: ${res.length} failing same-element color pair(s) below WCAG AA\n`)
for (const [file, list] of [...byFile.entries()].sort((a, b) => b[1].length - a[1].length)) {
  console.log(`${file}  (${list.length})`)
  const seen = new Set()
  for (const f of list) {
    const k = `${f.mode}|${f.text}|${f.bg}`
    if (seen.has(k)) continue
    seen.add(k)
    const lineN = f.line
    console.log(`  L${lineN}  [${f.mode}]  ${f.text} on ${f.bg}  =  ${f.ratio}:1  (min ${f.min})${f.large ? ' large' : ''}`)
  }
  console.log('')
}
process.exit(1)
