<template>
  <div class="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/70 backdrop-blur-sm p-3 sm:p-5">
    <div class="bg-white rounded-2xl shadow-2xl w-[1100px] max-w-[96vw] h-[92vh] max-h-[880px] flex flex-col overflow-hidden ring-1 ring-slate-200/70"
      role="dialog" aria-modal="true" aria-label="Imprimir etiquetas">

      <!-- ══ Cabecera ══ -->
      <div class="px-5 py-3.5 border-b border-slate-200 flex items-center gap-3 shrink-0 bg-slate-50/70">
        <div class="w-10 h-10 rounded-xl bg-slate-900 text-white flex items-center justify-center text-[20px]">&#128424;</div>
        <div class="flex-1 min-w-0">
          <h3 class="text-[15px] font-bold tracking-tight text-slate-950 leading-none">
            Imprimir {{ typeLabel }} en grupo
          </h3>
          <p class="text-[12px] text-slate-500 mt-0.5 truncate">
            {{ includedCount }} de {{ rows.length }} etiqueta(s) seleccionada(s) &middot; plantilla <b>{{ currentTemplate?.name || '—' }}</b>
          </p>
        </div>
        <button @click="$emit('close')" title="Cerrar" class="w-8 h-8 rounded-lg hover:bg-slate-200/70 text-slate-400 hover:text-slate-700 transition flex items-center justify-center text-[15px]">&#10005;</button>
      </div>

      <!-- ══ Cuerpo ══ -->
      <div class="flex-1 min-h-0 flex flex-col p-4 gap-3">

        <div v-if="loading" class="flex-1 flex items-center justify-center text-[13px] font-mono text-slate-400">Cargando plantillas...</div>

        <div v-else-if="!templates.length" class="flex-1 flex flex-col items-center justify-center text-center gap-3 p-8">
          <div class="w-16 h-16 rounded-2xl bg-slate-100 flex items-center justify-center text-[28px]">&#127470;</div>
          <p class="text-[14px] text-slate-500 max-w-sm">
            No hay plantillas {{ typeLabel }} configuradas. Crea una para empezar a imprimir.
          </p>
          <button v-if="isSuperUser" @click="openDesigner"
            class="px-5 py-2.5 rounded-xl bg-slate-950 text-white text-[13px] font-bold hover:bg-slate-800 transition shadow-lg shadow-slate-900/20">
            + Crear plantilla
          </button>
        </div>

        <template v-else>
          <!-- ══ Toolbar de config + lote ══ -->
          <div class="shrink-0 rounded-xl border border-slate-200 bg-slate-50/60 p-3 space-y-2.5">
            <div class="flex flex-wrap items-end gap-3">
              <label class="block min-w-[220px] flex-1">
                <span class="text-[10px] font-bold uppercase tracking-widest text-slate-500">Plantilla</span>
                <select v-model="templateId" class="w-full mt-1 px-2.5 py-1.5 rounded-lg border border-slate-300 bg-white text-[13px] font-mono focus:outline-none focus:ring-2 focus:ring-slate-950/10 focus:border-slate-950">
                  <option v-for="t in templates" :key="t.id" :value="t.id">
                    {{ templateOptionLabel(t) }}
                  </option>
                </select>
              </label>
              <label class="block w-[150px]">
                <span class="text-[10px] font-bold uppercase tracking-widest text-slate-500">Formato</span>
                <select v-model="format" class="w-full mt-1 px-2.5 py-1.5 rounded-lg border border-slate-300 bg-white text-[13px] font-mono focus:outline-none focus:ring-2 focus:ring-slate-950/10 focus:border-slate-950">
                  <option value="PDF">PDF</option>
                  <option value="ZPL">ZPL (Zebra)</option>
                </select>
              </label>
              <label class="block">
                <span class="text-[10px] font-bold uppercase tracking-widest text-slate-500">Copias</span>
                <div class="flex items-center mt-1 rounded-lg border border-slate-300 bg-white overflow-hidden">
                  <button type="button" @click="quantity = Math.max(1, quantity - 1)"
                    class="px-2.5 py-1.5 text-slate-600 hover:bg-slate-100 text-[14px] leading-none">&#8722;</button>
                  <span class="w-9 text-center text-[13px] font-mono font-bold">{{ quantity }}</span>
                  <button type="button" @click="quantity = Math.min(99, quantity + 1)"
                    class="px-2.5 py-1.5 text-slate-600 hover:bg-slate-100 text-[14px] leading-none">&#43;</button>
                </div>
              </label>
              <div class="flex-1"></div>
              <button v-if="isSuperUser" @click="openDesigner" title="Diseñar / editar plantillas"
                class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-slate-300 bg-white text-slate-600 hover:border-slate-950 hover:text-slate-950 text-[12px] font-bold transition">
                &#9998; Diseñar
              </button>
            </div>

            <div v-if="currentVariables.length" class="flex items-start gap-2 flex-wrap border-t border-slate-200/70 pt-2.5">
              <div class="flex items-center gap-1.5 text-[11px] font-bold uppercase tracking-widest text-slate-500 pt-1.5">
                <span>&#9889;</span> Lote
              </div>
              <label v-for="v in currentVariables" :key="v.key" :title="v.key"
                class="flex items-center gap-1 bg-white border border-slate-200 rounded-lg pl-2 pr-1 py-1 hover:border-slate-400 transition shadow-sm">
                <span class="text-[10px] font-bold uppercase tracking-wider text-slate-500 shrink-0">{{ v.label || v.key }}</span>
                <input :value="bulkVals[v.key] ?? ''" @input="onBulkInput(v.key, $event.target.value)"
                  :placeholder="'aplicar a ' + includedCount"
                  class="w-32 min-w-0 px-1.5 py-0.5 rounded border bg-slate-50 focus:bg-white border-transparent focus:border-slate-400 text-[12px] font-mono focus:outline-none transition" />
                <button v-if="(bulkVals[v.key] ?? '') !== ''" type="button" @click="clearBulk(v.key)" title="Limpiar en todas"
                  class="text-slate-300 hover:text-red-500 text-[11px] px-1">&#10005;</button>
              </label>
            </div>
          </div>

          <!-- ══ Workspace: hoja + vista previa ══ -->
          <div class="flex-1 min-h-0 flex flex-col lg:flex-row gap-3">

            <!-- Hoja de trabajo -->
            <div class="flex-1 min-w-0 min-h-0 flex flex-col rounded-xl border border-slate-200 overflow-hidden bg-white">
              <div class="shrink-0 flex items-center gap-2 px-3 py-2 border-b border-slate-200 bg-slate-50/60">
                <span class="text-[11px] font-bold uppercase tracking-widest text-slate-500">Etiquetas</span>
                <span class="text-[11px] text-slate-400 font-mono">{{ filteredRows.length }}</span>
                <div class="flex-1"></div>
                <div v-if="remainingItems.length" class="flex items-center gap-1.5">
                  <select v-model="pendingAdd" class="px-2 py-1 rounded-lg border border-slate-300 text-[12px] font-mono bg-white focus:outline-none max-w-[180px]">
                    <option value="" disabled>A&ntilde;adir etiqueta...</option>
                    <option v-for="it in remainingItems" :key="it.id" :value="it.id">{{ it.label }}</option>
                  </select>
                  <button @click="addRow" :disabled="!pendingAdd" title="Añadir a la impresión"
                    class="px-2 py-1 rounded-lg bg-slate-900 text-white text-[12px] font-bold disabled:opacity-40 hover:bg-slate-700 transition">+</button>
                </div>
                <input v-model="search" type="search" placeholder="Buscar..."
                  class="w-28 sm:w-40 px-2 py-1 rounded-lg border border-slate-200 text-[12px] font-mono bg-white focus:outline-none focus:border-slate-400 transition" />
                <label class="flex items-center gap-1.5 cursor-pointer select-none" title="Marcar / desmarcar todo">
                  <input type="checkbox" :checked="includedCount === rows.length && rows.length > 0" @change="toggleAll($event.target.checked)" />
                  <span class="text-[11px] font-mono text-slate-500">{{ includedCount }}/{{ rows.length }}</span>
                </label>
              </div>
              <div class="flex-1 min-h-0 overflow-auto">
                <table class="border-collapse w-full" :style="{ minWidth: (190 + currentVariables.length * 150 + 56) + 'px' }">
                  <thead>
                    <tr class="bg-slate-100 sticky top-0 z-10">
                      <th class="border-b border-slate-200 px-2 py-2 text-left w-[190px]">
                        <span class="text-[10px] font-bold uppercase tracking-widest text-slate-500">Etiqueta</span>
                      </th>
                      <th v-for="v in currentVariables" :key="v.key" class="border-b border-slate-200 px-2 py-2 text-left">
                        <span class="text-[10px] font-bold uppercase tracking-widest text-slate-500 truncate" :title="v.key">{{ v.label || v.key }}</span>
                      </th>
                      <th class="border-b border-slate-200 px-1 py-2 w-[56px] text-center">
                        <span class="text-[10px] font-bold uppercase tracking-widest text-slate-300">&#10005;</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="row in filteredRows" :key="row.id"
                      :class="[
                        previewRowId === row.id ? 'bg-blue-50/70' : row.included ? 'bg-white hover:bg-slate-50' : 'bg-slate-50/70 opacity-60 hover:opacity-100',
                      ]">
                      <td class="border-b border-slate-100 px-2 py-1.5 align-top">
                        <label class="flex items-start gap-2 cursor-pointer group">
                          <input type="checkbox" v-model="row.included" @change="onIncludeToggle(row)" class="mt-0.5" />
                          <button @click="selectPreview(row)" :title="(row.included ? '' : 'Excluida — ') + 'Vista previa'"
                            class="text-left text-[12px] font-mono font-bold text-slate-900 group-hover:text-slate-950 leading-tight">
                            {{ row.label }}
                            <span v-if="previewRowId === row.id" class="text-blue-500">&#128065;</span>
                          </button>
                        </label>
                      </td>
                      <td v-for="v in currentVariables" :key="v.key" class="border-b border-slate-100 px-1 py-1 align-top">
                        <input :value="row.values[v.key] ?? ''" @input="row.values[v.key] = $event.target.value"
                          type="text" :placeholder="v.label || v.key" :disabled="!row.included"
                          class="w-full px-1.5 py-1 rounded-md border border-slate-200 text-[12px] font-mono bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 disabled:bg-slate-100 disabled:text-slate-400 transition" />
                      </td>
                      <td class="border-b border-slate-100 px-1 py-1 text-center align-top">
                        <button @click="removeRow(row)" title="Quitar de esta impresión"
                          class="w-6 h-6 rounded-md hover:bg-red-100 text-slate-300 hover:text-red-500 text-[12px] transition">&#10005;</button>
                      </td>
                    </tr>
                    <tr v-if="!filteredRows.length">
                      <td :colspan="2 + currentVariables.length" class="px-3 py-8 text-center">
                        <p class="text-[13px] text-slate-400">Sin etiquetas para imprimir.</p>
                        <p v-if="remainingItems.length" class="text-[11px] text-slate-400 mt-1">Usa «A&ntilde;adir etiqueta» para incluir m&aacute;s.</p>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Vista previa en vivo -->
            <div v-if="currentTemplate" class="w-full lg:w-[360px] shrink-0 min-h-0 flex flex-col rounded-xl border border-slate-200 overflow-hidden bg-slate-50">
              <div class="shrink-0 flex items-center gap-2 px-3 py-2 border-b border-slate-200 bg-slate-50/80">
                <span class="text-[11px] font-bold uppercase tracking-widest text-slate-500">Vista previa</span>
                <span v-if="previewRow" class="ml-auto truncate text-[12px] font-mono font-bold text-slate-700">{{ previewRow.label }}</span>
              </div>
              <div class="flex-1 min-h-0 overflow-auto p-5 flex items-start justify-center">
                <div class="bg-white shadow-xl ring-1 ring-slate-200 relative shrink-0" :style="previewCanvasStyle">
                  <div v-for="el in previewElements" :key="el.id"
                    class="absolute" :style="previewElementStyle(el)">
                    <span v-if="el.type === 'text'" class="font-mono leading-none" :style="previewTextStyle(el)">{{ resolveValue(el) }}</span>
                    <span v-else-if="el.type === 'line'" class="block" style="height:1px;background:#000"></span>
                    <span v-else-if="el.type === 'rect'" class="block" style="width:100%;height:100%;border:1px solid #000"></span>
                    <span v-else class="flex items-center justify-center text-[8px] font-mono text-slate-400 font-bold uppercase" style="width:100%;height:100%">
                      {{ el.type === 'qrcode' ? 'QR' : 'CODE128' }}
                    </span>
                  </div>
                </div>
              </div>
              <div class="shrink-0 border-t border-slate-200 bg-white px-3 py-2.5">
                <div class="flex items-center gap-1 flex-wrap max-h-[92px] overflow-auto">
                  <template v-if="includedRows.length">
                    <button v-for="r in includedRows" :key="r.id" @click="previewRowId = r.id"
                      class="px-2 py-1 rounded-full text-[10px] font-mono uppercase border transition"
                      :class="previewRowId === r.id ? 'bg-slate-900 text-white border-slate-900' : 'bg-white text-slate-600 border-slate-200 hover:border-slate-400'">
                      {{ shortLabel(r) }}
                    </button>
                  </template>
                  <span v-else class="text-[11px] text-slate-400">Marca al menos una etiqueta para previsualizar.</span>
                </div>
                <p class="text-[10px] font-mono text-slate-400 mt-2 leading-snug">
                  El c&oacute;digo de barras/QR se genera al imprimir. Solo se imprimen las filas marcadas con sus valores propios.
                </p>
              </div>
            </div>
          </div>
        </template>
      </div>

      <!-- ══ Pie ══ -->
      <div class="px-5 py-3.5 border-t border-slate-200 flex items-center gap-2 shrink-0 bg-white">
        <span class="text-[12px] text-slate-400 mr-auto hidden sm:block font-mono">
          {{ currentTemplate ? currentTemplate.widthInches + 'x' + currentTemplate.heightInches + 'in · ' + format + ' · ' + currentVariables.length + ' campo(s) variable(s)' : '' }}
        </span>
        <button @click="$emit('close')" class="px-4 py-2 rounded-lg text-[13px] font-semibold text-slate-600 hover:bg-slate-100 transition">Cancelar</button>
        <button @click="download" :disabled="!currentTemplate || !includedCount || downloading"
          class="px-5 py-2.5 rounded-xl bg-slate-950 text-white text-[13px] font-bold flex items-center gap-2 hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed transition shadow-lg shadow-slate-900/25">
          <span class="text-[15px]">&#128424;</span>
          {{ downloadLabel }}
        </button>
      </div>
    </div>

    <LabelDesignerModal v-if="designerOpen" :type="type" @close="designerOpen = false" @saved="reload" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useToastStore } from '../../stores/toast'
