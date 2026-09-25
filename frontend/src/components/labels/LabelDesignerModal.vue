<template>
  <div class="fixed inset-0 z-[100] flex flex-col bg-slate-900/60 backdrop-blur-sm"
    role="dialog" aria-modal="true" aria-label="Editor de etiquetas">
    <!-- Top bar -->
    <div class="flex h-14 shrink-0 items-center gap-3 bg-slate-950 px-4 z-20">
      <span class="whitespace-nowrap font-mono text-[13px] font-bold uppercase tracking-widest text-white">
        Editor de Etiquetas — {{ typeLabel }}
      </span>
      <select v-model="loadId" class="bg-slate-800 text-white border border-slate-700 rounded px-2 py-1 text-[12px] font-mono max-w-[220px]">
        <option value="">— Cargar plantilla —</option>
        <option v-for="t in templates" :key="t.id" :value="t.id">{{ t.name }} {{ t.isDefault ? '(default)' : '' }}</option>
      </select>
      <button @click="loadSelected" class="px-2 py-1 rounded bg-slate-700 hover:bg-slate-600 text-white text-[12px] font-mono">Cargar</button>
      <button @click="newTemplate" class="px-2 py-1 rounded bg-slate-700 hover:bg-slate-600 text-white text-[12px] font-mono">Nueva</button>
      <div class="flex-1"></div>
      <span v-if="dirty" class="text-[11px] font-mono text-amber-300 uppercase">sin guardar</span>
      <button @click="save" :disabled="saving"
        class="px-3 py-1 rounded bg-emerald-700 hover:bg-emerald-500 disabled:opacity-50 text-white text-[12px] font-mono font-bold">
        {{ saving ? 'Guardando...' : currentId ? 'Actualizar' : 'Guardar' }}
      </button>
      <button @click="$emit('close')" class="w-8 h-8 rounded hover:bg-slate-700 text-white text-[14px] font-mono" :aria-label="t('common.close')">✕</button>
    </div>

    <div class="flex min-h-0 flex-1">
      <!-- Left: toolbox + element list -->
      <div class="w-56 shrink-0 bg-slate-100 border-r border-slate-300 flex flex-col">
        <div class="p-3 border-b border-slate-200">
          <div class="text-[11px] font-bold uppercase tracking-widest text-slate-500 mb-2">Añadir elemento</div>
          <div class="grid grid-cols-2 gap-1.5">
            <button v-for="tool in tools" :key="tool.type" @click="addElement(tool.type)"
              class="py-1.5 rounded border border-slate-300 bg-white hover:border-slate-950 text-[11px] font-mono font-bold text-slate-700 transition">
              + {{ tool.label }}
            </button>
          </div>
        </div>
        <div class="flex-1 overflow-y-auto p-3 space-y-1">
          <div class="text-[11px] font-bold uppercase tracking-widest text-slate-500 mb-1">Elementos ({{ elements.length }})</div>
          <button v-for="(el, i) in elements" :key="el.id"
            @click.exact="selectOne(i)" @click.shift="toggleSelect(el.id)"
            class="w-full text-left px-2 py-1.5 rounded border text-[12px] font-mono transition flex items-center justify-between gap-1"
            :class="isSelected(el.id) ? 'border-slate-950 bg-white shadow-sm' : 'border-slate-200 bg-white hover:border-slate-400'">
            <span class="truncate flex items-center gap-1 text-slate-700">
              <span class="font-bold text-slate-950">{{ el.type }}</span>
              <span class="text-slate-400"> · {{ Math.round(el.x) }},{{ Math.round(el.y) }}</span>
            </span>
            <span @click.stop="removeElement(el.id)" class="px-1 text-slate-300 hover:text-rose-500">✕</span>
          </button>
          <div v-if="!elements.length" class="text-[12px] font-mono text-slate-400 text-center pt-6">Sin elementos</div>
        </div>
        <div class="p-3 border-t border-slate-200">
          <label class="block text-[11px] font-mono font-bold text-slate-600 mb-1">Plantilla por defecto</label>
          <label class="flex items-center gap-2 text-[12px] font-mono text-slate-700 cursor-pointer">
            <input type="checkbox" v-model="isDefault" class="accent-slate-950" />
            Usar como default {{ typeLabel }}
          </label>
          <button v-if="currentId" @click="remove" class="mt-2 w-full py-1 rounded border border-rose-200 text-rose-600 hover:bg-rose-50 text-[12px] font-mono">Eliminar plantilla</button>
        </div>
      </div>

      <!-- Center: canvas + toolbar -->
      <div class="flex flex-1 flex-col bg-slate-200 min-w-0">
        <!-- editing toolbar -->
        <div class="flex h-11 shrink-0 items-center gap-1 border-b border-slate-300 bg-slate-100 px-2 overflow-x-auto">
          <span class="mr-1 text-[11px] font-mono font-bold uppercase tracking-widest text-slate-400 whitespace-nowrap">Alinear</span>
          <button v-for="a in alignOptions" :key="a.id" :disabled="!canAlign" :title="a.title" @click="alignSel(a.id)"
            class="h-7 w-7 shrink-0 rounded border border-slate-300 bg-white text-[12px] font-mono text-slate-700 hover:border-slate-950 disabled:opacity-40 disabled:hover:border-slate-300">
            {{ a.icon }}
          </button>
          <div class="mx-1 h-5 w-px bg-slate-300 shrink-0"></div>
          <span class="mr-1 text-[11px] font-mono font-bold uppercase tracking-widest text-slate-400 whitespace-nowrap">Distribuir</span>
          <button :disabled="!canDistribute" title="Distribuir horizontalmente" @click="distributeSel('h')"
            class="h-7 w-7 shrink-0 rounded border border-slate-300 bg-white text-[12px] font-mono text-slate-700 hover:border-slate-950 disabled:opacity-40 disabled:hover:border-slate-300" aria-label="Distribuir horizontalmente">⇄</button>
          <button :disabled="!canDistribute" title="Distribuir verticalmente" @click="distributeSel('v')"
            class="h-7 w-7 shrink-0 rounded border border-slate-300 bg-white text-[12px] font-mono text-slate-700 hover:border-slate-950 disabled:opacity-40 disabled:hover:border-slate-300" aria-label="Distribuir verticalmente">⇅</button>
          <div class="mx-1 h-5 w-px bg-slate-300 shrink-0"></div>
          <button :disabled="!undoStack.length" title="Deshacer (Ctrl+Z)" @click="undo"
            class="h-7 w-7 shrink-0 rounded border border-slate-300 bg-white text-[12px] font-mono text-slate-700 hover:border-slate-950 disabled:opacity-40 disabled:hover:border-slate-300" aria-label="Deshacer (Ctrl+Z)">↶</button>
          <button :disabled="!redoStack.length" title="Rehacer (Ctrl+Y)" @click="redo"
            class="h-7 w-7 shrink-0 rounded border border-slate-300 bg-white text-[12px] font-mono text-slate-700 hover:border-slate-950 disabled:opacity-40 disabled:hover:border-slate-300" aria-label="Rehacer (Ctrl+Y)">↷</button>
          <div class="mx-1 h-5 w-px bg-slate-300 shrink-0"></div>
          <button :disabled="!selectedCount" title="Duplicar selección" @click="duplicateSel"
            class="h-7 shrink-0 rounded border border-slate-300 bg-white px-2 text-[11px] font-mono text-slate-700 hover:border-slate-950 disabled:opacity-40 disabled:hover:border-slate-300" aria-label="Duplicar selección">⧉ Duplicar</button>
          <label class="ml-2 flex shrink-0 cursor-pointer items-center gap-1 whitespace-nowrap text-[11px] font-mono text-slate-600" title="Ajustar posiciones a la cuadrícula de 0.5 mm">
            <input type="checkbox" v-model="snapEnabled" class="accent-slate-950" /> Snap 0.5
          </label>
          <div class="mx-1 h-5 w-px bg-slate-300 shrink-0"></div>
          <span class="flex shrink-0 items-center gap-1 whitespace-nowrap text-[11px] font-mono text-slate-500">
            <button class="h-7 w-7 rounded border border-slate-300 bg-white text-[12px] font-mono text-slate-700 hover:border-slate-950" title="Alejar" @click="zoomOut">−</button>
            <input type="range" min="40" max="300" step="5" v-model.number="zoom" class="w-20 accent-slate-900" />
            <button class="h-7 w-7 rounded border border-slate-300 bg-white text-[12px] font-mono text-slate-700 hover:border-slate-950" title="Acercar" @click="zoomIn">+</button>
            <button class="h-7 rounded border border-slate-300 bg-white px-1.5 text-[11px] font-mono text-slate-700 hover:border-slate-950" title="Ajustar al 100%" @click="zoomFit">{{ zoomPct }}%</button>
          </span>
          <div class="flex-1"></div>
          <button :disabled="!elements.length" title="Exportar la etiqueta como imagen PNG a la resolución de impresión (DPI)" @click="exportPng"
            class="h-7 shrink-0 rounded border border-emerald-600 bg-emerald-700 px-2 text-[11px] font-mono font-bold text-white hover:bg-emerald-500 disabled:opacity-40">
            ⭳ PNG ({{ dpi }} dpi)
          </button>
        </div>

        <!-- canvas area -->
        <div class="relative flex flex-1 items-center justify-center overflow-auto p-4">
          <div ref="canvasRef" class="relative bg-white shadow-xl border border-slate-400 select-none cursor-crosshair"
            :style="canvasStyle" @mousedown.self="startMarquee">
            <!-- grid dots -->
            <div class="absolute inset-0 pointer-events-none" :style="gridStyle"></div>
            <div v-for="(el, i) in elements" :key="el.id"
              class="absolute"
              :class="isSelected(el.id) ? 'z-20 cursor-move' : 'z-10 hover:z-10 cursor-move'"
              :style="elementStyle(el)"
              @mousedown.left="startPointer(i, $event)">
              <div v-if="isSelected(el.id)" class="absolute -inset-[1px] border-2 border-slate-950 pointer-events-none"></div>
              <span v-if="el.type === 'text'" class="font-mono" :style="textStyle(el)">{{ resolveElementValue(el, type, variableSamples) }}</span>
              <span v-else-if="el.type === 'line'" class="block" style="height:1px;background:#000"></span>
              <span v-else-if="el.type === 'rect'" class="block" style="width:100%;height:100%;border:1px solid #000"></span>
              <template v-else>
                <img v-if="codeDataUrl(el)" :src="codeDataUrl(el)" alt=""
                  class="pointer-events-none block"
                  :style="{ width: el.type === 'qrcode' ? (el.h * scale) + 'px' : '100%', height: '100%' }" />
                <span v-else class="flex items-center justify-center text-[8px] font-mono text-rose-600 font-bold uppercase"
                  :style="{ width: '100%', height: '100%' }">
                  {{ el.type === 'qrcode' ? 'QR no válido' : 'Código no válido' }}
                </span>
              </template>
              <span v-if="activeId === el.id" @mousedown.left.stop="startResize($event)"
                class="absolute bottom-0 right-0 w-3 h-3 bg-slate-950 cursor-se-resize pointer-events-auto"></span>
            </div>
            <div v-if="!elements.length" class="pointer-events-none absolute inset-0 flex items-center justify-center text-[13px] font-mono text-slate-300">
              Añade elementos desde el panel izquierdo
            </div>
            <div v-if="marquee" class="pointer-events-none absolute z-30 border border-slate-950 bg-slate-500/10" :style="marqueeStyle"></div>
          </div>
        </div>
      </div>

      <!-- Right: properties -->
      <div class="w-72 shrink-0 bg-slate-100 border-l border-slate-300 flex flex-col overflow-y-auto">
        <div class="p-3 border-b border-slate-200">
          <div class="text-[11px] font-bold uppercase tracking-widest text-slate-500 mb-2">Propiedades de la etiqueta</div>
          <label class="block mb-2">
            <span class="text-[11px] font-mono text-slate-600">Nombre</span>
            <input v-model="name" type="text" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white" />
          </label>
          <label class="block mb-2">
            <span class="text-[11px] font-mono text-slate-600">Tamaño (pulgadas)</span>
            <select v-model="sizePreset" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white" @change="applySizePreset">
              <option v-for="sz in SIZE_PRESETS" :key="sz.label" :value="sz.label">{{ sz.label }}</option>
              <option value="custom">Personalizado</option>
            </select>
          </label>
          <div class="grid grid-cols-2 gap-2 mb-2">
            <label>
              <span class="text-[11px] font-mono text-slate-600">Ancho (in)</span>
              <input v-model.number="widthInches" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white" />
            </label>
            <label>
              <span class="text-[11px] font-mono text-slate-600">Alto (in)</span>
              <input v-model.number="heightInches" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white" />
            </label>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <label>
              <span class="text-[11px] font-mono text-slate-600">Orientación</span>
              <select v-model="orientation" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white">
                <option value="HORIZONTAL">Horizontal</option>
                <option value="VERTICAL">Vertical</option>
              </select>
            </label>
            <label>
              <span class="text-[11px] font-mono text-slate-600">DPI</span>
              <input v-model.number="dpi" type="number" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[13px] font-mono focus:outline-none focus:border-slate-950 bg-white" />
            </label>
          </div>
          <div class="mt-2 text-[11px] font-mono text-slate-400">
            Dimensiones efectivas: {{ eff.w.toFixed(1) }} x {{ eff.h.toFixed(1) }} pulg.
          </div>
        </div>

        <div class="p-3 border-b border-slate-200">
          <div class="text-[11px] font-bold uppercase tracking-widest text-slate-500 mb-1">Datos variables (formulario)</div>
          <p class="text-[10px] font-mono text-slate-400 mb-2">Campos que se llenan al imprimir. Luego enlaza un elemento a su clave desde "Fuente de datos".</p>
          <div v-for="(v, vi) in variables" :key="v.key" class="flex items-center gap-1 mb-1">
            <input v-model="v.label" type="text" :placeholder="v.key"
              class="flex-1 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white"
              @input="dirty = true" />
            <span class="text-[10px] font-mono text-slate-400 font-bold shrink-0">{{ v.key }}</span>
            <button @click="removeVariable(vi)" class="text-slate-300 hover:text-rose-500 px-1 text-[13px] font-mono shrink-0" title="Quitar campo">✕</button>
          </div>
          <button @click="addVariable" class="w-full py-1 rounded border border-dashed border-slate-300 text-slate-600 hover:border-slate-950 hover:text-slate-950 text-[12px] font-mono transition">
            + Añadir campo variable
          </button>
          <p v-if="variables.length === 0" class="text-[10px] font-mono text-slate-400 mt-1">
            Ej: matrícula, número de guía, lote, sello, observación...
          </p>
        </div>

        <div v-if="selectedCount" class="p-3 border-b border-slate-200 bg-slate-50">
          <div class="text-[11px] font-bold uppercase tracking-widest text-slate-500 mb-1">
            Selección ({{ selectedCount }})
          </div>
          <div class="flex flex-wrap gap-1 mb-2">
            <button v-for="a in alignOptions" :key="a.id" :disabled="!canAlign" :title="a.title" @click="alignSel(a.id)"
              class="h-6 w-6 rounded border border-slate-300 bg-white text-[11px] font-mono text-slate-700 hover:border-slate-950 disabled:opacity-40">{{ a.icon }}</button>
            <button :disabled="!canDistribute" title="Distribuir horizontalmente" @click="distributeSel('h')"
              class="h-6 w-6 rounded border border-slate-300 bg-white text-[11px] font-mono text-slate-700 hover:border-slate-950 disabled:opacity-40">⇄</button>
            <button :disabled="!canDistribute" title="Distribuir verticalmente" @click="distributeSel('v')"
              class="h-6 w-6 rounded border border-slate-300 bg-white text-[11px] font-mono text-slate-700 hover:border-slate-950 disabled:opacity-40">⇅</button>
            <button title="Duplicar selección" @click="duplicateSel"
              class="h-6 rounded border border-slate-300 bg-white px-1.5 text-[11px] font-mono text-slate-700 hover:border-slate-950">⧉</button>
          </div>
          <div class="mb-1 flex flex-wrap gap-1">
            <button title="Traer al frente" @click="bringFront"
              class="h-6 rounded border border-slate-300 bg-white px-1.5 text-[11px] font-mono text-slate-700 hover:border-slate-950" aria-label="Traer al frente">⏫</button>
            <button title="Traer adelante" @click="bringForward"
              class="h-6 rounded border border-slate-300 bg-white px-1.5 text-[11px] font-mono text-slate-700 hover:border-slate-950" aria-label="Traer adelante">▲</button>
            <button title="Enviar atrás" @click="sendBackward"
              class="h-6 rounded border border-slate-300 bg-white px-1.5 text-[11px] font-mono text-slate-700 hover:border-slate-950" aria-label="Enviar atrás">▼</button>
            <button title="Enviar al fondo" @click="sendToBack"
              class="h-6 rounded border border-slate-300 bg-white px-1.5 text-[11px] font-mono text-slate-700 hover:border-slate-950" aria-label="Enviar al fondo">⏬</button>
          </div>
        </div>

        <div v-if="activeElement" class="p-3 border-b border-slate-200">
          <div class="text-[11px] font-bold uppercase tracking-widest text-slate-500 mb-2">
            Elemento: {{ activeElement.type }}
          </div>

          <label v-if="hasDataSource" class="block mb-2">
            <span class="text-[11px] font-mono text-slate-600">Fuente de datos</span>
            <select v-model="activeElement.dataSource" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white">
              <option value="TEXT">Texto fijo</option>
              <option v-for="f in FIELDS[type]" :key="f.key" :value="f.key">{{ f.label }} ({{ f.key }})</option>
              <optgroup label="Variable (formulario)">
                <option v-for="v in variables" :key="v.key" :value="v.key">{{ v.label || v.key }} ({{ v.key }})</option>
              </optgroup>
            </select>
          </label>

          <label v-if="isTextLike && activeElement.dataSource === 'TEXT'" class="block mb-2">
            <span class="text-[11px] font-mono text-slate-600">Texto</span>
            <input v-model="activeElement.text" type="text" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" />
          </label>

          <div class="grid grid-cols-2 gap-2 mb-2">
            <label><span class="text-[11px] font-mono text-slate-600">X (mm)</span>
              <input v-model.number="activeElement.x" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
            <label><span class="text-[11px] font-mono text-slate-600">Y (mm)</span>
              <input v-model.number="activeElement.y" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
            <label v-if="!isLine"><span class="text-[11px] font-mono text-slate-600">Ancho (mm)</span>
              <input v-model.number="activeElement.w" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
            <label v-if="!isLine"><span class="text-[11px] font-mono text-slate-600">Alto (mm)</span>
              <input v-model.number="activeElement.h" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
          </div>

          <template v-if="isTextLike">
            <div class="grid grid-cols-2 gap-2 mb-2">
              <label><span class="text-[11px] font-mono text-slate-600">Fuente (mm)</span>
                <input v-model.number="activeElement.fontSize" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
              <label><span class="text-[11px] font-mono text-slate-600">Alinear</span>
                <select v-model="activeElement.align" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white">
                  <option value="left">Izquierda</option>
                  <option value="center">Centro</option>
                  <option value="right">Derecha</option>
                </select></label>
            </div>
            <label class="flex items-center gap-2 mb-2 text-[12px] font-mono text-slate-700 cursor-pointer">
              <input type="checkbox" v-model="activeElement.bold" class="accent-slate-950" /> Negrita
            </label>
          </template>

          <template v-if="activeElement.type === 'barcode' || activeElement.type === 'qrcode'">
            <label class="block mb-2">
              <span class="text-[11px] font-mono text-slate-600">Formato</span>
              <select v-model="activeElement.barcodeFormat" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white">
                <option value="CODE128">CODE128</option>
                <option value="QR">QR</option>
              </select>
            </label>
            <label class="block mb-2"><span class="text-[11px] font-mono text-slate-600">Altura código (mm)</span>
              <input v-model.number="activeElement.barcodeHeight" type="number" step="0.5" class="w-full mt-0.5 px-2 py-1 rounded border border-slate-300 text-[12px] font-mono focus:outline-none focus:border-slate-950 bg-white" /></label>
          </template>

          <button @click="removeElement(activeElement.id)" class="mt-2 w-full py-1 rounded border border-rose-200 text-rose-600 hover:bg-rose-50 text-[12px] font-mono">Eliminar elemento</button>
        </div>
        <div v-else class="p-3 text-[12px] font-mono text-slate-400">
          Selecciona un elemento del lienzo para editar sus propiedades.
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useToastStore } from '../../stores/toast'
import { useConfirm } from '../../composables/useConfirm'
import { extractError } from '../../utils/error'
import { labelTemplatesApi } from '../../api/labelTemplates'
import { SIZE_PRESETS, FIELDS, effectiveSize, defaultElement, resolveElementValue, nextVariableKey } from '../../utils/labelConfig'
import { barcodeSvg, qrSvg, svgToDataUrl, isValidQrPayload, isValidBarcodePayload, renderLabelCanvas } from '../../utils/codeRenderer'

