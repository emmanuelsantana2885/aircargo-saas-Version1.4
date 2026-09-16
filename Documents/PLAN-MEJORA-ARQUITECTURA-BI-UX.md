# Plan de mejora: Arquitectura (módulos de negocio) + BI (ETL/Data Mart) + UI/UX (Dashboard y Quick Actions)

Fecha: Sep 12, 2026 · Proyecto: aircargo-saas Version1.3
Objetivo: reducir el acoplamiento del backend hacia módulos de negocio, convertir la capa de analítica a
`DB -> ETL -> Data Mart -> BI Tools`, y hacer un dashboard operativo con acciones rápidas por rol.

---

## 0. Diagnóstico real (basado en inventario de código, no en opiniones)

### 0.1 Estado actual deseable vs real

Los 10 servicios existentes ya cubren casi todos los módulos objetivo. El mapeo es:

| Módulo objetivo (pedido) | Servicio actual | Estado |
|---|---|---|
| Identity Service | `aircargo-auth-service` (9092) | ✅ existe (usuarios, sitios, roles, MFA, auditoría, RBAC) |
| Booking Service | `aircargo-booking-service` (9094) | ✅ existe |
| Flight Service | `aircargo-flight-service` (9093) | ✅ existe |
| Warehouse Service | `aircargo-warehouse-service` (9096) | ✅ existe |
| ULD Service | `aircargo-uld-service` (9097) | ✅ existe |
| **Import Service** | ❌ NO existe como servicio — la lógica vive en load-planning + frontend | ❌ GAP |
| Notification Service | `aircargo-notification-service` (9100) | ✅ existe |
| **Reporting Service** | `aircargo-export-service` (9099) | ⚠️ existe pero es "API sobre OLTP", no un Reporting/Data Mart |
| (Adicionales) | gateway (8080), mawb (9095), load-planning (9098) | 🔸 mawb/load-planning se absorben/reagrupan |

### 0.2 Puntos de acoplamiento reales (evidencia en código)

1. **load-planning es un orquestador con escrituras cruzadas en 4 servicios** (el peor acoplamiento):
   - `LoadPlanningServiceImpl.java:162` escribe `flight.status=DEPARTED`; `:170` `updateUld`; `:181` `updateMawbStatus(DEPARTED)`.
   - `LoadPlanningImportServiceImpl.java:112/122/157/178/196` crea ULDs, MAWBs, bookings y ULD-AWBs vía Feign.
   - `LoadPlanningBatchImportService.java:149/160/195/216/235` idéntico (batch) — dos importadores duplicados.
2. **booking y uld escriben en MAWB por Feign**: `BookingServiceImpl.java:248/267` (`createMawb`/`updateMawb`); `UldServiceImpl.java:365-367` (`updateMawbStatus MANIFESTED`).
3. **export-service lee las tablas OLTP de 8 servicios a través de 11 entidades read-only** mapeando `public.*`, con agregación en memoria en Java (`BiService`), caché de 60s. El schema `export_bi` está **vacío** (solo `CREATE SCHEMA`).
4. **BD compartida**: todos los servicios con BD apuntan a la misma `aircargo` (variables `POSTGRES_*`). Esquemas dedicados solo para `export_bi` y `notification`.
5. **RBAC duplicado**: `common/auth/Permissions.java` + `auth/RolePermissionCatalog.java` son el catálogo central, pero los 9-10 `SecurityConfig` repiten bloques `permissionMatcher` por servicio (conteo: 12/7/6/16/8/7/4/5/3 match-blocks).
6. **notification depende de auth por Feign** (`NotificationEventListener.java:47,71,94,113` → `authClient.getAllUsers()` fan-out).
7. **Import duplicado**: parseo XLSX de bookings está en frontend (`BookingsView.vue:493`), ramp manifest en `LoadPlanningImportServiceImpl` y batch en `LoadPlanningBatchImportService` — sin servicio único de importación.

### 0.3 Frontend (inventario)

- Dashboard 100% **solo-lectura** (tabs flights / weight-report, 3 KPIs) — sin ninguna Quick Action.
- Los flujos de creación existen pero están **dispersos**: `FlightsView.openCreate() :340`, `BookingsView.openCreate() :719`, `UldsView.createNewBlankUld() :954`, `WarehouseReceiptsView` emit :949 / `addHawbEntry() :1854`.
- Ya hay un patrón de navegación a flujos con query (`BookingsView.vue:669` → `/receipts?mawbId=`), y un `BottomNav` móvil con 4 accesos dinámicos por rol — reutilizables.
- 8 vistas usan `useLiveRefresh` (30s) → los datos están frescos al volver de una Quick Action.

