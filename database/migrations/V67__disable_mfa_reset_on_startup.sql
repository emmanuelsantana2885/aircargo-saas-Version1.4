-- Desactiva el re-enrolamiento MFA forzado en cada reinicio del auth-service.
--
-- Contexto: V28 deja reset_on_startup = FALSE porque el reinicio del servicio
-- (despliegue, crash, reinicio de la maquina) invalidaba el MFA de TODOS los
-- usuarios y obligaba a re-escanear el QR en cada arranque. El requisito
-- original ("forzar re-enrolamiento en cada reinicio") es un global, no
-- vinculado a una version de despliegue concreta, asi que solo conviene
-- cuando hay un incidente de seguridad: para eso esta el reseteo manual por
-- usuario  POST /api/users/{id}/mfa/reset  (admin).
--
-- Se preservan las demas reglas de expiracion: max_age_days (7) sigue
-- caducaando el enrolamiento, por lo que un secreto de mas de 7 dias exige
-- re-enrolar aunque la politica ya no se resetee en cada arranque.
--
-- Idempotente: se resuelve en ambos schemas (la tabla pudo quedar en "public"
-- si el pivot V27__pivot_to_auth_schema nunca corrio) y se puede re-ejecutar.
DO $$
DECLARE
  target regclass;
BEGIN
  IF to_regclass('auth.mfa_policy') IS NOT NULL THEN
    target := 'auth.mfa_policy';
  ELSIF to_regclass('public.mfa_policy') IS NOT NULL THEN
    target := 'public.mfa_policy';
  ELSE
    RAISE NOTICE 'V28: mfa_policy no existe todavia — nada que hacer';
    RETURN;
  END IF;

  EXECUTE format('UPDATE %s SET reset_on_startup = FALSE WHERE reset_on_startup IS DISTINCT FROM FALSE', target);
  -- El DEFAULT de la columna tambien pasa a FALSE: una fila insertada a mano
  -- (o por un servicio futuro) no debe reintroducir el reseteo en el arranque.
  EXECUTE format('ALTER TABLE %s ALTER COLUMN reset_on_startup SET DEFAULT FALSE', target);

  RAISE NOTICE 'V28: reset_on_startup = FALSE aplicado en %', target;
END $$;
