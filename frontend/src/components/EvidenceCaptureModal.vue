<template>
  <Teleport to="body">
    <div
      v-if="show"
      class="fixed inset-0 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
      :style="{ zIndex }"
      @click.self="close"
    >
      <div
        class="ds-modal-panel w-full overflow-hidden flex flex-col"
        style="max-width: 560px; max-height: calc(100dvh - 32px);"
        role="dialog"
        aria-modal="true"
        :aria-label="t('warehouse.evidence.cameraTitle')"
      >
        <!-- header -->
        <div class="flex items-center justify-between px-4 py-2.5 border-b border-strong shrink-0">
          <span class="flex items-center gap-2 text-[13px] font-mono font-bold uppercase tracking-widest text-primary">
            <component :is="icons.Camera" :size="15" />
            {{ t('warehouse.evidence.cameraTitle') }}
          </span>
          <button
            type="button"
            @click="close"
            :aria-label="t('common.close')"
            class="text-tertiary hover:text-primary transition text-base leading-none px-1"
          >✕</button>
        </div>

        <!-- body -->
        <div class="overflow-y-auto px-4 py-3 space-y-3" style="min-height: 0;">
          <!-- Zona A: cámara en vivo (solo HTTPS / localhost) -->
          <div v-if="liveSupported" class="space-y-2">
            <div class="relative bg-black rounded overflow-hidden flex items-center justify-center" style="min-height: 220px;">
              <video
                ref="videoRef"
                autoplay
                playsinline
                muted
                class="w-full object-contain"
                style="max-height: 46dvh;"
              />

              <!-- overlay: iniciando -->
              <div
                v-if="starting"
                class="absolute inset-0 flex items-center justify-center text-[12px] font-mono text-slate-400 uppercase tracking-widest"
              >
                {{ t('warehouse.evidence.cameraStarting') }}
              </div>

              <!-- overlay: error -->
              <div
                v-else-if="liveError"
                class="absolute inset-0 flex flex-col items-center justify-center gap-2 px-4 text-center"
              >
                <component :is="icons.AlertCircle" :size="22" class="text-amber-400" />
                <span class="text-[12px] font-mono text-slate-200 leading-snug">{{ liveError }}</span>
                <button
                  type="button"
                  @click="retryLive"
                  class="mt-1 flex items-center gap-1.5 text-[11px] px-3 py-1.5 rounded font-mono uppercase tracking-wider font-bold bg-white/90 text-slate-800 hover:bg-white transition active:scale-95"
                >
                  <component :is="icons.Refresh" :size="12" /> {{ t('warehouse.evidence.cameraRetry') }}
                </button>
              </div>

              <!-- shutter -->
              <button
                v-if="!liveError && streamReady"
                type="button"
                @click="shutter"
                :disabled="busy"
                class="absolute bottom-3 left-1/2 -translate-x-1/2 w-14 h-14 rounded-full bg-white/25 border-4 border-white flex items-center justify-center transition active:scale-90 disabled:opacity-50"
                :aria-label="t('warehouse.evidence.cameraCapture')"
              >
                <span class="w-9 h-9 rounded-full bg-white" />
              </button>
            </div>
          </div>

          <!-- Zona B: vías siempre disponibles (funcionan en HTTP, móvil y escritorio) -->
          <div class="space-y-2">
            <p v-if="!liveSupported" class="text-[12px] font-mono text-secondary leading-snug">
              {{ t('warehouse.evidence.cameraHintInsecure') }}
            </p>

            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <!-- Tomar foto -->
              <button
                type="button"
                @click="pickFile('camera')"
                class="flex items-center justify-center gap-2 px-3 py-3 rounded border border-strong bg-surface hover:bg-surface-hover transition active:scale-[.98] disabled:opacity-50"
                :disabled="busy"
              >
                <component :is="icons.Camera" :size="16" class="text-primary" />
                <span class="text-[12px] font-mono font-bold uppercase tracking-wider text-primary">
                  {{ t('warehouse.evidence.takePhoto') }}
                </span>
              </button>

              <!-- Elegir archivo -->
              <button
                type="button"
                @click="pickFile('file')"
                class="flex items-center justify-center gap-2 px-3 py-3 rounded border border-strong bg-surface hover:bg-surface-hover transition active:scale-[.98] disabled:opacity-50"
                :disabled="busy"
              >
                <component :is="icons.FileUpload" :size="16" class="text-primary" />
                <span class="text-[12px] font-mono font-bold uppercase tracking-wider text-primary">
                  {{ t('warehouse.evidence.uploadPhoto') }}
                </span>
              </button>
            </div>

            <p class="text-[11px] font-mono text-tertiary leading-snug">
              {{ t('warehouse.evidence.cameraHintFiles') }}
            </p>
          </div>
        </div>

        <!-- footer -->
        <div class="flex items-center justify-between gap-2 px-4 py-3 border-t border-strong bg-surface-hover shrink-0">
          <span class="text-[11px] font-mono text-secondary truncate">
            <template v-if="busy">{{ t('warehouse.evidence.processing') }}</template>
            <template v-else-if="liveSupported">{{ t('warehouse.evidence.liveAvailable') }}</template>
          </span>
          <button
            type="button"
            @click="close"
            class="shrink-0 text-[11px] px-3 py-1.5 rounded border border-strong font-mono uppercase tracking-wider font-bold text-secondary hover:bg-surface transition"
          >
            {{ t('common.cancel') }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, nextTick, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useIcons } from '../composables/useIcons'
