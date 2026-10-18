// ULD pallet-label domain helpers — presets, defaults, prefill and client-side
// persistence. Labels live in localStorage (keyed per ULD) and are printed from
// the browser; there is no backend contract for this data.

const LBS_TO_KG = 0.45359237

export function lbsToKg(lbs) {
  const n = Number(lbs)
  if (!Number.isFinite(n) || n <= 0) return 0
  return Math.round(n * LBS_TO_KG)
}

export function kgToLbs(kg) {
  const n = Number(kg)
  if (!Number.isFinite(n) || n <= 0) return 0
  return Math.round(n / LBS_TO_KG)
}

export function todayIso() {
  return new Date().toISOString().slice(0, 10)
}

export function buildCarrierLabel(airlineCode, flightNumber) {
  const code = String(airlineCode || '').trim().toUpperCase()
  const num = String(flightNumber || '').trim()
  if (!code && !num) return ''
  if (!num) return code
  if (!code) return num
  return `${code}${num}`
}

// Airline brand presets. `code` matches the airline IATA code so the store can
// auto-select the right one when a ULD belongs to a known carrier. `logoUrl`
// points to a self-authored brand mark served from /public/carriers (swap in
// licensed official artwork here if desired — the layout is asset-agnostic).
export const PRESET_CARRIERS = [
  { code: 'LH', name: 'Lufthansa Cargo', iata: '020', color: '#00205B', logoUrl: '/carriers/lh.svg' },
  { code: 'EK', name: 'Emirates SkyCargo', iata: '176', color: '#D71920', logoUrl: '/carriers/ek.svg' },
  { code: 'QR', name: 'Qatar Airways Cargo', iata: '157', color: '#5C0632', logoUrl: '/carriers/qr.svg' },
  { code: 'FX', name: 'FedEx Express Cargo', iata: '023', color: '#4D148C', logoUrl: '/carriers/fx.svg' },
  { code: 'CV', name: 'Cargolux Airlines', iata: '172', color: '#C8102E', logoUrl: '/carriers/cv.svg' },
  { code: 'UPS', name: 'UPS Air Cargo', iata: '406', color: '#351C15', logoUrl: '/carriers/ups.svg' },
  { code: 'IATA', name: 'IATA Standard Cargo', iata: '999', color: '#0F172A', logoUrl: '/carriers/iata.svg' },
]

export const LEGEND_COLOR_SCHEMES = ['green', 'amber', 'red', 'blue', 'cyan', 'purple', 'black']

// Physical label sizes (inches) used for print. The browser print dialog applies
// these as an exact `@page size`, which keeps output identical on Zebra /
// Intermec / laser printers without depending on a vendor driver or raw ZPL.
export const LABEL_SIZES = [
  { id: 'thermal-4x6', label: '4 × 6 in', w: 4, h: 6 },
  { id: 'thermal-6x4', label: '6 × 4 in', w: 6, h: 4 },
  { id: 'thermal-4x3', label: '4 × 3 in', w: 4, h: 3 },
  { id: 'thermal-3x2', label: '3 × 2 in', w: 3, h: 2 },
  { id: 'thermal-2x1', label: '2 × 1 in', w: 2, h: 1 },
  { id: 'a6', label: 'A6 · 105 × 148 mm', w: 4.13, h: 5.83 },
  { id: 'a5', label: 'A5 · 148 × 210 mm', w: 5.83, h: 8.27 },
  { id: 'a4', label: 'A4 · 210 × 297 mm', w: 8.27, h: 11.69 },
  { id: 'letter', label: 'Letter · 8.5 × 11 in', w: 8.5, h: 11 },
  { id: 'custom', label: 'Custom (in)', w: 4, h: 6 },
]

export const LABEL_ORIENTATIONS = ['portrait', 'landscape']

