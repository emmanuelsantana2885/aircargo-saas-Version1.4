<template>
  <div v-if="show" class="fixed inset-0 bg-black/60 flex items-center justify-center" :style="{ zIndex: zIndex }" @click.self="requestClose">
    <div class="bg-white rounded-xl p-6 max-w-lg w-full mx-4 shadow-2xl"
      role="dialog" aria-modal="true" :aria-label="title">
      <div class="flex items-center justify-between mb-4">
        <span class="text-[14px] font-bold text-slate-900 uppercase">{{ title }}</span>
        <button @click="requestClose" class="text-slate-400 hover:text-red-500 text-lg font-bold" :aria-label="t('common.close')">✕</button>
      </div>
      <div class="relative w-full rounded-lg overflow-hidden border border-slate-200 bg-black" style="min-height: 300px;">
        <video ref="cameraVideo" autoplay playsinline muted class="w-full h-full object-cover" style="min-height: 300px;"></video>
        <!-- Retículo de apuntado -->
        <div v-if="cameraReady && !cameraError" class="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div class="w-[78%] h-[42%] border-2 border-white/80 rounded-lg shadow-[0_0_0_9999px_rgba(0,0,0,0.35)]"></div>
        </div>
        <div v-if="!cameraReady && !cameraError" class="absolute inset-0 flex flex-col items-center justify-center bg-black/60 text-white text-[13px] font-bold z-10 gap-2">
          <div class="w-6 h-6 border-2 border-white/70 border-t-transparent rounded-full animate-spin"></div>
          <span>{{ t('camera.starting') }}</span>
        </div>
        <div v-if="cameraError" class="absolute inset-0 flex flex-col items-center justify-center bg-black/70 text-white text-[12px] font-bold z-10 px-4 text-center">
          <span>{{ cameraError }}</span>
          <button @click="retryCamera"
            class="mt-3 text-[12px] font-bold px-3 py-1.5 rounded border border-white/60 text-white hover:bg-white/10 transition">
            ↻ {{ t('camera.retry') }}
          </button>
        </div>
        <div v-if="cameraReady && !cameraError" class="absolute bottom-2 left-2 bg-black/60 text-white text-[11px] px-2 py-1 rounded z-10">
          {{ engineLabel }}
        </div>
        <div v-if="cameraReady && !cameraError" class="absolute bottom-2 right-2 bg-black/60 text-white/80 text-[10px] px-2 py-1 rounded z-10 font-mono">
          {{ debugStatus }}
        </div>
      </div>
      <p class="text-[12px] text-slate-400 mt-3 text-center">{{ hint || t('camera.aim') }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  show: { type: Boolean, default: false },
  title: { type: String, default: '' },
  hint: { type: String, default: '' },
  zIndex: { type: Number, default: 50 },
  // El codigo detectado ya fue aceptado por el padre (evita re-disparar el mismo codigo)
  shouldAccept: { type: Function, default: null },
})

const emit = defineEmits(['detected', 'close'])

const cameraVideo = ref(null)
const cameraReady = ref(false)
const cameraError = ref('')
const engine = ref('') // 'zbar' | 'native'

// --- Diagnostico (temporal): ayuda a localizar por que la camara no decodifica ---
const framesScanned = ref(0)
const scanErrors = ref(0)
const lastScanError = ref('')

const engineLabel = computed(() =>
  engine.value === 'zbar' ? 'Barcode + QR (ZBar)' : 'Barcode + QR'
)

const debugStatus = computed(() => {
  if (engine.value === 'zbar') {
    return `zbar f:${framesScanned.value} e:${scanErrors.value}${lastScanError.value ? ' ' + lastScanError.value : ''}`
  }
  return `native f:${framesScanned.value}`
})

let cameraStream = null
let detectTimer = null
let scanBusy = false
let scanGeneration = 0
let zbarModule = null
let zbarCanvas = null
let zbarCtx = null

watch(() => props.show, async (val) => {
  if (val) {
    await nextTick()
    openCamera()
  } else {
    closeCamera()
  }
})

onBeforeUnmount(() => {
  closeCamera()
})

