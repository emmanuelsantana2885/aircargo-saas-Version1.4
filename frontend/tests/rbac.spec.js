import { describe, it, expect, beforeEach } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { canAccessView, viewsForRole, VIEW_CODES, CLIENT_ONLY_VIEWS, ROLE_VIEWS, ROLES } from '@/utils/rbac'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import { setActivePinia, createPinia } from 'pinia'

/* La tabla de utils/rbac.js pretende reflejar lo que el backend reparte. Este
 * test lee el catálogo de Java y falla si divergen, en vez de fiarse de un
 * comentario (patrón ya usado en SecurityConfigConsistencyTest / FeignContractSyncTest
 * del backend y en check-i18n-keys.mjs del front). */

const repoFile = (rel) => resolve(dirname(fileURLToPath(import.meta.url)), '../..', rel)
const CATALOG = repoFile('backend/aircargo-auth-service/src/main/java/com/aircargo/authservice/config/RolePermissionCatalog.java')
const PERMISSIONS = repoFile('backend/aircargo-common/src/main/java/com/aircargo/common/auth/Permissions.java')

function parseJavaCatalog() {
  const perms = readFileSync(PERMISSIONS, 'utf8')
  const cat = readFileSync(CATALOG, 'utf8')

  // Permissions.java: String CAN_X = "CAN_X";  -> el valor es el nombre.
  const allCodes = [...perms.matchAll(/String\s+(CAN_[A-Z_]+)\s*=/g)].map((m) => m[1])

  // RolePermissionCatalog.java: <ROLE> = build(Set.of(Permissions.CAN_X, ...));
  const perRole = {}
  for (const m of cat.matchAll(/^\s+([A-Z_]+)\s*=\s*build\(Set\.of\(([\s\S]*?)\)\);/gm)) {
    perRole[m[1]] = [...m[2].matchAll(/Permissions\.(CAN_[A-Z_]+)/g)].map((x) => x[1])
  }

  // ADMIN = build(ALL_SET, Set.of(...))  -> ALL menos las exclusiones.
  const minusMatch = cat.match(/ALL_MINUS_SITE_LABEL\s*=\s*build\(ALL_SET,\s*Set\.of\(([\s\S]*?)\)\)/)
  const removed = minusMatch ? [...minusMatch[1].matchAll(/Permissions\.(CAN_[A-Z_]+)/g)].map((m) => m[1]) : []

  return {
    SUPER_USER: new Set(allCodes),
    ADMIN: new Set(allCodes.filter((c) => !removed.includes(c))),
    ...Object.fromEntries(Object.entries(perRole).map(([k, v]) => [k, new Set(v)])),
  }
}

const JAVA = parseJavaCatalog()

/** ¿El backend permite a ese rol abrir la vista? Misma semántica ANY que rbac.js. */
const backendAllows = (role, view) => {
  const codes = VIEW_CODES[view]
  if (!codes) return false
  return codes.some((c) => JAVA[role]?.has(c))
}

beforeEach(() => {
  localStorage.clear()
  setActivePinia(createPinia())
})

describe('rbac — sincronía con el catálogo del backend', () => {
  it('el parser del catálogo de Java encuentra los 8 roles con códigos', () => {
    expect(Object.keys(JAVA).sort()).toEqual([...ROLES].sort())
    expect(JAVA.SUPER_USER.size).toBeGreaterThan(40)
    for (const role of Object.keys(JAVA)) expect(JAVA[role].size, role).toBeGreaterThan(0)
  })

  it('ADMIN recibe todo salvo CAN_MANAGE_SITE y CAN_MANAGE_LABEL_TEMPLATE', () => {
    expect(JAVA.SUPER_USER.has('CAN_MANAGE_SITE')).toBe(true)
    expect(JAVA.ADMIN.has('CAN_MANAGE_SITE')).toBe(false)
    expect(JAVA.ADMIN.has('CAN_MANAGE_LABEL_TEMPLATE')).toBe(false)
    expect(JAVA.ADMIN.has('CAN_READ_AUDIT')).toBe(true)
  })

  it('NINGÚN rol ve una vista que el backend le niegue (ningún over-grant)', () => {
    const offenders = []
    for (const role of ROLES) {
      for (const view of Object.keys(VIEW_CODES)) {
        if (canAccessView(role, view) && !backendAllows(role, view)) {
          offenders.push(`${role} -> ${view}`)
        }
      }
    }
    expect(offenders, `el front concede vistas que el backend responde 403: ${offenders.join(', ')}`).toEqual([])
  })

  it('SUPER_USER y ADMIN ven las doce vistas', () => {
    const all = Object.keys(VIEW_CODES)
    for (const role of ['SUPER_USER', 'ADMIN']) {
      expect(viewsForRole(role).sort(), role).toEqual([...all, ...CLIENT_ONLY_VIEWS].sort())
      for (const view of all) expect(backendAllows(role, view), `${role}/${view}`).toBe(true)
    }
  })

  it('no deja referencias muertas: toda vista citada existe en el catálogo', () => {
    const known = [...Object.keys(VIEW_CODES), ...CLIENT_ONLY_VIEWS]
    for (const [role, views] of Object.entries(ROLE_VIEWS)) {
      for (const v of views) expect(known, `${role} cita una vista inexistente: ${v}`).toContain(v)
    }
  })

  it('falla cerrado ante una vista desconocida', () => {
    for (const role of ROLES) {
      if (role === 'SUPER_USER' || role === 'ADMIN') continue
      expect(canAccessView(role, 'VISTA_INVENTADA')).toBe(false)
    }
    expect(canAccessView('ROL_INVENTADO', 'DASHBOARD')).toBe(false)
    expect(canAccessView(null, 'DASHBOARD')).toBe(false)
    expect(canAccessView('READ_ONLY', null)).toBe(true) // ruta sin meta.view
  })
})

