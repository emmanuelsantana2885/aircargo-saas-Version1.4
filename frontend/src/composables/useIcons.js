import { computed, shallowRef, watch } from 'vue'
import { iconLib } from '@/utils/iconLib'

const LOADERS = {
  tabler: () => import('./tablerIcons'),
  lucide: () => import('./lucideIcons'),
  mdi: () => import('@/mdi'),
  phosphor: () => import('@/phosphor'),
}
const PICK = (m) => m.tablerIcons || m.lucideIcons || m.mdiIcons || m.phosphorIcons

const maps = new Map()
const current = shallowRef({})

async function loadLib(lib) {
  if (maps.has(lib)) return maps.get(lib)
  const mod = await LOADERS[lib]()
  const map = PICK(mod)
  maps.set(lib, map)
  return map
}

async function ensure(lib) {
  try {
    const map = await loadLib(lib)
    if (map !== current.value) current.value = map
  } catch {
    // never let icon loading break the UI
  }
}

watch(iconLib, (lib) => ensure(lib), { immediate: true })

/* Precarga de las demas familias: con 4 sets esto ya no cabe en una ternaria
 * anidada, y la intencion era la misma (no paginar un import al primer clic).
 * Se usa el orden de VALID para que la familia siguiente en el ciclo sea la
 * primera precargada. */
if (typeof window !== 'undefined' && 'requestIdleCallback' in window) {
  window.requestIdleCallback(() => {
    Object.keys(LOADERS)
      .filter((lib) => lib !== iconLib.value)
      .forEach((lib) => loadLib(lib).catch(() => {}))
  }, { timeout: 3000 })
}

export function useIcons() {
  return computed(() => current.value)
}
