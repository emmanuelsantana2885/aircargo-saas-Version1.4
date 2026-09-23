<template>
  <div v-if="hasMessage" class="validation-message" :class="typeClass">
    <div class="validation-content">
      <component :is="icon" :size="16" :stroke-width="2" class="validation-icon" />
      <span class="validation-text">{{ message }}</span>
    </div>
    <button v-if="dismissible" class="validation-dismiss" @click="dismiss" aria-label="Cerrar">
      <component :is="icons.Close" :size="14" :stroke-width="2" />
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useIcons } from '@/composables/useIcons'

const props = defineProps({
  message: { type: String, default: '' },
  type: { type: String, default: 'info', validator: v => ['success', 'error', 'warning', 'info', 'valid', 'invalid'].includes(v) },
  dismissible: { type: Boolean, default: false },
  icon: { type: [String, Object], default: null },
})

const emit = defineEmits(['dismiss'])
const icons = useIcons()

const hasMessage = computed(() => !!props.message)

const typeClass = computed(() => {
  const base = 'validation-message'
  switch (props.type) {
    case 'success':
    case 'valid':
      return `${base} validation-success`
    case 'error':
    case 'invalid':
      return `${base} validation-error`
    case 'warning':
      return `${base} validation-warning`
    default:
      return `${base} validation-info`
  }
})

const icon = computed(() => {
  if (props.icon) return props.icon
  switch (props.type) {
    case 'success':
    case 'valid':
      return icons.CheckCircle
    case 'error':
    case 'invalid':
      return icons.AlertCircle
    case 'warning':
      return icons.AlertTriangle
    default:
      return icons.Info
  }
})

function dismiss() {
  emit('dismiss')
}
</script>

<style scoped>
.validation-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 12px;
  font-family: var(--font-family);
  font-weight: 500;
  line-height: 1.5;
  animation: ds-pop 0.2s ease-out;
  border: 1px solid;
}

.validation-content {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.validation-icon {
  flex-shrink: 0;
  margin-top: 1px;
}

.validation-text {
  word-break: break-word;
}

.validation-dismiss {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 6px;
  background: transparent;
  border: none;
  color: inherit;
  opacity: 0.5;
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;
}

.validation-dismiss:hover {
  opacity: 1;
  background: rgba(0, 0, 0, 0.05);
}

.validation-success {
  background: linear-gradient(135deg, #ecfdf5, #d1fae5);
  color: #065f46;
  border-color: #10b981;
}

.validation-error {
  background: linear-gradient(135deg, #fef2f2, #fee2e2);
  color: #991b1b;
  border-color: #ef4444;
}

.validation-warning {
  background: linear-gradient(135deg, #fffbeb, #fef3c7);
  color: #92400e;
  border-color: #f59e0b;
}

.validation-info {
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  color: #1e40af;
  border-color: #3b82f6;
}

@keyframes ds-pop {
  from { opacity: 0; transform: translateY(-4px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

@media (prefers-reduced-motion: reduce) {
  .validation-message { animation: none; }
}
</style>