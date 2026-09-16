#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# DRILL de restauración OFFSITE (sin tocar la BD de producción).
#
# Prueba de punta a punta de la recuperación ante desastre:
#   1. Trae el dump más reciente desde el destino OFFSITE (rsync:/rclone:)
#      — valida que la copia de seguridad externa está accesible y legible.
#   2. Verifica los magic bytes PGDMP (dump custom de pg_dump).
#   3. Restaura en una BD EFÍMERA `aircargo_drill_<estampa>` (NO producción).
#   4. Verifica conteos de las tablas principales y reporta RTO/RPO.
#   5. Deja la BD temporal (para inspección) salvo que uses --cleanup.
#
#   Uso:  ./scripts/db-restore-drill.sh [--cleanup]
#   Requiere: BACKUP_OFFSITE_TARGET en .env (rsync: o rclone:), Postgres up,
#             POSTGRES_USER con permiso CREATEDB.
#
#   GCS:  minutos = RTO (tiempo hasta DB restaurada y verificada)
#         fecha del dump = RPO (último punto con datos garantizados)
# ────────────────────────────────────────────────────────────────
set -euo pipefail

AIR_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
. "$AIR_ROOT/.env"

PG_HOST="${POSTGRES_HOST:-127.0.0.1}"
[ "$PG_HOST" = "localhost" ] && PG_HOST="127.0.0.1"
PG_PORT="${POSTGRES_PORT:-5432}"
DB_NAME="${POSTGRES_DB:-aircargo}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/aircargo-backups}"
DRILL_DIR="$BACKUP_DIR/.drill"
LOG="$BACKUP_DIR/drill.log"

TARGET="${BACKUP_OFFSITE_TARGET:-}"
mode="${TARGET%%:*}"
rest="${TARGET#*:}"

log() { echo "[$(date '+%F %T')] $*" | tee -a "$LOG"; }
die() { log "❌ $*"; exit 1; }

[ -n "$TARGET" ] || die "BACKUP_OFFSITE_TARGET no definido en .env (usar rsync:... o rclone:...)"
mkdir -p "$DRILL_DIR" "$BACKUP_DIR"

# ── 1. Fetch del dump más reciente desde OFFSITE ──────────────
log "──────────────────────────────────────────────"
log "DRILL restore offsite  destino=$mode:$rest"
START_FETCH="$(date +%s%3N)"
case "$mode" in
  rsync)
    command -v rsync >/dev/null 2>&1 || die "rsync no instalado"
    src="${rest%/}/${BACKUP_OFFSITE_DIR:-$(basename "$BACKUP_DIR")}/"
    log "pull rsync → $src"
    rsync -a --include="*.dump" --exclude="*" "$src" "$DRILL_DIR/" || die "rsync pull falló ($src) — ¿target montado?"
    ;;
  rclone)
    command -v rclone >/dev/null 2>&1 || die "rclone no instalado"
    src="${rest%/}/$(basename "$BACKUP_DIR")"
    log "pull rclone → $src"
    rclone copy "$src" "$DRILL_DIR" 2>/dev/null || die "rclone copy falló ($src) — ¿remote configurado?"
    ;;
  *) die "prefijo desconocido '$mode' (usar rsync: o rclone:)" ;;
esac

DUMP="$(ls -1t "$DRILL_DIR"/*.dump 2>/dev/null | head -1 || true)"
[ -n "$DUMP" ] || die "no hay dumps en el destino offsite ($DRILL_DIR)"
END_FETCH="$(date +%s%3N)"
log "dump offsite: $(basename "$DUMP") ($(du -h "$DUMP" | cut -f1))"

# ── 2. Validación PGDMP ───────────────────────────────────────
python3 - "$DUMP" <<'PY' || die "magic bytes PGDMP no encontrados (¿dump corrupto o de otro formato?)"
import sys
with open(sys.argv[1], 'rb') as f:
    if f.read(5) != b'PGDMP':
        sys.exit(1)
PY
log "✓ dump válido (magic PGDMP)"

# ── 3. Restaurar en BD efímera ────────────────────────────────
STAMP="$(date +%Y%m%d_%H%M%S)"
DRILL_DB="${DB_NAME}_drill_$STAMP"
export PGPASSWORD="$POSTGRES_PASSWORD"
START_RESTORE="$(date +%s%3N)"
createdb -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" "$DRILL_DB" || \
  die "no se pudo crear $DRILL_DB (¿POSTGRES_USER sin CREATEDB?)"
if ! pg_restore -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" -d "$DRILL_DB" \
     --no-owner --no-privileges --exit-on-error "$DUMP" >/dev/null 2>&1; then
  dropdb -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" --if-exists "$DRILL_DB" 2>/dev/null || true
  die "pg_restore falló (dump corrupto o faltan objetos)"
fi
END_RESTORE="$(date +%s%3N)"
RTO=$(( (END_RESTORE - START_FETCH) / 1000 ))

# ── 4. Verificación de conteos ────────────────────────────────
log "conteos en $DRILL_DB:"
TABLES="app_user mawb booking flight airline site warehouse_receipt uld"
for t in $TABLES; do
  cnt=$(psql -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" -d "$DRILL_DB" -tAF ' ' \
        -c "SELECT count(*) FROM $t;" 2>/dev/null || echo "?")
  printf "  %-20s %s\n" "$t" "$cnt" | tee -a "$LOG"
done

RPO_DATE="$(basename "$DUMP" | sed -E 's/.*_([0-9]{8})_([0-9]{6})\..*/\1 \2/')"
log "✓ DRILL OK  RTO≈${RTO}s  RPO≈${RPO_DATE}  BD temporal=$DRILL_DB"
log "  inspección: psql -h $PG_HOST -p $PG_PORT -U $POSTGRES_USER -d $DRILL_DB"
log "  limpiar:    ./scripts/db-restore-drill.sh --cleanup"

if [ "${1:-}" = "--cleanup" ]; then
  for db in $(psql -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" -d "$DB_NAME" -tAF ' ' \
              -c "SELECT datname FROM pg_database WHERE datname LIKE '${DB_NAME}_drill_%';" 2>/dev/null || true); do
    dropdb -h "$PG_HOST" -p "$PG_PORT" -U "$POSTGRES_USER" "$db" && log "✓ db temporal eliminada: $db"
  done
  rm -rf "$DRILL_DIR"
  log "✓ drill dir limpiado: $DRILL_DIR"
fi