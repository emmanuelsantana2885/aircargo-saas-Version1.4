# AirCargo Microservices Migration Plan
## Version 1.3 — Full Migration from Monolith to Microservices

**Date:** July 28, 2026
**Author:** AI Migration Assistant
**Status:** Phase 1 Complete (Gateway Hardening)

---

## Table of Contents

1. [Current State](#1-current-state)
2. [Target Architecture](#2-target-architecture)
3. [Phase 1: Gateway Hardening ✅](#3-phase-1-gateway-hardening)
4. [Phase 2: Auth Service Extraction](#4-phase-2-auth-service-extraction)
5. [Phase 3: Flight Service Extraction](#5-phase-3-flight-service-extraction)
6. [Phase 4: Booking Service Extraction](#6-phase-4-booking-service-extraction)
7. [Phase 5: MAWB Service Extraction](#7-phase-5-mawb-service-extraction)
8. [Phase 6: Warehouse Service Extraction](#8-phase-6-warehouse-service-extraction)
9. [Phase 7: ULD Service Extraction](#9-phase-7-uld-service-extraction)
10. [Phase 8: Load Planning Service Extraction](#10-phase-8-load-planning-service-extraction)
11. [Phase 9: Export Service Extraction](#11-phase-9-export-service-extraction)
12. [Phase 10: Notification Service](#12-phase-10-notification-service)
13. [Phase 11: Frontend Migration](#13-phase-11-frontend-migration)
14. [Phase 12: Delete Monolith](#14-phase-12-delete-monolith)
15. [Shared Infrastructure](#15-shared-infrastructure)
16. [Testing Strategy](#16-testing-strategy)
17. [Rollback Plan](#17-rollback-plan)

---

## 1. Current State

### Monolith: `aircargo-api` (port 9091)
- **25 controllers** with ~130+ endpoints
- **16 JPA entities** mapping to ~20 database tables
- **47 service classes**
- **17 repositories**
- **34 DTOs**
- **37 Flyway migrations** (V1–V37)
- **14 test classes** (72 tests)
- **1 shared PostgreSQL database** (`aircargo`)

### Already Implemented Microservices
- **auth-service** (9092): 37 Java files, fully mirrors monolith auth
- **flight-service** (9093): 10 Java files, fully mirrors monolith flights
- **gateway** (8080): Route config + JWT filter + rate limiting + circuit breaker (just hardened)

### Stub Services (empty `*Application.java` only)
- booking-service (9094)
- mawb-service (9095)
- warehouse-service (9096)
- uld-service (9097)
- load-planning-service (9098)
- export-service (9099)
- notification-service (9100)

---

## 2. Target Architecture

```
                    ┌─────────────────────────┐
                    │      Frontend (5173)     │
                    │   Vue 3 + Vite + Pinia   │
                    └──────────┬──────────────┘
                               │ All API calls via
                               ▼
                    ┌─────────────────────────┐
                    │   API Gateway (8080)      │
                    │  JWT · Rate Limit · CB    │
                    │  CORS · Access Log        │
                    └──────────┬──────────────┘
                               │ Routes by path
          ┌────────┬───────────┼───────────┬────────┐
          ▼        ▼           ▼           ▼        ▼
     ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
     │  Auth   │ │ Flight  │ │Booking  │ │  MAWB   │ │Warehouse│
     │  9092   │ │  9093   │ │  9094   │ │  9095   │ │  9096   │
     └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘
          │           │           │           │           │
          └─────┬─────┴─────┬─────┴─────┬─────┘           │
                ▼           ▼           ▼                  ▼
     ┌─────────────────────────────────────────────────────────┐
     │                    PostgreSQL (5432)                     │
     │  Schema per service: auth | flight | booking | mawb     │
     │                       warehouse | uld | load_planning    │
     └─────────────────────────────────────────────────────────┘
                               ▲
          ┌────────┬───────────┼───────────┬────────┐
          │        │           │           │        │
     ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
     │   ULD   │ │  Load   │ │ Export  │ │ Notif.  │ │Compliance│
     │  9097   │ │Planning │ │  9099   │ │  9100   │ │  9091   │
     └─────────┘ │  9098   │ └─────────┘ └─────────┘ └─────────┘
                 └─────────┘
```

### Communication Pattern
- **Synchronous**: REST via Spring Cloud OpenFeign (service-to-service)
- **Asynchronous**: RabbitMQ for events (receipt created → export, notification)
- **Data**: Database-per-service with no direct DB access between services

---

## 3. Phase 1: Gateway Hardening ✅ COMPLETE

### What Was Done
| Component | File | Purpose |
|-----------|------|---------|
| JWT Filter | `filter/JwtGatewayFilter.java` | Validates JWT at gateway, forwards user headers (X-User-Id, X-User-Email, X-User-Role, X-User-Airline-Id, X-User-Full-Name) |
| Rate Limiter | `filter/RateLimitFilter.java` | Per-user rate limiting (100 req/min) using Resilience4j |
| Access Logger | `filter/AccessLogFilter.java` | Logs all requests with method, path, status, user, duration |
| Circuit Breaker | `config/GatewayConfig.java` | Per-service circuit breaker config with fallback URIs |
| Route Config | `config/RouteConfig.java` | Programmatic routes with CB + retry per service |
| Fallback | `fallback/FallbackController.java` | 503 responses when circuit is open |
| Fallback Filter | `fallback/CircuitBreakerFallbackFilter.java` | Catches reactive errors and returns 503 |
| Properties | `application.properties` | All Resilience4j, CORS, JWT, actuator configs |

### Gateway Routes (10 routes)
| Route | Path Predicate | URI | Circuit Breaker |
|-------|---------------|-----|----------------|
| auth-service | `/api/auth/**, /api/users/**, /api/audit-logs/**, /api/sites/**, /api/role-permissions/**` | localhost:9092 | auth-service |
| flight-service | `/api/flights/**, /api/airlines/**, /api/aircraft-types/**` | localhost:9093 | flight-service |
| booking-service | `/api/bookings/**` | localhost:9094 | booking-service |
| mawb-service | `/api/cargo/mawbs/**, /api/cargo/hawbs/**, /api/tracking/**, /api/mawbs/**` | localhost:9095 | mawb-service |
| warehouse-service | `/api/warehouse/**, /api/receipts/**` | localhost:9096 | warehouse-service |
| uld-service | `/api/ulds/**, /api/uld-awbs/**, /api/uld-type-config/**, /api/scan/**` | localhost:9097 | uld-service |
| load-planning-service | `/api/load-planning/**, /api/cargo/flights/**` | localhost:9098 | load-planning-service |
| export-service | `/api/exports/**, /api/bi/**, /api/reports/**` | localhost:9099 | export-service |
| compliance-service | `/api/compliance/**` | localhost:9091 | compliance-service |
| api-fallback | `/api/**` (catch-all) | localhost:9091 | default |

---

## 4. Phase 2: Auth Service Extraction

**Status:** ✅ Already implemented (37 files in `aircargo-auth-service`)

### What Needs To Be Done
1. **Sync any missing logic** from monolith's auth controllers into auth-service
2. **Add Feign client** in other services to call auth-service for user validation
3. **Remove auth code from monolith** after migration is complete
4. **Update gateway routes** to point auth-service routes to port 9092

### Files in auth-service (already exist)
```
controller/
  AuthController.java          # POST /login, POST /set-password, POST /change-password, GET /me, GET /heartbeat
  AppUserController.java       # CRUD, reset-password, connected, MFA
  SiteController.java          # CRUD for sites
  AuditLogController.java      # GET /audit-logs
  RolePermissionController.java # CRUD for role permissions
entity/
  AppUser.java, Site.java, AuditLog.java, UserRole.java, RolePermission.java, ViewPermission.java
service/
  AppUserService.java, AppUserServiceImpl.java, SiteService.java, AuditService.java,
  ActiveSessionTracker.java, PermissionService.java, MfaService.java, RolePermissionService.java
repository/
  AppUserRepository.java, SiteRepository.java, AuditLogRepository.java, ViewPermissionRepository.java, RolePermissionRepository.java
dto/
  LoginRequest.java, LoginResponse.java, SetPasswordRequest.java, ChangePasswordRequest.java,
  AppUserDTO.java, SiteDTO.java, AuditLogDTO.java, ConnectedUserDTO.java, RolePermissionDTO.java, etc.
config/
  SecurityConfig.java
```

### Database Schema (auth schema)
```sql
-- Tables owned by auth-service:
app_user, site, user_sites, audit_log, role_permission, view_permission

-- Flyway migrations to move: V1, V8, V9, V10, V11, V15, V16, V17, V18, V19, V20, V26, V28, V29, V31
```

### Cross-Service Dependencies
- **Auth is a dependency for ALL other services** — they need to validate users/roles
- **Solution**: Other services call `GET /api/users/{id}` or `GET /api/auth/validate` via Feign to check permissions
- **Alternative**: JWT token already contains role — services just need to trust the `X-User-Role` header forwarded by gateway

### Effort Estimate: 2-3 days

---

## 5. Phase 3: Flight Service Extraction

**Status:** ✅ Already implemented (10 files in `aircargo-flight-service`)

### What Needs To Be Done
1. **Sync FlightController changes** from monolith (audit logging, OpenAPI annotations)
2. **Sync AirlineController** from monolith into flight-service
3. **Add FlightRepository** from monolith (with cache config)
4. **Add AirlineService/AirlineRepository** from monolith
5. **Add Flyway migrations** for flight tables
6. **Add Caffeine cache config**
7. **Test full CRUD** through gateway

### Files To Migrate From Monolith
```
controller/FlightController.java     → already in flight-service, update with audit logging
controller/AirlineController.java    → move to flight-service
entity/Flight.java                   → already in flight-service
entity/FlightStatus.java             → already in flight-service
entity/Airline.java                  → move to flight-service (currently in common!)
entity/AircraftType.java             → move to flight-service (currently in common!)
service/FlightService.java           → already in flight-service
service/FlightServiceImpl.java       → already in flight-service
service/AirlineService.java          → move to flight-service
service/AirlineServiceImpl.java      → move to flight-service
repository/FlightRepository.java     → move to flight-service
repository/AirlineRepository.java    → move to flight-service
dto/FlightDTO.java                   → move to flight-service
dto/AirlineDTO.java                  → move to flight-service
```

### Database Schema (flight schema)
```sql
-- Tables owned by flight-service:
flight, airline, uld_type_config

-- Flyway migrations to move: V2 (AAD enum), V12 (seed airlines), V33 (uld_awb rename), V34 (computed columns)
```

### Cross-Service Dependencies
- **Booking service** needs flight data (flight number, date, capacity)
- **ULD service** needs flight data (for ULD assignment)
- **Load planning service** needs flight data (for manifest)
- **Solution**: Feign clients `FlightClient` in dependent services

### Effort Estimate: 2-3 days

---

## 6. Phase 4: Booking Service Extraction

**Status:** Stub only (`BookingServiceApplication.java`)

### Files To Migrate From Monolith
```
controller/BookingController.java
entity/Booking.java
service/BookingService.java
service/BookingServiceImpl.java
repository/BookingRepository.java
dto/BookingDTO.java
dto/BookingAwbUpdateRequest.java
dto/PageResponse.java
event/BookingConfirmedEvent.java    → convert to RabbitMQ event
```

### Database Schema (booking schema)
```sql
-- Tables owned by booking-service:
booking

-- Foreign keys to resolve:
-- booking.airline_id → flight-service.airline.id (cross-service lookup via Feign)
-- booking.flight_id → flight-service.flight.id (cross-service lookup via Feign)
-- booking.mawb_id → mawb-service.mawb.id (cross-service lookup via Feign)
```

### Cross-Service Dependencies
- **Reads from**: flight-service (airline, flight), mawb-service (mawb for status)
- **Writes to**: none directly (other services read from booking)
- **Publishes events**: `BookingConfirmedEvent` → RabbitMQ → notification service

### Feign Clients Needed
```java
@FeignClient(name = "flight-service", url = "${flight-service.url:http://localhost:9093}")
public interface FlightClient {
    @GetMapping("/api/flights/{id}")
    FlightDTO getFlight(@PathVariable UUID id);
    
    @GetMapping("/api/airlines/{id}")
    AirlineDTO getAirline(@PathVariable UUID id);
}
```

### Gateway Route Update
```properties
# Already configured: booking-service → localhost:9094, path /api/bookings/**
```

### Effort Estimate: 3-4 days

---

## 7. Phase 5: MAWB Service Extraction

**Status:** Stub only (`MawbServiceApplication.java`)

### Files To Migrate From Monolith
```
controller/MawbController.java
controller/HawbController.java
controller/MawbTrackingController.java
entity/Mawb.java
entity/Hawb.java
entity/MawbStatus.java              → enum
service/MawbService.java
service/MawbServiceImpl.java
service/MawbValidationService.java
service/MawbTrackingService.java
service/HawbService.java
service/HawbServiceImpl.java
service/HawbValidationService.java
repository/MawbRepository.java
repository/HawbRepository.java
dto/MawbDTO.java
dto/HawbDTO.java
dto/ScanLookupDTO.java              → move scan lookup here or keep in ULD service
event/FlightCreatedEvent.java       → convert to RabbitMQ
```

### Database Schema (mawb schema)
```sql
-- Tables owned by mawb-service:
mawb, hawb

-- Foreign keys to resolve:
-- mawb.airline_id → flight-service.airline.id
-- mawb.flight_id → flight-service.flight.id
-- hawb.mawb_id → self (mawb table, same schema)
-- hawb.airline_id → flight-service.airline.id
```

### Cross-Service Dependencies
- **Reads from**: flight-service (flight details), booking-service (booking status for MAWB)
- **Writes to**: warehouse-service reads mawb data, ULD service links to mawb
- **Publishes events**: `MawbStatusChangedEvent` → RabbitMQ

### Effort Estimate: 4-5 days

---

## 8. Phase 6: Warehouse Service Extraction

**Status:** Stub only (`WarehouseServiceApplication.java`)

**This is the most complex service.** Contains receipt creation, correction/superseding logic, evidence PDF/HTML generation, Excel export, and signature handling.

### Files To Migrate From Monolith
```
controller/WarehouseController.java
controller/WarehouseReceiptController.java
entity/WarehouseReceipt.java
entity/ReceiptPiece.java
service/WarehouseService.java           ← COMPLEX: receipt emit, correction, evidence PDF/HTML
service/WarehouseReceiptService.java
service/WarehouseReceiptServiceImpl.java
service/ReceiptExportService.java       ← Excel export (XLSX)
service/ReceiptFullPdfService.java      ← Full receipt PDF
service/PdfGenerationService.java       ← HTML-to-PDF (openhtmltopdf)
service/EvidenceSheetRenderer.java      ← Evidence sheet rendering
service/ExcelExportStyles.java          ← Excel styling
repository/WarehouseReceiptRepository.java
repository/ReceiptPieceRepository.java
dto/WarehouseReceiptDTO.java
dto/ReceiptPieceDTO.java
```

### Database Schema (warehouse schema)
```sql
-- Tables owned by warehouse-service:
warehouse_receipt, receipt_piece

-- Foreign keys to resolve:
-- warehouse_receipt.airline_id → flight-service.airline.id
-- warehouse_receipt.mawb_id → mawb-service.mawb.id
-- warehouse_receipt.created_by_user_id → auth-service.app_user.id
-- warehouse_receipt.correction_of_id → self (warehouse_receipt table)
-- receipt_piece.hawb_id → mawb-service.hawb.id
```

### Cross-Service Dependencies
- **Reads from**: mawb-service (mawb data for receipt creation), auth-service (user info)
- **Writes to**: none (receipts are final)
- **Publishes events**: `ReceiptCreatedEvent` → RabbitMQ → export-service, notification-service
- **External calls**: None for CRUD, but evidence PDF uses `PdfGenerationService`

### Special Considerations
- `WarehouseService.processWarehouseReceipt()` is ~300 lines and does: MAWB lookup, receipt creation, piece creation, superseding old receipts, booking sync, async artifact generation
- **Booking sync** (`updateBooking.awbNumber`) currently calls `BookingRepository` directly — needs to become a Feign call to booking-service
- **Excel/PDF artifact generation** runs async after commit — can stay in warehouse-service (no cross-service dependency)

### Effort Estimate: 5-7 days (most complex)

---

## 9. Phase 7: ULD Service Extraction

**Status:** Stub only (`UldServiceApplication.java`)

### Files To Migrate From Monolith
```
controller/UldController.java
controller/UldAwbController.java
controller/UldTypeConfigController.java
controller/ScanController.java
entity/Uld.java
entity/UldAwb.java
entity/UldPiece.java
entity/UldStatus.java                  → enum
entity/UldType.java                    → enum
entity/PieceSource.java                → enum
service/UldService.java
service/UldServiceImpl.java
service/UldAwbService.java
service/UldAwbServiceImpl.java
service/ScanService.java               ← barcode scan logic, piece registration
repository/UldRepository.java
repository/UldAwbRepository.java
repository/UldPieceRepository.java
repository/UldTypeConfigRepository.java
dto/UldDTO.java
dto/UldAwbDTO.java
dto/ScanLookupDTO.java
dto/ScanPieceRequest.java
dto/ScanPieceResult.java
dto/TransferRequest.java
config/ScanEventListener.java          ← SSE event broadcast
```

### Database Schema (uld schema)
```sql
-- Tables owned by uld-service:
uld, uld_awb, uld_piece, uld_type_config

-- Foreign keys to resolve:
-- uld.airline_id → flight-service.airline.id
-- uld.flight_id → flight-service.flight.id
-- uld_awb.uld_id → self (uld table, same schema)
-- uld_awb.mawb_id → mawb-service.mawb.id
-- uld_piece.uld_id → self (uld table, same schema)
-- uld_piece.mawb_id → mawb-service.mawb.id
```

### Cross-Service Dependencies
- **Reads from**: flight-service (flight data), mawb-service (mawb data for scan lookup)
- **Writes to**: none (ULDs are standalone)
- **Special**: SSE (Server-Sent Events) for real-time scan updates — needs WebSocket/SSE support in gateway

### Effort Estimate: 4-5 days

---

## 10. Phase 8: Load Planning Service Extraction

**Status:** Stub only (`LoadPlanningServiceApplication.java`)

### Files To Migrate From Monolith
```
controller/LoadPlanningController.java
controller/FlightManifestController.java
service/LoadPlanningService.java
service/LoadPlanningServiceImpl.java
service/LoadPlanningImportService.java
service/LoadPlanningImportServiceImpl.java    ← Apache POI Excel parsing
service/LoadPlanningBatchImportService.java
service/LoadPlanningExportService.java
service/LoadPlanningValidationService.java
service/RampManifestParserService.java
service/FlightManifestService.java
dto/LoadPlanningDTO.java
dto/LoadPlanningUldDTO.java
dto/LoadPlanningImportResultDTO.java
dto/LoadPlanningBatchImportResultDTO.java
dto/LoadPlanningSheetImportResultDTO.java
dto/FlightLoadPlanDTO.java
```

### Database Schema
```sql
-- No own tables! Load planning is computed from:
-- uld-service: uld, uld_awb
-- flight-service: flight
-- mawb-service: mawb, hawb

-- This service is STATELESS — it reads data from other services via Feign
```

### Cross-Service Dependencies
- **Reads from**: flight-service (flight data), uld-service (ULD data + AWB links), mawb-service (mawb data)
- **Writes to**: none (load plan is computed, not persisted)
- **Special**: Uses Apache POI for Excel import/export

### Effort Estimate: 3-4 days

---

## 11. Phase 9: Export Service Extraction

**Status:** Stub only (`ExportServiceApplication.java`)

### Files To Migrate From Monolith
```
controller/ExportController.java
controller/BiController.java
controller/ReportController.java
controller/CatalogController.java
service/ExportService.java           ← CSV/JSON/Excel data export
service/BiService.java               ← BI aggregation queries
service/ReceiptExportService.java    ← audit export with receipt data
```

### Database Schema
```sql
-- No own tables! Export/BI service is STATELESS — reads from all other services via Feign

-- Cross-service reads:
-- flight-service: flights
-- booking-service: bookings
-- mawb-service: mawbs, hawbs
-- warehouse-service: receipts, pieces
-- uld-service: ulds, uld_awbs
```

### Cross-Service Dependencies
- **Reads from**: ALL other services (aggregation service)
- **Writes to**: none (read-only)
- **Special**: Needs Feign clients for every service; complex SQL aggregation may need to be done in application layer

### Effort Estimate: 4-5 days

---

## 12. Phase 10: Notification Service

**Status:** Stub only (`NotificationServiceApplication.java`)

### New Files To Create
```
controller/NotificationController.java
entity/Notification.java
entity/NotificationType.java           → enum (EMAIL, WEBSOCKET, WEBHOOK)
service/NotificationService.java
service/EmailNotificationService.java
service/WebSocketNotificationService.java
repository/NotificationRepository.java
dto/NotificationDTO.java
listener/ReceiptEventListener.java     ← listens to RabbitMQ events
listener/BookingEventListener.java     ← listens to RabbitMQ events
listener/FlightEventListener.java      ← listens to RabbitMQ events
```

### Database Schema (notification schema)
```sql
-- Tables owned by notification-service:
notification

CREATE TABLE notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    entity_type VARCHAR(50),
    entity_id UUID,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT now()
);
```

### Cross-Service Dependencies
- **Reads from**: auth-service (user email for notifications)
- **Subscribes to**: RabbitMQ events from all services
- **Writes to**: notification table only

### Effort Estimate: 3-4 days

---

## 13. Phase 11: Frontend Migration

### What Changes
Before migration the frontend called `localhost:9091` (monolith). After migration, ALL calls go through gateway at `localhost:8080`.

### Vite Proxy Update
```javascript
// frontend/vite.config.js
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // Gateway instead of monolith
        changeOrigin: true
      }
    }
  }
})
```

### Docker Compose Update
```yaml
# Frontend nginx config: proxy_pass http://gateway:8080
```

### No API Code Changes Needed
All frontend API files (`api/flights.js`, `api/bookings.js`, etc.) use paths like `/api/flights/` which the gateway routes correctly. **No frontend code changes needed** — only the proxy target changes.

---

## 14. Phase 12: Delete Monolith

### Steps
1. Verify ALL endpoints work through gateway → each microservice
2. Run full test suite against gateway (not monolith)
3. Update `docker-compose.services.yml` to remove `aircargo-api` service
4. Update `k8s/aircargo-api.yml` → remove or convert to legacy-compat service
5. Remove `backend/aircargo-api/` directory
6. Update parent `pom.xml` to remove `aircargo-api` module
7. Keep `aircargo-common` as shared library (used by all services)

---

## 15. Shared Infrastructure

### Database Strategy: Schema-Per-Service (Single PostgreSQL)
All services share one PostgreSQL instance but use **separate schemas**:

```sql
CREATE SCHEMA auth;
CREATE SCHEMA flight;
CREATE SCHEMA booking;
CREATE SCHEMA mawb;
CREATE SCHEMA warehouse;
CREATE SCHEMA uld;
CREATE SCHEMA load_planning;
CREATE SCHEMA export_bi;
CREATE SCHEMA notification;
```

Each service's `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/aircargo?currentSchema=warehouse
```

### Inter-Service Communication
| Pattern | Technology | Use Case |
|---------|-----------|----------|
| Sync REST | Spring Cloud OpenFeign | Service-to-service queries (get flight by ID, get mawb by ID) |
| Async Events | RabbitMQ | Cross-service notifications (receipt created, booking confirmed) |
| SSE | Spring WebFlux | Real-time UI updates (scan events) |

### RabbitMQ Exchange Configuration
```yaml
exchanges:
  aircargo.events:
    type: topic
    bindings:
      - routing-key: receipt.created
      - routing-key: booking.confirmed
      - routing-key: flight.created
      - routing-key: mawb.status.changed
      - routing-key: uld.transferred
```

---

## 16. Testing Strategy

### Per-Service Testing
1. **Unit tests**: Service layer with Mockito (same pattern as monolith)
2. **Integration tests**: `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers PostgreSQL
3. **Contract tests**: Spring Cloud Contract for Feign client validation
4. **E2E tests**: Run all services via Docker Compose, test through gateway

### Test Infrastructure
```yaml
# docker-compose.test.yml
services:
  postgres-test:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: aircargo_test
    tmpfs: /var/lib/postgresql/data  # In-memory for speed
  
  rabbitmq-test:
    image: rabbitmq:4.0-management-alpine
```

---

## 17. Rollback Plan

### Strategy: Strangler Fig Pattern
- Keep monolith running on port 9091 throughout migration
- Gateway routes NEW traffic to microservices
- Gateway FALLBACK routes to monolith if service is down
- After ALL services are migrated, remove monolith

### Per-Phase Rollback
1. If a service extraction fails: revert gateway route to point to monolith
2. If database migration fails: restore from backup
3. If Feign calls fail: circuit breaker returns cached/fallback data

---

## Migration Priority Order

| Priority | Service | Complexity | Dependencies | Estimate |
|----------|---------|-----------|--------------|----------|
| 1 | Auth | Low | None (standalone) | 2-3 days |
| 2 | Flight | Low | Auth (for airline) | 2-3 days |
| 3 | Booking | Medium | Auth, Flight, MAWB | 3-4 days |
| 4 | MAWB | Medium | Auth, Flight | 4-5 days |
| 5 | ULD | Medium | Auth, Flight, MAWB | 4-5 days |
| 6 | Warehouse | High | Auth, MAWB, Booking | 5-7 days |
| 7 | Load Planning | Medium | Flight, ULD, MAWB | 3-4 days |
| 8 | Export/BI | Medium | ALL services | 4-5 days |
| 9 | Notification | Low | Auth (RabbitMQ) | 3-4 days |

**Total estimated effort: 30-40 working days (6-8 weeks)**

---

## Execution Order

```
Week 1-2:  Phase 2 (Auth) + Phase 3 (Flight) + gateway integration test
Week 3-4:  Phase 4 (Booking) + Phase 5 (MAWB) + Feign client library
Week 5-6:  Phase 6 (Warehouse) + Phase 7 (ULD) + RabbitMQ setup
Week 7:    Phase 8 (Load Planning) + Phase 9 (Export/BI)
Week 8:    Phase 10 (Notification) + Phase 11 (Frontend) + Phase 12 (Delete monolith)
```

---

## Appendix A: Feign Client Library (`aircargo-feign-clients`)

Create a new shared module with all Feign clients:

```java
// In aircargo-feign-clients module

@FeignClient(name = "auth-service", url = "${auth-service.url:http://localhost:9092}")
public interface AuthClient {
    @GetMapping("/api/users/{id}")
    AppUserDTO getUser(@PathVariable UUID id);
    
    @GetMapping("/api/users")
    List<AppUserDTO> getAllUsers();
}

@FeignClient(name = "flight-service", url = "${flight-service.url:http://localhost:9093}")
public interface FlightClient {
    @GetMapping("/api/flights/{id}")
    FlightDTO getFlight(@PathVariable UUID id);
    
    @GetMapping("/api/flights")
    List<FlightDTO> getAllFlights();
    
    @GetMapping("/api/airlines/{id}")
    AirlineDTO getAirline(@PathVariable UUID id);
}

@FeignClient(name = "mawb-service", url = "${mawb-service.url:http://localhost:9095}")
public interface MawbClient {
    @GetMapping("/api/cargo/mawbs/{id}")
    MawbDTO getMawb(@PathVariable UUID id);
    
    @GetMapping("/api/cargo/mawbs/awb/{awbNumber}")
    MawbDTO getMawbByAwbNumber(@PathVariable String awbNumber);
}

@FeignClient(name = "booking-service", url = "${booking-service.url:http://localhost:9094}")
public interface BookingClient {
    @GetMapping("/api/bookings/{id}")
    BookingDTO getBooking(@PathVariable UUID id);
    
    @GetMapping("/api/bookings/mawb/{mawbId}")
    BookingDTO getBookingByMawbId(@PathVariable UUID mawbId);
    
    @PatchMapping("/api/bookings/{id}/awb")
    void updateBookingAwb(@PathVariable UUID id, @RequestBody BookingAwbUpdateRequest request);
}

@FeignClient(name = "uld-service", url = "${uld-service.url:http://localhost:9097}")
public interface UldClient {
    @GetMapping("/api/ulds/{id}")
    UldDTO getUld(@PathVariable UUID id);
    
    @GetMapping("/api/ulds/flight/{flightId}")
    List<UldDTO> getUldsByFlight(@PathVariable UUID flightId);
}
```

---

## Appendix B: Docker Compose Update

```yaml
# docker-compose.services.yml (updated)

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: aircargo
      POSTGRES_USER: aircargo_user
      POSTGRES_PASSWORD: aircargo_pass_2024
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  rabbitmq:
    image: rabbitmq:4.0-management-alpine
    ports:
      - "5672:5672"
      - "15672:15672"

  gateway:
    build: backend/aircargo-gateway
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - rabbitmq
    environment:
      JWT_SECRET: ${JWT_SECRET}
      POSTGRES_HOST: postgres

  auth-service:
    build: backend/aircargo-auth-service
    ports:
      - "9092:9092"
    depends_on:
      - postgres
    environment:
      POSTGRES_HOST: postgres
      POSTGRES_DB: aircargo
      POSTGRES_SCHEMA: auth

  flight-service:
    build: backend/aircargo-flight-service
    ports:
      - "9093:9093"
    depends_on:
      - postgres
    environment:
      POSTGRES_HOST: postgres
      POSTGRES_DB: aircargo
      POSTGRES_SCHEMA: flight

  booking-service:
    build: backend/aircargo-booking-service
    ports:
      - "9094:9094"
    depends_on:
      - postgres
      - flight-service
      - mawb-service
    environment:
      POSTGRES_HOST: postgres
      POSTGRES_DB: aircargo
      POSTGRES_SCHEMA: booking
      FLIGHT_SERVICE_URL: http://flight-service:9093
      MAWB_SERVICE_URL: http://mawb-service:9095

  mawb-service:
    build: backend/aircargo-mawb-service
    ports:
      - "9095:9095"
    depends_on:
      - postgres
      - flight-service
    environment:
      POSTGRES_HOST: postgres
      POSTGRES_DB: aircargo
      POSTGRES_SCHEMA: mawb
      FLIGHT_SERVICE_URL: http://flight-service:9093

  warehouse-service:
    build: backend/aircargo-warehouse-service
    ports:
      - "9096:9096"
    depends_on:
      - postgres
      - mawb-service
      - booking-service
      - auth-service
    environment:
      POSTGRES_HOST: postgres
      POSTGRES_DB: aircargo
      POSTGRES_SCHEMA: warehouse
      MAWB_SERVICE_URL: http://mawb-service:9095
      BOOKING_SERVICE_URL: http://booking-service:9094
      AUTH_SERVICE_URL: http://auth-service:9092

  uld-service:
    build: backend/aircargo-uld-service
    ports:
      - "9097:9097"
    depends_on:
      - postgres
      - flight-service
      - mawb-service
    environment:
      POSTGRES_HOST: postgres
      POSTGRES_DB: aircargo
      POSTGRES_SCHEMA: uld
      FLIGHT_SERVICE_URL: http://flight-service:9093
      MAWB_SERVICE_URL: http://mawb-service:9095

  load-planning-service:
    build: backend/aircargo-load-planning-service
    ports:
      - "9098:9098"
    depends_on:
      - flight-service
      - uld-service
      - mawb-service
    environment:
      FLIGHT_SERVICE_URL: http://flight-service:9093
      ULD_SERVICE_URL: http://uld-service:9097
      MAWB_SERVICE_URL: http://mawb-service:9095

  export-service:
    build: backend/aircargo-export-service
    ports:
      - "9099:9099"
    depends_on:
      - postgres
      - rabbitmq
    environment:
      POSTGRES_HOST: postgres
      POSTGRES_DB: aircargo
      RABBITMQ_HOST: rabbitmq
      FLIGHT_SERVICE_URL: http://flight-service:9093
      BOOKING_SERVICE_URL: http://booking-service:9094
      MAWB_SERVICE_URL: http://mawb-service:9095
      WAREHOUSE_SERVICE_URL: http://warehouse-service:9096
      ULD_SERVICE_URL: http://uld-service:9097

  notification-service:
    build: backend/aircargo-notification-service
    ports:
      - "9100:9100"
    depends_on:
      - postgres
      - rabbitmq
      - auth-service
    environment:
      POSTGRES_HOST: postgres
      POSTGRES_DB: aircargo
      POSTGRES_SCHEMA: notification
      RABBITMQ_HOST: rabbitmq
      AUTH_SERVICE_URL: http://auth-service:9092

  frontend:
    build:
      context: frontend
      dockerfile: Dockerfile
    ports:
      - "80:80"
      - "443:443"
    depends_on:
      - gateway
    environment:
      VITE_API_BASE_URL: http://gateway:8080

volumes:
  pgdata:
```

---

## Appendix C: Kubernetes ConfigMap Update

```yaml
# k8s/configmap.yml (updated)

apiVersion: v1
kind: ConfigMap
metadata:
  name: aircargo-config
  namespace: aircargo
data:
  POSTGRES_HOST: "postgres"
  POSTGRES_PORT: "5432"
  POSTGRES_DB: "aircargo"
  RABBITMQ_HOST: "rabbitmq"
  RABBITMQ_PORT: "5672"
  AUTH_SERVICE_URL: "http://auth-service:9092"
  FLIGHT_SERVICE_URL: "http://flight-service:9093"
  BOOKING_SERVICE_URL: "http://booking-service:9094"
  MAWB_SERVICE_URL: "http://mawb-service:9095"
  WAREHOUSE_SERVICE_URL: "http://warehouse-service:9096"
  ULD_SERVICE_URL: "http://uld-service:9097"
  LOAD_PLANNING_SERVICE_URL: "http://load-planning-service:9098"
  EXPORT_SERVICE_URL: "http://export-service:9099"
  NOTIFICATION_SERVICE_URL: "http://notification-service:9100"
```

---

*End of Migration Plan*