import { extractError } from '../../utils/error'
import { labelTemplatesApi, labelsApi } from '../../api/labelTemplates'
import { effectiveSize, resolveElementValue } from '../../utils/labelConfig'
import LabelDesignerModal from './LabelDesignerModal.vue'

const props = defineProps({
  type: { type: String, default: 'CARGO' },
  items: { type: Array, default: () => [] },
  show: { type: Boolean, default: true },
})
defineEmits(['close'])
const toast = useToastStore()
const isSuperUser = computed(() => {
  const raw = localStorage.getItem('aircargo_auth')
  if (!raw) return false
  try { return JSON.parse(raw).role === 'SUPER_USER' } catch { return false }
})

const typeLabel = computed(() => props.type === 'PALLET' ? 'Pallet' : 'Cargo')
const templates = ref([])
const loading = ref(false)
const templateId = ref('')
const format = ref('PDF')
const quantity = ref(1)
const downloading = ref(false)
const designerOpen = ref(false)
function openDesigner() { designerOpen.value = true }

function templateOptionLabel(t) {
  const dims = (t.widthInches || '?') + 'x' + (t.heightInches || '?')
  return t.isDefault ? `${t.name} · ${dims} (default)` : `${t.name} · ${dims}`
}

const currentTemplate = computed(() => templates.value.find(t => t.id === templateId.value) || null)

