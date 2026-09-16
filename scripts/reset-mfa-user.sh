#!/bin/bash
# Script to reset MFA for a specific user on AWS EC2
# Usage: ./reset-mfa-user.sh <email>
# Example: ./reset-mfa-user.sh esantana@rannik.com

set -e

EMAIL="${1:-esantana@rannik.com}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "========================================="
echo "MFA Reset for user: $EMAIL"
echo "========================================="

# Load environment variables
if [ -f "$PROJECT_ROOT/.env" ]; then
    export $(grep -v '^#' "$PROJECT_ROOT/.env" | xargs)
fi

# Check required env vars
: "${POSTGRES_HOST:?POSTGRES_HOST not set}"
: "${POSTGRES_PORT:?POSTGRES_PORT not set}"
: "${POSTGRES_DB:?POSTGRES_DB not set}"
: "${POSTGRES_USER:?POSTGRES_USER not set}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD not set}"

# Find user ID
USER_ID=$(PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -t -c "
    SELECT id FROM app_user WHERE email = '$EMAIL';
" | xargs)

if [ -z "$USER_ID" ]; then
    echo "ERROR: User not found: $EMAIL"
    exit 1
fi

echo "Found user: $EMAIL (ID: $USER_ID)"

# Reset MFA fields
PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
    UPDATE app_user 
    SET mfa_enabled = false,
        mfa_secret = NULL,
        mfa_locked = false,
        mfa_failed_attempts = 0,
        mfa_enrolled_at = NULL,
        tokens_valid_from = now(),
        updated_at = now()
    WHERE id = '$USER_ID';
"

# Also clear auth_session table for this user
PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
    DELETE FROM auth_session WHERE user_id = '$USER_ID';
"

echo "✅ MFA reset complete for $EMAIL"
echo "The user will be prompted to re-enroll MFA on next login."