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
/* El estilo de .data-table / .data-card / .card-* vive ahora en el design
   system (assets/main.css → @layer components) para que las <table> sueltas
   de las vistas compartan exactamente el mismo aspecto. Aquí sólo queda el
   layout propio del componente: el switch tabla↔cards por breakpoint. */
.responsive-table {
  width: 100%;
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