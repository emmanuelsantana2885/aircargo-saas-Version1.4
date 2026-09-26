import { createRouter, createWebHistory } from 'vue-router'
import i18n from '../i18n'
import { canAccessView } from '../utils/rbac'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { titleKey: 'login.title' },
    },
    {
      path: '/privacy',
      name: 'privacy',
      component: () => import('../views/PrivacyPolicyView.vue'),
      meta: { titleKey: 'privacy.title' },
    },
    {
      path: '/set-password',
      name: 'set-password',
      component: () => import('../views/SetPasswordView.vue'),
      meta: { titleKey: 'setPassword.title' },
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: () => import('../views/ChangePasswordView.vue'),
      meta: { titleKey: 'changePassword.title' },
    },
    {
      path: '/mfa-setup',
      name: 'mfa-setup',
      component: () => import('../views/MfaSetupView.vue'),
      meta: { titleKey: 'login.mfaEnroll.title' },
    },
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../views/DashboardView.vue'),
      meta: { view: 'DASHBOARD', titleKey: 'sidebar.dashboard' },
    },
    {
      path: '/flights',
      name: 'flights',
      component: () => import('../views/FlightsView.vue'),
      meta: { view: 'FLIGHTS', titleKey: 'sidebar.flights' },
    },
    {
      path: '/load-planning',
      name: 'load-planning',
      component: () => import('../views/LoadPlanningView.vue'),
      meta: { view: 'LOAD_PLANNING', titleKey: 'sidebar.loadPlanning' },
    },
    {
      path: '/ulds',
      name: 'ulds',
      component: () => import('../views/UldsView.vue'),
      meta: { view: 'ULDS', titleKey: 'sidebar.ulds' },
    },
    {
      path: '/mawbs',
      name: 'mawbs',
      component: () => import('../views/MawbsView.vue'),
      meta: { view: 'MAWBS', titleKey: 'sidebar.mawbs' },
    },
    {
      path: '/bookings',
      name: 'bookings',
      component: () => import('../views/BookingsView.vue'),
      meta: { view: 'BOOKINGS', titleKey: 'sidebar.bookings' },
    },
    {
      path: '/receipts',
      name: 'receipts',
      component: () => import('../views/WarehouseReceiptsView.vue'),
      meta: { view: 'RECEIPTS', titleKey: 'sidebar.receipts' },
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('../views/UsersView.vue'),
      meta: { view: 'USERS', titleKey: 'sidebar.users' },
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('../views/SettingsView.vue'),
      meta: { view: 'SETTINGS', titleKey: 'sidebar.settings' },
    },
    {
      path: '/security',
      name: 'security',
      component: () => import('../views/SecurityView.vue'),
      meta: { view: 'SECURITY', titleKey: 'sidebar.security' },
    },
    {
      path: '/exports',
      name: 'exports',
      component: () => import('../views/ExportsView.vue'),
      meta: { view: 'EXPORTS', titleKey: 'sidebar.exports' },
    },
    {
      path: '/api-catalog',
      name: 'api-catalog',
      component: () => import('../views/ApiCatalogView.vue'),
      meta: { view: 'API_CATALOG', titleKey: 'sidebar.apiCatalog' },
    },
    {
      path: '/home',
      redirect: '/'
    },
    /* Sin esta ruta comodín, una URL mal escrita o un enlace viejo dejaba
     * <main> sin ninguna vista montada: pantalla en blanco sin explicación ni
     * forma de volver. El guard global sigue aplicando (anónimo -> /login). */
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
      meta: { titleKey: 'notFound.title' },
    },
  ]
})

const publicPaths = ['/login', '/set-password', '/change-password', '/mfa-setup', '/privacy']

/* document.title por ruta. El título vive en el meta.titleKey de cada ruta.
 * Antes las públicas estaban además en un segundo mapa (PUBLIC_TITLES) que
 * cualquier ruta nueva olvidaba, dejando el título de index.html pegado. */
const APP_NAME = 'AirCargo'

router.beforeEach((to) => {
  const stored = localStorage.getItem('aircargo_auth')
  if (!publicPaths.includes(to.path) && !stored) {
    return '/login'
  }
  if (publicPaths.includes(to.path)) {
    if (stored) {
      const parsed = JSON.parse(stored)
      if (parsed.userId && parsed.selectedSiteId) {
        return '/'
      }
    }
    return
  }
  if (to.path !== '/login' && stored) {
    try {
      const { role, selectedSiteId, userId } = JSON.parse(stored)
      if (userId && !selectedSiteId) {
        return '/login'
      }
      if (role && to.meta?.view && !canAccessView(role, to.meta.view)) {
        return '/'
      }
    } catch {}
  }
})

/* Aplica el título al navegar. Se lee i18n en cada salto para que un
 * cambio de idioma ya realizado se refleje sin recargar. */
router.afterEach((to) => {
  const key = to.meta?.titleKey
  if (!key) return
  const label = i18n.global.t(key)
  document.title = `${label} · ${APP_NAME}`
})

export default router