// Resolve the physical size (inches) of a label after applying its orientation.
// Landscape swaps width/height so the same preset can print rotated.
export function resolveLabelSize(label) {
  const preset = LABEL_SIZES.find(s => s.id === (label && label.labelSize)) || LABEL_SIZES[0]
  let w
  let h
  if (preset.id === 'custom') {
    w = Number(label && label.customLabelW) || preset.w
    h = Number(label && label.customLabelH) || preset.h
  } else {
    w = preset.w
    h = preset.h
  }
  const landscape = (label && label.orientation) === 'landscape'
  if (landscape) {
    const tmp = w
    w = h
    h = tmp
  }
  return { w, h, preset: preset.id }
}

export const CUSTOM_FIELD_COLORS = ['slate', 'blue', 'amber', 'red', 'emerald']

export const CUSTOM_FIELD_SECTIONS = ['header', 'details', 'weights', 'routing', 'signoff', 'footer']

// Default security legends (IATA / AVSEC compliant). Users can toggle them on
// and off, or add fully custom ones.
export const PRESET_LEGENDS = [
  {
    id: 'spx',
    type: 'SPX',
    title: 'SPX',
    subtitle: 'SECURE FOR PASSENGER & ALL-CARGO AIRCRAFT',
    complianceText: 'Screened by Regulated Agent / X-Ray & ETD inspected · IATA Security Compliant',
    colorScheme: 'green',
  },
  {
    id: 'sco',
    type: 'SCO',
    title: 'SCO',
    subtitle: 'SECURE FOR ALL-CARGO AIRCRAFT ONLY',
    complianceText: 'Screened for Freighter Operations Only · Forbidden on Passenger Flights',
    colorScheme: 'amber',
  },
  {
    id: 'cao',
    type: 'CAO',
    title: 'CAO',
    subtitle: 'CARGO AIRCRAFT ONLY',
    complianceText: 'DGR / Hazardous Materials · Strictly Prohibited on Passenger Aircraft',
    colorScheme: 'red',
  },
  {
    id: 'tcr',
    type: 'TCR',
    title: 'TCR - COLD CHAIN',
    subtitle: 'TIME & TEMPERATURE SENSITIVE CARGO (+2°C to +8°C)',
    complianceText: 'Active Envirotainer Refrigeration · Maintain Cold Chain Integrity at All Times',
    colorScheme: 'cyan',
  },
  {
    id: 'dgr',
    type: 'DGR',
    title: 'DGR REGULATED',
    subtitle: 'DANGEROUS GOODS AS PER IATA DGR MANUAL',
    complianceText: 'Classified & Packed according to UN Packaging Standards · Shipper Declaration Attached',
    colorScheme: 'purple',
  },
  {
    id: 'per',
    type: 'PER',
    title: 'PERISHABLE',
    subtitle: 'PERISHABLE COMMODITIES · EXPEDITE ON RAMP',
    complianceText: 'Direct Apron Transfer · Keep in Temperature-Controlled Holding Area',
    colorScheme: 'green',
  },
]

// Handling pictograms — emoji based (IATA handling marks).
export const PRESET_HANDLING_ICONS = [
  { id: 'fragile', icon: '🍷', label: 'Frágil' },
  { id: 'this_way_up', icon: '⬆️', label: 'Este lado arriba' },
  { id: 'keep_dry', icon: '☔', label: 'Mantener seco' },
  { id: 'keep_cool', icon: '❄️', label: 'Mantener frío' },
  { id: 'heavy', icon: '🏋️', label: 'Carga pesada' },
  { id: 'live_animals', icon: '🐾', label: 'Animales vivos' },
  { id: 'food_stuff', icon: '🍽️', label: 'Productos alimenticios' },
  { id: 'battery', icon: '🔋', label: 'Baterías de litio' },
  { id: 'magnetized', icon: '🧲', label: 'Material magnetizado' },
  { id: 'thermometer', icon: '🌡️', label: 'Control térmico' },
  { id: 'flame', icon: '🔥', label: 'Inflamable' },
  { id: 'snowflake', icon: '🧊', label: 'Congelado' },
]

