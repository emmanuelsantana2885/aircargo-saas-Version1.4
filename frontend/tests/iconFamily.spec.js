import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import * as P from '@phosphor-icons/vue'
import { mdiIcons } from '@/mdi'
import { phosphorIcons } from '@/phosphor'
import { tablerIcons } from '@/composables/tablerIcons'
import { lucideIcons } from '@/composables/lucideIcons'
import { ICON_LIBS, ICON_WEIGHTS, iconWeight } from '@/utils/iconLib'
import { cycleIconWeight } from '@/utils/iconLib'

const CANONICAL = Object.keys(mdiIcons)
const FAMILIES = { tabler: tablerIcons, lucide: lucideIcons, mdi: mdiIcons, phosphor: phosphorIcons }

/* Recorre src/ y devuelve cada clave `icons.X` que consume un componente.
 * Solo PascalCase: los mapas exponen componentes Vue (AlertTriangle, CheckAll),
 * asi que una clave en minuscula nunca es un icono. Eso descarta de raiz los
 * falsos positivos `icons.value` (accessor del computed de useIcons) y el
 * `icons.info` de ToastNotifications (objeto local de glifos unicode). */
function usedIconKeys(dir = 'src', acc = new Map()) {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, e.name)
    if (e.isDirectory()) usedIconKeys(p, acc)
    else if (/\.(vue|js)$/.test(e.name)) {
      for (const m of readFileSync(p, 'utf8').matchAll(/\bicons\.([A-Z]\w*)/g)) {
        if (!acc.has(m[1])) acc.set(m[1], new Set())
        acc.get(m[1]).add(p)
      }
    }
  }
  return acc
}

describe('phosphor icon family', () => {
  it('exposes exactly the canonical icon names, no more no less', () => {
    /* Cambiar de familia no puede obligar a tocar un componente: los tres
     * mapas tienen que exponer el mismo conjunto de claves. Si un nombre
     * falta, el icono desaparece de la UI sin error de build ni de lint. */
    expect(CANONICAL.length).toBeGreaterThan(0)
    expect(Object.keys(phosphorIcons).sort()).toEqual([...CANONICAL].sort())
  })

  it('only maps icons that really exist in the package', () => {
    /* Un import a un componente inexistente no lo detectan ni lint ni el resto
     * de la suite: solo revienta al enlazar el bundle de phosphor. El wrapper
     * etiqueta cada icono con el nombre del componente real, asi que aqui se
     * pueden comprobar todos de golpe. */
    const names = Object.values(phosphorIcons).map((c) => c.__phosphorName)
    expect(names.every(Boolean)).toBe(true)
    for (const name of names) {
      expect(Object.keys(P), `phosphorIcons apunta a ${name}, que el paquete no exporta`)
        .toContain(name)
    }
  })

  it('los cuatro mapas exponen el mismo conjunto de claves', () => {
    /* El original solo comparaba phosphor contra mdi, asi que tabler y lucide
     * podian derivar sin que nada se enterase: llego a haber `BarChart` en
     * lucide y `ChartBar` en las otras tres. Esa clave renderizaba en una
     * sola familia y salia indefinida en las otras tres al ciclar. */
    for (const [name, map] of Object.entries(FAMILIES)) {
      expect(Object.keys(map).sort(), `${name} no expone las mismas claves que mdi`)
        .toEqual([...CANONICAL].sort())
    }
  })

  it('toda clave icons.X usada en los componentes existe en las cuatro familias', () => {
    /* El hueco que mas caro sale: un icono borrado del mapa sigue
     * referenciado en la vista, el build pasa, y el componente aparece
     * vacio al renderizar. Se comprueba contra el uso real de src/. */
    const used = usedIconKeys()
    expect(used.size).toBeGreaterThan(0)
    for (const [key, files] of used) {
      for (const [name, map] of Object.entries(FAMILIES)) {
        expect(Object.keys(map), `${key} (usada en ${[...files].join(', ')}) no existe en ${name}`)
          .toContain(key)
      }
    }
  })
})

describe('icon library state', () => {
  it('cycles through the four families', () => {
    expect(ICON_LIBS).toEqual(['tabler', 'lucide', 'mdi', 'phosphor'])
  })

  it('offers only the four stroke weights phosphor can render at 14px', () => {
    expect(ICON_WEIGHTS).toEqual(['thin', 'light', 'regular', 'bold'])
  })

  it('cycles the weight and always lands on a valid value', () => {
    const start = iconWeight.value
    const seen = new Set()
    for (let i = 0; i < ICON_WEIGHTS.length; i++) {
      seen.add(iconWeight.value)
      cycleIconWeight()
    }
    expect(seen).toEqual(new Set(ICON_WEIGHTS))
    expect(iconWeight.value).toBe(start)
  })
})
