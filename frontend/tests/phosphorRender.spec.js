import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { phosphorIcons } from '@/phosphor'
import { iconWeight } from '@/utils/iconLib'
import { iconLib } from '@/utils/iconLib'

/* El tree-shaking de Vite puede quedarse sin iconos si el mapa no llega a
 * referenciarlos de forma que el bundler los vea. Ni el build ni el lint lo
 * detectan: el componente simplemente se renderiza vacio. Montar de verdad es
 * la unica comprobacion fiable de que hay un <svg> con contenido. */
describe('phosphor icons render', () => {
  it('renders a real svg with path data', () => {
    const w = mount(phosphorIcons.Search, { props: { size: 16 } })
    const svg = w.find('svg')
    expect(svg.exists()).toBe(true)
    expect(svg.find('path').exists()).toBe(true)
    expect(svg.attributes('width')).toBe('16')
  })

  it('every canonical icon renders an svg with a path', () => {
    const rotos = Object.entries(phosphorIcons)
      .filter(([, comp]) => {
        const w = mount(comp, { props: { size: 16 } })
        return !w.find('svg path').exists()
      })
      .map(([name]) => name)
    expect(rotos, `iconos phosphor que no renderizan: ${rotos.join(', ')}`).toEqual([])
  })

  it('re-renders with different geometry when the global weight changes', () => {
    /* Phosphor no expone el grosor como atributo: lo hornea en los datos del
     * path. Por eso se compara el `d`, que es la unica senal observable. */
    const pathAt = (w) => {
      iconWeight.value = w
      return mount(phosphorIcons.Search, { props: { size: 16 } }).find('svg path').attributes('d')
    }
    const seen = ['thin', 'light', 'regular', 'bold'].map(pathAt)
    expect(new Set(seen).size, 'los 4 grosores deben dar paths distintos').toBe(4)
    iconWeight.value = 'regular'
  })

  it('ignores strokeWidth: the global weight wins, as designed', () => {
    /* Todos los call sites del repo pasan :stroke-width="2". Si ese prop
     * mandara sobre el peso global, la eleccion del usuario no tendria efecto
     * en ninguna pantalla. Se acepta por compatibilidad, pero manda el peso. */
    iconWeight.value = 'bold'
    const bold = mount(phosphorIcons.Search, { props: { size: 16 } }).find('svg path').attributes('d')
    for (const sw of [1, 2, 3]) {
      const d = mount(phosphorIcons.Search, { props: { size: 16, strokeWidth: sw } })
        .find('svg path').attributes('d')
      expect(d, `strokeWidth ${sw} no debe alterar el grosor elegido`).toBe(bold)
    }
    iconWeight.value = 'regular'
  })

  it('collapses fill and duotone to regular for a 14px icon set', () => {
    /* fill/duotone llenan la figura y a 14px emborronan el texto; el selector
     * solo ofrece los 4 niveles de trazo, pero el wrapper no debe romperse si
     * llega un valor de otro origen. */
    const pathAt = (w) => {
      iconWeight.value = w
      return mount(phosphorIcons.Search, { props: { size: 16 } }).find('svg path').attributes('d')
    }
    const regular = pathAt('regular')
    expect(pathAt('fill')).toBe(regular)
    expect(pathAt('duotone')).toBe(regular)
    iconWeight.value = 'regular'
  })

  it('phosphor can be selected as the active family', () => {
    iconLib.value = 'phosphor'
    expect(iconLib.value).toBe('phosphor')
    expect(typeof phosphorIcons).toBe('object')
  })
})