const props = defineProps({
  type: { type: String, default: 'CARGO' },
  show: { type: Boolean, default: true },
})
const emit = defineEmits(['close', 'saved'])

const toast = useToastStore()
const { confirm } = useConfirm()

const typeLabel = computed(() => props.type === 'PALLET' ? 'Pallet' : 'Cargo')

const templates = ref([])
const currentId = ref(null)
const name = ref('')
const sizePreset = ref('4 x 6')
const widthInches = ref(4)
const heightInches = ref(6)
const orientation = ref('HORIZONTAL')
const dpi = ref(203)
const isDefault = ref(false)
const elements = ref([])
const variables = ref([])
const selectedIds = ref([])
const loadId = ref('')
const dirty = ref(false)
const saving = ref(false)
const snapEnabled = ref(true)
const zoom = ref(100)

const eff = computed(() => effectiveSize({ widthInches: widthInches.value, heightInches: heightInches.value, orientation: orientation.value }))

const variableSamples = computed(() => {
  const map = {}
  for (const v of variables.value) map[v.key] = '[' + (v.label || v.key) + ']'
  return map
})

const baseScale = computed(() => Math.min(4, 760 / Math.max(1, eff.value.w * 25.4)))
const scale = computed(() => baseScale.value * (zoom.value / 100))
const zoomPct = computed(() => Math.round(zoom.value))
const canvasStyle = computed(() => ({
  width: Math.round(eff.value.w * 25.4 * scale.value) + 'px',
  height: Math.round(eff.value.h * 25.4 * scale.value) + 'px',
}))
const gridStyle = computed(() => {
  const step = 5 * scale.value
  return { backgroundImage: 'radial-gradient(circle, #cbd5e1 1px, transparent 1px)', backgroundSize: `${step}px ${step}px` }
})

