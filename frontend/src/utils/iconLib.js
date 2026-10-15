import { ref, watchEffect } from 'vue'

const STORAGE_KEY = 'aircargo_icon_lib'
const WEIGHT_KEY = 'aircargo_icon_weight'
const VALID = ['tabler', 'lucide', 'mdi', 'phosphor']

/* El peso global solo aplica a Phosphor (unico set con grosor enumerado); en
 * Tabler/Lucide/MDI el grosor ya lo fija cada componente. */
const VALID_WEIGHTS = ['thin', 'light', 'regular', 'bold']

export const iconLib = ref(
  VALID.includes(localStorage.getItem(STORAGE_KEY)) ? localStorage.getItem(STORAGE_KEY) : 'tabler'
)

export const iconWeight = ref(
  VALID_WEIGHTS.includes(localStorage.getItem(WEIGHT_KEY)) ? localStorage.getItem(WEIGHT_KEY) : 'regular'
)

watchEffect(() => {
  localStorage.setItem(STORAGE_KEY, iconLib.value)
})

watchEffect(() => {
  localStorage.setItem(WEIGHT_KEY, iconWeight.value)
})

export function toggleIconLib() {
  const idx = VALID.indexOf(iconLib.value)
  iconLib.value = VALID[(idx + 1) % VALID.length]
}

export function cycleIconWeight() {
  const idx = VALID_WEIGHTS.indexOf(iconWeight.value)
  iconWeight.value = VALID_WEIGHTS[(idx + 1) % VALID_WEIGHTS.length]
}

export { VALID as ICON_LIBS, VALID_WEIGHTS as ICON_WEIGHTS }