---

## 1. Arquitectura: separación en módulos de negocio (fases)

Principio rector: **cada servicio es dueño de su esquema y de sus mutaciones**. Las escrituras cruzadas
pasan a ser asíncronas (comandos/eventos idempotentes); el contrato (DTOs/eventos) vive en librerías compartidas,
la infraestructura común (seguridad/caché/auditoría) se mantiene pero deja de ser monolítica en el acoplamiento.

### Fase A1 — Cerrar acoplamiento de escritura síncrono (prioridad alta, riesgo medio)
- **load-planning**: convertir las escrituras Feign en **comandos publicados a colas** (`flight.command.depart`,
  `uld.command.create/update`, `mawb.command.create/status`, `booking.command.create`) consumidas por el servicio dueño,
  con **outbox table** en el productor (transaccionalidad) y handlers idempotentes (clave de negocio, no UUID).
  Alternativa de menor inversión si el broker es demasiado: mantener Feign para *lectura* y mover solo los writes.
- **booking y uld**: `createMawb`/`updateMawb`/`updateMawbStatus` → publicar `mawb.command.*` (o agregar al modelo
  de eventos ya existente `mawb.*`); el mawb-service procesa y re-publica `mawb.updated`/`mawb.status.changed`
  (infraestructura AMQP ya probada en la sesión EDA).
- Definir **contratos de evento versionados** en `common/event` (record + schema JSON) y tests de compatibilidad
  (estilo `FeignContractSyncTest` para eventos).

### Fase A2 — Consolidar **Import Service** (nuevo módulo de negocio)
- Crear `aircargo-import-service` (puerto nuevo) que concentre: ramp manifest (POI), batch load-plan,
  import XLSX/CSV de bookings, validación por lotes, y **reportería de resultados de importación**.
- Los parsers de `LoadPlanningImportServiceImpl`/`LoadPlanningBatchImportService` se mueven aquí;
  load-planning queda como orquestador del plan (solo lectura + comandos). El XLSX de Bookings pasa de
  frontend a este servicio (subida de fichero → validación server-side → eventos `booking.command.*`).
- Antes del corte, mapear el contrato del wiz de imports actual para no romper UX.

### Fase A3 — Desacoplar la capa de datos compartida
- Corto plazo (sin tocar física): **esquemas por dominio** en el mismo Postgres
  (`cargo`, `operations`, `warehouse`, `identity`, `reporting`) con usuarios/grant por dominio —
  preserva la operativa actual y permite revocar accesos cruzados (solo BI leerá desde `reporting`).
- Medio plazo: **database-per-service** físico (volumen/compose por servicio) + `bi_reader` solo contra el
  Data Mart. Migrar de a una BD por servicio sin cortar operativa: general el Data Mart antes (sección 2).

### Fase A4 — Unificar RBAC (eliminar duplicación)
- Extraer un **`security-config-template`** en `common` (la lógica `permissionMatcher`/`grant`/matchers de
  `SecurityConfigTemplates` ya existe) para que cada servicio declara SOLO su mapa `ruta→código(s)`, y el
  template construya la cadena. Elimina la divergencia 12/7/6/16… y unifica el entry-point 401.
- Mover el fan-out de notification fuera de Feign: el notification-service suscribe a eventos `identity.user.upserted`
  (auth publica al crear/editar usuarios) en lugar de `getAllUsers()`.

### Fase A5 — Formalizar bounded contexts (documentación + gobierno)
- Documento DDD en `Documents/`: CargoContext (MAWB/HAWB/Booking/DUA), OperationsContext (Flight/ULD/LoadPlanning),
  WarehouseContext, IdentityContext, NotificationContext, ReportingContext. Cada contexto = DEPS permitidas +
  eventos producidos/consumidos + owners de tabla. Sirve de regla para futuras features.

**Ganancias esperadas A1-A5**: el único camino de mutación es vía evento/comando idempotente; un fallo de
un servicio no corrompe datos de otro; el esquema de cada contexto es revocable individualmente.

---

## 2. BI: `DB -> ETL -> Data Mart -> BI Tools`

### 2.1 Estado actual y gap
- Export-service = **API sobre OLTP**: 11 entidades read-only leen `public.*` en vivo, agregan en memoria Java
  (`BiService`, 12 endpoints), caché 60s; `export_bi` vacío; sin ETL (`@Scheduled` solo en auth).