const activeId = computed(() => selectedIds.value[selectedIds.value.length - 1] ?? null)
const selectedElements = computed(() => elements.value.filter(el => selectedIds.value.includes(el.id)))
const selectedCount = computed(() => selectedElements.value.length)
const activeElement = computed(() => elements.value.find(el => el.id === activeId.value) ?? null)
const canAlign = computed(() => selectedCount.value >= 2)
const canDistribute = computed(() => selectedCount.value >= 3)
const isSelected = (id) => selectedIds.value.includes(id)
const isTextLike = computed(() => activeElement.value?.type === 'text')
const hasDataSource = computed(() => activeElement.value && ['text', 'barcode', 'qrcode'].includes(activeElement.value.type))
const isLine = computed(() => activeElement.value?.type === 'line')

const tools = [
  { type: 'text', label: 'Texto' },
  { type: 'barcode', label: 'Código 128' },
  { type: 'qrcode', label: 'QR' },
  { type: 'line', label: 'Línea' },
  { type: 'rect', label: 'Rectángulo' },
]

const alignOptions = [
  { id: 'left', icon: '⇤L', title: 'Alinear a la izquierda' },
  { id: 'cx', icon: '⇹H', title: 'Centrar horizontalmente' },
  { id: 'right', icon: 'L⇥', title: 'Alinear a la derecha' },
  { id: 'top', icon: '⇥T', title: 'Alinear arriba' },
  { id: 'cy', icon: '⇵V', title: 'Centrar verticalmente' },
  { id: 'bottom', icon: 'B⇤', title: 'Alinear abajo' },
]

