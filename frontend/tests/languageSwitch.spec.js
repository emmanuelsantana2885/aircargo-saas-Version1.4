import { describe, it, expect, beforeEach } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import LanguageSwitcher from '../src/components/LanguageSwitcher.vue'
import i18n from '../src/i18n'

const here = dirname(fileURLToPath(import.meta.url))
const src = (p) => readFileSync(join(here, '..', 'src', p), 'utf8')

/* El bug que(reportaba el usuario: `LanguageSwitcher.vue` existia pero no lo
 * montaba NINGUN componente, asi que no habia forma de cambiar de idioma. El
 * archivo podia seguir siendo correcto y la UI seguir muerta, por eso el guard
 * estatico ademas del test de comportamiento. */
describe('el selector de idioma esta montado y es alcanzable', () => {
  it('Header.vue lo importa y lo renderiza', () => {
    const header = src('components/layout/Header.vue')
    expect(header).toMatch(/import\s+LanguageSwitcher\s+from/)
    expect(header).toMatch(/<LanguageSwitcher\s*\/>/)
  })

  it('esta en la barra de acciones, no en un rama v-if que lo esconda', () => {
    const header = src('components/layout/Header.vue')
    /* El bloque de acciones abre despues de .header-actions; el switcher tiene
     * que caer dentro, si no quedaria debajo del header o no se veria. */
    const start = header.indexOf('class="header-actions"')
    const end = header.indexOf('</div>', start)
    expect(start).toBeGreaterThan(-1)
    expect(header.slice(start, end + 6)).toContain('<LanguageSwitcher />')
  })

  it('el chip de sitio ya no rotula "Idioma" (era texto sin accion)', () => {
    expect(src('components/layout/Header.vue')).not.toMatch(/t\('common\.lang'\)\s*·/)
  })
})

describe('LanguageSwitcher — cambio de idioma', () => {
  beforeEach(() => {
    localStorage.clear()
    i18n.global.locale.value = 'es'
  })

  it('expone los dos idiomas y marca el activo', () => {
    const w = mount(LanguageSwitcher, { global: { plugins: [i18n] } })
    const btns = w.findAll('button')
    expect(btns.map((b) => b.text())).toEqual(['ES', 'EN'])
    expect(btns[0].attributes('aria-pressed')).toBe('true')
    expect(btns[1].attributes('aria-pressed')).toBe('false')
  })

  it('cambia el locale activo de la app al pulsar', async () => {
    const w = mount(LanguageSwitcher, { global: { plugins: [i18n] } })
    await w.findAll('button')[1].trigger('click')
    expect(i18n.global.locale.value).toBe('en')
    await nextTick()
    expect(w.findAll('button')[1].attributes('aria-pressed')).toBe('true')
  })

  it('persiste la eleccion para la proxima sesion', async () => {
    const w = mount(LanguageSwitcher, { global: { plugins: [i18n] } })
    await w.findAll('button')[1].trigger('click')
    expect(localStorage.getItem('aircargo_lang')).toBe('en')
  })

  it('volver al idioma ya activo no toca el storage (no ensucia la preferencia)', async () => {
    const w = mount(LanguageSwitcher, { global: { plugins: [i18n] } })
    await w.findAll('button')[1].trigger('click')
    localStorage.removeItem('aircargo_lang')
    await w.findAll('button')[1].trigger('click')
    expect(localStorage.getItem('aircargo_lang')).toBeNull()
  })

  it('los textos de la interfaz cambian de verdad al cambiar de idioma', async () => {
    /* El ultimo test antes de este trabajo cambio 'Panel'/'Tablero' en las
     * traducciones, pero si el locale no fuera reactivo el nombre de la vista
     * se veria congelado. */
    const w = mount(LanguageSwitcher, { global: { plugins: [i18n] } })
    expect(i18n.global.t('sidebar.dashboard')).toBe('Tablero')
    await w.findAll('button')[1].trigger('click')
    expect(i18n.global.t('sidebar.dashboard')).toBe('Dashboard')
  })
})

describe('el idioma se propaga al documento', () => {
  it('<html lang> refleja el idioma activo', () => {
    /* Sin esto un lector de pantalla anunciaba todo en español con la app en
     * inglés: el atributo se quedaba en el "es" del index.html. */
    expect(src('i18n/index.js')).toMatch(/setAttribute\(\s*'lang'\s*,/)
  })

  it('el <title> se reaplica al cambiar de idioma sin navegar', () => {
    const router = src('router/index.js')
    expect(router).toMatch(/watch\(/)
    expect(router).toMatch(/i18n\.global\.locale\.value/)
  })
})