- Conexiones BI externas ya operativas: rol `bi_reader` (lectura directa PG) y connector Apps Script
  `?api_key=` (Looker Studio) — pero ambas golpean el OLTP, no un mart.

### 2.2 Modelo objetivo (Data Mart en schema `export_bi`)
Star schema, granularidad por evento operativo:

| Tabla | Tipo | Granularidad | Fuente OLTP |
|---|---|---|---|
| `fact_receipts` | Fact | 1 fila por recibo (general + por MAWB) | `warehouse_receipt` + `receipt_piece` |
| `fact_dispatch_flight` | Fact | 1 fila por (ULD, vuelo) | `uld` + `uid_flight` + `flight` |
| `fact_uld_awb` | Fact | 1 fila por AWB embarcado | `uld_awb` |
| `fact_bookings` | Fact | 1 fila por booking | `booking` |
| `dim_mawb` | Dim | por MAWB/HAWB | `mawb` + `hawb` + `dua_record` |
| `dim_flight` | Dim | por vuelo | `flight` |
| `dim_airline` | Dim | por aerolínea | `airline` |
| `dim_site` / `dim_date` | Dim | por sitio / calendario (aaaa-mm-dd) | `site` / generada |
| `dim_commodity` | Dim | por commodity | enum OLTP |

- Contenido: agregados de **reporting** (pesos gross/tare/net lbs+kg, chargeable, piezas, posiciones, payload)
  y métricas operativas (recibido vs despachado, UAWB por vuelo, cumplimiento).
- Columnas de auditoría en cada fact: `etl_run_id`, `extracted_at`, `source_updated_at` (watermark).
- Sin PII: shipper/consignee se **pseudonimizan** en el mart (en línea con la retención de auditoría existente);
  los reports con detalle de cliente se sirven solo vía `/api/reports/**` con `CAN_READ_REPORT`.

### 2.3 ETL (dentro de export-service, o bien un pequeño `data-pipeline` si el volume crece)
- **Carga inicial**: job one-off que hace full-load de todas las tablas OLTP al mart (idempotente por
  `etl_run_id` + truncate-and-reload por tabla o por watermark).
- **Carga incremental**: `@Scheduled(fixedRate)` 5-10 min leyendo de cada tabla OLTP **exclusivamente
  `created_at/updated_at > watermark`** (tabla `etl_watermarks`), upsert en el mart. Idempotente: las filas
  del mart llevan `source_pk` único para REPLACE.
- **Actualización near-real-time**: suscripción a los eventos ya existentes (`receipt.created`,
  `flight.departed`, `mawb.updated`, `booking.awb.updated`) para refrescar la fact/dim afectada en el momento.
- Controles: tabla `etl_runs` (start/end, filas, estado), alerta en log + notificación si una corrida falla;
  `POST /api/bi/etl/run` (manual, ADMIN) y `GET /api/bi/etl/status` para gobierno.
- Migraciones Flyway en `export_bi` (creación de mart + índices + medidas).

### 2.4 Revisión de APIs BI expuestas (lo pedido: "revisa las APIs BI")
- **Conservar el contrato** de los 12 endpoints BI + Weight Report para el frontend, pero **respaldarlos por el
  Data Mart** (joins sobre fact/dim, no OLTP): más rápido y consistente con el snapshot ETL.
- **Retirar** las 11 entidades read-only de OLTP del export-service (se leen vía SQL del mart o Sinks).
- **Nuevo grupo `/api/bi/mart/**`** (tablas y metadatos del mart) para Looker Studio connector y para Superset
  por HTTP; Sin embargo, **Superset/Looker seguirán pudiendo leer el mart directo por `bi_reader`** (PG) —
  documento de catálogo de tablas + diccionario de medidas/indicadores en `bi-integrations/`.
- **Dashboard Builder**: cambiar `baseSource` para que elija tablas del **mart** (fact/dim) en vez de joins vivos
  `uld_awb+flight+airline+...`. Ventaja: mismo producto, fuente estable y performante.

### 2.5 Corte y herramientas BI
1. `V<next>` migraciones crean mart + agua marca; job full-load.
2. Verificar paridad medida a medida: `bi/dashboard`, `weight-report`, top-mawbs, flight-performance vs mart.
3. Re-apuntar connector Looker y Superset URI al `export_bi`.
4. Documentar en `bi-integrations/README.md`: catálogo, ETL, TTL de frescura, y roles (`bi_reader`),
   Opcional: `data-pipeline` como servicio separado + snapshot programado de nube.

---

## 3. UI/UX: Dashboard principal + Quick Actions

