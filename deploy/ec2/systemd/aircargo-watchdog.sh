#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════
# WATCHDOG Aircargo — verifica health de los 10 servicios y
# reinicia la unidad systemd cuya health no responda (2 fallos
# consecutivos) y, si el proceso está muerto pero la unidad activa,
# la reinicia también.
#
# Diseñado para correr desde aircargo-watchdog.timer (cada 2 min).
# Salida auditada en aircargo-logs/watchdog.log.
# ══════════════════════════════════════════════════════════════
set -uo pipefail

APP_DIR="${AIRCARGO_APP_DIR:-$HOME/aircargo-saas}"
LOG_FILE="$APP_DIR/aircargo-logs/watchdog.log"
mkdir -p "$(dirname "$LOG_FILE")"

log() { echo "$(date '+%F %T') watchdog: $*" >> "$LOG_FILE"; }

# servicio puerto
SERVICES=(
  "gateway 8080"
  "auth-service 9092"
  "flight-service 9093"
  "booking-service 9094"
  "mawb-service 9095"
  "warehouse-service 9096"
  "uld-service 9097"
  "load-planning-service 9098"
  "export-service 9099"
  "notification-service 9100"
)

STATE_DIR="/tmp/aircargo-watchdog"
mkdir -p "$STATE_DIR"

for row in "${SERVICES[@]}"; do
  set -- $row
  name="$1"; port="$2"
  unit="aircargo-$name.service"
  state="$STATE_DIR/$name.fails"

  # Health vía actuator/gateway (usa auth cuando el bag añade 401 a rutas sin token)
  health=$(curl -s -m 5 -o /dev/null -w "%{http_code}" "http://localhost:$port/actuator/health" 2>/dev/null || echo 000)

  if systemctl is-active --quiet "$unit"; then
    if [ "$health" = "200" ]; then
      echo 0 > "$state"
    else
      fails=$(($(cat "$state" 2>/dev/null || echo 0) + 1))
      echo "$fails" > "$state"
      if [ "$fails" -ge 2 ]; then
        log "HEALTH $name → HTTP $health ×$fails → restart"
        systemctl restart "$unit"
        echo 0 > "$state"
      fi
    fi
  else
    # Unidad parada/fallida (no arrancada por el timer): reintentar
    fails=$(($(cat "$state" 2>/dev/null || echo 0) + 1))
    echo "$fails" > "$state"
    log "INACTIVE $name → attempt $fails"
    if [ "$fails" -ge 2 ]; then
      systemctl start "$unit"
      echo 0 > "$state"
    fi
  fi
done

# Capturar nada → el stack está sano y el log queda en silencio
[ -s "$LOG_FILE" ] || true