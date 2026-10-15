import { createI18n } from 'vue-i18n'
import en from './en.js'
import es from './es.js'

const saved = localStorage.getItem('aircargo_lang') || 'es'

const i18n = createI18n({
  legacy: false,
  locale: saved,
  fallbackLocale: 'en',
  messages: { en, es },
})

/* Sincroniza `<html lang>` con el idioma activo. Sin esto el atributo se
 * quedaba en el valor del index.html ("es") aunque el usuario estuviera en
 * inglés, y el lector de pantalla anunciaba todo en español. */
document.documentElement.setAttribute('lang', i18n.global.locale.value)

export default i18n
