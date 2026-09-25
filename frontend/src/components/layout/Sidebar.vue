<template>
  <aside class="sidebar" :class="{ 'sidebar-collapsed': collapsed, 'sidebar-mobile': isMobile && mobileOpen }">
    <div class="rail"></div>

    <!-- Logo -->
    <div class="sidebar-logo">
      <i class="pi pi-box"></i>
      <div v-if="!collapsed">
        <div class="font-bold text-[14px] tracking-wide">AirCargo</div>
        <div class="text-[10px] font-medium tracking-wider text-slate-500">{{ auth.selectedSite?.code || 'SDQ' }} Operations</div>
      </div>
      <div v-else class="text-center">
        <i class="pi pi-box" style="font-size: 20px;"></i>
      </div>
    </div>

    <!-- Nav -->
    <nav class="sidebar-nav" v-if="!collapsed">
      <div class="nav-section">{{ t('sidebar.operations') }}</div>
      <RouterLink v-for="item in mainMenu" :key="item.path" :to="item.path"
        class="nav-item" :class="{ on: isActive(item.path) }"
        @click="closeMobileSidebar">
        <component :is="icons[item.iconKey]" class="pi" />
        <span>{{ t(item.labelKey) }}</span>
      </RouterLink>

      <div class="nav-section" v-if="settingsMenu.length">{{ t('sidebar.settings') }}</div>
      <RouterLink v-for="item in settingsMenu" :key="item.path" :to="item.path"
        class="nav-item" :class="{ on: isActive(item.path) }"
        @click="closeMobileSidebar">
        <component :is="icons[item.iconKey]" class="pi" />
        <span>{{ t(item.labelKey) }}</span>
      </RouterLink>
    </nav>

    <!-- Collapsed nav (icons only) -->
    <nav class="sidebar-nav-collapsed" v-else>
      <RouterLink v-for="item in mainMenu" :key="item.path" :to="item.path"
        class="nav-item-collapsed" :class="{ on: isActive(item.path) }"
        :title="t(item.labelKey)"
        @click="closeMobileSidebar">
        <component :is="icons[item.iconKey]" class="pi" />
      </RouterLink>
      <div class="nav-divider" v-if="settingsMenu.length"></div>
      <RouterLink v-for="item in settingsMenu" :key="item.path" :to="item.path"
        class="nav-item-collapsed" :class="{ on: isActive(item.path) }"
        :title="t(item.labelKey)"
        @click="closeMobileSidebar">
        <component :is="icons[item.iconKey]" class="pi" />
      </RouterLink>
    </nav>

    <!-- User -->
    <div class="sidebar-user" v-if="!collapsed">
      <div class="user-avatar" :style="{ background: roleIcon.bg }">
        <component :is="icons[roleIcon.iconKey]" :size="16" :stroke-width="1.8" style="color: white" />
      </div>
      <div class="user-info">
        <div class="font-bold text-[12px] truncate">{{ auth.fullName || auth.email }}</div>
        <div class="text-[10px] truncate" :style="{ color: roleIcon.fg }">{{ roleLabel }}</div>
      </div>
      <div class="user-actions">
        <button @click="showPasswordChange = true" :title="t('sidebar.changePassword')" class="hover:opacity-70 transition-opacity" :aria-label="t('sidebar.changePassword')">
          <component :is="icons.Key" :size="14" :stroke-width="1.5" />
        </button>
        <button @click="handleLogout" :title="t('sidebar.logout')" class="hover:opacity-70 transition-opacity" :aria-label="t('sidebar.logout')">
          <component :is="icons.Logout" :size="14" :stroke-width="1.5" />
        </button>
      </div>
    </div>

    <!-- Mobile overlay -->
    <div v-if="isMobile && mobileOpen" @click="mobileOpen = false"
      class="fixed inset-0 bg-black/40 z-40 transition-opacity lg:hidden"></div>
  </aside>

  <PasswordChangeModal :show="showPasswordChange" @close="showPasswordChange = false" />
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../../stores/auth'
import { useIcons } from '../../composables/useIcons'
import PasswordChangeModal from '../PasswordChangeModal.vue'
import { captureForms, saveDraft, setReturnTo } from '../../utils/formDraft'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { t } = useI18n()
const icons = useIcons()

