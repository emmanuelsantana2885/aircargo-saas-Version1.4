/* Node >= 22 expone un `localStorage` experimental COMO GLOBAL y, cuando no
 * recibe `--localstorage-file` con una ruta valida, lo deja como un objeto
 * vacio. Ese global se instala ANTES que el entorno de happy-dom, asi que
 * dentro de vitest `localStorage` resuelve al stub de Node en vez de al
 * `Storage` de happy-dom, y cualquier test que llame a `localStorage.clear()`
 * o `.getItem()` revienta con "is not a function".
 *
 * Los 61 tests de themeEngine / apiClient / rbac usan el global DESNUDO (sin
 * `window.`), asi que basta con reinstalar el binding global: no hay que
 * tocar ningun consumidor.
 */
import { Window } from 'happy-dom'

const store = new Window()

for (const name of ['localStorage', 'sessionStorage']) {
  Object.defineProperty(globalThis, name, {
    value: store[name],
    configurable: true,
    writable: true,
  })
}
