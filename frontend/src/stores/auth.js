import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api/auth'
import { canAccessView } from '../utils/rbac'


const STORAGE_KEY = 'aircargo_auth'

function loadStored() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw)
  } catch (e) { console.warn('Failed to parse auth storage:', e) }
  return null
}

export const useAuthStore = defineStore('auth', () => {
  // Los tokens viven en cookies httpOnly (no accesibles por JS).
  // Aquí solo se persiste el perfil no sensible para la UI.
  const stored = loadStored()
  const userId = ref(stored?.userId || null)
  const email = ref(stored?.email || '')
  const fullName = ref(stored?.fullName || '')
  const role = ref(stored?.role || '')
  const airlineId = ref(stored?.airlineId || null)
  const hasPasswordSet = ref(stored?.hasPasswordSet ?? false)
  const sites = ref(stored?.sites || [])
  const selectedSiteId = ref(stored?.selectedSiteId || null)
  const mfaEnabled = ref(stored?.mfaEnabled ?? false)
  const mustChangePassword = ref(stored?.mustChangePassword ?? false)
  const permissions = ref(stored?.permissions || [])

  const isAuthenticated = computed(() => !!userId.value && !!selectedSiteId.value)
  const hasSession = computed(() => !!userId.value)
  const initials = computed(() => {
    if (!fullName.value) return '??'
    return fullName.value.split(' ').map(s => s[0]).join('').toUpperCase().slice(0, 2)
  })
  const selectedSite = computed(() => {
    if (!selectedSiteId.value) return null
    return sites.value.find(s => s.id === selectedSiteId.value) || null
  })

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      userId: userId.value,
      email: email.value,
      fullName: fullName.value,
      role: role.value,
      airlineId: airlineId.value,
      hasPasswordSet: hasPasswordSet.value,
      sites: sites.value,
      selectedSiteId: selectedSiteId.value,
      mfaEnabled: mfaEnabled.value,
      mustChangePassword: mustChangePassword.value,
      permissions: permissions.value,
    }))
  }

  async function login(loginEmail, password, totpCode) {
    const res = await authApi.login(loginEmail, password, totpCode)
    const data = res.data
    userId.value = data.userId
    email.value = data.email
    fullName.value = data.fullName
    role.value = data.role
    airlineId.value = data.airlineId
    hasPasswordSet.value = data.hasPasswordSet
    sites.value = data.sites || []
    selectedSiteId.value = null
    mfaEnabled.value = data.mfaEnabled ?? false
    mustChangePassword.value = data.mustChangePassword ?? false
    permissions.value = data.permissions || []
    persist()   // los tokens ya fueron emitidos como cookies httpOnly
    return data
  }

  function confirmSite(siteId) {
    selectedSiteId.value = siteId
    persist()
  }

  async function refreshProfile() {
    try {
      const res = await authApi.me()
      const data = res.data
      userId.value = data.userId
      email.value = data.email
      fullName.value = data.fullName
      role.value = data.role
      airlineId.value = data.airlineId
      hasPasswordSet.value = data.hasPasswordSet
      mustChangePassword.value = data.mustChangePassword ?? false
      permissions.value = data.permissions || []
      persist()
    } catch (e) {
      console.warn('Failed to refresh profile:', e)
    }
  }

  function clearProfile() {
    userId.value = null
    email.value = ''
    fullName.value = ''
    role.value = ''
    airlineId.value = null
    hasPasswordSet.value = false
    sites.value = []
    selectedSiteId.value = null
    mfaEnabled.value = false
    mustChangePassword.value = false
    permissions.value = []
    localStorage.removeItem(STORAGE_KEY)
  }

  async function logout() {
    authApi.logout().catch(() => {})   // el backend limpia las cookies y revoca
    clearProfile()
  }

  function can(code) {
    // Backward-compat: sesiones viejas sin permissiones no bloquean (permit-all en UI).
    if (!Array.isArray(permissions.value) || permissions.value.length === 0) return true
    return permissions.value.includes(code)
  }

  /* La tabla rol->vistas vive en utils/rbac.js (fuente única compartida con el
   * router y el Sidebar). No duplicar el switch aquí: se desincronizaba. */
  function canView(viewName) {
    return canAccessView(role.value, viewName)
  }

  return {
    userId, email, fullName, role, airlineId, hasPasswordSet,
    sites, selectedSiteId, selectedSite, mfaEnabled, mustChangePassword, permissions,
    isAuthenticated, hasSession, initials,
    login, confirmSite, logout, canView, can,
    refreshProfile, persist, clearProfile,
  }
})