import { setCapturing } from '../composables/captureState'

const icons = useIcons()
const { t } = useI18n()

const props = defineProps({
  show: { type: Boolean, default: false },
  /** Debe superar el z-index del modal que hospeda el trigger (ops-overlay = 60). */
  zIndex: { type: [Number, String], default: 100 },
  maxDim: { type: Number, default: 1280 },
  quality: { type: Number, default: 0.72 },
  maxBytes: { type: Number, default: 700 * 1024 },
})

const emit = defineEmits(['close', 'captured'])

const videoRef = ref(null)
const streamReady = ref(false)
const starting = ref(false)
const liveError = ref('')
const busy = ref(false)

let mediaStream = null
/** Mount token: invalida cualquier getUserMedia en vuelo cuando el modal se cierra. */
let runId = 0

/* getUserMedia solo existe en contexto seguro (HTTPS / localhost). En HTTP la
 * cámara en vivo no es posible, pero la vía "Tomar foto" (input[capture]) sí. */
const liveSupported = computed(() => {
  if (typeof window === 'undefined') return false
  const secure = window.isSecureContext === true
    || location.hostname === 'localhost'
    || location.hostname === '127.0.0.1'
  return secure && !!navigator.mediaDevices?.getUserMedia
})

function reset() {
  runId++
  stopStream()
  streamReady.value = false
  starting.value = false
  liveError.value = ''
  busy.value = false
  setCapturing(false)
}

/* ── Cámara en vivo ───────────────────────────────────────────────────── */

async function startLive() {
  setCapturing(true)
  const id = ++runId
  stopStream()
  streamReady.value = false
  starting.value = true
  liveError.value = ''

  const constraintsList = [
    { video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } }, audio: false },
    { video: { width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false },
  ]

  let stream = null
  let lastErr = null
  for (const constraints of constraintsList) {
    try {
      stream = await navigator.mediaDevices.getUserMedia(constraints)
      break
    } catch (err) {
      lastErr = err
      if (err?.name === 'OverconstrainedError') continue
      break
    }
  }

  // El modal pudo cerrarse (o reintentarse) mientras se resolvía el stream.
  if (id !== runId || !props.show) {
    stream?.getTracks().forEach((t) => t.stop())
    return
  }

  if (!stream) {
    starting.value = false
    liveError.value = mapCameraError(lastErr)
    return
  }

  mediaStream = stream
  const video = videoRef.value
  if (!video) {
    stopStream()
    starting.value = false
    liveError.value = t('warehouse.evidence.cameraCaptureFailed')
    return
  }

  video.srcObject = stream
  try {
    await video.play()
    // Un frame real debe existir antes de permitir la captura (evita canvas 0x0).
    if (id !== runId || !props.show) { stopStream(); return }
    streamReady.value = true
    starting.value = false
  } catch {
    liveError.value = t('warehouse.evidence.cameraAutoplayBlocked')
    starting.value = false
  }
}

function mapCameraError(err) {
  const name = err?.name || ''
  if (name === 'NotAllowedError' || name === 'SecurityError')
    return t('warehouse.evidence.cameraDenied')
  if (name === 'NotFoundError' || name === 'OverconstrainedError' || name === 'DevicesNotFoundError')
    return t('warehouse.evidence.cameraNotFound')
  if (name === 'NotReadableError' || name === 'TrackStartError')
    return t('warehouse.evidence.cameraBusy')
  if (!liveSupported.value)
    return t('warehouse.evidence.cameraUnavailable')
  return err?.message
    ? t('warehouse.evidence.cameraError', { error: err.message })
    : t('warehouse.evidence.cameraUnavailable')
}

function stopStream() {
  if (mediaStream) {
    mediaStream.getTracks().forEach((track) => track.stop())
    mediaStream = null
  }
  const video = videoRef.value
  if (video) video.srcObject = null
}