const collapsed = ref(false)
const mobileOpen = ref(false)
const isMobile = ref(false)
const showPasswordChange = ref(false)

const roleConfig = {
  SUPER_USER:        { iconKey: 'CrownFilled',       labelKey: 'users.roles.SUPER_USER', bg: 'rgba(234,179,8,.15)',  fg: '#b45309' },
  ADMIN:             { iconKey: 'ShieldLock',         labelKey: 'users.roles.ADMIN', bg: 'rgba(37,99,235,.12)',  fg: '#2563eb' },
  OPERATIONS:        { iconKey: 'AirTrafficControl',  labelKey: 'users.roles.OPERATIONS', bg: 'rgba(22,163,74,.12)',  fg: '#16a34a' },
  TRAFFIC:           { iconKey: 'ArrowsExchange',     labelKey: 'users.roles.TRAFFIC', bg: 'rgba(124,58,237,.12)', fg: '#7c3aed' },
  LOAD_PLANNER:      { iconKey: 'Scale',              labelKey: 'users.roles.LOAD_PLANNER', bg: 'rgba(7,148,148,.12)',  fg: '#0891b2' },
  WAREHOUSE_ASSISTANT:{ iconKey: 'Forklift',          labelKey: 'users.roles.WAREHOUSE_ASSISTANT', bg: 'rgba(217,119,6,.12)',  fg: '#d97706' },
  READ_ONLY:         { iconKey: 'Eye',                labelKey: 'users.roles.READ_ONLY', bg: 'rgba(100,116,139,.12)',fg: '#64748b' },
}
const roleIcon = computed(() => roleConfig[auth.role] || { iconKey: 'User', labelKey: 'users.roles.READ_ONLY', bg: 'rgba(100,116,139,.12)', fg: '#64748b' })
const roleLabel = computed(() => t(roleIcon.value.labelKey) || auth.role?.replace('_', ' ') || '')

const isActive = (path) => path === '/' ? route.path === '/' : route.path.startsWith(path)

function checkViewport() {
  const w = window.innerWidth
  isMobile.value = w < 768
  if (isMobile.value) mobileOpen.value = false
}

function closeMobileSidebar() {
  if (isMobile.value) mobileOpen.value = false
}

function handleLogout() {
  try {
    saveDraft({ route: router.currentRoute.value.fullPath, forms: captureForms() })
    setReturnTo(router.currentRoute.value.fullPath)
  } catch {}
  auth.logout()
  router.push('/login')
}

defineExpose({ mobileOpen, isMobile, collapsed })
onMounted(() => { checkViewport(); window.addEventListener('resize', checkViewport) })
onUnmounted(() => { window.removeEventListener('resize', checkViewport) })

