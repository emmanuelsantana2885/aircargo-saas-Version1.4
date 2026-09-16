# Legado (era Docker) — desactualizado

Estos scripts corresponden a la topología antigua de despliegue (contenedores Docker vía
`docker/docker-compose.services.yml` + monolito `aircargo-api:9091`) y NO reflejan la
topología real de producción actual (**EC2 nativo**: jars Java 21 en `backend/*/target` +
nginx del sistema en :80/:443 + Postgres/RabbitMQ del sistema). Se conservan aquí por
referencia; NO usarlos para desplegar.

| Script | Hacía | Reemplazo actual |
|--------|-------|------------------|
| `deploy-ec2.sh` | Instala Docker y levanta el stack en contenedores (t3.medium+) | `deploy/PRODUCTION_DEPLOYMENT_RUNBOOK.md` §1 (EC2 Nativo) + `deploy/ec2/systemd/generate-units.sh --install` |
| `setup-https.sh` | Let's Encrypt standalone + certbot dentro del contenedor frontend | `deploy/PRODUCTION_DEPLOYMENT_RUNBOOK.md` §1 *Transporte TLS* (`deploy/ec2/nginx-aircargo.conf` + `sudo certbot --nginx`) |
| `check-status.sh` | `docker compose ps` + puertos 9091/5432 del contenedor | `ss -tlnp` (verificar listeners 8080 + 9092–9100) + `systemctl status 'aircargo-*.service'` |
| `start-services.sh` | `mvn clean package` + `spring-boot:run` por servicio (dev-loop, logs en /tmp) | `./start-all.sh` / `./start-backend.sh` (build jars + arranque con health waifs, logs en `~/aircargo-logs`) |