function retryLive() {
  startLive()
}

async function shutter() {
  const video = videoRef.value
  if (!video) return
  if (video.readyState < 2 || !video.videoWidth || !video.videoHeight) {
    liveError.value = t('warehouse.evidence.cameraNotReady')
    return
  }
  busy.value = true
  try {
    const url = await grabCanvas(video)
    if (url) finish(url, 'Foto')
  } catch (err) {
    liveError.value = t('warehouse.evidence.cameraCaptureError', { error: err?.message || String(err) })
  } finally {
    busy.value = false
  }
}

/* ── Vías de archivo (siempre disponibles) ──────────────────────────── */

function pickFile(mode) {
  const input = document.createElement('input')
  input.type = 'file'
  input.style.cssText = 'position:fixed;left:-9999px;top:-9999px;width:0;height:0;opacity:0;pointer-events:none'
  if (mode === 'camera') {
    input.accept = 'image/*'
    // Atributo clave: abre la cámara nativa del móvil sin exigir HTTPS.
    input.setAttribute('capture', 'environment')
  } else {
    input.accept = 'image/*,.pdf'
  }
  input.addEventListener('change', () => {
    const files = input.files
    input.remove()
    onFilesPicked(mode, { target: { files } })
  })
  // Si el usuario cancela, en algunos navegadores no llega 'change': liberamos
  // el input huérfano en el siguiente tick.
  setTimeout(() => input.isConnected && input.remove(), 60_000)
  document.body.appendChild(input)
  input.click()
}

async function onFilesPicked(mode, event) {
  const files = event?.target?.files
  const list = files && files.length ? Array.from(files) : []
  if (list.length === 0) return

  busy.value = true
  try {
    for (const file of list) {
      const isImage = file.type.startsWith('image/')
      const raw = await readFile(file)
      const url = isImage ? await downscale(raw, props.maxDim, props.quality) : raw
      if (!url || url === 'data:,') continue
      if (url.length > props.maxBytes * 1.4) {
        // Reintento más agresivo antes de rendirse.
        const retry = await downscale(raw, 960, 0.55)
        if (retry && retry.length <= props.maxBytes * 1.4) {
          finish(retry, baseName(file.name), mode)
          continue
        }
        liveError.value = t('warehouse.evidence.fileTooLarge')
        continue
      }
      finish(url, baseName(file.name), mode)
    }
  } finally {
    busy.value = false
  }
}

function baseName(name) {
  return name || 'evidencia'
}

function readFile(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('read-failed'))
    reader.readAsDataURL(file)
  })
}

/* ── Escalado / compresión ───────────────────────────────────────────── */

function drawScaled(source, w, h, maxDim, quality) {
  const scale = Math.min(maxDim / w, maxDim / h, 1)
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(w * scale))
  canvas.height = Math.max(1, Math.round(h * scale))
  const ctx = canvas.getContext('2d')
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(source, 0, 0, canvas.width, canvas.height)
  return canvas.toDataURL('image/jpeg', quality)
}

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error('decode-failed'))
    img.src = src
  })
}

function downscale(dataUrl, maxDim, quality) {
  return loadImage(dataUrl)
    .then((img) => drawScaled(img, img.width, img.height, maxDim, quality))
    .catch(() => dataUrl)
}

async function grabCanvas(video) {
  const url = drawScaled(video, video.videoWidth, video.videoHeight, props.maxDim, props.quality)
  if (!url || url === 'data:,') throw new Error('empty-canvas')
  return url
}

/* ── Emisión ─────────────────────────────────────────────────────────── */

function finish(url, name, source = 'camera') {
  const isImage = typeof url === 'string' && url.startsWith('data:image')
  emit('captured', {
    url,
    type: isImage ? 'image' : 'document',
    name: buildName(name, isImage),
    source,
  })
  // Varias fotos seguidas desde "Tomar foto": se cierra el modal para no dejar
  // al usuario atrapado en el overlay.
  close()
}

function buildName(rawName, isImage) {
  const stamp = new Date().toISOString().slice(0, 19).replace(/[T:]/g, '-')
  if (!isImage) return rawName
  if (rawName && rawName !== 'Foto' && !/^Foto_/.test(rawName)) return rawName
  return `Foto_${stamp}.jpg`
}

/* ── Ciclo de vida ───────────────────────────────────────────────────── */

function close() {
  reset()
  emit('close')
}

watch(() => props.show, async (val) => {
  if (!val) {
    reset()
    return
  }
  await nextTick()
  if (!props.show) return
  setCapturing(true)
  if (liveSupported.value) startLive()
})

onUnmounted(() => {
  runId++
  stopStream()
  setCapturing(false)
})
</script>
