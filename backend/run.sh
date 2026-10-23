# ── Load secrets from gitignored .env at repo root ──────────────
. "$(cd "$(dirname "$0")" && pwd)/../aircargo-env.sh"

MAX_CONNS="-Dspring.datasource.hikari.maximum-pool-size=3"

java $MAX_CONNS -jar aircargo-gateway/target/aircargo-gateway-1.2.0-SNAPSHOT.jar > /tmp/gateway.log 2>&1 &
java $MAX_CONNS -jar aircargo-auth-service/target/aircargo-auth-service-1.2.0-SNAPSHOT.jar > /tmp/auth.log 2>&1 &
java $MAX_CONNS -jar aircargo-flight-service/target/aircargo-flight-service-1.2.0-SNAPSHOT.jar > /tmp/flight.log 2>&1 &

sleep 20

java $MAX_CONNS -jar aircargo-booking-service/target/aircargo-booking-service-1.2.0-SNAPSHOT.jar > /tmp/booking.log 2>&1 &
java $MAX_CONNS -jar aircargo-mawb-service/target/aircargo-mawb-service-1.2.0-SNAPSHOT.jar > /tmp/mawb.log 2>&1 &
java $MAX_CONNS -jar aircargo-warehouse-service/target/aircargo-warehouse-service-1.2.0-SNAPSHOT.jar > /tmp/warehouse.log 2>&1 &
java $MAX_CONNS -jar aircargo-uld-service/target/aircargo-uld-service-1.2.0-SNAPSHOT.jar > /tmp/uld.log 2>&1 &
java $MAX_CONNS -jar aircargo-load-planning-service/target/aircargo-load-planning-service-1.2.0-SNAPSHOT.jar > /tmp/loadplanning.log 2>&1 &
java $MAX_CONNS -jar aircargo-audit-reports-service/target/aircargo-audit-reports-service-1.2.0-SNAPSHOT.jar > /tmp/audit-reports.log 2>&1 &
java $MAX_CONNS -jar aircargo-notification-service/target/aircargo-notification-service-1.2.0-SNAPSHOT.jar > /tmp/notification.log 2>&1 &
