<template>
  <nav
    class="bottom-nav md:hidden shrink-0 flex items-stretch justify-around"
    aria-label="bottom navigation"
  >
    <button
      v-for="item in visibleItems"
      :key="item.path"
      type="button"
      class="flex flex-col items-center justify-center gap-0.5 flex-1 min-w-0 transition-colors duration-150"
      :aria-label="t(item.labelKey)"
      @click="router.push(item.path)"
    >
      <component :is="icons[item.iconKey]" :size="22" :stroke-width="1.8" :color="isActive(item.path) ? item.color : '#64748b'" />
      <span
        class="text-[10px] font-bold truncate max-w-full px-0.5"
        :style="{ color: isActive(item.path) ? item.color : 'var(--muted, #64748b)' }"
      >{{ t(item.labelKey) }}</span>
    </button>

    <button
      v-if="quickActions.length"
      type="button"
      class="flex flex-col items-center justify-center gap-0.5 flex-1 min-w-0 transition-colors duration-150"
      :aria-label="t('bottomnav.quickActions')"
      :data-qa="`bottomnav-quick-actions`"
      @click="quickOpen = true"
    >
      <span class="w-7 h-7 rounded-full flex items-center justify-center"
        style="background: linear-gradient(135deg, var(--accent, #2563eb), var(--accent-violet, #7c3aed)); color: #fff; box-shadow: 0 2px 10px rgba(37,99,235,.35)">
        <component :is="icons.Plus" :size="18" :stroke-width="2.5" />
      </span>
      <span class="text-[10px] font-bold truncate max-w-full px-0.5" style="color: var(--accent, #2563eb)">{{ t('bottomnav.quickActions') }}</span>
    </button>

    <button
      type="button"
      class="flex flex-col items-center justify-center gap-0.5 flex-1 min-w-0 transition-colors duration-150"
      :aria-label="t('bottomnav.profile')"
      @click="profileOpen = true"
    >
      <span
        v-if="auth.initials !== '??'"
        class="w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold"
        :style="{ background: roleIcon.bg, color: roleIcon.fg, boxShadow: `0 2px 8px ${roleIcon.bg}` }"
      >{{ auth.initials }}</span>
      <component :is="icons.User" v-else :size="22" :stroke-width="1.8" color="#334155" />
      <span class="text-[10px] font-bold truncate max-w-full px-0.5" style="color: var(--muted, #64748b)">{{ t('bottomnav.profile') }}</span>
    </button>
  </nav>

  <PasswordChangeModal :show="showPasswordChange" @close="showPasswordChange = false" />

  <Teleport to="body">
    <transition name="sheet">
      <div v-if="profileOpen" class="sheet-backdrop md:hidden" @click.self="profileOpen = false">
        <div class="sheet-panel">
          <div class="sheet-handle"></div>

          <div class="flex flex-col items-center pt-1 pb-4 px-4">
            <div
              class="w-14 h-14 rounded-full ring-2 ring-white flex items-center justify-center text-lg font-bold"
              :style="{ background: roleIcon.bg, color: roleIcon.fg, boxShadow: `0 4px 16px ${roleIcon.bg}` }"
            >{{ auth.initials }}</div>
            <div class="mt-2 text-sm font-bold text-center" style="color: var(--text, #0f172a)">{{ auth.fullName || auth.email }}</div>
            <div class="text-xs" :style="{ color: roleIcon.fg }">{{ roleLabel }}</div>
            <div
              v-if="auth.selectedSite"
              class="mt-1.5 text-[11px] px-2.5 py-0.5 rounded-full"
              style="background: rgba(37,99,235,.08); color: #2563eb"
            >{{ t('bottomnav.site') }}: {{ auth.selectedSite.code || auth.selectedSite.name }}</div>
            <div class="mt-1 text-[11px]" style="color: var(--muted, #64748b)">{{ auth.email }}</div>
          </div>

          <div class="px-4 pb-8 space-y-2">
            <button type="button" class="sheet-action" @click="openPasswordChange">
              <component :is="icons.Key" :size="18" :stroke-width="1.8" color="#3e7bfa" />
              <span>{{ t('bottomnav.changePassword') }}</span>
            </button>
            <button type="button" class="sheet-action sheet-action-danger" @click="handleLogout">
              <component :is="icons.Logout" :size="18" :stroke-width="1.8" color="#dc2626" />
              <span>{{ t('bottomnav.logout') }}</span>
            </button>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>

  <Teleport to="body">
    <transition name="sheet">
      <div v-if="quickOpen" class="sheet-backdrop md:hidden" @click.self="quickOpen = false">
        <div class="sheet-panel">
          <div class="sheet-handle"></div>
          <div class="px-4 pt-2 pb-8 space-y-2">
            <div class="text-[11px] uppercase tracking-wider font-bold" style="color: var(--muted, #64748b)">
              {{ t('bottomnav.quickActionsTitle', { n: quickActions.length }) }}
            </div>
            <button
              v-for="action in quickActions"
              :key="action.key"
              type="button"
              class="sheet-action"
              :data-qa="`bottomnav-quick-action-${action.key}`"
              @click="runQuickAction(action)"
            >
              <span class="shrink-0 w-8 h-8 rounded-lg flex items-center justify-center" :style="{ background: action.color, color: '#fff' }">
                <component :is="icons[action.icon]" :size="18" :stroke-width="2" />
              </span>
              <span class="min-w-0 flex flex-col items-start">
                <span class="text-[13px] font-semibold" style="color: var(--text, #0f172a)">{{ t(`dashboard.quickActions.${action.key}`) }}</span>
                <span class="text-[11px] font-normal truncate max-w-full" style="color: var(--muted, #64748b)">{{ t(`dashboard.quickActions.${action.key}Desc`) }}</span>
              </span>
            </button>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../stores/auth'
