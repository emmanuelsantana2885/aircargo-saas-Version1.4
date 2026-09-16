# Aircargo SaaS — Production Deployment Runbook

> **Topología real en producción (preferida): EC2 nativo** (jars Java + nginx del
> sistema). El resto del documento describe el despliegue K8s (legacy/referencia);
> para EC2 usar la sección 1 y el template `deploy/ec2/nginx-aircargo.conf`.

## Prerequisites

- Kubernetes cluster (v1.28+) with nginx-ingress-controller and cert-manager installed
- kubectl configured with target cluster context
- Domain names pointed to ingress controller LB (A/AAAA records)
- SMTP credentials for transactional emails
- (Optional) S3-compatible bucket for backup offsite storage

---

## 1. EC2 Nativo (despliegue actual en producción)

Topología: los 10 servicios corren como jars Java 21 en `~/aircargo-saas/backend/*/target/`,
nginx del sistema en :80 (también :443) sirviendo `~/aircargo-saas/frontend/dist`, proxy
`/api/` → `localhost:8080` (gateway), Postgres y RabbitMQ del sistema. Se lanza con
`nohup ./start-13-nobuild.sh` (o plantillas systemd, ver sección 10).

### Transporte TLS (Let's Encrypt + certbot)

El template `deploy/ec2/nginx-aircargo.conf` es reload-safe: sin certificados sigue
sirviendo por :80 (nunca rompe el sitio); con `certbot --nginx` se habilita :443 +
redirect + hardening TLS.

```bash
# 1. Instalar certbot
sudo apt install -y certbot python3-certbot-nginx

# 2. Activar el template (sustituir <DOMAIN> y la ruta del dist en el bloque 443)
sudo cp deploy/ec2/nginx-aircargo.conf /etc/nginx/sites-available/aircargo
sudo ln -sf /etc/nginx/sites-available/aircargo /etc/nginx/sites-enabled/aircargo
sudo nginx -t && sudo systemctl reload nginx

# 3. Emitir/renew certificado (certbot edita el template y recarga nginx)
sudo certbot --nginx -d <DOMAIN>

# 4. Verificar renovación automática (timer daily de certbot)
sudo systemctl list-timers | grep certbot
sudo certbot renew --dry-run
```

Tras la emisión verificar:
```bash
curl -sI https://<DOMAIN>/ | grep -i "strict-transport-security"   # max-age=31536000
curl -sI https://<DOMAIN>/api/auth/health   # 401 o 200, nunca "firma inválida" en :80
```

Notas TLS:
- El gateway emite cookies httpOnly con `Secure` según `COOKIE_SECURE` (default true en
  producción). Con TLS activo en nginx, `X-Forwarded-Proto https` llega al gateway y las
  cookies se marcan Secure correctamente.
- No publicar :8080 (gateway) directamente: solo nginx debe exponer el servicio.

### Dimensionado de heap (t2/t3.small ≈ 2 GB)

10 JVM nativas (sin contenedor): usar **tamaño fijo por JVM**, no `MaxRAMPercentage`
(aunque el run de systemd expone cgroup v2, los jars a secas no lo leen). Defaults:

- scripts `start-all.sh`/`start-backend.sh`: `JAVA_OPTS` del `.env` → `-Xms256m -Xmx512m`
  por JVM (≈ 2 GB total con Hikari a `maximum-pool-size=3`). Ajustar en `.env.example`.
- systemd (`deploy/ec2/systemd/generate-units.sh --install`): `RAM_OPTS=-Xms128m -Xmx256m`
  por unidad (≈ 1.3 GB total, margen para Postgres+nginx); override global para todas las
  unidades con la variable `JAVA_MEMS` (p.ej. `JAVA_MEMS="-Xmx512m" ./generate-units.sh`).
- En contenedores (Dockerfiles/k8s) sí se usa `-XX:MaxRAMPercentage=75` (RAM-aware).

Ver memoria actual: `free -h` y `ss -tlnp` para confirmar los 10 listeners.

---

## 1. One-Time Cluster Setup

### Install cert-manager (if not present)
```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml
kubectl wait --for=condition=available deployment --all -n cert-manager --timeout=120s
```

### Install nginx-ingress-controller (if not present)
```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.service.type=LoadBalancer \
  --set controller.publishService.enabled=true
```

### Verify DNS
```bash
# Point these to your ingress LB IP:
# api.yourdomain.com      -> gateway ingress
# yourdomain.com          -> frontend ingress
dig +short api.yourdomain.com
dig +short yourdomain.com
```

---

## 2. Generate & Apply Secrets