const currentVariables = computed(() => {
  const t = currentTemplate.value
  if (!t) return []
  try { return JSON.parse(t.configJson || '{"variables":[]}').variables || [] } catch { return [] }
})

const previewElements = computed(() => {
  const t = currentTemplate.value
  if (!t) return []
  try { return JSON.parse(t.configJson || '{"elements":[]}').elements || [] } catch { return [] }
})

function normalizeItems(list) {
  return (list || []).map(it => {
    if (typeof it === 'string') return { id: it, label: it }
    return { id: it.id, label: it.label || it.id }
  }).filter(it => it.id)
}

const rows = ref([])
const pendingAdd = ref('')
const previewRowId = ref(null)
const cachedValues = reactive({})
const search = ref('')
const bulkVals = reactive({})

function rebuildRows() {
  const next = normalizeItems(props.items)
  rows.value = next.map(it => {
    const prev = cachedValues[it.id]
    return { id: it.id, label: it.label, included: true, values: prev ? { ...prev } : {} }
  })
  const firstIncluded = rows.value[0]
  previewRowId.value = firstIncluded ? firstIncluded.id : null
  pendingAdd.value = ''
  search.value = ''
}

const remainingItems = computed(() => {
  const used = new Set(rows.value.map(r => r.id))
  return normalizeItems(props.items).filter(it => !used.has(it.id))
})

