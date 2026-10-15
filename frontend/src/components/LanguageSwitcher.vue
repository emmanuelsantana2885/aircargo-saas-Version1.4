<template>
  <div class="lang-toggle" role="group" :aria-label="t('common.lang')">
    <button
      v-for="opt in OPTIONS"
      :key="opt.code"
      type="button"
      class="lang-btn"
      :class="locale === opt.code ? 'active' : 'inactive'"
      :aria-pressed="locale === opt.code"
      :title="opt.title"
      @click="setLang(opt.code)">
      {{ opt.code.toUpperCase() }}
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { locale, t } = useI18n()

/* El `title` de cada opción está en su propio idioma para que el propio
 * switcher se entienda: en ES ofrece pasar a EN y viceversa. */
const OPTIONS = computed(() => [
  { code: 'es', title: 'Cambiar a Español' },
  { code: 'en', title: 'Switch to English' },
])

function setLang(lang) {
  if (locale.value === lang) return
  locale.value = lang
  localStorage.setItem('aircargo_lang', lang)
}
</script>

<style scoped>
.lang-toggle {
  display: inline-flex;
  align-items: center;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.05);
  flex-shrink: 0;
}
.lang-btn {
  padding: 3px 9px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  border: none;
  cursor: pointer;
  line-height: 1.5;
  /* Solo color y fondo: `all` arrastraba tambien el box-shadow del glow. */
  transition: color 0.2s ease, background-color 0.2s ease;
}
.lang-btn:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}
.lang-btn.active {
  background: rgba(255, 255, 255, 0.95);
  color: #1e293b;
}
.lang-btn.inactive {
  background: transparent;
  color: rgba(255, 255, 255, 0.45);
}
.lang-btn.inactive:hover {
  color: rgba(255, 255, 255, 0.75);
  background: rgba(255, 255, 255, 0.06);
}
</style>