const allMenuItems = computed(() => [
  { path: '/',              labelKey: 'sidebar.dashboard',   iconKey: 'ChartPie',   view: 'DASHBOARD',     },
  { path: '/bookings',      labelKey: 'sidebar.bookings',    iconKey: 'Book',       view: 'BOOKINGS',      },
  { path: '/receipts',      labelKey: 'sidebar.receipts',    iconKey: 'Inbox',      view: 'RECEIPTS',      },
  { path: '/flights',       labelKey: 'sidebar.flights',     iconKey: 'Send',       view: 'FLIGHTS',       },
  { path: '/mawbs',         labelKey: 'sidebar.mawbs',       iconKey: 'File',       view: 'MAWBS',         },
  { path: '/load-planning', labelKey: 'sidebar.loadPlanning',iconKey: 'ThLarge',    view: 'LOAD_PLANNING', },
  { path: '/ulds',          labelKey: 'sidebar.ulds',        iconKey: 'Pallet',     view: 'ULDS',          },
  { path: '/exports',       labelKey: 'sidebar.exports',     iconKey: 'ChartBar',   view: 'EXPORTS',       },
])
const mainMenu = computed(() => allMenuItems.value.filter(item => auth.canView(item.view)))
const settingsMenu = computed(() => {
  const items = []
  if (auth.canView('USERS')) items.push({ path: '/users', labelKey: 'sidebar.users', iconKey: 'Users' })
  if (auth.canView('SETTINGS')) items.push({ path: '/settings', labelKey: 'sidebar.settings', iconKey: 'Cog' })
  if (auth.canView('SECURITY')) items.push({ path: '/security', labelKey: 'sidebar.security', iconKey: 'Shield' })
  if (auth.canView('API_CATALOG')) items.push({ path: '/api-catalog', labelKey: 'sidebar.apiCatalog', iconKey: 'Code' })
  return items
})
</script>

<style scoped>
.sidebar {
  width: 210px;
  background: #ededed;
  border-right: 1px solid #dfe2e7;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  height: 100vh;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 50;
  transition: width 0.3s ease;
}
.sidebar.sidebar-collapsed {
  width: 60px;
}
.sidebar.sidebar-mobile {
  transform: translateX(0);
  box-shadow: 4px 0 20px rgba(0,0,0,0.15);
}
.sidebar.sidebar-mobile:not(.sidebar-collapsed) {
  width: 210px;
}
.rail {
  width: 5px;
  background: #31216b;
  border-radius: 0 4px 4px 0;
  margin: 8px 0;
}
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 14px;
  border-bottom: 1px solid #dfe2e7;
  font-weight: 800;
  color: #31216b;
}
.sidebar-logo .pi {
  font-size: 20px;
}
.sidebar-nav {
  flex: 1;
  padding: 8px;
  overflow-y: auto;
}
.sidebar-nav-collapsed {
  flex: 1;
  padding: 8px 4px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.nav-section {
  padding: 8px 14px 4px;
  font-size: 10px;
  font-weight: 800;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.nav-divider {
  width: 100%;
  height: 1px;
  background: #dfe2e7;
  margin: 8px 0;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 14px;
  cursor: pointer;
  color: #31216b;
  font-weight: 600;
  font-size: 13px;
  border-radius: 8px;
  margin: 1px 4px;
  transition: background 0.15s, color 0.15s;
  text-decoration: none;
}
.nav-item .pi {
  font-size: 15px;
}
.nav-item:hover {
  background: #e2e2e6;
}
.nav-item.on {
  background: #31216b;
  color: #fff;
}
.nav-item.on .pi {
  color: #fff;
}
.nav-item-collapsed {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  cursor: pointer;
  color: #31216b;
  border-radius: 8px;
  margin: 4px 0;
  transition: background 0.15s, color 0.15s;
  text-decoration: none;
}
.nav-item-collapsed .pi {
  font-size: 18px;
}
.nav-item-collapsed:hover {
  background: #e2e2e6;
}
.nav-item-collapsed.on {
  background: #31216b;
  color: #fff;
}
.nav-item-collapsed.on .pi {
  color: #fff;
}
.sidebar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border-top: 1px solid #dfe2e7;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
}
.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.user-info {
  flex: 1;
  min-width: 0;
}
.user-actions {
  display: flex;
  gap: 6px;
}
.user-actions button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: #64748b;
  background: transparent;
  border: none;
}
.user-actions button:hover {
  background: #dfe2e7;
  color: #31216b;
}
@media (max-width: 767px) {
  .sidebar {
    transform: translateX(-100%);
    transition: transform 0.3s ease, width 0.3s ease;
  }
  .sidebar.sidebar-mobile {
    transform: translateX(0);
  }
}
@media (min-width: 768px) and (max-width: 1023px) {
  .sidebar:not(.sidebar-collapsed) {
    width: 60px;
  }
}
</style>
