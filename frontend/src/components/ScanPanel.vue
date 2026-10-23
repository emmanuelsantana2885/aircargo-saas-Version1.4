<template>
  <div v-if="active" class="border-2 border-dashed rounded-lg p-3 sm:p-4 mb-4 transition-all duration-300"
       :class="flashClass">
    <!-- Barra de estado contextual (mockup + SDD §5.2) -->
    <div class="rounded-2xl border-[1.5px] border-blue-600 bg-blue-50 p-3 sm:p-4 mb-3 flex flex-col gap-2.5"
         aria-live="polite">
      <div class="flex items-start justify-between gap-2">
        <div class="min-w-0 flex items-center gap-2">
          <span class="w-2.5 h-2.5 rounded-full shrink-0 animate-pulse" :class="scanning ? 'bg-blue-600' : 'bg-blue-300'"></span>
          <div class="min-w-0">
            <div class="text-[17px] font-bold leading-tight text-blue-800">{{ t('scan.banner.' + expected) }}</div>
            <div class="text-[13px] sm:text-[14px] text-slate-700">{{ t('scan.bannerHint.' + expected) }}</div>
          </div>
        </div>
        <button @click="$emit('exit-scan')"
          class="shrink-0 text-[12px] font-bold text-slate-500 hover:text-red-600 uppercase px-2 py-1 rounded hover:bg-red-50">
          {{ t('scan.exit') }}
        </button>
      </div>

      <!-- Stepper guiado: 1·ULD → 2·Seal → 3·MAWBs -->
      <div class="flex items-center gap-2 flex-wrap">
        <span v-for="s in STEPS" :key="s.id"
          class="inline-flex items-center gap-1.5 h-8 px-3 rounded-full border text-[13px] font-semibold whitespace-nowrap transition-colors"
          :class="stepClass(s.id)">
          <span v-if="expected === s.id" class="w-2 h-2 rounded-full bg-white"></span>
          <span v-else-if="stepDone(s.id)" class="font-bold">&#10003;</span>
          {{ t(s.key) }}
        </span>
        <span v-if="pieceCounter"
          class="inline-flex items-center h-8 px-3 rounded-full border border-blue-300 bg-white text-[13px] font-bold text-blue-800 font-mono">
          {{ t('scan.pieceCounter', pieceCounter) }}
        </span>
      </div>

      <!-- Último resultado -->
      <div v-if="lastResult"
           class="text-[13px] font-bold px-2.5 py-1.5 rounded-lg self-start"
           :class="lastResult.success ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'">
        {{ lastResult.message || lastResult.error }}
      </div>
    </div>

    <!-- Scan target: Auto (contextual), pieza (MAWB/HAWB) o sello -->
    <div class="flex items-center gap-1.5 mb-3 flex-wrap">
      <span class="text-[11px] font-bold text-slate-400 uppercase tracking-wider mr-1">{{ t('scan.mode.label') }}</span>
      <button @click="setTarget('auto')"
        class="text-[12px] font-bold uppercase px-3 py-1.5 rounded border transition-colors"
        :class="[target === 'auto' ? 'bg-slate-900 border-slate-900 text-white' : 'bg-white border-slate-300 text-slate-500 hover:bg-slate-50',
                 selectorAlert ? 'ring-2 ring-amber-400 animate-pulse' : '']">
        ⚡ {{ t('scan.mode.auto') }}
      </button>
      <button @click="setTarget('piece')"
        class="text-[12px] font-bold uppercase px-3 py-1.5 rounded border transition-colors"
        :class="[target === 'piece' ? 'bg-slate-900 border-slate-900 text-white' : 'bg-white border-slate-300 text-slate-500 hover:bg-slate-50',
                 selectorAlert ? 'ring-2 ring-amber-400 animate-pulse' : '']">
        🧾 {{ t('scan.mode.piece') }}
      </button>
      <button @click="setTarget('seal')"
        class="text-[12px] font-bold uppercase px-3 py-1.5 rounded border transition-colors"
        :class="[target === 'seal' ? 'bg-slate-900 border-slate-900 text-white' : 'bg-white border-slate-300 text-slate-500 hover:bg-slate-50',
                 selectorAlert ? 'ring-2 ring-amber-400 animate-pulse' : '']">
        🔒 {{ t('scan.mode.seal') }}
      </button>
    </div>

    <!-- Info for unsaved ULD -->
    <div v-if="!uldId" class="bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mb-3 flex items-center gap-2">
      <span class="text-amber-600 text-sm">⚡</span>
      <span class="text-[12px] font-bold text-amber-800 uppercase tracking-wide">
        {{ t('scan.instructions') }}
      </span>
    </div>

    <!-- Scan Input -->
    <div class="relative mb-3">
      <input
        ref="scanInput"
        v-model="scanCode"
        @keydown.enter.prevent="processScan"
        type="text"
        :placeholder="inputPlaceholder"
        inputmode="none"
        class="w-full bg-white border-2 border-slate-300 rounded-lg px-4 py-3 text-[15px] font-mono font-bold text-slate-900
               focus:outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200
               placeholder:text-slate-300 placeholder:font-sans placeholder:font-normal placeholder:text-[14px]"
        :disabled="processing"
      />
      <div v-if="processing" class="absolute right-3 top-1/2 -translate-y-1/2">
        <div class="w-5 h-5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin"></div>
      </div>
    </div>

    <!-- Scan History -->
    <div v-if="history.length" class="max-h-32 overflow-y-auto space-y-1 mb-2">
      <div v-for="(entry, i) in history" :key="i"
           class="flex items-center gap-2 text-[12px] py-1 px-2 rounded"
           :class="entry.warning ? 'bg-amber-50 text-amber-700' : entry.success ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'">
        <span>{{ entry.success ? (entry.warning ? '⚠' : '✓') : '✗' }}</span>
        <span class="font-bold">{{ entry.awbNumber }}</span>
        <span v-if="entry.kind === 'uld'" class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded bg-blue-100 text-blue-700">ULD</span>
        <span v-else-if="entry.kind === 'seal'" class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded bg-amber-100 text-amber-700">{{ t('scan.mode.seal') }}</span>
        <span v-else-if="entry.kind === 'unknown'" class="text-[10px] font-bold uppercase px-1.5 py-0.5 rounded bg-amber-100 text-amber-700">?</span>
        <span v-if="entry.pieceNumber">{{ t('scan.pieceLabel', { n: entry.pieceNumber }) }}</span>
        <span v-if="entry.totalOnUld">({{ entry.totalOnUld }}/{{ entry.maxAllowed }})</span>
        <span v-if="entry.warning" class="ml-1 text-amber-600 italic truncate max-w-[120px]" :title="entry.warning">{{ t('scan.noBooking') }}</span>
        <span class="ml-auto opacity-60">{{ entry.time }}</span>
      </div>
    </div>

    <!-- Actions -->
    <div class="flex items-center gap-2 mt-2">
      <button @click="undoLast" :disabled="!canUndo"
              class="text-[12px] font-bold px-3 py-1.5 rounded border transition-colors"
              :class="canUndo ? 'border-slate-300 text-slate-600 hover:bg-slate-100' : 'border-slate-200 text-slate-300 cursor-not-allowed'">
        ↩ {{ t('scan.undoLast') }}
      </button>
      <button @click="openCamera"
              class="text-[12px] font-bold px-3 py-1.5 rounded border border-blue-300 text-blue-600 hover:bg-blue-50 transition-colors ml-auto">
        📷 {{ t('scan.cameraBtn') }}
      </button>
    </div>

    <!-- Camera Modal (reusable) -->
    <BarcodeCameraModal
      :show="showCamera"
      :title="t('ulds.scanWithCamera')"
      :hint="t('scan.cameraHint')"
      :should-accept="shouldScan"
      @detected="onCameraDetected"
      @close="closeCamera"
    />
  </div>
