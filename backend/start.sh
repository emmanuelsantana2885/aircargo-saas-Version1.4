#!/bin/bash
set -e

BACKEND="$(cd "$(dirname "$0")" && pwd)"
# ── Load secrets from gitignored .env at repo root ──────────────
. "$BACKEND/../aircargo-env.sh"

MAX_CONNS="-Dspring.datasource.hikari.maximum-pool-size=3"
JAVA_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED"

echo "Starting all 10 services..."

java $JAVA_OPTS $MAX_CONNS -jar "$BACKEND/aircargo-gateway/target/aircargo-gateway-1.2.0-SNAPSHOT.jar" &
java $JAVA_OPTS $MAX_CONNS -jar "$BACKEND/aircargo-auth-service/target/aircargo-auth-service-1.2.0-SNAPSHOT.jar" &
java $JAVA_OPTS $MAX_CONNS -jar "$BACKEND/aircargo-flight-service/target/aircargo-flight-service-1.2.0-SNAPSHOT.jar" &

sleep 20

java $JAVA_OPTS $MAX_CONNS -jar "$BACKEND/aircargo-booking-service/target/aircargo-booking-service-1.2.0-SNAPSHOT.jar" &
java $MAX_CONNS -jar "$BACKEND/aircargo-mawb-service/target/aircargo-mawb-service-1.2.0-SNAPSHOT.jar" &
java $MAX_CONNS -jar "$BACKEND/aircargo-warehouse-service/target/aircargo-warehouse-service-1.2.0-SNAPSHOT.jar" &
java $MAX_CONNS -jar "$BACKEND/aircargo-uld-service/target/aircargo-uld-service-1.2.0-SNAPSHOT.jar" &
java $MAX_CONNS -jar "$BACKEND/aircargo-load-planning-service/target/aircargo-load-planning-service-1.2.0-SNAPSHOT.jar" &
java $MAX_CONNS -jar "$BACKEND/aircargo-audit-reports-service/target/aircargo-audit-reports-service-1.2.0-SNAPSHOT.jar" &
java $MAX_CONNS -jar "$BACKEND/aircargo-notification-service/target/aircargo-notification-service-1.2.0-SNAPSHOT.jar" &

echo "Done. Wait 30s then check: curl -s http://localhost:8080/actuator/health"
