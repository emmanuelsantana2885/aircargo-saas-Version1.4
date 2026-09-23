<template>
  <div class="kpi-card" :class="[variant, trendClass]">
    <div class="kpi-header">
      <span class="kpi-label">{{ label }}</span>
      <component v-if="icon" :is="typeof icon === 'function' ? icon() : icon" :size="18" :stroke-width="2" :class="iconClass" />
    </div>
    <div class="kpi-value">{{ formattedValue }}</div>
    <div v-if="delta !== undefined && delta !== null" class="kpi-delta" :class="trendClass">
      <component v-if="delta > 0" :is="icons.TrendingUp" :size="12" :stroke-width="2" />
      <component v-else-if="delta < 0" :is="icons.TrendingDown" :size="12" :stroke-width="2" />
      <component v-else :is="icons.Minus" :size="12" :stroke-width="2" />
      <span>{{ deltaText }}</span>
    </div>
    <div v-if="subtitle" class="kpi-subtitle">{{ subtitle }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useIcons } from '@/composables/useIcons'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  delta: { type: Number, default: undefined },
  deltaText: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  icon: { type: [String, Object, Function], default: null },
  variant: { type: String, default: 'default', validator: v => ['default', 'primary', 'success', 'warning', 'danger', 'info'].includes(v) },
  format: { type: String, default: 'number', validator: v => ['number', 'currency', 'percent', 'compact'].includes(v) },
})

const icons = useIcons()

const formattedValue = computed(() => {
  const val = props.value
  if (typeof val === 'string') return val
  switch (props.format) {
    case 'currency':
      return new Intl.NumberFormat('es-DO', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }).format(val)
    case 'percent':
      return `${(val * 100).toFixed(1)}%`
    case 'compact':
      return new Intl.NumberFormat('es-DO', { notation: 'compact', maximumFractionDigits: 1 }).format(val)
    default:
      return new Intl.NumberFormat('es-DO').format(val)
  }
})

const trendClass = computed(() => {
  if (props.delta === undefined || props.delta === null) return ''
  if (props.delta > 0) return 'trend-up'
  if (props.delta < 0) return 'trend-down'
  return 'trend-neutral'
})

const iconClass = computed(() => {
  if (!props.icon) return ''
  switch (props.variant) {
    case 'primary': return 'text-blue-500'
    case 'success': return 'text-emerald-500'
    case 'warning': return 'text-amber-500'
    case 'danger': return 'text-red-500'
    case 'info': return 'text-cyan-500'
    default: return 'text-slate-400'
  }
})
</script>

<style scoped>
.kpi-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 12px;
  padding: 16px;
  box-shadow: var(--shadow-xs), 0 8px 24px -16px rgba(15, 23, 42, 0.14);
  transition: all 0.2s ease;
}

.kpi-card:hover {
  transform: translateY(-1px);
  border-color: var(--accent);
  box-shadow: 0 2px 4px rgba(15, 23, 42, 0.05), 0 16px 32px -18px color-mix(in srgb, var(--accent) 28%, transparent);
}

.kpi-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.kpi-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--muted);
  font-family: var(--font-family-mono);
}

.kpi-value {
  font-size: 28px;
  font-weight: 800;
  color: var(--text);
  font-family: var(--font-family-mono);
  line-height: 1;
  margin-bottom: 6px;
}

.kpi-delta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 600;
  font-family: var(--font-family-mono);
  margin-bottom: 4px;
}

.kpi-delta.trend-up { color: #059669; }
.kpi-delta.trend-down { color: #dc2626; }
.kpi-delta.trend-neutral { color: var(--muted); }

.kpi-subtitle {
  font-size: 11px;
  color: var(--muted);
  font-family: var(--font-family-mono);
}

/* Variants */
.kpi-card.primary { border-left: 4px solid var(--accent); }
.kpi-card.success { border-left: 4px solid #059669; }
.kpi-card.warning { border-left: 4px solid #f59e0b; }
.kpi-card.danger { border-left: 4px solid #dc2626; }
.kpi-card.info { border-left: 4px solid #06b6d4; }
</style>