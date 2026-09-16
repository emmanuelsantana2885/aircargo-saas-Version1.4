# Conexiones BI externas — Google Looker Studio & Apache Superset

Aircargo ya expone `/api/bi/**` (11 agregados + Weight Report) a través del
gateway. Esta carpeta añade dos métodos de consumo externos:

| Herramienta | Método | Protección | Dónde |
|-------------|--------|------------|-------|
| **Looker Studio** | Conector comunitario Apps Script → `/api/bi/**` (`?api_key=JWT-service`) | Web (HTTPS + token) | `looker-studio-connector.gs` |
| **Looker Studio** (alternativa) | Conector nativo PostgreSQL → BD aircargo | Red + rol `bi_reader` (SELECT-only) | `scripts/configure-bi-reader.sh` |
| **Apache Superset** | SQLAlchemy directo a BD + datos vía API key | Red + rol `bi_reader` (SELECT-only) | `docker/docker-compose.superset.yml` |

---

## 0. Token de servicio BI y rol de solo lectura

Ambos caminos usan credenciales que se generan una vez:

### 0a. API Key (para Looker Studio vía HTTP)
1. Login como ADMIN/SUPER_USER.
2. `Configuración → Backups/BI → botón "Servicio BI"` → copia el JWT
   (`tokenType=service`, `bi@rannik.com`, TTL 365 días).
3. Pégalo como `api_key` en el conector. El gateway lo acepta en
   `?api_key=`, `Authorization: Bearer` o cookie `aircargo_at`.

### 0b. Rol `bi_reader` (para conexión directa a Postgres)
```sh
./scripts/configure-bi-reader.sh        # crea/actualiza bi_reader, solo SELECT
```
- Si `BI_READER_PASSWORD` no está en `.env`, el script genera una y la imprime
  (fijarla después para que las re-ejecuciones no la cambien).
- Verificado: `SELECT` OK, `INSERT` → `permission denied`.

---

## 1. Google Looker Studio

### Opción A — Conector comunitario (HTTP a `/api/bi/**`)
Ventaja: no expone la BD; usa la misma capa JWT que la app. Requiere que el
gateway sea **HTTPS y alcanzable desde Internet** (Apps Script corre en la
nube de Google). Si hoy la instancia es HTTP, pon delante un túnel/tunnel
(Caddy/Let's Encrypt, Cloudflare Tunnel, ngrok) o usa la Opción B.

1. `script.google.com` → proyecto nuevo → pega `looker-studio-connector.gs`.
2. `Implementar → Nuevo despliegue → Add-on/Editor`; marca **Looker Studio**
   en el tipo de despliegue y copia el **ID de despliegue**.
3. En Looker Studio: *Crear fuente de datos → Conectores (Partner) →*
   *Aircargo BI*. Configura:
   - URL base: `https://tu-dominio.com`
   - API Key: el JWT del paso 0a
   - Endpoint: el dataset (ej. `bi/flights`)
   - Filas máximas: default 1000 (máx. 5000).
4. Las columnas se derivan automáticamente del endpoint al configurar la
   fuente (los endpoints `dashboard`, `summary`, `weight-summary` devuelven
   un objeto único y se exponen como una sola fila).

Endpoints disponibles: `bi/flights`, `bi/bookings`, `bi/mawbs`, `bi/receipts`,
`bi/ulds`, `bi/daily`, `bi/by-location`, `bi/timeline`, `bi/top-mawbs`,
`bi/flight-performance`, `bi/dashboard`, `bi/summary`, `bi/weight-report`,
`bi/weight-summary`. Los marcados *aceptan `dateFrom`/`dateTo`* (se envían
desde el rango de la fuente si está activado).

> Seguridad: el token viaja en la URL de cada petición y Looker Studio lo
> guarda cifrado en su catálogo. Regenera el token en Settings → BI si
> alguna vez se filtra o al revocar acceso.

### Opción B — Conector nativo PostgreSQL (sin Apps Script)
Para un servidor **privado** (sin HTTPS público) o si prefieres acceso SQL
directo:

1. `./scripts/configure-bi-reader.sh`.
2. Looker Studio → *Crear fuente de datos → Conectores → PostgreSQL*.
   - Servidor, puerto 5432, base `aircargo`, usuario `bi_reader`, password.
   - Corre Japón/regiones tal cual; el rol es de **solo lectura** (SELECT).

> La BD comparte hosting con la app: el acceso es por red (LAN/VPN/allow-list
> de IPs), no Internet.

---

## 2. Apache Superset (self-hosted, Docker)

```sh
# 1) Variables (una vez)
printf 'SUPERSET_SECRET_KEY=%s\n' "$(openssl rand -base64 42)" >> .env
#    + SUPERSET_ADMIN_PASSWORD=algo

# 2) Rol readonly
./scripts/configure-bi-reader.sh

# 3) Levantar
docker compose -f docker/docker-compose.superset.yml up -d
# Abre http://localhost:8088 (admin / SUPERSET_ADMIN_PASSWORD)
```

En Superset:
1. **Settings → Database Connections → + Database → PostgreSQL**:
   - Host `host.docker.internal`, puerto `5432`, DB `aircargo`,
     usuario `bi_reader`, password (`extra_hosts` del compose hace
     `host.docker.internal` → el host).
   - SQLAlchemy URI equivalente:
     `postgresql+psycopg2://bi_reader:PASS@host.docker.internal:5432/aircargo`
2. **+ Dataset** → elige tablas de `public` (`app_user`, `flight`, `booking`,
   `mawb`, `warehouse_receipt`, `uld`, `uld_awb`, …).
3. Construye charts/dashboards. El rol `bi_reader` garantiza que Superset
   **nunca puede escribir**.

Opciones de despliegue:
- **Mismo servidor** (`docker-compose.superset.yml`): el contenedor llega al
  Postgres del host vía `host.docker.internal`.
- **EC2 / nativo**: exponer el puerto `SUPERSET_PORT` (default 8088) o usar
  el mismo nginx para un subdominio.
- **Metadatos de Superset**: SQLite en el volumen `aircargo-superset-home`
  (no toca la BD de Aircargo). Para mayor robustez, definir
  `SUPERSET_METADATA_URI` apuntando a un Postgres dedicado.

---

## Power BI / otros
Cualquier herramienta que acepte SQL o HTTP puede usar los mismos dos caminos:
`bi_reader` para queries directas o `/api/bi/**` con `?api_key=` para los
agregados ya calculados (Documento técnico: `Documents/BI.md` donde exista).