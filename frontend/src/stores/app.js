import { defineStore } from 'pinia'
import { ref, shallowRef, computed } from 'vue'
import api from '../api/client'
import { flightsApi } from '../api/flights'
import { airlinesApi } from '../api/airlines'
import { bookingsApi } from '../api/bookings'
import { mawbsApi } from '../api/mawbs'
import { receiptsApi } from '../api/receipts'
import { uldsApi } from '../api/ulds'
import { uldAwbsApi } from '../api/uldAwbs'

import { useToastStore } from '../stores/toast'
import { extractError } from '../utils/error'

// Inferir UldType desde el código del ULD
export function inferUldType(uldNumber) {
  const code = (uldNumber || '').toUpperCase().substring(0, 3)
  const map = { PMC: 'PMC', PAH: 'PAH', PAG: 'PAG', PAJ: 'PAJ', AAY: 'AAY',
                AAZ: 'AAZ', AAD: 'AAD', PIP: 'PIP', AMP: 'AMP', AMJ: 'AMJ', PMH: 'PMH' }
  return map[code] || 'PMC'
}

export const useAppStore = defineStore('app', () => {

  // ── Estado global ──────────────────────────────────────────────
  const flights    = shallowRef([])
  const airlines   = shallowRef([])
  const bookings   = shallowRef([])
  const mawbs      = shallowRef([])
  const receipts   = shallowRef([])
  const ulds       = shallowRef([])
  const uldAwbs    = shallowRef([])


  const selectedFlightId = ref(null)
  // `loading` era un booleano que cada loader ponía a `true` y apagaba en su
  // propio `finally`. Con `selectFlight()` lanzando 3 loaders en paralelo, el
  // primero en terminar apagaba el spinner mientras los otros dos seguían en
  // vuelo (y la vista se pintaba con datos a medias). Se cuenta el número de
  // cargas en curso y el spinner cae sólo cuando todas terminan.
  const pendingLoads     = ref(0)
  const loading          = computed(() => pendingLoads.value > 0)
  // Error de carga del LISTADO DE VUELOS únicamente: es el único `error` que
  // existe en el store y sólo FlightsView lo pinta. Antes los otros 5 loaders
  // lo sobrescribían sin límite y nunca se limpiaba, así que un fallo al
  // cargar bookings/MAWB aparecía como banner dentro de Flights y persistía
  // para siempre incluso tras recargar vuelos con éxito.
  const error            = ref(null)

  const toast = useToastStore()

  // Contabilidad de cargas en curso (ver `loading`).
  function beginLoad(silent) { if (!silent) pendingLoads.value++ }
  function endLoad(silent) {
    if (!silent) pendingLoads.value = Math.max(0, pendingLoads.value - 1)
  }

  // ── Computed ───────────────────────────────────────────────────
  const selectedFlight = computed(() =>
    flights.value.find(f => f.id === selectedFlightId.value) || null
  )

  const bookingsByFlight = computed(() =>
    selectedFlightId.value
      ? bookings.value.filter(b => b.flightId === selectedFlightId.value)
      : bookings.value
  )

  const mawbsByFlight = computed(() =>
    selectedFlightId.value
      ? mawbs.value.filter(m => m.flightId === selectedFlightId.value)
      : mawbs.value
  )

  const uldsByFlight = computed(() =>
    selectedFlightId.value
      ? ulds.value.filter(u => u.flightId === selectedFlightId.value)
      : ulds.value
  )

  // ── Actions ────────────────────────────────────────────────────

  async function loadFlights({ page = 0, size = 50, silent = false } = {}) {
    try {
      beginLoad(silent)
      const res = await flightsApi.getAll({ page, size })
      flights.value = res.data.content || res.data
      if (flights.value.length && !selectedFlightId.value) {
        selectedFlightId.value = flights.value[0].id
      }
      // `error` solo lo escribe esta función y solo lo lee FlightsView (banner),
      // así que cualquier vuelo cargado con exito demuestra que el banner viejo
      // ya no aplica: se limpia tambien en las cargas silenciosas. `silent`
      // gobierna el toast y el spinner, no la veracidad del estado de error
      // (si no, un banner rojo sobrevivia a la recuperación hasta que el usuario
      // recargara a mano).
      error.value = null
    } catch (e) {
      if (!silent) { toast.error(extractError(e)); error.value = extractError(e) }
    } finally { endLoad(silent) }
  }

  async function loadAirlines() {
    try {
      const res = await airlinesApi.getAll()
      airlines.value = res.data
    } catch { /* silent — non-critical */ }
  }

  async function createFlight(dto) {
    const res = await flightsApi.create(dto)
    flights.value.unshift(res.data)
    return res.data
  }

  async function updateFlight(id, dto) {
    const res = await flightsApi.update(id, dto)
    const idx = flights.value.findIndex(f => f.id === id)
    if (idx >= 0) flights.value[idx] = res.data
    return res.data
  }

  async function deleteFlight(id) {
    await flightsApi.delete(id)
    flights.value = flights.value.filter(f => f.id !== id)
  }

  async function loadBookings(flightId, { page = 0, size = 50, silent = false } = {}) {
    try {
      beginLoad(silent)
      const params = { page, size }
      if (flightId) params.flightId = flightId
      const res = await bookingsApi.getAll(params)
      bookings.value = res.data.content || res.data
    } catch (e) { if (!silent) toast.error(extractError(e)) } finally { endLoad(silent) }
  }

  async function createBooking(dto) {
    const res = await bookingsApi.create(dto)
    bookings.value.unshift(res.data)
    return res.data
  }

  async function updateBooking(id, dto) {
    const res = await bookingsApi.update(id, dto)
    const idx = bookings.value.findIndex(b => b.id === id)
    if (idx >= 0) bookings.value[idx] = res.data
    return res.data
  }

  async function deleteBooking(id) {
    await bookingsApi.delete(id)
    bookings.value = bookings.value.filter(b => b.id !== id)
  }

  async function loadMawbs(flightId, { page = 0, size = 500, silent = false } = {}) {
    if (!flightId) return
    try {
      beginLoad(silent)
      const res = await mawbsApi.getByFlight(flightId, { page, size })
      mawbs.value = res.data.content || res.data
    } catch (e) { if (!silent) toast.error(extractError(e)) } finally { endLoad(silent) }
  }

  async function loadAllMawbs({ page = 0, size = 500, silent = false } = {}) {
    try {
      beginLoad(silent)
      const res = await mawbsApi.getAll({ page, size })
      mawbs.value = res.data.content || res.data
    } catch (e) { if (!silent) toast.error(extractError(e)) } finally { endLoad(silent) }
  }

  async function createMawb(dto) {
    const res = await mawbsApi.create(dto)
    const data = res.data
    const mawb = data.mawb || data
    mawbs.value.push(mawb)
    return data
  }

  async function loadReceipts({ page = 0, size = 500, silent = false } = {}) {
    try {
      beginLoad(silent)
      const res = await receiptsApi.getAll({ page, size })
      receipts.value = (res.data.content || res.data) || []
    } catch (e) { if (!silent) toast.error(extractError(e)) } finally { endLoad(silent) }
  }

  async function emitReceipt(payload) {
    const res = await api.post('/warehouse/receipts/emit', payload)
    return res.data
  }

  async function saveReceiptDraft(payload) {
    const res = await api.post('/warehouse/receipts/draft', payload)
    return res.data
  }

  async function loadUlds(flightId, { page = 0, size = 50, silent = false } = {}) {
    try {
      beginLoad(silent)
      const params = { page, size }
      if (flightId) params.flightId = flightId
      const res = await uldsApi.getAll(params)
      ulds.value = res.data.content || res.data
    } catch (e) { if (!silent) toast.error(extractError(e)) } finally { endLoad(silent) }
  }

  async function loadUldAwbs() {
    try {
      const res = await uldAwbsApi.getAll()
      uldAwbs.value = res.data
    } catch { /* non-critical */ }
  }

  async function selectFlight(flightId) {
    selectedFlightId.value = flightId
    await Promise.all([
      loadBookings(flightId),
      loadMawbs(flightId),
      loadUlds(flightId),
    ])
  }

  return {
    // state
    flights, airlines, bookings, mawbs, receipts, ulds, uldAwbs,
    selectedFlightId, selectedFlight, loading, error,
    // computed
    bookingsByFlight, mawbsByFlight, uldsByFlight,
    // actions
    loadFlights, loadAirlines, createFlight, updateFlight, deleteFlight,
    loadBookings, createBooking, updateBooking, deleteBooking,
    loadMawbs, loadAllMawbs, createMawb,
    loadReceipts, emitReceipt, saveReceiptDraft,
    loadUlds, loadUldAwbs,
    selectFlight,
  }
})
