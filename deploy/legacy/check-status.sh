#!/bin/bash
# Quick status check for Aircargo SaaS on EC2

echo "═══════════════════════════════════════════════"
echo "  Aircargo SaaS — Status Check"
echo "═══════════════════════════════════════════════"

cd ~/aircargo-saas 2>/dev/null || { echo "❌ App directory not found"; exit 1; }

echo ""
echo "▸ Docker containers:"
docker compose ps -a

echo ""
echo "▸ Disk usage:"
df -h / | awk 'NR==2 {printf "  %s used of %s (%s)\n", $3, $2, $5}'

echo ""
echo "▸ Docker disk:"
docker system df 2>/dev/null

echo ""
echo "▸ Port check:"
for port in 80 9091 5432; do
  if ss -tlnp | grep -q ":${port} "; then
    echo "  ✓ Port ${port} open"
  else
    echo "  ✗ Port ${port} NOT listening"
  fi
done

echo ""
echo "▸ Health check:"
if curl -sf http://localhost/ > /dev/null 2>&1; then
  echo "  ✓ Frontend responding"
else
  echo "  ✗ Frontend NOT responding"
fi

if curl -sf http://localhost:9091/api/ > /dev/null 2>&1; then
  echo "  ✓ Backend responding"
else
  echo "  ✗ Backend NOT responding (may need auth)"
fi

echo ""
echo "▸ Recent backend logs (last 20 lines):"
docker compose logs --tail=20 backend 2>/dev/null

PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo "YOUR_PUBLIC_IP")
echo ""
echo "═══════════════════════════════════════════════"
echo "  App URL: http://${PUBLIC_IP}"
echo "═══════════════════════════════════════════════"
