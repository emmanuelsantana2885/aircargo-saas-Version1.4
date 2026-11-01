#!/bin/bash
# Entrypoint del Space: nginx (7860) responde siempre — HF exige que el puerto
# conteste pronto — y detrás van arrancando gateway + 9 servicios escalonados.
set -uo pipefail

export JAVA_OPTS="${JAVA_OPTS:--XX:+UseContainerSupport -XX:MaxRAMPercentage=60}"

LOG_DIR="${LOG_DIR:-/app/logs}"
mkdir -p "$LOG_DIR"

# ---------------------------------------------------------------- nginx primero
nginx -t 2>&1 | head -2
echo "[start.sh] nginx :7860 arrancando primero (栈 interno arranca detrás)..." | tee -a "$LOG_DIR/boot.log"
nginx -g 'daemon off;' &
NGINX_PID=$!

start () {
  local name="$1" jar="$2" port="$3" extra="${4:-}"
  echo "[start.sh] $name -> :$port ..." | tee -a "$LOG_DIR/boot.log"
  ( java $JAVA_OPTS $extra \
        -Dserver.address=127.0.0.1 \
        -Dserver.port="$port" \
        -jar "/app/$jar" \
        > "$LOG_DIR/$name.log" 2>&1 ) &
  echo $! > "$LOG_DIR/$name.pid"
}

# ------------------------------------------------------------------- gateway
start gateway /app/gateway.jar 8080 \
  "-Dapp.env=production -Dspring.cloud.gateway.httpclient.connect-timeout=10000 -Dspring.cloud.gateway.httpclient.response-timeout=60s"
sleep 15

# ---------------------------------------------- los 9 microservicios (stagger)
SERVICES=(
  "auth:9092:auth.jar"
  "flight:9093:flight.jar"
  "booking:9094:booking.jar"
  "mawb:9095:mawb.jar"
  "warehouse:9096:warehouse.jar"
  "uld:9097:uld.jar"
  "load-planning:9098:load-planning.jar"
  "audit-reports:9099:audit-reports.jar"
  "notification:9100:notification.jar"
)
HIKARI='-Dspring.datasource.hikari.maximum-pool-size=2'
for s in "${SERVICES[@]}"; do
  name="${s%%:*}"; rest="${s#*:}"; port="${rest%%:*}"; jar="${rest#*:}"
  start "$name" "/app/$jar" "$port" "$HIKARI"
  sleep 8
done

# -------------------------------------------------- health-check de bootstrap
wait_healthy () {
  local name="$1" url="$2" tries="${3:-30}" code
  for i in $(seq 1 "$tries"); do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$url" 2>/dev/null || echo 000)
    if [ "$code" = "200" ]; then
      echo "[start.sh] $name OK (200 en $url)" | tee -a "$LOG_DIR/boot.log"
      return 0
    fi
    sleep 10
  done
  echo "[start.sh] $name SIN salud tras $((tries*10))s (último código $code)" | tee -a "$LOG_DIR/boot.log"
  return 1
}

wait_healthy gateway "http://127.0.0.1:8080/actuator/health" 40 || true
for s in "${SERVICES[@]}"; do
  name="${s%%:*}"; port="${s#*:}"; port="${port%%:*}"
  wait_healthy "$name" "http://127.0.0.1:$port/actuator/health" 40 || true
done

echo "[start.sh] Stack listo. Manteniendo PID 1 vivo..." | tee -a "$LOG_DIR/boot.log"
# PID1 vivo mientras nginx siga con vida (y el resto queda en background)
while kill -0 "$NGINX_PID" 2>/dev/null; do
  sleep 15
done
echo "[start.sh] nginx murió — re-lanzando" | tee -a "$LOG_DIR/boot.log"
nginx -g 'daemon off;' &
NGINX_PID=$!
wait "$NGINX_PID"
