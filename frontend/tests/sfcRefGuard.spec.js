import { describe, it, expect } from 'vitest'
import { extractDefinitions } from '../scripts/check-sfc-refs.mjs'

/* Regresion del falso negativo que llevo a dos crashes reales:
 * `extractDefinitions` metia los params de arrow en el mismo conjunto que las
 * declaraciones top-level, asi que un `forEach(t => t.stop())` hacia creer al
 * guard que el template de CameraCapture.vue tenia un `t` valido. En runtime
 * Vue tiraba `TypeError: _ctx.t is not a function`.
 *
 * Contrato que fija este test:
 *  - `topLevel`: lo que un <template> puede ver (bindings de primer nivel).
 *  - `lenient`  : ademas params de arrow/function, `catch (e)` y variables de
 *                 `for`, que SI son validos dentro del script.
 */

const define = (script) => extractDefinitions(script)

describe('extractDefinitions: alcance de los simbolos', () => {
  it('un param de arrow NO cuenta como binding de template (el bug original)', () => {
    const { topLevel, lenient } = define(`
const items = [1]
items.forEach(t => t.stop())
function x() {}
`)
    expect(lenient.has('t')).toBe(true)   // valido dentro del script
    expect(topLevel.has('t')).toBe(false)  // invisible desde el template
    expect(topLevel.has('x')).toBe(true)
  })

  it('el param de useI18n SI cuenta como binding de template', () => {
    const { topLevel } = define(`
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
`)
    expect(topLevel.has('t')).toBe(true)
  })

  it('los params de una arrow multinombre llegan a lenient y no a topLevel', () => {
    const { topLevel, lenient } = define(`
const rows = [1]
rows.map((a, b) => a + b)
`)
    for (const n of ['a', 'b']) {
      expect(lenient.has(n)).toBe(true)
      expect(topLevel.has(n)).toBe(false)
    }
  })

  it('una function nombrada anidada existe en lenient (fix de splitLine)', () => {
    const { topLevel, lenient } = define(`
function parse(t) {
  function splitLine(line) { return line.split(',') }
  return splitLine(t)
}
`)
    expect(lenient.has('splitLine')).toBe(true)
    expect(topLevel.has('splitLine')).toBe(false)
  })

  it('un const anidado no se filtra a topLevel', () => {
    const { topLevel, lenient } = define(`
function outer() {
  const inner = 1
  return inner
}
`)
    expect(topLevel.has('inner')).toBe(false)
    expect(lenient.has('inner')).toBe(true)
  })

  it('una llave desbalanceada dentro de un literal no rompe el nivel superior', () => {
    // El depth map crudo ve `}` de mas dentro del regex y de la cadena; el
    // stripped los neutraliza. Hace falta el OR de ambos para no perder
    // declaraciones de primer nivel (los 185 falsos positivos de WarehouseReceipts).
    const { topLevel } = define(`
const re = /}/g
const s = 'a } b'
function splitLine(line) { return line }
const y = splitLine('a')
`)
    expect(topLevel.has('splitLine')).toBe(true)
  })

  it('catch y variables de for no se exported al template', () => {
    const { topLevel, lenient } = define(`
try { risky() } catch (err) { log(err) }
for (const item of list) { use(item) }
`)
    expect(lenient.has('err')).toBe(true)
    expect(topLevel.has('err')).toBe(false)
    expect(lenient.has('item')).toBe(true)
    expect(topLevel.has('item')).toBe(false)
  })
})
