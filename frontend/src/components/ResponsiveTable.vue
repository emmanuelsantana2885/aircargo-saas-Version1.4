<template>
  <div class="responsive-table" :class="{ 'mobile-cards': isMobile }">
    <div v-if="!isMobile" class="table-scroll-wrapper">
      <table class="data-table">
        <thead>
          <tr>
            <th v-for="col in visibleColumns" :key="col.key" :class="col.class">
              {{ col.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in rows"
            :key="getRowKey(row)"
            :class="rowClass ? rowClass(row) : ''"
            @click="rowClick && $emit('row-click', row)"
          >
            <td v-for="col in visibleColumns" :key="col.key" :class="col.class">
              <slot :name="`cell-${col.key}`" :row="row" :value="getCellValue(row, col.key)">
                {{ getCellValue(row, col.key) }}
              </slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="cards-list">
      <div
        v-for="row in rows"
        :key="getRowKey(row)"
        class="data-card"
        :class="rowClass ? rowClass(row) : ''"
        @click="rowClick && $emit('row-click', row)"
      >
        <template v-for="col in visibleColumns" :key="col.key">
          <div class="card-field" v-if="getCellValue(row, col.key) !== null && getCellValue(row, col.key) !== ''">
            <span class="card-label">{{ col.label }}</span>
            <span class="card-value">
              <slot :name="`cell-${col.key}`" :row="row" :value="getCellValue(row, col.key)">
                {{ getCellValue(row, col.key) }}
              </slot>
            </span>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  columns: {
    type: Array,
    required: true,
  },
  rows: {
    type: Array,
    required: true,
  },
  rowKey: {
    type: [String, Function],
    default: 'id',
  },
  rowClass: { type: Function, default: () => null },
  rowClick: Boolean,
  mobileBreakpoint: {
    type: Number,
    default: 768,
  },
  hiddenOnMobile: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['row-click'])
// eslint-disable-next-line no-unused-vars
const _emit = emit

const isMobile = ref(false)

function updateMobile() {
  isMobile.value = window.innerWidth < props.mobileBreakpoint
}

const visibleColumns = computed(() => {
  if (!isMobile.value) return props.columns
  return props.columns.filter(c => !props.hiddenOnMobile.includes(c.key))
})

function getRowKey(row) {
  return typeof props.rowKey === 'function' ? props.rowKey(row) : row[props.rowKey]
}

function getCellValue(row, key) {
  const col = props.columns.find(c => c.key === key)
  if (col?.value) return col.value(row)
  return row[key]
}

onMounted(() => {
  updateMobile()
  window.addEventListener('resize', updateMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', updateMobile)
})
</script>

<style scoped>
.responsive-table {
  width: 100%;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.data-table th {
  background: var(--surface-2);
  color: var(--muted);
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 8px 12px;
  text-align: left;
  border-bottom: 2px solid var(--border);
  white-space: nowrap;
  font-family: var(--font-family-mono);
}

.data-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
  color: var(--text);
}

.data-table tbody tr:hover {
  background: var(--accent-soft);
}

.cards-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.data-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 12px 14px;
  box-shadow: var(--shadow-xs);
  transition: all 0.15s ease;
}

.data-card:hover {
  border-color: var(--accent);
  box-shadow: var(--shadow-sm);
}

.card-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 0;
}

.card-label {
  font-size: 9px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--muted);
  font-family: var(--font-family-mono);
}

.card-value {
  font-size: 13px;
  color: var(--text);
  font-family: var(--font-family);
}

@media (max-width: 767px) {
  .table-scroll-wrapper {
    display: none;
  }
}

@media (min-width: 768px) {
  .cards-list {
    display: none;
  }
}
</style>