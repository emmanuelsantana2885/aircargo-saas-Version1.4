#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════
# Genera las 10 unidades systemd para la topología EC2 nativa
# (jars Java 21 en backend/*/target + Postgres/RabbitMQ del sistema).
#
#   ./deploy/ec2/systemd/generate-units.sh            # genera en ./deploy/ec2/systemd/generated/
#   ./deploy/ec2/systemd/generate-units.sh --install  # además instala y habilita los 10 servicios
#
# Nota: reemplaza nohup/start-*.sh. El watchdog (aircargo-watchdog.timer)
# reinicia unidades cuyo health no responda.
# ══════════════════════════════════════════════════════════════
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GEN_DIR="$SCRIPT_DIR/generated"
APP_DIR="${AIRCARGO_APP_DIR:-$HOME/aircargo-saas}"
USER_NAME="${AIRCARGO_USER:-ubuntu}"
JAVA_BIN="${JAVA_BIN:-/usr/bin/java}"
RAM_OPTS="${JAVA_MEMS:--Xms128m -Xmx256m}"

# nombre_lógico puerto deps_orden_de_arranque
SERVICES=(
  "gateway 8080  30"
  "auth-service 9092  10"
  "flight-service 9093 20"
  "booking-service 9094 20"
  "mawb-service 9095 20"
  "warehouse-service 9096 20"
  "uld-service 9097 20"
  "load-planning-service 9098 20"
  "export-service 9099 30"
  "notification-service 9100 90"
)

mkdir -p "$GEN_DIR"
rm -f "$GEN_DIR"/*.service

for row in "${SERVICES[@]}"; do
  set -- $row
  name="$1"; port="$2"; start_sec="$3"

  cat > "$GEN_DIR/aircargo-$name.service" <<EOF
[Unit]
Description=Aircargo $name (puerto $port)
After=network-online.target postgresql.service rabbitmq-server.service
Wants=network-online.target
StartLimitIntervalSec=0

[Service]
Type=simple
User=$USER_NAME
WorkingDirectory=$APP_DIR
EnvironmentFile=$APP_DIR/.env
ExecStartPre=/usr/bin/sleep $start_sec
ExecStart=$JAVA_BIN $RAM_OPTS -jar $APP_DIR/backend/aircargo-$name/target/aircargo-$name-1.2.0-SNAPSHOT.jar
Restart=always
RestartSec=15
TimeoutStartSec=300
TimeoutStopSec=30
KillSignal=SIGTERM
SuccessExitStatus=143
# Logging a ~/aircargo-logs (igual que start-all.sh)
StandardOutput=append:$APP_DIR/aircargo-logs/aircargo-$name.log
StandardError=append:$APP_DIR/aircargo-logs/aircargo-$name.log

[Install]
WantedBy=multi-user.target
EOF
  echo "  ✓ aircargo-$name.service (port $port) → $GEN_DIR"
done

echo ""
echo "Unidades generadas en $GEN_DIR/"

if [ "${1:-}" = "--install" ]; then
  echo ""
  echo "Instalando los 10 servicios..."
  # Los servicios escuchan en localhost:8080+9092-9100; nginx (sistema) expone :80/:443
  for svc in "${SERVICES[@]}"; do
    name="${svc%% *}"
    sudo cp "$GEN_DIR/aircargo-$name.service" /etc/systemd/system/
  done
  sudo systemctl daemon-reload
  sudo systemctl enable --now aircargo-gateway.service
  echo "Enabled. Arranca dependencias (postgres/rabbitmq/redis) ANTES del stack:"
  echo "  sudo systemctl enable --now postgresql rabbitmq-server"
  echo "Estado: systemctl status 'aircargo-*.service' | watch: systemctl list-units 'aircargo-*'"
fi