#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="/home/manolov/Projects/Rannik/Aircargo-saas/aircargo-saas-Version1.4"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
NGROK_DOMAIN="placidly-blasphemy-freeway.ngrok-free.dev"
GATEWAY_PORT=8080
VITE_PORT=5173
NGROK_WEB_PORT=4040

echo "=========================================="
echo "  Aircargo SaaS — Backend + Frontend + ngrok"
echo "=========================================="

# 1. Requisitos
for cmd in java node npm ngrok curl; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "ERROR: '$cmd' no está instalado ni en el PATH."
    exit 1
  fi
done

# 2. Limpiar puertos previos
echo "Liberando puertos previos..."
pkill -f "aircargo-gateway" 2>/dev/null || true
pkill -f "aircargo-.*-service" 2>/dev/null || true
pkill -f "vite.*5173" 2>/dev/null || true
pkill -f "ngrok http 5173" 2>/dev/null || true
sleep 2

# 3. Levantar backend
echo "Levantando backend (gateway + 10 servicios)..."
cd "$PROJECT_ROOT"
./start-backend.sh > /tmp/aircargo-backend.log 2>&1 &
BACKEND_PID=$!
echo "Backend iniciado (PID $BACKEND_PID). Logs: /tmp/aircargo-backend.log"

echo "Esperando a que el gateway responda en :$GATEWAY_PORT..."
for i in $(seq 1 60); do
  if curl -s http://127.0.0.1:"$GATEWAY_PORT"/actuator/health > /dev/null 2>&1; then
    echo "Gateway listo en http://127.0.0.1:$GATEWAY_PORT"
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "ERROR: El gateway no respondió en :$GATEWAY_PORT."
    echo "Revisa /tmp/aircargo-backend.log"
    kill "$BACKEND_PID" 2>/dev/null || true
    exit 1
  fi
  sleep 2
done

# 4. Levantar frontend expuesto a todas las interfaces
echo "Levantando frontend en 0.0.0.0:$VITE_PORT..."
cd "$FRONTEND_DIR"
VITE_HOST=0.0.0.0 npm run dev -- --host 0.0.0.0 --port "$VITE_PORT" > /tmp/aircargo-vite.log 2>&1 &
VITE_PID=$!
echo "Vite iniciado (PID $VITE_PID). Logs: /tmp/aircargo-vite.log"

echo "Esperando a que Vite esté listo..."
for i in $(seq 1 30); do
  if curl -s http://127.0.0.1:"$VITE_PORT" > /dev/null 2>&1; then
    echo "Vite listo en http://127.0.0.1:$VITE_PORT"
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "ERROR: Vite no respondió en :$VITE_PORT. Revisa /tmp/aircargo-vite.log"
    kill "$BACKEND_PID" 2>/dev/null || true
    kill "$VITE_PID" 2>/dev/null || true
    exit 1
  fi
  sleep 1
done

# 5. Levantar ngrok con dominio estático
echo "Levantando ngrok con dominio $NGROK_DOMAIN..."
ngrok http "$VITE_PORT" --domain "$NGROK_DOMAIN" > /tmp/aircargo-ngrok.log 2>&1 &
NGROK_PID=$!
echo "ngrok iniciado (PID $NGROK_PID). Logs: /tmp/aircargo-ngrok.log"

echo "Esperando a que ngrok esté listo..."
for i in $(seq 1 30); do
  if curl -s http://127.0.0.1:"$NGROK_WEB_PORT"/api/tunnels > /dev/null 2>&1; then
    echo "ngrok listo. Panel local: http://127.0.0.1:$NGROK_WEB_PORT"
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "ERROR: ngrok no respondió. Revisa /tmp/aircargo-ngrok.log"
    kill "$BACKEND_PID" 2>/dev/null || true
    kill "$VITE_PID" 2>/dev/null || true
    kill "$NGROK_PID" 2>/dev/null || true
    exit 1
  fi
  sleep 1
done

echo ""
echo "=========================================="
echo "¡SAAS COMPLETO LISTO!"
echo "URL pública (ngrok): https://$NGROK_DOMAIN"
echo "Panel ngrok:         http://127.0.0.1:$NGROK_WEB_PORT"
echo "Frontend local:      http://127.0.0.1:$VITE_PORT"
echo "Gateway backend:     http://127.0.0.1:$GATEWAY_PORT"
echo "=========================================="
echo "Presiona Ctrl+C para detener todo."
echo ""

cleanup() {
  echo ""
  echo "Deteniendo todos los servicios..."
  kill "$NGROK_PID" 2>/dev/null || true
  kill "$VITE_PID" 2>/dev/null || true
  kill "$BACKEND_PID" 2>/dev/null || true
  pkill -f "ngrok http 5173" 2>/dev/null || true
  pkill -f "vite.*5173" 2>/dev/null || true
  pkill -f "aircargo-.*-service" 2>/dev/null || true
  pkill -f "aircargo-gateway" 2>/dev/null || true
  echo "Servicios detenidos."
  exit 0
}
trap cleanup SIGINT SIGTERM

wait
