# Aircargo stack completo en un solo Space (Hugging Face, CPU Basic free)
# Construye los 10 servicios + gateway con Maven y el frontend con Vite.
# Runtime: un runtime JRE + nginx (7860) que sirve el SPA y reenvía /api/* al
# gateway (8080). Los 10 servicios corren en localhost:9092-9100.

FROM maven:3.9-eclipse-temurin-21 AS backend-builder
WORKDIR /build
COPY backend/ ./backend/
RUN mvn -q -f backend/pom.xml install -DskipTests

FROM node:20-alpine AS frontend-builder
WORKDIR /fe
COPY frontend/ ./frontend/
RUN cd frontend && npm ci --no-audit --no-fund && npm run build

FROM eclipse-temurin:21-jre-alpine
# nginx (proxy + estático), bash/curl para el script de arranque
RUN apk add --no-cache nginx bash curl \
 && addgroup -g 1000 -S appgroup && adduser -u 1000 -S appuser -G appgroup \
 && mkdir -p /app /var/frontend

WORKDIR /app

COPY --from=backend-builder /build/backend/aircargo-gateway/target/aircargo-gateway-*.jar            /app/gateway.jar
COPY --from=backend-builder /build/backend/aircargo-auth-service/target/aircargo-auth-service-*.jar /app/auth.jar
COPY --from=backend-builder /build/backend/aircargo-flight-service/target/aircargo-flight-service-*.jar /app/flight.jar
COPY --from=backend-builder /build/backend/aircargo-booking-service/target/aircargo-booking-service-*.jar /app/booking.jar
COPY --from=backend-builder /build/backend/aircargo-mawb-service/target/aircargo-mawb-service-*.jar /app/mawb.jar
COPY --from=backend-builder /build/backend/aircargo-warehouse-service/target/aircargo-warehouse-service-*.jar /app/warehouse.jar
COPY --from=backend-builder /build/backend/aircargo-uld-service/target/aircargo-uld-service-*.jar   /app/uld.jar
COPY --from=backend-builder /build/backend/aircargo-load-planning-service/target/aircargo-load-planning-service-*.jar /app/load-planning.jar
COPY --from=backend-builder /build/backend/aircargo-audit-reports-service/target/aircargo-audit-reports-service-*.jar /app/audit-reports.jar
COPY --from=backend-builder /build/backend/aircargo-notification-service/target/aircargo-notification-service-*.jar /app/notification.jar
COPY --from=frontend-builder /fe/frontend/dist/ /var/frontend/

COPY deploy/hf/spaces/nginx.conf  /etc/nginx/http.d/default.conf
COPY deploy/hf/spaces/start.sh    /app/start.sh

RUN chmod 0755 /app/start.sh /var/frontend && chown -R appuser:appgroup /app /var/frontend \
 && mkdir -p /var/lib/nginx/tmp /var/log/nginx /run \
 && chown -R appuser:appgroup /var/lib/nginx /var/log/nginx

USER appuser
ENV PORT=7860
EXPOSE 7860
ENTRYPOINT ["/app/start.sh"]
