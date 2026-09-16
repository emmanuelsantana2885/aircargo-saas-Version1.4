#!/bin/bash
set -euo pipefail

# ─── Aircargo SaaS — HTTPS Setup Script ───
# Run this ON the EC2 instance after the app is already running on HTTP
# Usage: chmod +x setup-https.sh && ./setup-https.sh
#
# How it works:
#   1. Obtains a Let's Encrypt certificate for $DOMAIN (standalone).
#   2. Writes DOMAIN + CORS_ORIGINS into .env.
#   3. Restarts the frontend container — its entrypoint detects the cert
#      and generates the HTTPS nginx config automatically.
#   4. Installs a cron job to renew the certificate.

echo "═══════════════════════════════════════════════"
echo "  Aircargo SaaS — HTTPS Setup"
echo "═══════════════════════════════════════════════"

# ── 1. Get domain ─────────────────────────────
if [ -z "${DOMAIN:-}" ]; then
  read -p "Enter your domain (e.g. app.rannik.com): " DOMAIN
fi

if [ -z "$DOMAIN" ]; then
  echo "ERROR: Domain is required"
  exit 1
fi

PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo "")
echo ""
echo "  Domain:     $DOMAIN"
echo "  Public IP:  $PUBLIC_IP"
echo ""
echo "  ⚠  Make sure DNS A record points: $DOMAIN → $PUBLIC_IP"
read -p "Press Enter to continue..."

# ── 2. Install Certbot ────────────────────────
echo ""
echo "▸ Installing Certbot..."
sudo apt-get update -qq
sudo apt-get install -y -qq certbot

# ── 3. Stop nginx temporarily ─────────────────
echo "▸ Stopping nginx container..."
docker stop aircargo-web 2>/dev/null || true

# ── 4. Get certificate ────────────────────────
echo "▸ Requesting SSL certificate for $DOMAIN..."
sudo certbot certonly --standalone \
  -d "$DOMAIN" \
  --non-interactive \
  --agree-tos \
  --email "admin@rannik.com" \
  --keep-until-expiring

echo "✓ Certificate obtained"

# ── 5. Write DOMAIN + CORS into .env ──────────
APP_DIR="$HOME/aircargo-saas"
cd "$APP_DIR"

if [ ! -f .env ]; then
  echo "ERROR: .env not found in $APP_DIR"
  exit 1
fi

# DOMAIN
if grep -q "^DOMAIN=" .env; then
  sed -i "s|^DOMAIN=.*|DOMAIN=${DOMAIN}|" .env
else
  echo "DOMAIN=${DOMAIN}" >> .env
fi

# CORS_ORIGINS
if grep -q "^CORS_ORIGINS=" .env; then
  sed -i "s|^CORS_ORIGINS=.*|CORS_ORIGINS=https://${DOMAIN},http://${DOMAIN},http://localhost|" .env
else
  echo "CORS_ORIGINS=https://${DOMAIN},http://${DOMAIN},http://localhost" >> .env
fi

echo "✓ .env updated (DOMAIN + CORS_ORIGINS)"

# ── 6. Rebuild and restart ────────────────────
echo "▸ Rebuilding and restarting containers..."
docker compose down
docker compose up -d --build

# ── 7. Wait for health ────────────────────────
echo "▸ Waiting for services..."
sleep 10

# ── 8. Setup auto-renewal cron ────────────────
echo "▸ Setting up certificate auto-renewal..."
(sudo crontab -l 2>/dev/null | grep -v "certbot renew" ; echo "0 3 * * * cd $APP_DIR && docker compose run --rm certbot renew --quiet && docker compose restart frontend") | sudo crontab -
echo "✓ Auto-renewal cron installed"

# ── 9. Summary ──────────────────────────────
echo ""
echo "═══════════════════════════════════════════════"
echo "  ✓ HTTPS Setup Complete!"
echo "═══════════════════════════════════════════════"
echo ""
echo "  HTTPS:   https://$DOMAIN"
echo "  HTTP:    http://$DOMAIN (redirects to HTTPS)"
echo ""
echo "  Certificate expires: $(sudo certbot certificates 2>/dev/null | grep -A2 "Certificate Name" | grep "Expiry" || echo 'check manually')"
echo "  Auto-renewal: cron job at 3:00 AM daily"
echo ""
echo "  Verify: curl -I https://$DOMAIN"
echo ""
