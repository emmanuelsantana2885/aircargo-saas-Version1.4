/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        // `sans` = tipografia de interfaz (--font-family) y `mono` = tipografia
        // de datos (--font-family-mono). Antes ambas apuntaban al MISMO token,
        // asi que la app se renderizaba entera en monoespaciada. El valor
        // concreto de cada uno lo decide el perfil activo (ver los bloques
        // [data-font] de src/assets/main.css), no estas listas: por eso solo
        // declaran el token + un fallback generico.
        mono: ['var(--font-family-mono)', 'ui-monospace', 'monospace'],
        sans: ['var(--font-family)', 'system-ui', 'sans-serif'],
      },
      fontSize: {
        xs: '12px',
      },
    },
  },
  plugins: [],
}