// Ready-made label templates (apply cargo profile in one click).
export const LABEL_TEMPLATES = [
  {
    id: 'standard',
    carrierCode: 'IATA',
    headerTitle: 'IATA ULD IDENTIFICATION TAG',
    headerSubtitle: 'STANDARD PALLET / CONTAINER LABEL',
    cargoClass: 'General',
    handlingIcons: ['fragile', 'this_way_up', 'keep_dry'],
    secondaryBadge: 'AVSEC',
    activeLegends: ['spx'],
    fields: [
      { label: 'SHC Special Handling', value: 'GEN (General Cargo)', section: 'details', badgeColor: 'slate', highlight: false },
      { label: 'Trincaje / Lashing', value: 'Red de Amarre Aprobada', section: 'signoff', badgeColor: 'slate', highlight: false },
    ],
    remarks: 'SIN SOBRESALIENTES / NETTING TENSADA Y AJUSTADA / ESTIBA HOMOGÉNEA',
  },
  {
    id: 'val',
    carrierCode: 'QR',
    headerTitle: 'HIGH SECURITY VALUABLE CARGO ULD',
    headerSubtitle: 'ARMORED ESCORT / DOUBLE LOCK PROTOCOL',
    cargoClass: 'Valuables',
    handlingIcons: ['this_way_up', 'fragile'],
    secondaryBadge: 'AVSEC',
    activeLegends: ['spx'],
    fields: [
      { label: 'Protocolo de Seguridad', value: 'VUN / VAL - Custodia Blindada Continua', section: 'details', badgeColor: 'amber', highlight: true },
      { label: 'Precinto Secundario', value: 'SL-SEC-000000', section: 'signoff', badgeColor: 'red', highlight: true },
      { label: 'Custodio AVSEC Asignado', value: 'Cap. Roberto Mendez (Credencial AVSEC #112)', section: 'signoff', badgeColor: 'slate', highlight: false },
    ],
    remarks: 'CARGA VALIOSA - CUSTODIA CONTINUA / DOBLE SELLO VERIFICADO',
  },
]

let _fieldSeq = 0
function fieldId() {
  _fieldSeq += 1
  return `cf-${Date.now().toString(36)}-${_fieldSeq}`
}

function defaultVisibleBlocks() {
  return {
    barcode: true,
    header: true,
    uldId: true,
    weights: true,
    routing: true,
    signoffs: true,
    handling: true,
    custom: true,
    legends: true,
  }
}

// Normalise the local Uld view-model + flight context into label defaults.
export function createEmptyLabel(uld = {}, ctx = {}) {
  const tareLbs = Number(uld.tareLbs) || 0
  const grossLbs = Number(uld.grossWeightLbs || uld.grossWeight) || 0
  const netLbs = Number(uld.netWeightLbs) || Math.max(grossLbs - tareLbs, 0)
  const airlineCode = ctx.airlineCode || ''
  const matchedCarrier = PRESET_CARRIERS.find(c => c.code === airlineCode)

  return {
    id: '',
    uldId: uld.uldNumber || '',
    type: uld.uldType || '',
    contour: uld.config || '',
    flightNumber: buildCarrierLabel(airlineCode, ctx.flightNumber),
    origin: ctx.origin || '',
    destination: ctx.destination || uld.destination || '',
    flightDate: ctx.flightDate ? String(ctx.flightDate).slice(0, 10) : todayIso(),
    position: uld.position || '',
    // print layout (physical size in inches + orientation)
    labelSize: 'thermal-4x6',
    orientation: 'portrait',
    customLabelW: 4,
    customLabelH: 6,
    tareWeightKg: lbsToKg(tareLbs),
    netWeightKg: lbsToKg(netLbs),
    // exact source pounds (kept for drift-free LB display on the label)
    tareWeightLbs: Math.round(tareLbs),
    netWeightLbs: Math.round(netLbs),
    customGrossWeight: null,
    // displayed weight unit on the printed label (LB default per ops)
    weightUnit: 'LB',
    // sign-offs
    builtBy: uld.loadedBy || '',
    weighedBy: uld.weighedBy || '',
    sealNumber: uld.sealNumber || '',
    sealedBy: uld.confirmedWith || '',
    remarks: uld.notes || '',
    spxAccepted: false,
    // branding
    headerTitle: 'IATA ULD IDENTIFICATION TAG',
    headerSubtitle: 'STANDARD PALLET / CONTAINER LABEL',
    carrierCode: matchedCarrier ? matchedCarrier.code : 'IATA',
    customLogoUrl: '',
    logoPosition: 'left',
    logoHeightPx: 40,
    secondaryBadge: 'AVSEC',
    handlingIcons: [],
    // legends
    securityStatus: 'SPX',
    activeLegends: ['spx'],
    // custom + visibility
    customFields: [],
    visibleBlocks: defaultVisibleBlocks(),
  }
}

