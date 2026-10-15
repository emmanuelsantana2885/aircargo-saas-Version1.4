-- Códigos OTP de recuperación de MFA (usuario que perdió el autenticador).
-- Después del pivot V27 las tablas de auth viven en el schema "auth", así que
-- esta tabla se crea ahí de forma explícita (no depende del search_path).
--
-- IDEMPOTENTE: CREATE ... IF NOT EXISTS, seguro en reintentos.
CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.mfa_recovery_code (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES auth.app_user(id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    attempts   INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mfa_rec_user ON auth.mfa_recovery_code (user_id);
CREATE INDEX IF NOT EXISTS idx_mfa_rec_hash ON auth.mfa_recovery_code (code_hash);

-- La tabla la gestiona auth-service por JPA (ddl-auto=validate); el rol de la
-- aplicación necesita permisos explícitos porque el schema es nuevo.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aircargo_user') THEN
    GRANT SELECT, INSERT, UPDATE, DELETE ON auth.mfa_recovery_code TO aircargo_user;
  END IF;
END $$;
