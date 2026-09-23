<template>
  <header class="shell-header">
    <button v-if="!isMobile" @click="emit('toggleSidebar')"
      class="sidebar-toggle" :class="{ 'rotated': collapsed }"
      :title="collapsed ? t('header.expand') : t('header.collapse')">
      <component :is="icons.ChevronRight" :size="16" :stroke-width="2" />
    </button>
    <button v-else @click="emit('toggleSidebar')" class="sidebar-toggle" :title="t('header.menu')">
      <component :is="icons.Menu" :size="16" :stroke-width="2" />
    </button>
    <div class="brand">
      <i class="pi pi-box"></i>
      <span>AirCargo<small>{{ auth.selectedSite?.code || 'SDQ' }} · Ums</small></span>
    </div>
    <div class="spacer"></div>
    <div class="header-actions">
      <span class="h-chip hide-sm"><i class="pi pi-globe"></i> {{ t('common.lang') }} · {{ auth.selectedSite?.code || 'SDQ' }}</span>
      <span class="h-chip hide-sm"><i class="pi pi-user"></i> {{ auth.fullName || auth.email }} — {{ roleLabel }}</span>
      <button @click="toggleIconLib" class="ctrl-chip" :title="t('header.appearanceIconLib')">{{ iconCode }}</button>
      <button @click="cycleFont" class="ctrl-chip" :title="t('header.fontHint')">{{ fontLabel }}</button>
      <button @click="cycleDensity" class="ctrl-chip" :title="t('header.densityHint')">{{ densityLabel }}</button>
      <button @click="toggleMode" class="h-chip" :title="mode === 'light' ? t('header.themeLight') : t('header.themeDark')">
        <component :is="icons.Moon" v-if="mode === 'light'" :size="14" :stroke-width="2" />
        <component :is="icons.Sun" v-else :size="14" :stroke-width="2" style="color: #ffd700" />
      </button>
      <button ref="appearanceBtnRef" @click="toggleAppearance" class="h-chip h-chip-swatch" :title="t('header.appearanceHint')">
        <span class="appearance-swatch"></span>
      </button>
      <button ref="notifBtnRef" @click="toggleNotif" class="h-chip h-chip-bell" :title="t('notifications.title')">
        <component :is="icons.Bell" :size="14" :stroke-width="2" />
        <span v-if="notif.unread > 0" class="notif-badge">{{ notif.unread > 99 ? '99+' : notif.unread }}</span>
      </button>
    </div>
  </header>
  <Teleport to="body">
    <div v-if="notificationsOpen" @click.stop class="fixed z-[100]"
      :style="{ top: notifPopStyle.top, right: notifPopStyle.right }">
      <div class="rounded-xl w-[340px] max-w-[92vw] overflow-hidden"
        style="background: #0f172a; border: 1px solid rgba(255,255,255,0.12); box-shadow: 0 24px 56px -16px rgba(0,0,0,0.6);">
        <div class="flex items-center justify-between px-3 py-2" style="border-bottom: 1px solid rgba(255,255,255,0.1)">
          <div class="flex items-center gap-2">
            <span class="text-[11px] font-bold uppercase tracking-wide" style="color: rgba(255,255,255,0.7)">{{ t('notifications.title') }}</span>
            <span class="w-1.5 h-1.5 rounded-full" :style="{ background: notif.connected ? '#34d399' : '#64748b' }"
              :title="notif.connected ? t('notifications.live') : t('notifications.offline')"></span>
          </div>
          <button v-if="notif.unread > 0" @click="notif.markAllRead()"
            class="flex items-center gap-1 text-[10px] font-bold underline text-slate-300 hover:text-white">
            <component :is="icons.CheckCheck" :size="12" :stroke-width="2" />
            {{ t('notifications.markAll') }}
          </button>
        </div>
        <div class="max-h-[360px] overflow-y-auto">
          <button v-for="n in notif.items" :key="n.id" @click="openNotification(n)"
            class="w-full text-left px-3 py-2 flex items-start gap-2 transition hover:bg-white/5"
            :style="!n.isRead ? 'background: rgba(122,162,247,0.10)' : ''">
            <span class="mt-1.5 w-1.5 h-1.5 rounded-full flex-shrink-0" :style="{ background: n.isRead ? 'transparent' : '#7aa2f7' }"></span>
            <span class="min-w-0 flex-1">
              <span class="block text-[12px] font-bold text-white truncate">{{ n.title }}</span>
              <span class="block text-[11px] text-slate-400 truncate">{{ n.body }}</span>
              <span class="block text-[9px] text-slate-500 mt-0.5">{{ timeAgo(n.createdAt) }}</span>
            </span>
            <span @click.stop="notif.remove(n.id)" title="×"
              class="text-slate-500 hover:text-white text-[13px] leading-none px-1">×</span>
          </button>
          <div v-if="!notif.items.length" class="px-3 py-8 text-center text-[11px] italic text-slate-500">
            {{ t('notifications.empty') }}
          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <Teleport to="body">
    <div v-if="appearanceOpen" @click.stop class="fixed z-[100]"
      :style="{ top: appearancePopStyle.top, right: appearancePopStyle.right }">
      <div class="rounded-xl w-[300px] max-w-[92vw] overflow-hidden"
        style="background: #0f172a; border: 1px solid rgba(255,255,255,0.12); box-shadow: 0 24px 56px -16px rgba(0,0,0,0.6);">
        <div class="flex items-center justify-between px-3 py-2" style="border-bottom: 1px solid rgba(255,255,255,0.1)">
          <span class="text-[12px] font-bold tracking-wide" style="color: rgba(255,255,255,0.85)">{{ t('header.appearanceTitle') }}</span>
          <button @click="resetAppearance" class="text-[10px] font-bold uppercase tracking-wider underline text-slate-300 hover:text-white">
            {{ t('header.appearanceReset') }}
          </button>
        </div>
        <div class="max-h-[70vh] overflow-y-auto px-3 py-3 flex flex-col gap-3"
          style="scrollbar-width: thin; scrollbar-color: rgba(255,255,255,0.25) transparent;">
          <div class="grid grid-cols-5 gap-1.5">
            <button v-for="(th, key) in THEMES" :key="key" type="button"
              class="h-9 rounded-lg transition-all"
              :class="themeKey === key ? 'ring-2 ring-white scale-105' : 'opacity-80 hover:opacity-100 hover:ring-1 hover:ring-white/50'"
              :style="{ background: accentFor(key, tone).acc }"
              :title="th.name + ' · ' + th.mode"
              @click="pickTheme(key)">
              <span v-if="themeKey === key" class="flex items-center justify-center h-full text-white drop-shadow" style="font-size: 12px">✓</span>
            </button>
          </div>
          <div class="flex items-center gap-1.5">
            <span class="text-[10px] font-bold uppercase tracking-widest mr-1" style="color: rgba(255,255,255,0.45)">{{ t('header.appearanceTone') }}</span>
            <button v-for="(tn, i) in TONE_NAMES" :key="`t${i}`" type="button"
              class="h-5 w-5 rounded-full transition-all"
              :class="tone === i ? 'ring-2 ring-white scale-110' : 'opacity-75 hover:opacity-100'"
              :style="{ background: accentFor(themeKey, i).acc }"
              :title="tn"
              @click="pickTone(i)">
              <span v-if="tone === i" class="flex items-center justify-center h-full text-white drop-shadow" style="font-size: 10px">✓</span>
            </button>
          </div>
          <div class="flex items-center gap-1 flex-wrap">
            <span class="text-[10px] font-bold uppercase tracking-widest mr-1" style="color: rgba(255,255,255,0.45)">{{ t('header.appearanceFont') }}</span>
            <button v-for="f in FONT_ORDER" :key="f" type="button"
              class="px-1.5 py-1 rounded-md text-[10px] font-extrabold tracking-wide border transition"
              :class="font === f
                ? 'border-white text-white bg-white/15'
                : 'border-white/20 text-slate-300 hover:border-white/50 hover:text-white'"
              :title="f"
              @click="pickFont(f)">{{ FONT_LABEL[f] }}</button>
          </div>
          <div class="flex items-center gap-1.5">
            <span class="text-[10px] font-bold uppercase tracking-widest mr-1" style="color: rgba(255,255,255,0.45)">{{ t('header.appearanceDensity') }}</span>
            <button type="button" @click="pickDensity('comfortable')"
              class="px-2 py-1 rounded-md text-[10px] font-bold border transition capitalize"
              :class="density === 'comfortable' ? 'border-white text-white bg-white/15' : 'border-white/20 text-slate-300 hover:border-white/50'">
              {{ t('header.appearanceDensityComfort') }}
            </button>
            <button type="button" @click="pickDensity('compact')"
              class="px-2 py-1 rounded-md text-[10px] font-bold border transition capitalize"
              :class="density === 'compact' ? 'border-white text-white bg-white/15' : 'border-white/20 text-slate-300 hover:border-white/50'">
              {{ t('header.appearanceDensityCompact') }}
            </button>
          </div>
          <div class="flex items-center gap-1.5">
            <span class="text-[10px] font-bold uppercase tracking-widest mr-1" style="color: rgba(255,255,255,0.45)">{{ t('header.appearanceIconLib') }}</span>
            <button type="button" @click="pickIconLib('tabler')"
              class="px-2 py-1 rounded-md text-[10px] font-bold border transition"
              :class="iconLib === 'tabler' ? 'border-white text-white bg-white/15' : 'border-white/20 text-slate-300 hover:border-white/50'">
              {{ t('header.appearanceIconTabler') }}
            </button>
            <button type="button" @click="pickIconLib('lucide')"
              class="px-2 py-1 rounded-md text-[10px] font-bold border transition"
              :class="iconLib === 'lucide' ? 'border-white text-white bg-white/15' : 'border-white/20 text-slate-300 hover:border-white/50'">
              {{ t('header.appearanceIconLucide') }}
            </button>
            <button type="button" @click="pickIconLib('mdi')"
              class="px-2 py-1 rounded-md text-[10px] font-bold border transition"
              :class="iconLib === 'mdi' ? 'border-white text-white bg-white/15' : 'border-white/20 text-slate-300 hover:border-white/50'">
              {{ t('header.appearanceIconMdi') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getMode, setMode, setTheme, setTone, resetTheme, THEMES, TONE_NAMES, accentFor, getTheme, getTone } from '../../utils/theme'
import { getFont, setFont } from '../../utils/font'
import { getDensity, setDensity } from '../../utils/density'
import { iconLib, toggleIconLib } from '../../utils/iconLib'
import { useIcons } from '../../composables/useIcons'
import { useNotificationsStore } from '../../stores/notifications'
import { useAuthStore } from '../../stores/auth'

const emit = defineEmits(['toggleSidebar'])
defineProps({
  collapsed: { type: Boolean, default: false },
})

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const isMobile = ref(false)
const mode = ref(getMode())
const appearanceOpen = ref(false)
const notificationsOpen = ref(false)
const notifPopStyle = reactive({ top: '0px', right: '0px' })
const notifBtnRef = ref(null)
const appearanceBtnRef = ref(null)
const appearancePopStyle = reactive({ top: '0px', right: '0px' })
const themeKey = ref(getTheme())
const tone = ref(getTone())
const font = ref(getFont())
const density = ref(getDensity())
const icons = useIcons()
const notif = useNotificationsStore()
const auth = useAuthStore()

const roleConfig = {
  SUPER_USER:        { iconKey: 'CrownFilled', labelKey: 'users.roles.SUPER_USER' },
  ADMIN:             { iconKey: 'ShieldLock', labelKey: 'users.roles.ADMIN' },
  OPERATIONS:        { iconKey: 'AirTrafficControl', labelKey: 'users.roles.OPERATIONS' },
  TRAFFIC:           { iconKey: 'ArrowsExchange', labelKey: 'users.roles.TRAFFIC' },
  LOAD_PLANNER:      { iconKey: 'Scale', labelKey: 'users.roles.LOAD_PLANNER' },
  WAREHOUSE_ASSISTANT:{ iconKey: 'Forklift', labelKey: 'users.roles.WAREHOUSE_ASSISTANT' },
  READ_ONLY:         { iconKey: 'Eye', labelKey: 'users.roles.READ_ONLY' },
}
const roleIcon = computed(() => roleConfig[auth.role] || { iconKey: 'User', labelKey: 'users.roles.READ_ONLY' })
const roleLabel = computed(() => t(roleIcon.value.labelKey) || auth.role?.replace('_', ' ') || '')

const FONT_ORDER = ['combo', 'cascadia', 'bodoni', 'consolas', 'nerd', 'sans']
const FONT_LABEL = { combo: 'CMB', cascadia: 'CSC', bodoni: 'BDN', consolas: 'CON', nerd: 'NRD', sans: 'SNS' }
const iconCode = computed(() => iconLib.value === 'tabler' ? 'TB' : iconLib.value === 'mdi' ? 'MD' : 'LC')
const fontLabel = computed(() => FONT_LABEL[font.value] || 'FNT')
const densityLabel = computed(() => density.value === 'compact' ? 'CMP' : 'COM')

function toggleMode() {
  setMode(mode.value === 'dark' ? 'light' : 'dark')
  mode.value = getMode()
}

const NOTIF_ROUTES = {
  RECEIPT: '/receipts',
  MAWB: '/mawbs',
  HAWB: '/mawbs',
  DUA: '/mawbs',
  FLIGHT: '/flights',
  ULD: '/ulds',
  BOOKING: '/bookings',
}

function positionNotifPop() {
  if (notifBtnRef.value) {
    const r = notifBtnRef.value.getBoundingClientRect()
    notifPopStyle.top = `${Math.round(r.bottom + 8)}px`
    notifPopStyle.right = `${Math.max(8, Math.round(window.innerWidth - r.right))}px`
  }
}

function toggleNotif() {
  notificationsOpen.value = !notificationsOpen.value
  if (notificationsOpen.value) positionNotifPop()
}

function toggleAppearance() {
  appearanceOpen.value = !appearanceOpen.value
  if (appearanceOpen.value) positionAppearancePop()
}

function positionAppearancePop() {
  if (appearanceBtnRef.value) {
    const r = appearanceBtnRef.value.getBoundingClientRect()
    appearancePopStyle.top = `${Math.round(r.bottom + 8)}px`
    appearancePopStyle.right = `${Math.max(8, Math.round(window.innerWidth - r.right))}px`
  }
}

function cycleFont() {
  const next = FONT_ORDER[(FONT_ORDER.indexOf(font.value) + 1) % FONT_ORDER.length]
  font.value = setFont(next)
}

function cycleDensity() {
  const next = density.value === 'compact' ? 'comfortable' : 'compact'
  density.value = setDensity(next)
}

function pickTheme(key) {
  themeKey.value = setTheme(key)
  mode.value = getMode()
}

function pickTone(i) {
  tone.value = setTone(i)
  mode.value = getMode()
}

function pickFont(f) {
  font.value = setFont(f)
}

function pickDensity(d) {
  density.value = setDensity(d)
}

function pickIconLib(lib) {
  iconLib.value = lib
}

function resetAppearance() {
  themeKey.value = resetTheme()
  tone.value = getTone()
  mode.value = getMode()
  font.value = setFont('combo')
  density.value = setDensity('comfortable')
  iconLib.value = 'tabler'
}

function openNotification(n) {
  notif.markRead(n.id)
  notificationsOpen.value = false
  const dest = NOTIF_ROUTES[(n.entityType || '').toUpperCase()]
  if (dest && route.path !== dest) router.push(dest)
}

function timeAgo(iso) {
  if (!iso) return ''
  const s = Math.floor(Math.max(0, Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 45) return t('security.justNow')
  if (s < 60) return t('security.secondsAgo', { n: s })
  const m = Math.floor(s / 60)
  if (m < 60) return t('security.minutesAgo', { n: m })
  return t('security.hoursAgo', { n: Math.floor(m / 60) })
}

function onDocClick() {
  if (appearanceOpen.value) appearanceOpen.value = false
  if (notificationsOpen.value) notificationsOpen.value = false
}

function onKeydown(e) {
  if (e.key !== 'Escape') return
  if (appearanceOpen.value) appearanceOpen.value = false
  if (notificationsOpen.value) notificationsOpen.value = false
}

function checkViewport() {
  isMobile.value = window.innerWidth < 768
  if (notificationsOpen.value) positionNotifPop()
  if (appearanceOpen.value) positionAppearancePop()
}

onMounted(() => {
  checkViewport()
  window.addEventListener('resize', checkViewport)
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkViewport)
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
})

</script>

<style scoped>
.shell-header {
  height: 56px;
  background: #0d9488;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 20px;
  color: #fff;
  box-shadow: 0 1px 6px rgba(2,44,34,.2);
  position: sticky;
  top: 0;
  z-index: 50;
}
.sidebar-toggle {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: rgba(255,255,255,.16);
  border: 1px solid rgba(255,255,255,.28);
  color: #fff;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform 0.3s ease, background 0.15s, border-color 0.15s;
}
.sidebar-toggle:hover {
  background: rgba(255,255,255,.3);
  border-color: rgba(255,255,255,.45);
}
.sidebar-toggle.rotated {
  transform: rotate(180deg);
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 800;
  letter-spacing: .3px;
  font-size: 16px;
}
.brand .pi {
  font-size: 20px;
}
.brand small {
  display: block;
  font-weight: 500;
  font-size: 10.5px;
  opacity: .85;
  letter-spacing: .5px;
}
.spacer {
  flex: 1;
}
.h-chip {
  background: rgba(255,255,255,.14);
  border: 1px solid rgba(255,255,255,.2);
  padding: 5px 11px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 7px;
  color: #fff;
  cursor: pointer;
  transition: background 0.15s;
}
.h-chip:hover {
  background: rgba(255,255,255,.22);
}
.ctrl-chip {
  height: 26px;
  min-width: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: #ffffff;
  border: 1px solid rgba(2,44,34,.18);
  color: #0d9488;
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: .4px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(2,44,34,.3);
  transition: background 0.15s, transform 0.1s, box-shadow 0.15s;
}
.ctrl-chip:hover {
  background: #f0fdfa;
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(2,44,34,.35);
}
.h-chip-swatch {
  padding: 5px 9px;
}
.appearance-swatch {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--accent, #0d9488);
  box-shadow: 0 0 0 2px rgba(255,255,255,.25);
  display: block;
}
.h-chip-bell {
  position: relative;
  padding: 5px 9px;
}
.notif-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 15px;
  height: 15px;
  padding: 0 3px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 9px;
  font-weight: 800;
  line-height: 15px;
  text-align: center;
  border: 1px solid rgba(0,0,0,.25);
}
@media (max-width: 900px) {
  .hide-sm {
    display: none !important;
  }
}
@media (max-width: 480px) {
  .header-actions {
    overflow-x: auto;
    scrollbar-width: none;
  }
  .header-actions::-webkit-scrollbar {
    display: none;
  }
}
</style>