const includedCount = computed(() => rows.value.filter(r => r.included).length)
const includedRows = computed(() => rows.value.filter(r => r.included))
const previewRow = computed(() => rows.value.find(r => r.id === previewRowId.value) || rows.value[0] || null)

const filteredRows = computed(() => {
  const q = (search.value || '').trim().toLowerCase()
  if (!q) return rows.value
  return rows.value.filter(r => (r.label || '').toLowerCase().includes(q))
})

watch(() => props.items, () => rebuildRows(), { deep: true })
onMounted(rebuildRows)

function addRow() {
  const it = remainingItems.value.find(x => x.id === pendingAdd.value)
  if (!it) return
  const row = { id: it.id, label: it.label, included: true, values: {} }
  for (const k of Object.keys(bulkVals)) if (bulkVals[k] !== undefined && bulkVals[k] !== '') row.values[k] = bulkVals[k]
  rows.value.push(row)
  previewRowId.value = it.id
  pendingAdd.value = ''
}

function removeRow(row) {
  const i = rows.value.indexOf(row)
  if (i < 0) return
  cachedValues[row.id] = { ...row.values }
  rows.value.splice(i, 1)
  if (previewRowId.value === row.id) previewRowId.value = rows.value[0] ? rows.value[0].id : null
}

function selectPreview(row) { previewRowId.value = row.id }
function toggleAll(on) { for (const r of rows.value) r.included = on }
function onIncludeToggle(row) {
  if (row.included) {
    for (const k of Object.keys(bulkVals)) if (bulkVals[k] !== undefined && bulkVals[k] !== '') row.values[k] = bulkVals[k]
  }
}