export function grossWeightKg(label) {
  if (label.customGrossWeight != null && Number.isFinite(Number(label.customGrossWeight))) {
    return Number(label.customGrossWeight)
  }
  return (Number(label.tareWeightKg) || 0) + (Number(label.netWeightKg) || 0)
}

export function makeCustomField({ label: lbl, value, section, badgeColor }) {
  return {
    id: fieldId(),
    label: String(lbl || '').trim(),
    value: String(value || '').trim(),
    section: CUSTOM_FIELD_SECTIONS.includes(section) ? section : 'details',
    badgeColor: CUSTOM_FIELD_COLORS.includes(badgeColor) ? badgeColor : 'slate',
    highlight: badgeColor === 'red' || badgeColor === 'amber',
  }
}

export function makeLegend({ title, subtitle, complianceText, colorScheme }) {
  return {
    id: `lg-${Date.now().toString(36)}-${Math.floor(Math.random() * 1e4)}`,
    type: 'CUSTOM',
    title: String(title || '').trim(),
    subtitle: String(subtitle || '').trim(),
    complianceText: String(complianceText || '').trim(),
    colorScheme: LEGEND_COLOR_SCHEMES.includes(colorScheme) ? colorScheme : 'black',
    active: true,
  }
}

export function applyTemplate(label, templateId) {
  const tpl = LABEL_TEMPLATES.find(t => t.id === templateId)
  if (!tpl) return
  label.headerTitle = tpl.headerTitle
  label.headerSubtitle = tpl.headerSubtitle
  label.carrierCode = tpl.carrierCode
  label.secondaryBadge = tpl.secondaryBadge
  label.handlingIcons = [...tpl.handlingIcons]
  label.activeLegends = [...tpl.activeLegends]
  label.remarks = tpl.remarks
  label.customFields = tpl.fields.map(f => makeCustomField(f))
}

export function generateSeal(origin) {
  const o = String(origin || 'ULD').trim().toUpperCase() || 'ULD'
  return `SL-${o}-${Math.floor(100000 + Math.random() * 900000)}`
}

export function buildBarcodeValue(label) {
  const uld = String(label.uldId || '').trim().toUpperCase()
  const flt = String(label.flightNumber || '').trim().toUpperCase()
  const seal = String(label.sealNumber || '').trim()
  return `ULD:${uld}/FLT:${flt}/SEAL:${seal}`
}

// ---------------------------------------------------------------------------
// Client-side persistence
// ---------------------------------------------------------------------------

const STORAGE_KEY = 'aircargo_uld_labels'

function readStore() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const data = raw ? JSON.parse(raw) : {}
    return data && typeof data === 'object' ? data : {}
  } catch {
    return {}
  }
}

function writeStore(store) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
    return true
  } catch {
    return false
  }
}

export function labelKeyFor(uld) {
  if (!uld) return ''
  return String(uld.backendId || uld.uid || uld.uldNumber || '').trim()
}

export function getStoredLabel(uld) {
  const key = labelKeyFor(uld)
  if (!key) return null
  return readStore()[key] || null
}

export function saveStoredLabel(uld, label) {
  const key = labelKeyFor(uld)
  if (!key) return false
  const store = readStore()
  store[key] = JSON.parse(JSON.stringify(label))
  return writeStore(store)
}

export function removeStoredLabel(uld) {
  const key = labelKeyFor(uld)
  if (!key) return false
  const store = readStore()
  if (store[key] === undefined) return false
  delete store[key]
  return writeStore(store)
}

export function hasStoredLabel(uld) {
  return getStoredLabel(uld) !== null
}
