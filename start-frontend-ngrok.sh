#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="/home/manolov/Projects/Rannik/Aircargo-saas/aircargo-saas-Version1.4"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
NGROK_DOMAIN="placidly-blasphemy-freeway.ngrok-free.dev"
VITE_PORT=5173
NGROK_PORT=4040

echo "=== Iniciando entorno para acceso remoto vía ngrok ==="

if [ ! -d "$FRONTEND_DIR" ]; then
  echo "ERROR: No se encontró $FRONTEND_DIR"
  exit 1
fi

echo "Liberando puertos previos..."
pkill -f "vite.*5173" 2>/dev/null || true
pkill -f "ngrok http 5173" 2>/dev/null || true
sleep 1

echo "Levantando Vite en 0.0.0.0:$VITE_PORT..."
cd "$FRONTEND_DIR"
VITE_HOST=0.0.0.0 npm run dev -- --host 0.0.0.0 --port "$VITE_PORT" > /tmp/aircargo-vite.log 2>&1 &
VITE_PID=$!
echo "Vite PID: $VITE_PID"

echo "Esperando a que Vite esté listo..."
for i in $(seq 1 30); do
  if curl -s http://127.0.0.1:"$VITE_PORT" > /dev/null 2>&1; then
    echo "Vite listo en http://127.0.0.1:$VITE_PORT"
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "ERROR: Vite no respondió en $VITE_PORT. Revisa /tmp/aircargo-vite.log"
    kill "$VITE_PID" 2>/dev/null || true
    exit 1
  fi
  sleep 1
done

echo "Levantando ngrok con dominio $NGROK_DOMAIN..."
ngrok http "$VITE_PORT" --domain "$NGROK_DOMAIN" > /tmp/aircargo-ngrok.log 2>&1 &
NGROK_PID=$!
echo "ngrok PID: $NGROK_PID"

echo "Esperando a que ngrok esté listo..."
for i in $(seq 1 30); do
  if curl -s http://127.0.0.1:"$NGROK_PORT"/api/tunnels > /dev/null 2>&1; then
    echo "ngrok listo. Panel local: http://127.0.0.1:$NGROK_PORT"
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "ERROR: ngrok no respondió. Revisa /tmp/aircargo-ngrok.log"
    kill "$VITE_PID" 2>/dev/null || true
    kill "$NGROK_PID" 2>/dev/null || true
    exit 1
  fi
  sleep 1
done

echo ""
echo "=========================================="
echo "¡ENTORNO LISTO!"
echo "URL pública: https://$NGROK_DOMAIN"
echo "Panel ngrok: http://127.0.0.1:$NGROK_PORT"
echo "Vite local: http://127.0.0.1:$VITE_PORT"
echo "=========================================="
echo "Presiona Ctrl+C para detener todo."
echo ""

cleanup() {
  echo ""
  echo "Deteniendo servicios..."
  kill "$VITE_PID" 2>/dev/null || true
  kill "$NGROK_PID" 2>/dev/null || true
  pkill -f "vite.*5173" 2>/dev/null || true
  pkill -f "ngrok http 5173" 2>/dev/null || true
  echo "Servicios detenidos."
  exit 0
}
trap cleanup SIGINT SIGTERM

wait