describe('rbac — regresiones de permisos concretos', () => {
  it('READ_ONLY no ve las vistas administrativas que el backend no le da', () => {
    // READ_ONLY no tiene CAN_READ_RECEIPT, CAN_READ_AUDIT, CAN_READ_REPORT ni
    // ningún CAN_MANAGE_*: antes el front le mostraba los cinco accesos.
    for (const view of ['RECEIPTS', 'SECURITY', 'EXPORTS', 'USERS', 'SETTINGS']) {
      expect(JAVA.READ_ONLY.has(VIEW_CODES[view][0]) || VIEW_CODES[view].some((c) => JAVA.READ_ONLY.has(c)), `sanity ${view}`).toBe(false)
      expect(canAccessView('READ_ONLY', view), `READ_ONLY no debe ver ${view}`).toBe(false)
    }
    expect(viewsForRole('READ_ONLY')).toContain('DASHBOARD')
    expect(viewsForRole('READ_ONLY')).toContain('BOOKINGS')
  })

  it('BI_USER ve /exports (su propósito) y no /settings (que le daría 403)', () => {
    expect(JAVA.BI_USER.has('CAN_READ_REPORT')).toBe(true)
    expect(canAccessView('BI_USER', 'EXPORTS')).toBe(true)
    const manageCodes = VIEW_CODES.SETTINGS
    expect(manageCodes.some((c) => JAVA.BI_USER.has(c))).toBe(false)
    expect(canAccessView('BI_USER', 'SETTINGS')).toBe(false)
  })

  it('WAREHOUSE_ASSISTANT entra a /receipts y a nada más de gestión', () => {
    expect(canAccessView('WAREHOUSE_ASSISTANT', 'RECEIPTS')).toBe(true)
    expect(canAccessView('WAREHOUSE_ASSISTANT', 'DASHBOARD')).toBe(true)
    for (const view of ['BOOKINGS', 'LOAD_PLANNING', 'USERS', 'SETTINGS', 'SECURITY', 'EXPORTS']) {
      expect(canAccessView('WAREHOUSE_ASSISTANT', view), view).toBe(false)
    }
  })

  it('OPERATIONS y TRAFFIC conservan exactamente su alcance', () => {
    expect(viewsForRole('OPERATIONS').sort()).toEqual(['DASHBOARD', 'FLIGHTS', 'LOAD_PLANNING', 'MAWBS', 'ULDS'])
    expect(viewsForRole('TRAFFIC').sort()).toEqual(['BOOKINGS', 'DASHBOARD', 'LOAD_PLANNING', 'MAWBS', 'ULDS'])
  })
})

describe('rbac — rutas y consumidores', () => {
  it('toda vista de VIEW_CODES tiene al menos una ruta, y toda ruta protegida una vista del catálogo', () => {
    const routed = router.getRoutes().filter((r) => r.meta?.view).map((r) => r.meta.view)
    const known = [...Object.keys(VIEW_CODES), ...CLIENT_ONLY_VIEWS]
    for (const v of new Set(routed)) expect(known, `ruta con meta.view desconocido: ${v}`).toContain(v)
    for (const v of Object.keys(VIEW_CODES)) {
      expect(routed, `ninguna ruta usa la vista ${v}`).toContain(v)
    }
  })

  it('toda ruta tiene titleKey (document.title por vista)', () => {
    for (const r of router.getRoutes()) {
      if (r.redirect) continue // /home es un alias puro, no monta vista
      expect(r.name, `ruta sin nombre: ${r.path}`).toBeTruthy()
      expect(r.meta?.titleKey, `ruta ${r.path} sin titleKey`).toBeTruthy()
    }
  })

  it('las rutas públicas están declaradas y la comodín existe', () => {
    const paths = router.getRoutes().map((r) => r.path)
    for (const p of ['/login', '/set-password', '/change-password', '/mfa-setup', '/privacy']) {
      expect(paths, `falta la ruta pública ${p}`).toContain(p)
    }
    expect(paths.some((p) => p.includes('pathMatch'))).toBe(true)
  })

  it('el store delega en rbac: Sidebar y guard no pueden divergir', () => {
    for (const role of ROLES) {
      localStorage.setItem('aircargo_auth', JSON.stringify({ userId: 'u', selectedSiteId: 's', role }))
      // El store se cachea por pinia, así que hace falta una instancia nueva en
      // cada iteración (si no, repite el rol de la primera vuelta).
      setActivePinia(createPinia())
      const auth = useAuthStore()
      expect(auth.role).toBe(role)
      for (const view of Object.keys(VIEW_CODES)) {
        expect(auth.canView(view), `${role}/${view}`).toBe(canAccessView(role, view))
      }
    }
  })

  it('sin rol no se concede ninguna vista', () => {
    localStorage.setItem('aircargo_auth', JSON.stringify({ userId: 'u', selectedSiteId: 's' }))
    const auth = useAuthStore()
    for (const view of Object.keys(VIEW_CODES)) expect(auth.canView(view), view).toBe(false)
  })
})
