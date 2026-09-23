<template>
  <Teleport to="body">
    <div v-if="open" class="cp-backdrop" @pointerdown="close">
      <div class="cp-panel" @pointerdown.stop>
        <div class="cp-search">
          <component :is="iconFor('Search')" :size="16" :stroke-width="2" class="cp-search-ic" />
          <input
            ref="searchInput"
            v-model="query"
            class="cp-input"
            :placeholder="t('palette.placeholder')"
            @keydown.down.prevent="move(1)"
            @keydown.up.prevent="move(-1)"
            @keydown.enter.prevent="exec(selectedIndex)"
            @keydown.esc.prevent="close"
          />
          <kbd class="cp-kbd">ESC</kbd>
        </div>

        <div v-if="sections.every(s => s.items.length === 0)" class="cp-empty">
          {{ t('palette.empty', { q: query.trim() || t('palette.placeholder') }) }}
        </div>

        <div v-else class="cp-body">
          <template v-for="sec in sections" :key="sec.id">
            <div v-if="sec.items.length" class="cp-sec">{{ sec.label }}</div>
            <button
              v-for="item in sec.items"
              :key="item.id"
              :id="'cp-item-' + item.gi"
              class="cp-item"
              :class="{ on: item.gi === selectedIndex }"
              @click="exec(item.gi)"
              @mouseenter="selectedIndex = item.gi"
            >
              <span class="cp-ic" :style="itemColor(item)">
                <component :is="iconFor(item.icon)" :size="16" :stroke-width="2" />
              </span>
              <span class="cp-label">{{ item.label }}</span>
              <kbd v-if="item.hint" class="cp-hint">{{ item.hint }}</kbd>
              <component :is="iconFor('ChevronRight')" :size="14" :stroke-width="2" class="cp-arrow" />
            </button>
          </template>
        </div>

        <div class="cp-foot">
          <span><kbd class="cp-kbd-s">↑</kbd><kbd class="cp-kbd-s">↓</kbd> {{ t('palette.hintNav') }}</span>
          <span><kbd class="cp-kbd-s">↵</kbd> {{ t('palette.hintOpen') }}</span>
          <span><kbd class="cp-kbd-s">Esc</kbd> {{ t('palette.hintClose') }}</span>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useIcons } from '../composables/useIcons'
import { useQuickActions } from '../composables/useQuickActions'
import { useAuthStore } from '../stores/auth'
import { getMode, setMode } from '../utils/theme'
import { getDensity, setDensity } from '../utils/density'
import { iconLib, toggleIconLib } from '../utils/iconLib'

const props = defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close'])

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()
const icons = useIcons()
const { actions } = useQuickActions()

const query = ref('')
const selectedIndex = ref(0)
const searchInput = ref(null)

function close() { emit('close') }

function iconFor(name) {
  if (!icons.value) return null
  return icons.value[name] || icons.value.Check || null
}

const NAMED_COLORS = { emerald: '#10b981', indigo: '#6366f1', slate: '#64748b', cyan: '#06b6d4' }
function itemColor(item) {
  const c = NAMED_COLORS[item.color] || item.color || '#64748b'
  return { '--ci': c, background: c + '1a', color: c, borderColor: c + '40' }
}

const NAV = [
  { id: 'nav-dashboard', path: '/', key: 'dashboard', view: 'DASHBOARD', icon: 'Gauge', color: '#e11d48' },
  { id: 'nav-flights', path: '/flights', key: 'flights', view: 'FLIGHTS', icon: 'PlaneDeparture', color: '#7c3aed' },
  { id: 'nav-load-planning', path: '/load-planning', key: 'loadPlanning', view: 'LOAD_PLANNING', icon: 'Route', color: '#475569' },
  { id: 'nav-ulds', path: '/ulds', key: 'ulds', view: 'ULDS', icon: 'Package', color: '#0891b2' },
  { id: 'nav-mawbs', path: '/mawbs', key: 'mawbs', view: 'MAWBS', icon: 'ClipboardList', color: '#16a34a' },
  { id: 'nav-bookings', path: '/bookings', key: 'bookings', view: 'BOOKINGS', icon: 'CalendarEvent', color: '#3e7bfa' },
  { id: 'nav-receipts', path: '/receipts', key: 'receipts', view: 'RECEIPTS', icon: 'FileInvoice', color: '#d97706' },
  { id: 'nav-users', path: '/users', key: 'users', view: 'USERS', icon: 'Users', color: '#334155' },
  { id: 'nav-settings', path: '/settings', key: 'settings', view: 'SETTINGS', icon: 'Settings', color: '#6d28d9' },
  { id: 'nav-security', path: '/security', key: 'security', view: 'SECURITY', icon: 'Key', color: '#dc2626' },
  { id: 'nav-exports', path: '/exports', key: 'exports', view: 'EXPORTS', icon: 'LayoutGrid', color: '#ea580c' },
  { id: 'nav-api', path: '/api-catalog', key: 'apiCatalog', view: 'API_CATALOG', icon: 'Api', color: '#0e7490' },
]

const ACTION_LABEL = {
  receive: 'newReceipt',
  flight: 'newFlight',
  uld: 'newUld',
  export: 'openExports',
}

const NEXT_LIB = { tabler: 'lucide', lucide: 'mdi', mdi: 'tabler' }
const LIB_KEY = { tabler: 'appearanceIconTabler', lucide: 'appearanceIconLucide', mdi: 'appearanceIconMdi' }

