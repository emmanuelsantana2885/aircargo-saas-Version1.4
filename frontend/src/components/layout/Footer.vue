<template>
  <footer class="ds-footer">
    <div class="ds-footer-inner">
      <span class="ds-footer-brand">AirCargo</span>
      <div class="ds-footer-divider"></div>
      <span class="ds-footer-rights">{{ t('siteFooter.rights') }}</span>
      <a
        :href="t('siteFooter.privacyLink', { default: '/privacy' })"
        target="_blank"
        rel="noopener noreferrer"
        class="ds-footer-link"
        :aria-label="t('siteFooter.privacy')"
      >
        {{ t('siteFooter.privacy') }}
      </a>
    </div>
  </footer>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<style scoped>
.ds-footer {
  height: 44px;
  flex-shrink: 0;
  background: var(--surface-2, #f6f8fd);
  border-top: 1px solid var(--border, #dbe4f2);
  color: var(--text-3, #94a3b8);
  font-size: 11.5px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-sizing: border-box;
}

.ds-footer-inner {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: 1;
  min-width: 0;
}

.ds-footer-brand {
  font-weight: 800;
  font-size: 13px;
  color: var(--text, #0f172a);
  letter-spacing: 0.3px;
  flex-shrink: 0;
}

.ds-footer-divider {
  width: 1px;
  height: 12px;
  background: var(--border, #dbe4f2);
  flex-shrink: 0;
}

.ds-footer-rights {
  font-size: 11px;
  flex-shrink: 0;
  white-space: nowrap;
}

.ds-footer-link {
  color: var(--text-2, #475569);
  text-decoration: none;
  font-size: 11px;
  flex-shrink: 0;
  white-space: nowrap;
  transition: color 0.15s;
}

.ds-footer-link:hover {
  color: var(--accent, #3e7bfa);
  text-decoration: underline;
}

[data-theme='tokyo'] .ds-footer {
  background: var(--surface-2, #111827);
  border-top-color: var(--border, #334155);
  color: var(--text-3, #94a3b8);
}
[data-theme='tokyo'] .ds-footer-brand {
  color: var(--text, #f8fafc);
}
[data-theme='tokyo'] .ds-footer-divider {
  background: var(--border, #334155);
}
[data-theme='tokyo'] .ds-footer-link {
  color: var(--text-2, #cbd5e1);
}
[data-theme='tokyo'] .ds-footer-link:hover {
  color: var(--accent, #7aa2f7);
}

/* Ocultar en móvil: ya existe BottomNav que ocupa el pie de pantalla */
@media (max-width: 767px) {
  .ds-footer {
    display: none !important;
  }
}

/* Tablet: más compacto */
@media (min-width: 768px) and (max-width: 1023px) {
  .ds-footer {
    height: 40px;
    font-size: 11px;
    padding: 0 16px;
  }
  .ds-footer-brand {
    font-size: 12px;
  }
  .ds-footer-rights,
  .ds-footer-link {
    font-size: 10.5px;
  }
}
</style>
