# Runbook de despliegue en Render (Aircargo SaaS)

Guía paso a paso para desplegar el stack completo en **Render** usando el Blueprint
(`render.yaml`) y **migrar la base de datos actual** (Postgres local) al Postgres de Render.

> Arquitectura desplegada: **1 Postgres** (Render) + **1 Key Value** (Render, opcional)
> + **1 Environment Group** de secretos + **10 Web Services Docker** + **1 Static Site** (frontend).
> RabbitMQ es **externo** (CloudAMQP free) porque Render no ofrece AMQP gestionado.

---

## 0. Antes de empezar — requisitos

| Requisito | Detalle |
|-----------|---------|
| Cuenta Render | https://dashboard.render.com — con acceso a Blueprints y Docker |
| Repo en GitHub | `git@github.com:emmanuelsantana2885/aircargo-saas-Version1.4.git` (rama `main`) |
| Broker AMQP externo | **CloudAMQP** free ("Little Lemur") → https://cloudamqp.com |
| `pg_dump` / `pg_restore` / `psql` locales | Ya instalados en la máquina de desarrollo |
| Postgres local corriendo | `127.0.0.1:5432`, DB `aircargo`, user `aircargo_user` |

### 0.1 Crear el broker CloudAMQP (PASO MANUAL OBLIGATORIO)

1. Entra a https://cloudamqp.com y crea una instancia **Little Lemur (free)** en la región más cercana.
2. Copia la **AMQP URL**, con la forma `amqps://usuario:password@host/vhost`.
3. Desglósala en 4 valores (los usarás en el paso 3.2):

   | Variable | Valor (ejemplo) |
   |----------|-----------------|
   | `RABBITMQ_HOST` | `host` (sin `amqps://`) |
   | `RABBITMQ_PORT` | `5671` (TLS; CloudAMQP free usa amqps) |
   | `RABBITMQ_USER` | `usuario` |
   | `RABBITMQ_PASSWORD` | `password` |

   > Si tu broker exige TLS (CloudAMQP **sí**), añade también `RABBITMQ_SSL=true` en cada
   > servicio con AMQP. Render lo pide junto con los demás en el primer Apply.

---

## 1. Copia / migración de la base de datos actual

La BD local tiene los datos reales (usuarios, MAWBs, bookings, etc.) repartidos en
**esquemas por servicio** (`auth`, `flight`, `booking`, `mawb`, `warehouse`, `uld`,
`notification`, `export_bi`, `public`). Hay que llevarla a Render Postgres **antes**
de que los servicios arranquen, para que sus migraciones Flyway vean el historial ya
aplicado y no re-ejecuten nada.

### 1.1 Generar el dump de la BD actual (en tu máquina)

```sh
# Desde la raíz del repo (usa scripts/db-backup.sh → pg_dump custom comprimido)
./scripts/db-backup.sh pre-deploy
```

Salida esperada:

```
[2026-… ] ✓ /home/<user>/aircargo-backups/aircargo_pre-deploy_YYYYMMDD_HHMMSS.dump (X MB) — N respaldos retenidos (tipo: pre-deploy)
```

El archivo queda en `~/aircargo-backups/` (o en `BACKUP_DIR` si lo configuraste).
Copia su ruta exacta; la usarás en el paso 1.3.

> Alternativa manual equivalente:
> ```sh
> pg_dump -h 127.0.0.1 -p 5432 -U aircargo_user -d aircargo \
>   -Fc -Z 6 -f ~/aircargo-backups/aircargo_local.dump
> ```

### 1.2 Obtener la URL externa del Postgres de Render

Tras aplicar el Blueprint (sección 2), abre en Render **aircargo-db → Connect** y copia:

- **External Database URL** → `postgresql://aircargo_user:XXX@dpg-XXXX.oregon-postgres.render.com/aircargo`
- O bien el **Hostname externo** + puerto `5432`.

> ⚠️ Usa la **External** URL/host (con `dpg-….`), no la Internal, para restaurar desde tu máquina.
> La Internal solo es alcanzable dentro de la red privada de Render.

### 1.3 Restaurar el dump en Render Postgres

Render Postgres **fuerza SSL**, así que `sslmode=require`. `pg_restore` no acepta
conexión estilo URL directamente; usa los parámetros por partes:

```sh
export PGPASSWORD='<password de aircargo_user en Render>'

pg_restore \
  -h dpg-XXXX.oregon-postgres.render.com \
  -p 5432 \
  -U aircargo_user \
  -d aircargo \
  --clean --if-exists --no-owner --no-privileges \
  --sslmode=require \
  ~/aircargo-backups/aircargo_pre-deploy_YYYYMMDD_HHMMSS.dump
```