function uid() {
  return 'el' + Date.now() + '_' + Math.floor(Math.random() * 1000000000).toString(36)
}
function snapVal(v) {
  return snapEnabled.value ? Math.round(v * 2) / 2 : Math.round(v * 10) / 10
}

// ── Historial (undo / redo) ──
const undoStack = ref([])
const redoStack = ref([])
function snapshot() {
  return JSON.stringify({
    elements: elements.value,
    variables: variables.value,
    name: name.value,
    widthInches: widthInches.value,
    heightInches: heightInches.value,
    orientation: orientation.value,
    dpi: dpi.value,
    sizePreset: sizePreset.value,
    isDefault: isDefault.value,
  })
}
function pushHistory() {
  undoStack.value.push(snapshot())
  if (undoStack.value.length > 50) undoStack.value.shift()
  redoStack.value = []
}
function restoreSnapshot(s) {
  const data = JSON.parse(s)
  elements.value = data.elements || []
  variables.value = data.variables || []
  name.value = data.name ?? ''
  widthInches.value = data.widthInches ?? 4
  heightInches.value = data.heightInches ?? 6
  orientation.value = data.orientation ?? 'HORIZONTAL'
  dpi.value = data.dpi ?? 203
  sizePreset.value = data.sizePreset ?? '4 x 6'
  isDefault.value = !!data.isDefault
  const ids = new Set(elements.value.map(el => el.id))
  selectedIds.value = selectedIds.value.filter(id => ids.has(id))
  codeCache.clear()
}
function undo() {
  if (!undoStack.value.length) return
  redoStack.value.push(snapshot())
  restoreSnapshot(undoStack.value.pop())
  dirty.value = true
}
function redo() {
  if (!redoStack.value.length) return
  undoStack.value.push(snapshot())
  restoreSnapshot(redoStack.value.pop())
  dirty.value = true
}

