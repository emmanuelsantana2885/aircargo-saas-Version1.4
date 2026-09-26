/* Única fuente de verdad de "qué vistas puede abrir cada rol".
 *
 * Antes esta tabla estaba triplicada (router/index.js hasPermission, el switch
 * de stores/auth.js canView y por extensión el Sidebar). Cualquier edición en
 * un sitio y no en los demás producía navegación rota o, peor, un link visible
 * que el backend responde 403.
 *
 * La tabla NO es una invención del front: se deriva de los códigos CAN_* que el
 * backend reparte en
 *   backend/aircargo-auth-service/.../config/RolePermissionCatalog.java
 * y de los códigos de
 *   backend/aircargo-common/.../auth/Permissions.java
 * El test tests/rbac.spec.js lee ambos ficheros y falla si se desincronizan, así
 * que tocar el catálogo del backend obliga a tocar esta tabla (y viceversa).
 *
 * Regla: cada vista declara los códigos que la abren (semántica ANY). Si el
 * backend no da ninguno de esos códigos a un rol, el front tampoco le muestra
 * el link, aunque el router lo protegería igual.
 */

/* Vista -> códigos CAN_* que la abren (ANY). */
export const VIEW_CODES = {
  DASHBOARD: ['CAN_READ_DASHBOARD'],
  FLIGHTS: ['CAN_READ_FLIGHT'],
  MAWBS: ['CAN_READ_MAWB'],
  BOOKINGS: ['CAN_READ_BOOKING'],
  RECEIPTS: ['CAN_READ_RECEIPT'],
  ULDS: ['CAN_READ_ULD'],
  LOAD_PLANNING: ['CAN_READ_LOAD_PLAN'],
  USERS: ['CAN_MANAGE_USER'],
  SECURITY: ['CAN_READ_AUDIT'],
  EXPORTS: ['CAN_READ_REPORT'],
  // Configuración (aerolíneas, catálogo ULD, commodities, respaldos):
  // basta con poseer cualquiera de los permisos de administración de catálogo.
  SETTINGS: [
    'CAN_MANAGE_AIRLINE',
    'CAN_MANAGE_ULD_TYPE',
    'CAN_MANAGE_COMMODITY_TYPE',
    'CAN_MANAGE_BACKUP',
  ],
}

/* Vistas que no leen nada del backend (documentación estática embebida en el
 * bundle), así que el catálogo de permisos no aplica. */
export const CLIENT_ONLY_VIEWS = ['API_CATALOG']

/* Rol -> vistas visibles. SUPER_USER y ADMIN no se listan: ADMIN recibe
 * Permissions.allCodesAsList() menos CAN_MANAGE_SITE/CAN_MANAGE_LABEL_TEMPLATE,
 * lo que cubre las doce vistas. */
export const ROLE_VIEWS = {
  // El backend da lectura de dashboard/airline/flight/aircraft/booking/mawb/
  // uld/load_plan/bi/notification. NO da CAN_READ_RECEIPT ni CAN_READ_AUDIT ni
  // CAN_READ_REPORT ni ningún CAN_MANAGE_*: por eso no ve /receipts, /users,
  // /settings, /security ni /exports.
  READ_ONLY: [
    'DASHBOARD', 'FLIGHTS', 'MAWBS', 'BOOKINGS', 'ULDS',
    'LOAD_PLANNING', 'API_CATALOG',
  ],
  // El backend da CAN_READ_RECEIPT + escrituras de recibo. También puede leer
  // dashboard/flights/mawbs/ulds, pero el front mantiene el mínimo útil: la
  // operación de bodega vive en /receipts.
  WAREHOUSE_ASSISTANT: ['DASHBOARD', 'RECEIPTS'],
  // Coincide con el backend: vuelo/mawb/uld/load-planning, sin bookings ni
  // recibos.
  OPERATIONS: ['DASHBOARD', 'FLIGHTS', 'MAWBS', 'LOAD_PLANNING', 'ULDS'],
  TRAFFIC: ['DASHBOARD', 'BOOKINGS', 'MAWBS', 'LOAD_PLANNING', 'ULDS'],
  LOAD_PLANNER: ['DASHBOARD', 'FLIGHTS', 'LOAD_PLANNING', 'ULDS'],
  // El backend da CAN_READ_BI y CAN_READ_REPORT: /exports es literalmente su
  // vista. No da ningún CAN_MANAGE_*, así que /settings le daría 403.
  BI_USER: ['DASHBOARD', 'EXPORTS', 'API_CATALOG'],
}

export const ROLES = ['SUPER_USER', 'ADMIN', ...Object.keys(ROLE_VIEWS)]

/** ¿El rol puede abrir la vista? Falla cerrado ante una vista desconocida. */
export function canAccessView(role, view) {
  if (!role) return false
  if (role === 'SUPER_USER' || role === 'ADMIN') return true
  if (!view) return true // ruta sin meta.view: no hay vista que proteger
  if (CLIENT_ONLY_VIEWS.includes(view)) return true
  return (ROLE_VIEWS[role] || []).includes(view)
}

/** Todas las vistas de un rol (para navegación y pruebas). */
export function viewsForRole(role) {
  if (role === 'SUPER_USER' || role === 'ADMIN') {
    return [...Object.keys(VIEW_CODES), ...CLIENT_ONLY_VIEWS]
  }
  return [...(ROLE_VIEWS[role] || [])]
}
