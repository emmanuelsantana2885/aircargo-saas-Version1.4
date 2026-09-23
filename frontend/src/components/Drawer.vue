<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="modelValue" class="drawer-backdrop" @click.self="close">
        <div class="drawer-panel" :class="[position, size]" @click.stop>
          <header class="drawer-header">
            <h3 class="drawer-title">{{ title }}</h3>
            <button
              class="drawer-close"
              @click="close"
              :aria-label="t('common.close')"
            >
              <component :is="icons.Close" :size="20" :stroke-width="2" />
            </button>
          </header>
          <div class="drawer-content">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="drawer-footer">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue'
import { useI18n } from 'vue-i18n'
import { useIcons } from '@/composables/useIcons'

const props = defineProps({
  modelValue: Boolean,
  title: { type: String, default: '' },
  position: { type: String, default: 'right', validator: v => ['right', 'left'].includes(v) },
  size: { type: String, default: 'md', validator: v => ['sm', 'md', 'lg', 'xl', 'full'].includes(v) },
  closeOnOverlayClick: { type: Boolean, default: true },
})

const emit = defineEmits(['update:modelValue', 'close'])

const { t } = useI18n()
const icons = useIcons()

function close() {
  if (props.closeOnOverlayClick) {
    emit('update:modelValue', false)
    emit('close')
  }
}
</script>

<style scoped>
.drawer-backdrop {
  position: fixed;
  inset: 0;
  z-index: 9998;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  display: flex;
  justify-content: flex-end;
  padding: max(16px, env(safe-area-inset-top))
    max(16px, env(safe-area-inset-right))
    max(24px, env(safe-area-inset-bottom))
    max(16px, env(safe-area-inset-left));
}

.drawer-backdrop.left {
  justify-content: flex-start;
}

.drawer-panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  box-shadow: 0 24px 80px -24px rgba(15, 23, 42, 0.55), inset 0 1px 0 rgba(255, 255, 255, 0.7);
  display: flex;
  flex-direction: column;
  max-height: 100%;
  overflow: hidden;
  animation: ds-pop 0.22s cubic-bezier(0.22, 1, 0.36, 1);
  min-width: 0;
}

.drawer-backdrop.left .drawer-panel {
  border-radius: 16px;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.drawer-title {
  font-size: 15px;
  font-weight: 700;
  font-family: var(--font-family-mono);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text);
  margin: 0;
}

.drawer-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: transparent;
  border: 1px solid var(--border);
  color: var(--muted);
  cursor: pointer;
  transition: all 0.15s ease;
}

.drawer-close:hover {
  background: var(--surface-2);
  border-color: var(--accent);
  color: var(--accent);
}

.drawer-content {
  flex: 1;
  overflow: auto;
  padding: 20px;
  min-height: 0;
}

.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid var(--border);
  flex-shrink: 0;
  flex-wrap: wrap;
}

/* Sizes */
.drawer-panel.sm { width: 320px; max-width: 92vw; }
.drawer-panel.md { width: 440px; max-width: 92vw; }
.drawer-panel.lg { width: 560px; max-width: 92vw; }
.drawer-panel.xl { width: 720px; max-width: 92vw; }
.drawer-panel.full { width: 100%; max-width: 100%; max-height: 100%; border-radius: 0; }

@media (max-width: 640px) {
  .drawer-panel.sm,
  .drawer-panel.md,
  .drawer-panel.lg,
  .drawer-panel.xl {
    width: 100%;
    max-width: 100%;
    border-radius: 0;
    height: 100dvh;
    max-height: 100dvh;
  }
  .drawer-backdrop {
    padding: 0;
    align-items: stretch;
  }
}

/* Animations */
.drawer-enter-active { animation: ds-pop 0.22s cubic-bezier(0.22, 1, 0.36, 1); }
.drawer-leave-active { animation: ds-pop 0.18s cubic-bezier(0.22, 1, 0.36, 1) reverse; }
.drawer-enter-from { opacity: 0; transform: translateX(32px); }
.drawer-leave-to { opacity: 0; transform: translateX(32px); }

.drawer-backdrop.left .drawer-enter-from { transform: translateX(-32px); }
.drawer-backdrop.left .drawer-leave-to { transform: translateX(-32px); }

@media (prefers-reduced-motion: reduce) {
  .drawer-enter-active,
  .drawer-leave-active { animation: none; }
  .drawer-enter-from,
  .drawer-leave-to { transform: none; opacity: 1; }
}
</style>