import { useIcons } from '../../composables/useIcons'
import PasswordChangeModal from '../PasswordChangeModal.vue'
import { captureForms, saveDraft, setReturnTo } from '../../utils/formDraft'
import { useQuickActions } from '../../composables/useQuickActions'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n()
const icons = useIcons()
const profileOpen = ref(false)
const showPasswordChange = ref(false)
const quickOpen = ref(false)
const { actions: quickActions } = useQuickActions()

const menuItems = [
  { path: '/',        labelKey: 'bottomnav.home',      iconKey: 'Gauge',          view: 'DASHBOARD', color: '#e11d48' },
  { path: '/bookings', labelKey: 'bottomnav.bookings', iconKey: 'CalendarEvent',  view: 'BOOKINGS',  color: '#3e7bfa' },
  { path: '/receipts', labelKey: 'bottomnav.warehouse', iconKey: 'FileInvoice',   view: 'RECEIPTS',  color: '#d97706' },
  { path: '/mawbs',   labelKey: 'bottomnav.tracking',  iconKey: 'ClipboardList',  view: 'MAWBS',     color: '#16a34a' },
]

const visibleItems = computed(() => menuItems.filter(item => auth.canView(item.view)))
const isActive = (path) => path === '/' ? route.path === '/' : route.path.startsWith(path)

const roleConfig = {
  SUPER_USER:         { iconKey: 'CrownFilled',       bg: 'rgba(234,179,8,.15)',  fg: '#b45309' },
  ADMIN:              { iconKey: 'ShieldLock',         bg: 'rgba(37,99,235,.12)',  fg: '#2563eb' },
  OPERATIONS:         { iconKey: 'AirTrafficControl',  bg: 'rgba(22,163,74,.12)',  fg: '#16a34a' },
  TRAFFIC:            { iconKey: 'ArrowsExchange',     bg: 'rgba(124,58,237,.12)', fg: '#7c3aed' },
  LOAD_PLANNER:       { iconKey: 'Scale',              bg: 'rgba(7,148,148,.12)',  fg: '#0891b2' },
  WAREHOUSE_ASSISTANT:{ iconKey: 'Forklift',           bg: 'rgba(217,119,6,.12)',  fg: '#d97706' },
  READ_ONLY:          { iconKey: 'Eye',                bg: 'rgba(100,116,139,.12)',fg: '#64748b' },
}
const roleIcon = computed(() => {
  const cfg = roleConfig[auth.role] || { iconKey: 'User', bg: 'rgba(100,116,139,.12)', fg: '#64748b' }
  return { icon: icons.value[cfg.iconKey], bg: cfg.bg, fg: cfg.fg }
})
const roleLabel = computed(() => t(`users.roles.${auth.role}`) || auth.role?.replace('_', ' ') || '')

function openPasswordChange() {
  profileOpen.value = false
  showPasswordChange.value = true
}

function handleLogout() {
  try {
    saveDraft({ route: router.currentRoute.value.fullPath, forms: captureForms() })
    setReturnTo(router.currentRoute.value.fullPath)
  } catch (e) {
    console.warn('Could not save form draft on logout:', e)
  }
  auth.logout()
  router.push('/login')
}

function runQuickAction(action) {
  quickOpen.value = false
  router.push(action.to)
}
</script>

<style scoped>
.sheet-backdrop {
  position: fixed;
  inset: 0;
  z-index: 90;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.sheet-panel {
  width: 100%;
  background: var(--surface, #ffffff);
  color: var(--text, #0f172a);
  border-radius: 16px 16px 0 0;
  box-shadow: 0 -8px 40px rgba(15, 23, 42, 0.18);
  padding-bottom: env(safe-area-inset-bottom, 16px);
}

.sheet-handle {
  width: 40px;
  height: 5px;
  border-radius: 999px;
  margin: 8px auto 4px;
  background: var(--border, #cbd5e1);
}

.sheet-action {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text, #0f172a);
  background: var(--surface-raised, #f8fafc);
  border: 1px solid var(--border, #e2e8f0);
  transition: background 0.15s ease, transform 0.15s ease;
}

.sheet-action:active {
  transform: scale(0.985);
}

.sheet-action-danger {
  color: #dc2626;
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.22s ease;
}

.sheet-enter-active .sheet-panel,
.sheet-leave-active .sheet-panel {
  transition: transform 0.22s cubic-bezier(0.22, 1, 0.36, 1);
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}

.sheet-enter-from .sheet-panel,
.sheet-leave-to .sheet-panel {
  transform: translateY(100%);
}
</style>