async function openCamera() {
  cameraReady.value = false
  cameraError.value = ''
  engine.value = ''
  framesScanned.value = 0
  scanErrors.value = 0
  lastScanError.value = ''
  const gen = ++scanGeneration
  await nextTick()

  const video = cameraVideo.value
  if (!video) { closeCamera(); return }

  if (!navigator.mediaDevices?.getUserMedia) {
    cameraError.value = t('camera.unavailable')
    return
  }

  try {
    cameraStream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: 'environment' },
        width: { ideal: 1280 },
        height: { ideal: 720 },
      },
      audio: false,
    })

    if (!props.show) { stopStream(); return }

    video.srcObject = cameraStream
    await video.play()
    cameraReady.value = true

    // Espera a que haya un frame real antes de arrancar la deteccion
    await waitForFrame(video)
    if (gen !== scanGeneration) return

    // ZBar WASM es el motor principal: decodifica 1D y QR de forma fiable
    // en cualquier navegador (BarcodeDetector nativo en desktop es poco fiable para 1D).
    const ok = await initZbar()
    if (gen !== scanGeneration) return
    if (ok) {
      engine.value = 'zbar'
      startZbarLoop(video, gen)
      return
    }

    // Ultimo recurso: BarcodeDetector nativo (habitualmente solo QR en desktop)
    if ('BarcodeDetector' in window) {
      try {
        const bd = new BarcodeDetector()
        engine.value = 'native'
        detectTimer = setInterval(async () => {
          if (gen !== scanGeneration) return
          if (!cameraStream?.active || !video.videoWidth || scanBusy) return
          scanBusy = true
          try {
            const codes = await bd.detect(video)
            if (codes.length > 0 && accept(codes[0].rawValue)) emit('detected', codes[0].rawValue)
          } catch {} finally { scanBusy = false }
        }, 250)
        return
      } catch {}
    }

    cameraError.value = t('camera.decoder')
  } catch (err) {
    console.error('[Scan] Camera error:', err)
    stopStream()
    cameraReady.value = false
    if (err?.name === 'NotAllowedError' || err?.name === 'SecurityError') {
      cameraError.value = t('camera.denied')
    } else if (err?.name === 'NotFoundError' || err?.name === 'DevicesNotFoundError') {
      cameraError.value = t('camera.notFound')
    } else if (err?.name === 'NotReadableError') {
      cameraError.value = t('camera.inUse')
    } else {
      cameraError.value = t('camera.error') + (err?.message || err)
    }
  }
}

async function initZbar() {
  if (zbarModule) return true
  try {
    // El modulo inlined (WASM en base64) NO es bundle-safe: Emscripten depende de
    // import.meta.url / document.currentScript y Rollup lo rompe al trocearlo.
    // Por eso se sirve tal cual desde /public y se importa en runtime con una URL
    // calculada (que Rollup no puede reescribir). Ver frontend/public/zbar-inlined/.
    // OJO: la base NO puede ser import.meta.url del propio chunk, porque el chunk
    // vive en /assets/ y el archivo en la raiz. Se usa BASE_URL (raiz del sitio).
    const base = import.meta.env?.BASE_URL || '/'
    const url = new URL('zbar-inlined/index.mjs', new URL(base, location.href)).href
    const mod = await import(/* @vite-ignore */ url)
    // Calienta el WASM para que el primer frame no se pierda
    const probe = await mod.scanImageData(new ImageData(new Uint8ClampedArray(4), 1, 1))
    console.warn('[Scan] ZBar WASM listo (probe symbols:', probe.length, ')')
    zbarModule = mod
    zbarCanvas = document.createElement('canvas')
    zbarCtx = zbarCanvas.getContext('2d', { willReadFrequently: true })
    return true
  } catch (e) {
    console.warn('[Scan] ZBar init failed:', e)
    lastScanError.value = 'init:' + (e?.message || e)
    return false
  }
}

function startZbarLoop(video, gen) {
  detectTimer = setInterval(async () => {
    if (gen !== scanGeneration) return
    if (!cameraStream?.active || !video.videoWidth || scanBusy) return
    scanBusy = true
    try {
      // Analiza la region central (donde apunta el reticulo) a resolucion nativa
      const vw = video.videoWidth
      const vh = video.videoHeight
      const cw = Math.round(vw * 0.86)
      const ch = Math.round(vh * 0.6)
      const cx = Math.round((vw - cw) / 2)
      const cy = Math.round((vh - ch) / 2)
      zbarCanvas.width = cw
      zbarCanvas.height = ch
      zbarCtx.drawImage(video, cx, cy, cw, ch, 0, 0, cw, ch)
      const id = zbarCtx.getImageData(0, 0, cw, ch)
      const symbols = await zbarModule.scanImageData(id)
      framesScanned.value++
      if (gen !== scanGeneration) return
      if (symbols.length > 0) {
        const code = symbols[0].decode()
        console.warn('[Scan] ZBar decoded:', code)
        if (code && accept(code)) emit('detected', code)
      }
    } catch (e) {
      scanErrors.value++
      lastScanError.value = e?.message || String(e)
      if (e?.message !== 'index out of bounds') console.warn('[Scan] ZBar error:', e)
    } finally {
      scanBusy = false
    }
  }, 200)
}

function waitForFrame(video, timeoutMs = 5000) {
  return new Promise((resolve, reject) => {
    const start = Date.now()
    const check = () => {
      if (video.videoWidth > 0 && video.videoHeight > 0) { resolve(); return }
      if (Date.now() - start > timeoutMs) { reject(new Error('No frames')); return }
      setTimeout(check, 50)
    }
    check()
  })
}

function accept(code) {
  if (!code) return false
  if (props.shouldAccept && !props.shouldAccept(code)) return false
  return true
}

async function retryCamera() {
  cameraError.value = ''
  cameraReady.value = false
  stopStream()
  openCamera()
}

function stopStream() {
  if (cameraStream) {
    cameraStream.getTracks().forEach((track) => track.stop())
    cameraStream = null
  }
}

function closeCamera() {
  scanGeneration++
  scanBusy = false
  if (detectTimer) { clearInterval(detectTimer); detectTimer = null }
  stopStream()
  cameraReady.value = false
  cameraError.value = ''
}

function requestClose() {
  emit('close')
}

defineExpose({ closeCamera })
</script>