</template>

<script setup>
import { ref, nextTick, watch, computed, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { scanApi } from '@/api/scan.js'
import { uldTypeCatalogApi } from '@/api/uldTypeCatalog'
import { detectScanType } from '@/utils/scanType'
import BarcodeCameraModal from '@/components/BarcodeCameraModal.vue'

const { t } = useI18n()

const props = defineProps({
  active: { type: Boolean, default: false },
  uldId: { type: String, default: '' },
  uldNumber: { type: String, default: '' },
  sealNumber: { type: String, default: '' },
})

// scan-focus: el padre resalta el campo del formulario que espera a continuación
// ( 'uld' | 'seal' | 'mawb' | '' ). scan-error marca ese campo en rojo.
const emit = defineEmits(['piece-added', 'piece-removed', 'exit-scan', 'update-mawb-pieces', 'ULD_FOUND',
  'uld-number-scanned', 'seal-scanned', 'scan-focus', 'scan-error', 'unknown-code'])

const scanInput = ref(null)
const scanCode = ref('')
const processing = ref(false)
const scanning = ref(false)
const lastResult = ref(null)
const flashClass = ref('')
const history = ref([])
const showCamera = ref(false)
// Destino del escaneo: 'auto' (contextual, por prefijo), pieza (MAWB/HAWB) o sello.
const target = ref('auto')
// Siguiente paso esperado de la secuencia guiada ULD → Sello → MAWBs (SDD §5)
const expected = ref('uld')
// Destello del selector cuando un código no se reconoce (SDD §7)
const selectorAlert = ref(false)
// Contador de piezas del ULD activo mostrado en la barra de estado (SDD §5.2)
const pieceCounter = ref(null)
// Pasos de la secuencia guiada ULD → Sello → MAWBs (mockup: stepper 1-2-3)
const STEPS = [
  { id: 'uld', key: 'scan.step.uld' },
  { id: 'seal', key: 'scan.step.seal' },
  { id: 'mawb', key: 'scan.step.mawb' },
]

function stepDone(step) {
  if (step === 'uld') return !!props.uldNumber
  if (step === 'seal') return !!props.sealNumber
  return false
}

function stepClass(step) {
  if (expected.value === step) return 'bg-blue-600 border-blue-600 text-white'
  if (stepDone(step)) return 'bg-emerald-50 border-emerald-600 text-emerald-700'
  return 'bg-white border-slate-300 text-slate-500'
}
const canUndo = computed(() => {
  const last = history.value[0]
  return !!(last && last.success && last.mawbId) // solo piezas registradas son deshacibles
})
const inputPlaceholder = computed(() => {
  if (target.value === 'seal') return t('scan.placeholderSeal')
  if (target.value === 'auto') {
    if (expected.value === 'uld') return t('scan.placeholderUld')
    if (expected.value === 'seal') return t('scan.placeholderSeal')
  }
  return t('scan.inputPlaceholder')
})

function setTarget(mode) {
  target.value = mode
  selectorAlert.value = false
  // Al elegir manualmente un tipo, el foco del formulario pasa a ese campo
  emit('scan-focus', mode === 'seal' ? 'seal' : mode === 'piece' ? 'mawb' : '')
  scanInput.value?.focus()
}

watch(target, () => { scanInput.value?.focus() })

// Tipos ULD desde el catálogo dinámico (normas IATA); fallback a lista legacy
const LEGACY_ULD_TYPES = ['PMC', 'PAH', 'PAG', 'PAJ', 'AAY', 'AAZ', 'AAD', 'PIP', 'AMP', 'AMJ', 'PMH']
const uldCodes = ref([...LEGACY_ULD_TYPES])
uldTypeCatalogApi.getAll(true)
  .then(res => {
    const codes = (res.data || []).map(x => x.code).filter(Boolean)
    if (codes.length) uldCodes.value = codes
  })
  .catch(() => {})

let lastDetectedCode = ''
let lastDetectedAt = 0
let selectorAlertTimer = null

// Auto-focus when activated: la secuencia empieza por el campo que falta
// (immediate: también al montar ya activo, p. ej. al restaurar un ULD en scan)
watch(() => props.active, async (val) => {
  if (val) {
    expected.value = props.uldNumber ? 'seal' : 'uld'
    emit('scan-focus', expected.value)
    await nextTick()
    scanInput.value?.focus()
  } else {
    pieceCounter.value = null
    closeCamera()
  }
}, { immediate: true })

// Clean up on unmount
onBeforeUnmount(() => {
  closeCamera()
  if (selectorAlertTimer) clearTimeout(selectorAlertTimer)
})

// Añade una entrada al historial (máx. 10) y la muestra como último resultado
function addHistory(entry) {
  entry.time = entry.time || 'ahora'
  lastResult.value = entry
  history.value.unshift(entry)
  if (history.value.length > 10) history.value.pop()
}

// Feedback háptico en éxito (SDD §7); solo en dispositivos que lo soportan
function haptic() {
  try { navigator.vibrate?.(15) } catch { /* sin soporte */ }
}

// El código escaneado no encaja con ningún tipo conocido → toast + selector (SDD §7)
function handleUnknown(code) {
  addHistory({ success: false, kind: 'unknown', awbNumber: code, error: t('scan.message.unknown') })
  flash('red')
  selectorAlert.value = true
  if (selectorAlertTimer) clearTimeout(selectorAlertTimer)
  selectorAlertTimer = setTimeout(() => { selectorAlert.value = false }, 2500)
  emit('unknown-code', code)
  emit('scan-focus', '')
}

// En modo Auto, un código que no parece ni ULD, ni sello, ni MAWB/HAWB
const MAWB_RE = /^\d{3}-?\d{7,8}$/
const HAWB_RE = /^[A-Z0-9][A-Z0-9-]{3,}$/i

async function processScan(codeOverride) {
  // Enter (teclado/lápiz-disparador) envía el evento como argumento: solo aceptamos strings explícitos
  const code = (typeof codeOverride === 'string' ? codeOverride : (scanCode.value || '')).trim()
  if (!code || processing.value) return

  processing.value = true
  scanning.value = true

  try {
    const detected = detectScanType(code, uldCodes.value)
    // Destino efectivo: en Auto manda la autodetección; en modo explícito manda el
    // selector, salvo un código ULD que siempre se asigna (compatibilidad total).
    let kind = target.value === 'auto' ? detected : target.value
    if (target.value === 'piece' && detected === 'uld') kind = 'uld'

    // 1) Códigos ULD: asignan el número de ULD y avanzan a Sello
    if (kind === 'uld') {
      const upper = code.toUpperCase()
      const norm = (s) => (s || '').toUpperCase().replace(/[\s-]/g, '')
      const already = norm(upper) === norm(props.uldNumber)
      if (!already) emit('uld-number-scanned', upper)
      addHistory({
        success: true,
        kind: 'uld',
        awbNumber: upper,
        message: already ? t('scan.message.uldAlready', { code: upper }) : t('scan.message.uldAssigned', { code: upper }),
      })
      haptic()
      flash('emerald')
      expected.value = 'seal'
      emit('scan-focus', 'seal')
      return
    }

    // 2) Sello: va al campo sello del formulario ULD y avanza a MAWBs
    if (kind === 'seal') {
      const upper = code.toUpperCase()
      emit('seal-scanned', upper)
      addHistory({ success: true, kind: 'seal', awbNumber: upper, message: t('scan.message.sealSet', { code: upper }) })
      haptic()
      flash('emerald')
      expected.value = 'mawb'
      emit('scan-focus', 'mawb')
      return
    }

    // 3) Modo Auto: si no parece ni ULD, ni sello, ni MAWB/HAWB → no reconocido
    // (sin llamada al backend; toast + selector rápido de tipo, SDD §7)
    if (target.value === 'auto' && kind === 'mawb' && !MAWB_RE.test(code) && !HAWB_RE.test(code)) {
      handleUnknown(code)
      return
    }

    // 4) Sin ULD guardado no hay piezas que registrar
    if (!props.uldId) {
      addHistory({ success: false, kind: 'piece', awbNumber: code, error: t('scan.message.uldFirst') })
      flash('red')
      emit('scan-error', 'uld')
      return
    }

    const res = await scanApi.piece({
      uldId: props.uldId,
      awbNumber: code,
      source: 'BARCODE',
    })

    const entry = { awbNumber: code, time: 'ahora', kind: 'piece' }
    if (res.data.success) {
      Object.assign(entry, {
        success: true,
        message: `Pieza #${res.data.pieceNumber} — ${res.data.awbNumber}`,
        pieceNumber: res.data.pieceNumber,
        mawbId: res.data.mawbId,
        totalOnUld: res.data.totalOnUld,
        maxAllowed: res.data.totalOnUld + res.data.availablePieces,
        warning: res.data.warning || null,
      })
      haptic()
      flash(res.data.warning ? 'amber' : 'emerald')
      pieceCounter.value = {
        n: res.data.totalOnUld,
        max: res.data.totalOnUld + res.data.availablePieces,
      }
      expected.value = 'mawb'
      emit('scan-focus', 'mawb')
      emit('piece-added', res.data)
    } else {
      Object.assign(entry, { success: false, error: res.data.error })
      flash('red')
      emit('scan-error', 'mawb')
    }
    addHistory(entry)
  } catch (e) {
    // Modo Auto: si el backend rechaza el código y no parece un MAWB/HAWB válido,
    // trátalo como no reconocido (toast + selector rápido de tipo, SDD §7)
    const looksLikeAwb = MAWB_RE.test(code) || HAWB_RE.test(code)
    if (target.value === 'auto' && !looksLikeAwb) {
      handleUnknown(code)
    } else {
      const msg = e.response?.data?.error || e.message || 'Error de conexión'
      addHistory({ success: false, kind: 'piece', error: msg, awbNumber: code })
      flash('red')
      emit('scan-error', 'mawb')
    }
  } finally {
    processing.value = false
    scanning.value = false
    // El código no reconocido se conserva en el input para re-dirigirlo con el selector
    if (!(selectorAlert.value)) scanCode.value = ''
    await nextTick()
    scanInput.value?.focus()
  }
}

async function undoLast() {
  if (!canUndo.value) return
  const last = history.value[0]
  try {
    await scanApi.undoLast(props.uldId, last.mawbId)
    history.value.shift()
    if (pieceCounter.value) {
      pieceCounter.value = { ...pieceCounter.value, n: Math.max(0, pieceCounter.value.n - 1) }
    }
    lastResult.value = { success: true, message: 'Última pieza eliminada', awbNumber: last.awbNumber }
    flash('amber')
    emit('piece-removed', { awbNumber: last.awbNumber, pieceNumber: last.pieceNumber })
  } catch {
    lastResult.value = { success: false, error: 'No se pudo deshacer', awbNumber: last.awbNumber }
  }
}

function openCamera() {
  showCamera.value = true
}

// Callback del modal de cámara reutilizable
function onCameraDetected(code) {
  if (!shouldScan(code)) return
  processScan(markScanned(code))
}

// Evita re-disparar el mismo código mientras sigue enfrente (ventana de 3s)
function shouldScan(code) {
  if (!code || processing.value) return false
  if (code === lastDetectedCode && Date.now() - lastDetectedAt < 3000) return false
  return true
}

function markScanned(code) {
  lastDetectedCode = code
  lastDetectedAt = Date.now()
  return code
}

function closeCamera() {
  showCamera.value = false
}

const FLASH_CLASSES = {
  emerald: 'border-emerald-400 bg-emerald-50',
  green: 'border-green-400 bg-green-50',
  amber: 'border-amber-400 bg-amber-50',
  red: 'border-red-400 bg-red-50',
}

function flash(color) {
  flashClass.value = FLASH_CLASSES[color] || ''
  // SDD §5.2: resalte 2 s en acierto; vibración doble en error (sin bloquear el escaneo)
  if (color === 'red') {
    try { navigator.vibrate?.([50, 70, 50]) } catch { /* sin soporte */ }
  }
  setTimeout(() => { flashClass.value = '' }, 2000)
}
</script>