function systemItems() {
  const mode = getMode()
  return [
    {
      id: 'sys-mode',
      group: 'system',
      icon: mode === 'dark' ? 'Sun' : 'Moon',
      color: '#7aa2f7',
      label: t(mode === 'dark' ? 'header.themeLight' : 'header.themeDark'),
      run: () => { setMode(mode === 'dark' ? 'light' : 'dark'); close() },
    },
    {
      id: 'sys-density',
      group: 'system',
      icon: 'LayoutSidebarFilled',
      color: '#f59e0b',
      label: t(getDensity() === 'compact' ? 'header.appearanceDensityComfort' : 'header.appearanceDensityCompact'),
      run: () => { setDensity(getDensity() === 'compact' ? 'comfortable' : 'compact'); close() },
    },
    {
      id: 'sys-iconlib',
      group: 'system',
      icon: 'LayoutGrid',
      color: '#06b6d4',
      label: t('header.appearanceIconLib') + ': ' + t(LIB_KEY[NEXT_LIB[iconLib.value] || 'tabler']),
      run: () => { toggleIconLib(); close() },
    },
    {
      id: 'sys-logout',
      group: 'system',
      icon: 'Logout',
      color: '#dc2626',
      label: t('sidebar.logout'),
      run: () => { auth.logout(); router.push('/login'); close() },
    },
  ]
}

const allItems = computed(() => {
  const nav = NAV
    .filter(n => auth.canView && auth.canView(n.view))
    .map(n => ({
      ...n,
      group: 'nav',
      label: t('palette.items.' + n.key),
      hint: n.path,
      search: [t('palette.items.' + n.key), n.path],
      run: () => { router.push(n.path); close() },
    }))

  const acts = actions.value.map(a => {
    const labelKey = 'palette.items.' + (ACTION_LABEL[a.key] || a.key)
    const label = t(labelKey)
    return {
      ...a,
      id: 'act-' + a.key,
      group: 'create',
      label,
      hint: '',
      search: [label, a.key],
      run: () => { router.push(a.to); close() },
    }
  })

  return [...nav, ...acts, ...systemItems()]
})

const GROUP_LABEL = {
  nav: () => t('palette.goTo'),
  create: () => t('palette.create'),
  system: () => t('palette.system'),
}

function norm(s) {
  return (s || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
}

const sections = computed(() => {
  const q = norm(query.value.trim())
  let gi = 0
  const make = (items) => items
    .filter(it => !q || norm(Array.isArray(it.search) ? it.search.join(' ') : (it.search || it.label)).includes(q))
    .map(it => ({ ...it, gi: gi++ }))
  return [
    { id: 'nav', label: GROUP_LABEL.nav(), items: make(allItems.value.filter(i => i.group === 'nav')) },
    { id: 'create', label: GROUP_LABEL.create(), items: make(allItems.value.filter(i => i.group === 'create')) },
    { id: 'system', label: GROUP_LABEL.system(), items: make(allItems.value.filter(i => i.group === 'system')) },
  ]
})

const visibleItems = computed(() => sections.value.flatMap(s => s.items))

function move(delta) {
  const len = visibleItems.value.length
  if (!len) return
  selectedIndex.value = (selectedIndex.value + delta + len) % len
}

function exec(gi) {
  const item = visibleItems.value.find(i => i.gi === gi)
  if (item) item.run()
}

watch(() => props.open, (op) => {
  if (op) {
    query.value = ''
    selectedIndex.value = 0
    nextTick(() => {
      if (searchInput.value) searchInput.value.focus()
      scrollSelected()
    })
  }
})

function scrollSelected() {
  const el = document.getElementById('cp-item-' + selectedIndex.value)
  if (el) el.scrollIntoView({ block: 'nearest' })
}

watch(selectedIndex, scrollSelected)
</script>

<style scoped>
.cp-backdrop {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 12vh 16px 0;
  background: rgba(2, 6, 23, 0.55);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.cp-panel {
  width: min(100%, 560px);
  border-radius: 16px;
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 24px 70px -20px rgba(0, 0, 0, 0.7);
  overflow: hidden;
  animation: cp-pop 0.16s cubic-bezier(0.22, 1, 0.36, 1);
}

.cp-search {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.cp-search-ic {
  color: #64748b;
  flex-shrink: 0;
}

.cp-input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  outline: none;
  color: #e2e8f0;
  font-size: 15px;
  font-family: var(--font-family, sans-serif);
}

.cp-input::placeholder { color: #475569; }

.cp-kbd {
  padding: 3px 8px;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.05);
  color: #94a3b8;
  font-size: 10px;
}

.cp-body {
  max-height: 380px;
  overflow-y: auto;
  padding: 8px;
  overscroll-behavior: contain;
}

.cp-sec {
  padding: 8px 12px 4px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #64748b;
}

.cp-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 7px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.12s ease;
}

.cp-item.on {
  background: rgba(122, 162, 247, 0.14);
  border-color: rgba(122, 162, 247, 0.35);
}

.cp-ic {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid;
  flex-shrink: 0;
}

.cp-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #e2e8f0;
  font-size: 13.5px;
  font-weight: 500;
}

.cp-item.on .cp-label { color: #ffffff; }

.cp-hint {
  padding: 2px 6px;
  border-radius: 5px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
  color: #64748b;
  font-size: 10px;
  font-family: var(--font-family-mono, monospace);
}

.cp-arrow { color: #475569; flex-shrink: 0; }
.cp-item.on .cp-arrow { color: #7aa2f7; }

.cp-empty {
  padding: 28px 16px;
  text-align: center;
  color: #64748b;
  font-size: 13px;
}

.cp-foot {
  display: flex;
  gap: 16px;
  padding: 10px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(2, 6, 23, 0.4);
  color: #475569;
  font-size: 10.5px;
}

.cp-kbd-s {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  padding: 2px 5px;
  border-radius: 5px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.06);
  color: #94a3b8;
  font-size: 10px;
  font-family: var(--font-family-mono, monospace);
}

@keyframes cp-pop {
  from { opacity: 0; transform: translateY(-8px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
</style>