> Errores benignos: si el único mensaje es `SET transaction_timeout` (dump creado con
> `pg_dump` ≥ 17 contra un servidor que no la soporta) es **inofensivo** — `scripts/db-restore.sh`
> ya lo clasifica como éxito. Cualquier **otro** `ERROR` sí es un fallo real.

### 1.4 Verificar los conteos post-restore

```sh
export PGPASSWORD='<password de aircargo_user en Render>'
psql -h dpg-XXXX.oregon-postgres.render.com -p 5432 -U aircargo_user -d aircargo \
  --sslmode=require -c "
    SELECT 'app_user' AS tabla, count(*) FROM auth.app_user
    UNION ALL SELECT 'mawb', count(*) FROM mawb.mawb
    UNION ALL SELECT 'booking', count(*) FROM booking.booking
    UNION ALL SELECT 'flight', count(*) FROM flight.flight
    UNION ALL SELECT 'receipt', count(*) FROM warehouse.warehouse_receipt
    UNION ALL SELECT 'uld', count(*) FROM uld.uld;"
```

Referencia de la BD local que **debe** coincidir (valores al momento de este runbook):

| Esquema.tabla | Filas esperadas |
|---------------|-----------------|
| `auth.app_user` | 14 |
| `auth.site` | 4 |
| `auth.user_sites` | 26 |
| `flight.flight` | 3 |
| `flight.airline` | 10 |
| `booking.booking` | 43 |
| `mawb.mawb` | 38 |
| `mawb.hawb` | 0 |
| `warehouse.warehouse_receipt` | 4 |
| `uld.uld` | 2 |

> **Importante:** el dump incluye las tablas de historial Flyway
> (`flyway_schema_history_auth`, `_flight`, `_booking`, `_mawb`, `_uld`, `_warehouse`,
> y las de `export_bi`/`notification`). Al estar presentes, los servicios **no**
> re-ejecutan migraciones al arrancar. **No borres** esas tablas.

---

## 2. Aplicar el Blueprint en Render

1. Render Dashboard → **New +** → **Blueprint**.
2. Conecta el repo `aircargo-saas-Version1.4`, rama `main`.
3. Render detecta `render.yaml` y muestra los recursos:

   - `aircargo-db` (Postgres 16, plan `basic-256mb`) — cámbialo a `free` aquí si quieres free tier
     (⚠️ expira a los 30 días).
   - `aircargo-cache` (Key Value, free).
   - Env Group `aircargo-secrets` (`JWT_SECRET` + `APP_ENCRYPTION_KEY` generados).
   - 10 Web Services Docker + 1 Static Site.

4. Durante el primer **Apply**, Render pedirá los valores marcados con `sync: false`
   (ver sección 3). Rellénalos y aplica.

> **Nota sobre `rootDir`:** el Static Site sí lo usa (`rootDir: frontend`); los servicios
> Docker **no** (el `dockerContext` es la raíz del repo por el `COPY backend/ .`).

---

## 3. Variables `sync: false` — rellenar en Render

Estas variables **no** pueden venir del Blueprint (Render no las conoce) ni vivir en el
Env Group (la spec prohíbe `sync: false` ahí). Render las pide en el Apply y se pueden
editar luego en **cada servicio → Environment**.

### 3.1 `POSTGRES_HOST` / `POSTGRES_PORT` — en todos los servicios con BD

Valores a copiar de **aircargo-db → Connect → Internal** (los servicios hablan por la red
interna de Render, no por la externa):

| Variable | Valor |
|----------|-------|
| `POSTGRES_HOST` | *Internal Hostname* de `aircargo-db` (ej. `dpg-XXXX-a.oregon-postgres.render.com`) |
| `POSTGRES_PORT` | `5432` |

Servicios que usan BD: `aircargo-auth`, `aircargo-flight`, `aircargo-booking`,
`aircargo-mawb`, `aircargo-warehouse`, `aircargo-uld`, `aircargo-audit-reports`,
`aircargo-notification`.

### 3.2 `RABBITMQ_*` — en todos los servicios con AMQP

De tu URL de CloudAMQP (sección 0.1). Los **8** servicios con AMQP ya traen su bloque
`RABBITMQ_*` en `render.yaml` (`sync: false`, a rellenar en el primer Apply):
`aircargo-auth`, `aircargo-flight`, `aircargo-booking`, `aircargo-mawb`,
`aircargo-warehouse`, `aircargo-uld`, `aircargo-load-planning`, `aircargo-notification`.
(`aircargo-gateway` y `aircargo-audit-reports` no requieren AMQP.)

| Variable | Valor |
|----------|-------|
| `RABBITMQ_HOST` | host de CloudAMQP (sin `amqps://`) |
| `RABBITMQ_PORT` | `5671` |
| `RABBITMQ_USER` | usuario de CloudAMQP |
| `RABBITMQ_PASSWORD` | password de CloudAMQP |
| `RABBITMQ_SSL` | `true` (ya en `render.yaml`; cableado a `spring.rabbitmq.ssl.enabled`) |