```bash
cd /home/manolov/Projects/Rannik/aircargo-saas-Version1.3/deploy

# Option A: Interactive (prompts for SMTP)
./generate-secrets.sh - \
  --smtp-host smtp.yourprovider.com \
  --smtp-port 587 \
  --smtp-user noreply@yourdomain.com \
  --smtp-pass "YOUR_SMTP_PASSWORD" \
  --smtp-from noreply@yourdomain.com \
  | kubectl apply -f -

# Option B: With S3 backup offsite
./generate-secrets.sh - \
  --smtp-host smtp.yourprovider.com \
  --smtp-port 587 \
  --smtp-user noreply@yourdomain.com \
  --smtp-pass "YOUR_SMTP_PASSWORD" \
  --smtp-from noreply@yourdomain.com \
  --aws-key "AKIA..." \
  --aws-secret "SECRET..." \
  --aws-bucket "aircargo-backups-prod" \
  --aws-region "us-east-1" \
  | kubectl apply -f -

# Verify
kubectl get secret aircargo-secrets -n aircargo -o yaml
```

**Required secret keys**: `JWT_SECRET`, `POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`, `APP_ENCRYPTION_KEY`, `SMTP_*`, `APP_MFA_MANDATORY=true` (default), `MFA_RESET_ON_STARTUP=false`

---

## 3. Deploy to Staging

```bash
# Dry run first
./deploy/deploy.sh staging --dry-run

# Actual deploy
./deploy/deploy.sh staging

# Verify
curl -sf https://staging-api.yourdomain.com/actuator/health | jq .
curl -sf https://staging.yourdomain.com/ | head -5
```

### Staging Smoke Tests
```bash
# 1. Login
TOKEN=$(curl -s -X POST https://staging-api.yourdomain.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@yourdomain.com"}' | jq -r .token)

# 2. Authenticated request
curl -s -H "Authorization: Bearer $TOKEN" \
  https://staging-api.yourdomain.com/api/users | jq .

# 3. Check TLS
curl -sI https://staging-api.yourdomain.com/ | grep -i "strict-transport-security"
```

---

## 4. Deploy to Production

```bash
# REQUIRES CONFIRMATION - double-check context!
./deploy/deploy.sh production

# Verify all pods ready
kubectl get pods -n aircargo -o wide

# Verify health endpoints
for svc in gateway auth flight booking mawb warehouse uld load-planning export notification; do
  echo "=== $svc ==="
  kubectl exec -n aircargo deploy/aircargo-$svc -- wget -qO- http://localhost:$(kubectl get svc aircargo-$svc -n aircargo -o jsonpath='{.spec.ports[0].port}')/actuator/health 2>/dev/null | jq .status || echo "FAIL"
done

# Verify TLS certs issued
kubectl get certificates -n aircargo
kubectl get certificaterequests -n aircargo
```

---

## 5. Post-Deploy Verification Checklist

| Check | Command | Expected |
|-------|---------|----------|
| **All pods Running** | `kubectl get pods -n aircargo` | All `Running`, `Ready 1/1` |
| **Gateway health** | `curl -sf https://api.yourdomain.com/actuator/health` | `{"status":"UP"}` |
| **Frontend loads** | `curl -sf https://yourdomain.com/ \| grep -c "AirCargo"` | `>0` |
| **TLS valid** | `curl -sI https://api.yourdomain.com/ \| grep -i "strict-transport-security"` | `max-age=31536000` |
| **Cert-manager issued** | `kubectl get cert -n aircargo` | `READY=True` |
| **Secrets mounted** | `kubectl exec -n aircargo deploy/aircargo-gateway -- env \| grep JWT_SECRET` | Set |
| **SMTP works** | Check notification-service logs for "Email sent" | No errors |
| **Rate limit HA** | `for i in {1..10}; do curl -s -H "Authorization: Bearer $TOKEN" https://api.yourdomain.com/api/users; done` | Distributed across replicas |
| **Cache HA** | Update user → verify invalidated on all auth replicas | Logs show eviction |
| **Backup runs** | `kubectl get cronjob -n aircargo` | `aircargo-backup` exists |
| **Monitoring** | `curl -sf https://api.yourdomain.com/actuator/prometheus \| head -20` | Metrics present |

---

## 6. Rollback Procedure

```bash
# 1. List available backups
kubectl exec -n aircargo deploy/aircargo-auth-service -- ls -la /backups/

# 2. Restore from backup (creates protection backup first)
kubectl exec -n aircargo deploy/aircargo-auth-service -- /scripts/db-restore.sh --file /backups/aircargo_backup_20240115_020000.dump

# 3. Or use rollback script (creates flag for auto-restore on next boot)
kubectl exec -n aircargo deploy/aircargo-auth-service -- /scripts/rollback.sh --emergency

# 4. Restart services to pick up restored DB
kubectl rollout restart deployment -n aircargo
```