// ── Selección ──
function selectOne(i) {
  const el = elements.value[i]
  if (el) selectedIds.value = [el.id]
}
function toggleSelect(id) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter(x => x !== id)
    : [...selectedIds.value, id]
}

// ── Elementos ──
function addElement(type) {
  pushHistory()
  const el = defaultElement(type, elements.value.length)
  el.id = uid()
  elements.value.push(el)
  selectedIds.value = [el.id]
  dirty.value = true
}
function removeElement(id) {
  pushHistory()
  elements.value = elements.value.filter(el => el.id !== id)
  selectedIds.value = selectedIds.value.filter(x => x !== id)
  dirty.value = true
}
function removeSelected() {
  if (!selectedIds.value.length) return
  pushHistory()
  const set = new Set(selectedIds.value)
  elements.value = elements.value.filter(el => !set.has(el.id))
  selectedIds.value = []
  dirty.value = true
}
function addVariable() {
  variables.value.push({ key: nextVariableKey(variables.value), label: '' })
  dirty.value = true
}
function removeVariable(i) {
  variables.value.splice(i, 1)
  dirty.value = true
}

function duplicateSel() {
  const els = selectedElements.value
  if (!els.length) return
  pushHistory()
  const clones = els.map(el => ({ ...JSON.parse(JSON.stringify(el)), id: uid() }))
  clones.forEach(c => {
    c.x = snapVal(Math.max(0, c.x - 2))
    c.y = snapVal(Math.max(0, c.y - 2))
  })
  elements.value.push(...clones)
  selectedIds.value = clones.map(c => c.id)
  dirty.value = true
}

