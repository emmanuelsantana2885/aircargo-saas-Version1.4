-- Sesión 5: rotación de refresh tokens con detección de reuso (gracia → revoke).
-- auth_session: una fila por refresh token emitido; en BD solo vive el SHA-256
-- del JWT (token_hash), nunca el token crudo.
--
-- Flujo de rotación:
--   · login            → se inserta la fila del refresh token emitido.
--   · refresh exitoso  → UPDATE atómico rotated_at=now()+replaced_by sobre la
--                        fila (WHERE rotated_at IS NULL) y se inserta el sucesor.
--   · reuso de un token ya rotado (colisión multi-pestaña legítima) → primera
--     vez se rota "en gracia" (grace_used=true, sin revocar).
--   · segundo reuso del MISMO token → robo → revocación de TODA la familia del
--     usuario (revoked=true en todas sus filas + tokens_valid_from adelantado).
--
-- Purga: filas con expires_at muy antiguo se limpian por @Scheduled (6h);
-- expires_at queda indexado para un DELETE barato.

CREATE TABLE IF NOT EXISTS auth_session (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    rotated_at  TIMESTAMPTZ,
    replaced_by VARCHAR(64),
    grace_used  BOOLEAN NOT NULL DEFAULT false,
    revoked     BOOLEAN NOT NULL DEFAULT false,
    created_by  VARCHAR(20) NOT NULL DEFAULT 'LOGIN',
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_session_token_hash ON auth_session(token_hash);
CREATE INDEX IF NOT EXISTS idx_auth_session_user_id     ON auth_session(user_id);
CREATE INDEX IF NOT EXISTS idx_auth_session_expires_at  ON auth_session(expires_at);

-- Lockout de MFA: contador de intentos TOTP fallidos (espejo del contador de
-- contraseña failed_login_attempts). Al alcanzar el umbral (5) mfa_locked=true.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS mfa_failed_attempts INTEGER NOT NULL DEFAULT 0;