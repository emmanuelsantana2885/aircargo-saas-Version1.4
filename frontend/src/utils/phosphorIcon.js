import { h, computed } from 'vue'
import { iconWeight } from './iconLib'

/* Phosphor no usa `stroke-width` como Lucide/Tabler: sus variantes de grosor
 * son un prop `weight` enumerado (thin, light, regular, bold, fill, duotone).
 * Este adaptador envuelve el componente real para traducir el peso global de la
 * app, de modo que el patron de uso `<component :is="icons.X" :size="14" />` no
 * cambia y el usuario elige el grosor desde el panel de apariencia.
 *
 * `duotone`/`fill` son unusables en un set de iconos de 14px sobre texto, asi
 * que el peso global se mapea a los tres niveles de trazo reales. */
const WEIGHTS = { thin: 'thin', light: 'light', regular: 'regular', bold: 'bold', fill: 'regular', duotone: 'regular' }

export function phosphorIcon(component) {
  return {
    name: 'PhosphorIcon',
    /* Se guarda el nombre del componente original para que el guard de paridad
     * pueda comprobar que cada icono apunta a algo que el paquete exporta de
     * verdad. Un import a un componente inexistente no lo detecta ni lint ni
     * el resto de la suite: solo revienta en el bundle de phosphor. */
    __phosphorName: component && (component.name || component.__name),
    props: {
      size: { type: [Number, String], default: 24 },
      strokeWidth: { type: [Number, String], default: 2 }
    },
    setup(props) {
      const weight = computed(() => WEIGHTS[iconWeight.value] || 'regular')
      return () => h(component, { size: props.size, weight: weight.value })
    }
  }
}