// ── Capas ──
function reorder(id, to) {
  pushHistory()
  const i = elements.value.findIndex(el => el.id === id)
  if (i < 0) return
  const [el] = elements.value.splice(i, 1)
  elements.value.splice(Math.max(0, Math.min(to, elements.value.length)), 0, el)
  dirty.value = true
}
function bringFront() { if (activeId.value) reorder(activeId.value, elements.value.length) }
function bringForward() { if (activeId.value) { const i = elements.value.findIndex(el => el.id === activeId.value); reorder(activeId.value, i + 1) } }
function sendBackward() { if (activeId.value) { const i = elements.value.findIndex(el => el.id === activeId.value); reorder(activeId.value, i - 1) } }
function sendToBack() { if (activeId.value) reorder(activeId.value, 0) }

// ── Alinear / distribuir ──
function alignAll(id, apply) {
  const els = selectedElements.value
  if (!els.length) return
  pushHistory()
  apply(els)
  dirty.value = true
}
function alignSel(id) {
  if (!canAlign.value) return
  alignAll(id, (els) => {
    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity
    els.forEach(el => { minX = Math.min(minX, el.x); maxX = Math.max(maxX, el.x + el.w); minY = Math.min(minY, el.y); maxY = Math.max(maxY, el.y + el.h) })
    const cx = (minX + maxX) / 2
    const cy = (minY + maxY) / 2
    els.forEach(el => {
      switch (id) {
        case 'left': el.x = snapVal(minX); break
        case 'cx': el.x = snapVal(cx - el.w / 2); break
        case 'right': el.x = snapVal(maxX - el.w); break
        case 'top': el.y = snapVal(minY); break
        case 'cy': el.y = snapVal(cy - el.h / 2); break
        case 'bottom': el.y = snapVal(maxY - el.h); break
      }
    })
  })
}
function distributeSel(axis) {
  if (!canDistribute.value) return
  alignAll(axis, (els) => {
    if (axis === 'h') {
      const sorted = [...els].sort((a, b) => a.x - b.x)
      const totalW = sorted.reduce((s, e) => s + e.w, 0)
      const start = sorted[0].x
      const end = sorted[sorted.length - 1].x + sorted[sorted.length - 1].w
      const gap = (end - start - totalW) / (sorted.length - 1)
      let x = start
      sorted.forEach((el, i) => { el.x = snapVal(i === 0 ? x : (x + gap)); x = el.x + el.w })
    } else {
      const sorted = [...els].sort((a, b) => a.y - b.y)
      const totalH = sorted.reduce((s, e) => s + e.h, 0)
      const start = sorted[0].y
      const end = sorted[sorted.length - 1].y + sorted[sorted.length - 1].h
      const gap = (end - start - totalH) / (sorted.length - 1)
      let y = start
      sorted.forEach((el, i) => { el.y = snapVal(i === 0 ? y : (y + gap)); y = el.y + el.h })
    }
  })
}

// ── Zoom ──
function zoomIn() { zoom.value = Math.min(300, zoom.value + 10) }
function zoomOut() { zoom.value = Math.max(40, zoom.value - 10) }
function zoomFit() { zoom.value = 100 }

// ── Drag / resize / marquesina ──
const canvasRef = ref(null)
let dragState = null
const marqueeState = ref(null)

