/**
 * Registro global de streams de cámara activos.
 *
 * Un `MediaStream` que no se cierra mantiene el dispositivo ocupado para el
 * resto de pestañas y aplicaciones: al reabrir la cámara el navegador responde
 * `NotReadableError` ("la cámara está siendo usada por otra aplicación").
 * Todos los componentes que abren cámara registran su stream aquí y al abrir
 * una nueva cámara se libera primero cualquier stream anterior.
 */

const activeStreams = new Set()

export function registerStream(stream) {
  if (stream) activeStreams.add(stream)
  return stream
}

export function releaseStream(stream) {
  if (!stream) return
  try {
    stream.getTracks().forEach((track) => track.stop())
  } catch {
    /* stream ya cerrado */
  }
  activeStreams.delete(stream)
}

export function releaseAllStreams() {
  for (const stream of Array.from(activeStreams)) releaseStream(stream)
}