---

## 7. Scaling Operations

### Manual Scale
```bash
# Scale gateway to 5 replicas
kubectl scale deploy aircargo-gateway -n aircargo --replicas=5

# Scale all backend services
for svc in auth flight booking mawb warehouse uld load-planning export notification; do
  kubectl scale deploy aircargo-$svc -n aircargo --replicas=3
done
```

### HPA Tuning (already configured for gateway/frontend)
```bash
# View HPA status
kubectl get hpa -n aircargo

# Adjust thresholds
kubectl patch hpa aircargo-gateway-hpa -n aircargo -p '{"spec":{"targetCPUUtilizationPercentage":60}}'
```

---

## 8. Monitoring & Alerting

### Key Metrics to Alert On
| Metric | Threshold | Action |
|--------|-----------|--------|
| `jvm_memory_used_bytes / jvm_memory_max_bytes` | > 85% | Scale up / investigate leak |
| `http_server_requests_seconds_count{status=~"5.."}` | > 1% of total | Check logs |
| `rabbitmq_queue_messages_ready` | > 1000 | Consumer down |
| `pg_stat_activity_count` | > 120 (of 150) | Pool exhaustion |
| `cert_manager_certificate_renewal_timestamp` | < 30 days | Renewal failing |
| `aircargo_backup_last_success_timestamp` | > 26 hours | Backup failing |

### Grafana Dashboards (import from `deploy/grafana/dashboards/`)
- JVM Micrometer
- Spring Boot Stats
- RabbitMQ Overview
- PostgreSQL Overview
- NGINX Ingress Controller

---

## 9. Troubleshooting Quick Reference

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| `401 Invalid token` on valid JWT | Gateway JWT_SECRET ≠ Auth JWT_SECRET | Verify same secret in `aircargo-secrets` |
| `502 Bad Gateway` | Service not ready / crashloop | `kubectl logs -n aircargo deploy/aircargo-<svc>` |
| `Certificate not ready` | DNS not pointing to ingress LB / cert-manager not installed | Check DNS, cert-manager logs |
| `Rate limit exceeded` immediately | `RATE_LIMIT_USE_REDIS=true` but Redis down | Check Redis pod, or set `RATE_LIMIT_USE_REDIS=false` |
| `Cache not invalidating` | `SPRING_CACHE_TYPE=redis` but Redis not configured | Verify `REDIS_HOST`, `SPRING_CACHE_TYPE` |
| `MFA email not sent` | SMTP credentials wrong | Check notification-service logs, test SMTP manually |
| `Backup cronjob not running` | `Persistent=true` but no schedule | `kubectl describe cronjob aircargo-backup -n aircargo` |

---

## 10. Environment-Specific Config

### Staging (`deploy/k8s/overlays/staging/`)
- 1 replica each (cost optimized)
- Caffeine cache (in-memory)
- In-memory rate limiting
- Debug logging
- Self-signed / staging certs (Let's Encrypt staging issuer)

### Production (`deploy/k8s/overlays/production/`)
- 2-3 replicas (HA)
- Redis cache (shared)
- Redis rate limiting (shared)
- Warn/Info logging
- Production Let's Encrypt certs
- HPA enabled (3-20 gateway, 3-30 frontend)

---

## 11. Security Hardening Checklist

- [ ] NetworkPolicies applied (`09-networkpolicies.yml`) - default deny
- [ ] PodSecurityPolicies / Kyverno / OPA Gatekeeper enforcing non-root, read-only FS
- [ ] Secrets encrypted at rest (KMS provider for etcd)
- [ ] Audit logging enabled on API server
- [ ] Image vulnerability scanning in CI (Trivy/Snyk)
- [ ] Runtime security (Falco/Tetragon) detecting execs, network anomalies
- [ ] Regular pen-test / dependency audit (`mvn dependency-check:check`)

---

## 12. Contacts & Escalation

| Role | Contact | Channel |
|------|---------|---------|
| Platform/Infra | platform@yourcompany.com | #infra-alerts |
| App Dev | dev@yourcompany.com | #aircargo-dev |
| Security | security@yourcompany.com | #security-incidents |
| On-call | PagerDuty / Opsgenie | Rotation schedule |

---

**Document Version**: 1.0  
**Last Updated**: $(date -u +%Y-%m-%d)  
**Next Review**: +30 days