function startPointer(i, e) {
  const el = elements.value[i]
  if (!el) return
  pushHistory()
  if (e.shiftKey) {
    toggleSelect(el.id)
  } else if (!selectedIds.value.includes(el.id)) {
    selectedIds.value = [...selectedIds.value, el.id]
  }
  const ids = [...selectedIds.value]
  const entries = ids
    .map(id => elements.value.find(x => x.id === id))
    .filter(Boolean)
    .map(x => ({ id: x.id, ox: x.x, oy: x.y }))
  dragState = { mode: 'drag', startX: e.clientX, startY: e.clientY, entries }
  document.addEventListener('pointermove', onPointerMove)
  document.addEventListener('pointerup', endPointer)
}
function startResize(e) {
  const el = activeElement.value
  if (!el) return
  pushHistory()
  dragState = { mode: 'resize', startX: e.clientX, startY: e.clientY, origW: el.w, origH: el.h }
  document.addEventListener('pointermove', onPointerMove)
  document.addEventListener('pointerup', endPointer)
}
function onPointerMove(e) {
  if (!dragState) return
  const dx = (e.clientX - dragState.startX) / scale.value
  const dy = (e.clientY - dragState.startY) / scale.value
  if (dragState.mode === 'drag') {
    for (const entry of dragState.entries) {
      const el = elements.value.find(x => x.id === entry.id)
      if (el) {
        el.x = Math.max(0, snapVal(entry.ox + dx))
        el.y = Math.max(0, snapVal(entry.oy + dy))
      }
    }
  } else {
    const el = activeElement.value
    if (el) {
      el.w = Math.max(2, Math.round((dragState.origW + dx) * 10) / 10)
      el.h = Math.max(1, Math.round((dragState.origH + dy) * 10) / 10)
    }
  }
  dirty.value = true
}
function endPointer() {
  dragState = null
  document.removeEventListener('pointermove', onPointerMove)
  document.removeEventListener('pointerup', endPointer)
}

