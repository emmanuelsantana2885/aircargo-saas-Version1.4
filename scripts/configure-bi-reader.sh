#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# Crea el rol de SOLO LECTURA `bi_reader` para herramientas BI
# que se conectan DIRECTAMENTE a la base de datos (Apache Superset,
# Looker Studio / Power BI vía driver nativo PostgreSQL, etc.).
#
#   Uso manual : ./scripts/configure-bi-reader.sh
#
#   Qué hace (idempotente, puede re-ejecutarse):
#     1. Crea (o actualiza) el rol LOGIN `bi_reader` con la password
#        de BI_READER_PASSWORD (o genera una aleatoria si no existe).
#     2. GRANT CONNECT en la BD, USAGE en los schemas y SELECT en
#        TODAS las tablas existentes (public, notification, export_bi).
#     3. ALTER DEFAULT PRIVILEGES → tablas nuevas leen solas.
#     4. NO otorga INSERT/UPDATE/DELETE: estrictamente de lectura.
#
#   Conexión administrativa (requiere superusuario o rol con
#   CREATEROLE + poder de GRANT). Auto-detecta en este orden:
#     a. Modo sudo (Postgres del sistema): si BI_USE_SUDO=1 o si
#        `sudo -n -u postgres true` funciona cuando no hay
#        POSTGRES_ADMIN_USER definido. Requiere sudo sin password.
#     b. TCP como POSTGRES_ADMIN_USER (default: POSTGRES_USER).
#
#   Variables de entorno (.env):
#     POSTGRES_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD
#     POSTGRES_ADMIN_USER / POSTGRES_ADMIN_PASSWORD   (admin TCP opcional)
#     BI_READER_PASSWORD   password del rol de solo lectura (aleatoria si vacía)
#     BI_READER_ROLE       nombre del rol                    (default: bi_reader)
# ────────────────────────────────────────────────────────────────
set -euo pipefail

AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ -f "$AIR_ROOT/.env" ] && . "$AIR_ROOT/.env"

PG_HOST="${POSTGRES_HOST:-127.0.0.1}"
[ "$PG_HOST" = "localhost" ] && PG_HOST="127.0.0.1"
PG_PORT="${POSTGRES_PORT:-5432}"
DB_NAME="${POSTGRES_DB:-aircargo}"
BI_ROLE="${BI_READER_ROLE:-bi_reader}"

if [ -z "${BI_READER_PASSWORD:-}" ]; then
  BI_READER_PASSWORD="$(openssl rand -hex 16)"
  GENERATED_PW=1
fi

# ── Selección del modo admin ──────────────────────────────────────
ADMIN_MODE="tcp"
if [ "${BI_USE_SUDO:-0}" = "1" ]; then
  ADMIN_MODE="sudo"
elif [ -z "${POSTGRES_ADMIN_USER:-}" ] && sudo -n -u postgres true 2>/dev/null; then
  echo "[i] sudo -u postgres disponible — usando el Postgres del sistema."
  ADMIN_MODE="sudo"
else
  ADMIN_USER="${POSTGRES_ADMIN_USER:-${POSTGRES_USER:?Define POSTGRES_USER en .env}}"
  ADMIN_PASSWORD="${POSTGRES_ADMIN_PASSWORD:-${POSTGRES_PASSWORD:-}}"
fi

# Variables psql compartidas (interpolación segura con :'var' / :"var")
PSQL_VARS=(-v db="$DB_NAME" -v role="$BI_ROLE" -v pw="$BI_READER_PASSWORD")

admin_psql() {
  # $@ : resto de opciones psql + SQL (por archivo 'stdin' se envía por el heredoc solventado)
  if [ "$ADMIN_MODE" = "sudo" ]; then
    sudo -n -u postgres psql -v ON_ERROR_STOP=1 -qAtX "${PSQL_VARS[@]}" "$@"
  else
    PGPASSWORD="$ADMIN_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$ADMIN_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -qAtX "${PSQL_VARS[@]}" "$@"
  fi
}

role_psql() {
  PGPASSWORD="$BI_READER_PASSWORD" psql -h "$PG_HOST" -p "$PG_PORT" -U "$BI_ROLE" -d "$DB_NAME" -qAtX "$@"
}

# Comprobar conectividad admin
probe="$(admin_psql -tc "SELECT 1")"
[ "$probe" = "1" ] || { echo "[x] No se pudo conectar como administrador a $DB_NAME." >&2; exit 1; }

echo "[i] BD: $DB_NAME  host: $PG_HOST:$PG_PORT  rol destino: $BI_ROLE  (modo admin: $ADMIN_MODE)"

# ── 1. Crear rol (idempotente) + password ─────────────────────────
# Nota: psql NO interpola :'var' dentro de $dollar$-quoted strings,
# por eso el CREATE/ALTER idempotente se hace con \gexec (fuera de ellos).
admin_psql <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'pw')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'role') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
              :'role', :'pw')
WHERE EXISTS (SELECT FROM pg_roles WHERE rolname = :'role') \gexec
SQL

# ── 2. CONNECT + USAGE + SELECT (schemas existentes) ──────────────
admin_psql <<'SQL'
GRANT CONNECT ON DATABASE :"db" TO :"role";
SQL
for schema in public notification export_bi; do
  exists="$(admin_psql -tc "SELECT 1 FROM pg_namespace WHERE nspname='$schema'")" || true
  [ "$exists" = "1" ] || continue
  admin_psql -v sch="$schema" <<'SQL'
GRANT USAGE ON SCHEMA :"sch" TO :"role";
GRANT SELECT ON ALL TABLES IN SCHEMA :"sch" TO :"role";
GRANT SELECT ON ALL SEQUENCES IN SCHEMA :"sch" TO :"role";
SQL
done

# ── 3. Default privileges → tablas nuevas heredan SELECT ───────────
for schema in public notification export_bi; do
  exists="$(admin_psql -tc "SELECT 1 FROM pg_namespace WHERE nspname='$schema'")" || true
  [ "$exists" = "1" ] || continue
  admin_psql -v sch="$schema" <<'SQL'
ALTER DEFAULT PRIVILEGES IN SCHEMA :"sch" GRANT SELECT ON TABLES TO :"role";
SQL
done

# ── 4. Prueba de lectura en vivo con el rol nuevo ──────────────────
tables="$(role_psql -tc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")"
echo "[i] Prueba SELECT como '$BI_ROLE' OK — $tables tablas en public visibles."

if [ -n "${GENERATED_PW:-}" ]; then
  echo
  echo "⚠ Se GENERÓ una password aleatoria (BI_READER_PASSWORD no estaba en .env). Guárdala:"
  echo "  BI_READER_PASSWORD=$BI_READER_PASSWORD"
  echo "  → agrégala a .env para que las re-ejecuciones la reutilicen."
fi

echo
echo "✓ Rol $BI_ROLE listo (SOLO LECTURA)."
echo "  Host: $PG_HOST  Puerto: $PG_PORT  BD: $DB_NAME"
echo "  User: $BI_ROLE   Pass: $BI_READER_PASSWORD"
echo
echo "  SQLAlchemy URI (Superset / Python / pandas):"
echo "  postgresql+psycopg2://$BI_ROLE:$BI_READER_PASSWORD@$PG_HOST:$PG_PORT/$DB_NAME"
echo
echo "  psql CLI:"
echo "  PGPASSWORD='$BI_READER_PASSWORD' psql -h $PG_HOST -p $PG_PORT -U $BI_ROLE -d $DB_NAME"