function onBulkInput(key, val) {
  bulkVals[key] = val
  for (const r of rows.value) if (r.included) r.values[key] = val
}
function clearBulk(key) {
  bulkVals[key] = ''
  for (const r of rows.value) r.values[key] = ''
}

function shortLabel(row) {
  const s = (row.label || '')
  return s.length > 16 ? s.slice(0, 15) + '…' : s
}

const previewScale = computed(() => {
  if (!currentTemplate.value) return 1
  const eff = effectiveSize(currentTemplate.value)
  return Math.min(2.5, 320 / Math.max(1, eff.w * 25.4))
})

const previewCanvasStyle = computed(() => {
  const eff = effectiveSize(currentTemplate.value)
  const sc = previewScale.value
  return { width: Math.round(eff.w * 25.4 * sc) + 'px', height: Math.round(eff.h * 25.4 * sc) + 'px' }
})

function previewElementStyle(el) {
  const sc = previewScale.value
  return {
    left: (el.x * sc) + 'px',
    top: (el.y * sc) + 'px',
    width: (el.w * sc) + 'px',
    height: (el.type === 'line' ? 1 : el.h * sc) + 'px',
  }
}
function previewTextStyle(el) {
  const sc = previewScale.value
  return { fontSize: (el.fontSize * sc * 3.3) + 'px', fontWeight: el.bold ? 'bold' : 'normal', textAlign: el.align }
}

function resolveValue(el) {
  if (el.dataSource === 'TEXT' || !el.dataSource) return el.text || ''
  return resolveElementValue(el, props.type, previewRow.value?.values || {})
}

async function load() {
  loading.value = true
  try {
    const res = await labelTemplatesApi.list(props.type)
    templates.value = res.data || []
    if (templateId.value && templates.value.some(t => t.id === templateId.value)) return
    const def = templates.value.find(t => t.isDefault) || templates.value[0]
    templateId.value = def?.id || ''
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    loading.value = false
  }
}

async function reload() {
  designerOpen.value = false
  await load()
}

const downloadLabel = computed(() => {
  if (downloading.value) return 'Generando…'
  const n = includedCount.value
  return `Imprimir ${n} ${n === 1 ? 'etiqueta' : 'etiquetas'}`
})

async function download() {
  if (!currentTemplate.value) return
  const inc = includedRows.value.filter(r => r.id)
  if (!inc.length) { toast.warning('Marca al menos una etiqueta'); return }
  const payload = {
    templateId: currentTemplate.value.id,
    format: format.value,
    ids: inc.map(r => r.id),
    quantity: quantity.value,
  }
  const overrides = {}
  for (const r of inc) {
    const item = {}
    for (const v of currentVariables.value) {
      const val = (r.values[v.key] || '').trim()
      if (val) item[v.key] = val
    }
    if (Object.keys(item).length) overrides[r.id] = item
  }
  if (Object.keys(overrides).length) payload.overrides = overrides
  downloading.value = true
  try {
    if (props.type === 'PALLET') await labelsApi.downloadPallet(payload)
    else await labelsApi.downloadCargo(payload)
    toast.success('Etiquetas generadas')
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    downloading.value = false
  }
}

watch(() => props.show, (v) => { if (v) load() })
onMounted(load)
</script>