function startMarquee(e) {
  if (!canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  const px = e.clientX - rect.left
  const py = e.clientY - rect.top
  marqueeState.value = { sx: px, sy: py, cx: px, cy: py }
  document.addEventListener('pointermove', onMarqueeMove)
  document.addEventListener('pointerup', endMarquee)
}
function onMarqueeMove(e) {
  if (!marqueeState.value || !canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  marqueeState.value.cx = e.clientX - rect.left
  marqueeState.value.cy = e.clientY - rect.top
}
function endMarquee() {
  if (!marqueeState.value || !canvasRef.value) {
    marqueeState.value = null
    return
  }
  const s = scale.value
  const x1 = Math.min(marqueeState.value.sx, marqueeState.value.cx) / s
  const y1 = Math.min(marqueeState.value.sy, marqueeState.value.cy) / s
  const x2 = Math.max(marqueeState.value.sx, marqueeState.value.cx) / s
  const y2 = Math.max(marqueeState.value.sy, marqueeState.value.cy) / s
  selectedIds.value = elements.value
    .filter(el => el.x < x2 && el.x + el.w > x1 && el.y < y2 && el.y + el.h > y1)
    .map(el => el.id)
  marqueeState.value = null
  document.removeEventListener('pointermove', onMarqueeMove)
  document.removeEventListener('pointerup', endMarquee)
}
const marquee = computed(() => marqueeState.value)
const marqueeStyle = computed(() => {
  if (!marqueeState.value) return {}
  const x = Math.min(marqueeState.value.sx, marqueeState.value.cx)
  const y = Math.min(marqueeState.value.sy, marqueeState.value.cy)
  return {
    left: x + 'px',
    top: y + 'px',
    width: Math.abs(marqueeState.value.cx - marqueeState.value.sx) + 'px',
    height: Math.abs(marqueeState.value.cy - marqueeState.value.sy) + 'px',
  }
})

// ── Preview real de códigos ──
const codeCache = new Map()
function codeDataUrl(el) {
  if (el.type !== 'barcode' && el.type !== 'qrcode') return null
  const value = resolveElementValue(el, props.type, variableSamples.value)
  const valid = el.type === 'qrcode'
    ? isValidQrPayload(value)
    : isValidBarcodePayload(value, el.barcodeFormat || 'CODE128')
  if (!valid) return null
  const fmt = (el.barcodeFormat || (el.type === 'qrcode' ? 'QR' : 'CODE128'))
  const key = `${el.id}|${fmt}|${value}`
  if (codeCache.has(key)) return codeCache.get(key)
  const svg = el.type === 'qrcode' ? qrSvg(String(value), 2) : barcodeSvg(String(value), fmt, 120, 2)
  const url = svgToDataUrl(svg)
  codeCache.set(key, url)
  if (codeCache.size > 300) codeCache.clear()
  return url
}

function elementStyle(el) {
  const s = scale.value
  return {
    left: (el.x * s) + 'px',
    top: (el.y * s) + 'px',
    width: (el.w * s) + 'px',
    height: (el.type === 'line' ? 1 : el.h * s) + 'px',
  }
}
function textStyle(el) {
  return { fontSize: (el.fontSize * scale.value * 3.3) + 'px', fontWeight: el.bold ? 'bold' : 'normal', textAlign: el.align }
}

// ── Plantilla ──
function applySizePreset() {
  pushHistory()
  const p = SIZE_PRESETS.find(x => x.label === sizePreset.value)
  if (p) { widthInches.value = p.w; heightInches.value = p.h }
  dirty.value = true
}

function newTemplate() {
  pushHistory()
  currentId.value = null
  name.value = ''
  sizePreset.value = '4 x 6'
  widthInches.value = 4
  heightInches.value = 6
  orientation.value = 'HORIZONTAL'
  dpi.value = 203
  isDefault.value = false
  elements.value = []
  variables.value = []
  selectedIds.value = []
  codeCache.clear()
  dirty.value = true
}

async function loadSelected() {
  if (!loadId.value) return
  try {
    const res = await labelTemplatesApi.get(loadId.value)
    applyDto(res.data)
  } catch (e) {
    toast.error(extractError(e))
  }
}

function applyDto(t) {
  pushHistory()
  currentId.value = t.id
  name.value = t.name || ''
  widthInches.value = Number(t.widthInches || 4)
  heightInches.value = Number(t.heightInches || 6)
  orientation.value = t.orientation || 'HORIZONTAL'
  dpi.value = t.dpi || 203
  isDefault.value = !!t.isDefault
  const sizeMatch = SIZE_PRESETS.find(x => x.w === widthInches.value && x.h === heightInches.value)
  sizePreset.value = sizeMatch ? sizeMatch.label : 'custom'
  let parsed = []
  let parsedVars = []
  try { parsed = JSON.parse(t.configJson || '{"elements":[]}').elements || [] } catch {}
  try { parsedVars = JSON.parse(t.configJson || '{}').variables || [] } catch {}
  elements.value = parsed
  variables.value = parsedVars
  selectedIds.value = []
  codeCache.clear()
  dirty.value = false
}

async function save() {
  if (!name.value) { toast.warning('Ingresa un nombre para la plantilla'); return }
  if (!elements.value.length) { toast.warning('La plantilla no tiene elementos'); return }
  const payload = {
    name: name.value,
    type: props.type,
    widthInches: widthInches.value,
    heightInches: heightInches.value,
    orientation: orientation.value,
    dpi: dpi.value,
    isDefault: isDefault.value,
    configJson: JSON.stringify({ elements: elements.value.map(el => ({ ...el })), variables: variables.value.map(v => ({ key: v.key, label: v.label || v.key })) }),
  }
  saving.value = true
  try {
    if (currentId.value) {
      await labelTemplatesApi.update(currentId.value, payload)
      toast.success('Plantilla actualizada')
    } else {
      const res = await labelTemplatesApi.create(payload)
      currentId.value = res.data?.id || currentId.value
      toast.success('Plantilla guardada')
    }
    dirty.value = false
    await refreshTemplates()
    emit('saved')
  } catch (e) {
    toast.error(extractError(e))
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (!currentId.value) return
  if (!(await confirm({ message: `¿Eliminar la plantilla "${name.value}"?`, danger: true }))) return
  try {
    await labelTemplatesApi.remove(currentId.value)
    toast.success('Plantilla eliminada')
    await refreshTemplates()
    emit('saved')
  } catch (e) {
    toast.error(extractError(e))
  }
}

// ── Export PNG a resolución de impresión ──
async function exportPng() {
  if (!elements.value.length) { toast.warning('La plantilla no tiene elementos'); return }
  try {
    const { canvas } = await renderLabelCanvas({
      widthInches: widthInches.value,
      heightInches: heightInches.value,
      orientation: orientation.value,
      dpi: dpi.value,
      elements: elements.value,
      resolveValue: (el) => resolveElementValue(el, props.type, variableSamples.value),
    })
    const url = canvas.toDataURL('image/png')
    const a = document.createElement('a')
    a.href = url
    a.download = (name.value || 'etiqueta').replace(/\s+/g, '_') + '.png'
    document.body.appendChild(a)
    a.click()
    a.remove()
    toast.success(`PNG exportado (${dpi.value} dpi)`)
  } catch (e) {
    toast.error(extractError(e))
  }
}

async function refreshTemplates() {
  try {
    const res = await labelTemplatesApi.list(props.type)
    templates.value = res.data || []
  } catch (e) {
    console.warn('Failed to load templates', e)
  }
}

// ── Teclado: borrar, flechas (0.5 mm / 5 mm con shift), undo/redo ──
function onKeydown(e) {
  if (!props.show) return
  const target = e.target
  const typing = target && /INPUT|SELECT|TEXTAREA/.test(target.tagName)
  const ctrl = e.ctrlKey || e.metaKey
  if (ctrl && e.key === 'z' && !e.shiftKey) { e.preventDefault(); undo(); return }
  if ((ctrl && e.key === 'y') || (ctrl && e.shiftKey && e.key === 'Z')) { e.preventDefault(); redo(); return }
  if (typing) return
  if (e.key === 'Delete' || e.key === 'Backspace') {
    if (selectedIds.value.length) { e.preventDefault(); removeSelected() }
    return
  }
  const moves = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }
  const mv = moves[e.key]
  if (mv) {
    const step = e.shiftKey ? 5 : 0.5
    const els = selectedElements.value
    if (!els.length) return
    e.preventDefault()
    pushHistory()
    els.forEach(el => {
      el.x = Math.max(0, snapVal(el.x + mv[0] * step))
      el.y = Math.max(0, snapVal(el.y + mv[1] * step))
    })
    dirty.value = true
  }
}

onMounted(() => {
  refreshTemplates()
  newTemplate()
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  endPointer()
  document.removeEventListener('pointermove', onMarqueeMove)
  document.removeEventListener('pointerup', endMarquee)
})
</script>