### 3.1 Dashboard operativo (evolución de `DashboardView.vue`)
- **Fila de KPI cards** ampliada por rol: vuelos activos hoy, MAWBs recibidos hoy, ULDs en rampa (llenado %),
  bookings pendientes de AWB, recargado por `useLiveRefresh` (ya existe).
- **Panel Quick Actions** (nuevo componente `QuickActionsPanel.vue`) en la parte superior del dashboard
  (desktop) y en el `BottomNav` móvil.
- **Actividad reciente**: mini-lista en vivo (últimos recibos/eventos) vía endpoints existentes.

Acciones rápidas por rol (mapeo a flujos ya existentes):

| Quick Action | Rol | Navegación / handler existente |
|---|---|---|
| Recibir carga (emitir recibo) | WAREHOUSE_ASSISTANT/ADMIN/SUPER_USER | `/receipts?mawbId=` (auto-expansión ya soportada) |
| Registrar HAWB de una MAWB | OPERATIONS/TRAFFIC/ADMIN | `/receipts?mawbId=&addHawb=1` → `addHawbEntry(m)` |
| Nuevo booking | TRAFFIC/ADMIN | `/bookings?new=1` → `openCreate()` |
| Registrar vuelo | OPERATIONS/ADMIN | `/flights?new=1` → `openCreate()` |
| Crear ULD | OPERATIONS/TRAFFIC/LOAD_PLANNER | `/ulds?new=1` → `createNewBlankUld()` |
| Importar manifiesto/ramp | OPERATIONS/TRAFFIC | `/load-planning?import=1` (futuro Import Service) |
| Exportar reporte | Con permiso `CAN_READ_BI` | `/exports` o acción de descarga del dashboard |

- Mecánica: `router.push` con query; las vistas ya leen query (`route.query.mawbId`) y llaman al handler
  después del mount (patrón de BookingsView:669). Sin duplicar lógica.
- Filtrado por permisos reales del backend: `auth.canView(view)` + códigos `Permissions` (CAN_RECEIPT_*,
  CAN_ULDS_* etc.) para mostrar solo lo autorizado.
- i18n es/en para el nuevo panel. Tokens del DS ya existen (ds-*), no se toca main.css más allá de variantes.

### 3.2 Resultado esperado
- El dashboard pasa de "solo lectura" a **centro de mando**: KPIs + acciones + actividad.
- El operador hace su día sin buscar botones por vista; en móvil las acciones viven en el BottomNav.

---

## 4. Roadmap sugerido (orden por valor/riesgo)

| # | Entregable | Esfuerzo (relativo) | Riesgo | Depende de |
|---|---|---|---|---|
| 1 | **Sección 3**: Dashboard + Quick Actions | S | Bajo | nada (frontend puro, handlers ya existen) |
| 2 | **Sección 2.3**: migración del export-service al Data Mart (mart + ETL + re-apuntar BI) | M | Medio | nada (aditivo; se pueden conservar los 12 endpoints) |
| 3 | **Sección 2.4-2.5**: revisión APIs BI, Dashboard Builder sobre mart, corte de Looker/Superset | M | Medio | 2 |
| 4 | **A1**: cerrar escrituras síncronas (outbox + comandos idempotentes) | L | Alto | contrato de eventos (Fase 1) |
| 5 | **A2**: crear Import Service | M-L | Medio | A1 (eventos de comando) |
| 6 | **A3**: esquemas por dominio → database-per-service | L | Alto | 2 (mart listo), 4-5 |
| 7 | **A4+A5**: RBAC unificado + bounded contexts doc | M | Bajo | — |

Recomendación: ejecutar **1** primero (valor visible inmediato y sin riesgo), luego **2-3** (BI)
y dejar **4-6** como fase de arquitectura con validación por eventos idempotentes.

---

## 5. Verificación por fase
- **UI/UX**: check:refs + lint + vitest + build; E2E CDP (harness existente) para cada Quick Action (navega y
  confirma que el handler abre el modal/flujo correcto y con datos frescos).
- **BI**: tests del ETL (full + incremental + idempotencia + watermark), comparación medida a medida del
  mart vs OLTP (query dual en CI/dev), jMeter básico de `/api/bi/**`, y verificaciones de `bi_reader`
  (SELECT permitido / INSERT denegado, igual que la prueba existente).
- **Arquitectura**: tests de contrato por evento (schema compat), `FeignContractSyncTest` existente extendido a
  comandos, y el invariante del guard `SecurityConfigConsistencyTest` para el template unificado.