`RABBITMQ_SSL` sí tiene efecto: los 8 `application.properties` con AMQP declaran
`spring.rabbitmq.ssl.enabled=${RABBITMQ_SSL:false}` (default `false` para el broker
local en claro; `true` activa amqps/TLS hacia CloudAMQP).

> Si el broker rechazara TLS, pon `RABBITMQ_SSL=false` + `RABBITMQ_PORT=5672`
> (o el puerto en claro que indique tu plan).

---

## 4. Verificación post-deploy

Con el deploy en verde (Render muestra cada servicio como **Live**):

1. **Health de cada servicio** (Render ya sondea `/actuator/health`):

   ```sh
   for s in auth flight booking mawb warehouse uld load-planning audit-reports notification gateway; do
     printf "%-16s " "$s"; curl -s -o /dev/null -w "%{http_code}\n" "https://aircargo-$s.onrender.com/actuator/health"
   done
   ```

   Esperado: `200` en cada uno.

2. **Frontend** — abre `https://aircargo-frontend.onrender.com` y verifica que carga el login.

3. **Login E2E por el gateway** (el frontend proxya `/api/*` → gateway):

   ```sh
   curl -s -X POST https://aircargo-frontend.onrender.com/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@aircargo.com","password":"<tu_password>"}'
   ```

   - Con MFA obligatorio → esperado `428` con `mfaEnrollmentRequired:true` (flujo normal).
   - Credenciales inválidas → `401` con mensaje genérico (correcto, no filtra usuarios).

4. **Datos migrados** — el login debe resolver contra los usuarios reales del paso 1.4.

---

## 5. Rollback / recuperación

- **Re-desplegar una versión anterior:** Render → servicio → *Deploys* → *Rollback*,
  o `git revert` + push (auto-deploy está en `true`).
- **Restaurar la BD a un dump anterior:** repite el paso 1.3 apuntando al Postgres de Render.
  Genera siempre un dump nuevo antes de restaurar (misma disciplina que `scripts/db-restore.sh`,
  que crea un backup de protección automáticamente).
- **Antes de cada deploy importante:** `./scripts/db-backup.sh pre-deploy` local y sube el `.dump`
  a un almacenamiento externo (o configura `BACKUP_OFFSITE_TARGET` con `rsync:`/`rclone:`).

---

## 6. Notas y decisiones de diseño

| Tema | Decisión |
|------|----------|
| Frontend `/api` | Proxy **rewrite** del Static Site → gateway (`frontend/src/api/client.js` usa `baseURL: '/api'`, no se edita) |
| HTTPS internos | `SERVICE_*_URL` usan `https://aircargo-<svc>.onrender.com` (Render expone HTTPS automático) |
| Secretos compartidos | `JWT_SECRET` y `APP_ENCRYPTION_KEY` en Env Group `aircargo-secrets` (`generateValue: true`) → idénticos en los 10 servicios |
| Postgres vars | `POSTGRES_USER/PASSWORD/DB` vía `fromDatabase`; `POSTGRES_HOST/PORT` manuales (`sync: false`); `sslmode=${POSTGRES_SSLMODE:disable}` → `require` en Render |
| Feign | No pasa por el gateway; `FeignServiceAuthInterceptor` reenvía `Authorization` o firma un token `service:<svc>` (rol `SUPER_USER`) |
| RabbitMQ | Externo (CloudAMQP free); Render no ofrece AMQP gestionado |
| Planes | Web Services `starter`; Postgres `basic-256mb` (cambiable a `free`); Static Site `free` |
| Región | `oregon` en todos los recursos (debe ser la misma para la red interna) |

---

## 7. Checklist resumido

- [ ] 0.1 Broker CloudAMQP creado y `RABBITMQ_*` desglosados.
- [ ] 1.1 Dump local generado (`./scripts/db-backup.sh pre-deploy`).
- [ ] 2 Blueprint aplicado (recursos creados).
- [ ] 3.1 `POSTGRES_HOST/PORT` rellenados en los 8 servicios con BD.
- [ ] 3.2 `RABBITMQ_*` (+`RABBITMQ_SSL=true`) rellenados en los 8 servicios con AMQP.
- [ ] 1.3 Dump restaurado en Render Postgres (`--sslmode=require`).
- [ ] 1.4 Conteos post-restore coinciden con la tabla de referencia.
- [ ] 4 Health `200` en los 10 servicios y frontend cargando.
- [ ] 4 Login E2E por el gateway OK (428 MFA o 200).

> Orden recomendado: **1.1 → 2 → 3 → 1.3 → 1.4 → 4** (el dump local puede generarse en
> cualquier momento, pero la restauración 1.3 necesita el Postgres ya creado en 2).
