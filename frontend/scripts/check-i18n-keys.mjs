#!/usr/bin/env node
/* Verifica que las claves de i18n usadas en templates existan en es y en.
 *
 * check-sfc-refs.mjs sólo recorre src/views/, por eso no detectó que
 * Sidebar.vue pintaba 'sidebar.exports' y 'sidebar.apiCatalog' crudas
 * (no existían en ningún idioma) ni que el encabezado de sección de
 * administración reusaba la etiqueta de la vista /settings.
 *
 * Cubre src/ entero (views + components) y falla si una clave se usa sin
 * existir en alguno de los dos idiomas.
 */
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const SRC = new URL('../src/', import.meta.url).pathname

function walk(dir, out = []) {
  for (const e of readdirSync(dir)) {
    const p = join(dir, e)
    if (statSync(p).isDirectory()) walk(p, out)
    else if (p.endsWith('.vue')) out.push(p)
  }
  return out
}

/* Aplanamos los locales reales importándolos como módulos ES. Un parser
 * textual se rompía con los objetos anidados de 3+ niveles
 * (bookings.status.RECEIVED, filterBar.periods.today, …). */
function flatten(obj, prefix = '', out = new Set()) {
  for (const [k, v] of Object.entries(obj || {})) {
    const key = prefix ? `${prefix}.${k}` : k
    if (v && typeof v === 'object' && !Array.isArray(v)) flatten(v, key, out)
    else out.add(key)
  }
  return out
}

const es = flatten((await import(join(SRC, 'i18n/es.js'))).default)
const en = flatten((await import(join(SRC, 'i18n/en.js'))).default)

/* t('x.y') y t("x.y") literales. Se ignoran t(`x.${v}`) dinámicas y las
 * pseudo-claves __NOT_RECEIVED del WarehouseReceipts (filtros de cliente). */
const USE = /\bt\(\s*['"]([a-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)['"]/g
const KNOWN_DYNAMIC = new Set(['__NOT_RECEIVED', '__NOT_DISPATCHED'])

const missing = []
const files = walk(SRC)
for (const file of files) {
  const text = readFileSync(file, 'utf8')
  const seen = new Set()
  let m
  while ((m = USE.exec(text))) {
    const key = m[1]
    if (seen.has(key) || KNOWN_DYNAMIC.has(key)) continue
    seen.add(key)
    const inEs = es.has(key)
    const inEn = en.has(key)
    if (!inEs || !inEn) {
      missing.push({ file: relative(SRC, file), key, inEs, inEn })
    }
  }
}

/* El router también referencia claves (meta.titleKey y PUBLIC_TITLES) y
 * ninguna aparece en un template, así que hacía falta mirarlo aparte: un
 * typo ahí pintaba la clave cruda en la pestaña del navegador. */
const routerPath = join(SRC, 'router/index.js')
const routerRefs = []
if (existsSync(routerPath)) {
  const text = readFileSync(routerPath, 'utf8')
  const lineOf = (idx) => text.slice(0, idx).split('\n').length
  const keyRe = /'([a-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)'/g
  // meta.titleKey: 'x.y'
  for (const m of text.matchAll(/titleKey:\s*'([a-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)'/g)) {
    routerRefs.push({ key: m[1], line: lineOf(m.index) })
  }
  // PUBLIC_TITLES: cada entrada es 'path': 'clave.con.dots' — el bloque entero
  // se recorre porque el patrón anterior (no-greedy + un solo ':') solo veía la 1ª.
  const block = /const\s+PUBLIC_TITLES\s*=\s*\{([\s\S]*?)\n\}/.exec(text)
  if (block) {
    for (const m of block[1].matchAll(keyRe)) {
      routerRefs.push({ key: m[1], line: lineOf(block.index + m.index) })
    }
  }
}
for (const { key, line } of routerRefs) {
  if (!es.has(key) || !en.has(key)) {
    missing.push({ file: `router/index.js:${line}`, key, inEs: es.has(key), inEn: en.has(key) })
  }
}

const total = files.length
if (missing.length) {
  console.error(`✗ ${missing.length} clave(s) de i18n usada(s) y sin definir:`)
  for (const x of missing) {
    const falta = [!x.inEs && 'es', !x.inEn && 'en'].filter(Boolean).join('+')
    console.error(`  ${x.key.padEnd(38)} falta en ${falta.padEnd(6)} ${x.file}`)
  }
  process.exit(1)
}
console.log(`✓ i18n: ${total} SFC + ${routerRefs.length} rutas, ${es.size} claves es / ${en.size} en — 0 referencias sin definir`)
