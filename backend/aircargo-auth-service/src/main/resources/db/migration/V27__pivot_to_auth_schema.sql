-- Pivot to auth-owned schema: move the auth-service application tables from
-- public into schema "auth". Only tables are moved; enum types stay in public.
-- Fully qualified SQL so resolution does not depend on the connection search_path.
--
-- IDEMPOTENT: every move is guarded by "still in public AND not already in auth".
-- This matters because a bare "ALTER TABLE public.X SET SCHEMA auth" aborts the
-- whole migration (and, because Flyway wraps each file in a transaction, leaves
-- zero rows applied) on any database where the pivot already happened or where
-- the table was never created in public. Same guard style as the flight/booking/
-- mawb/uld/warehouse pivots.
CREATE SCHEMA IF NOT EXISTS auth;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'app_user')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'app_user') THEN
    ALTER TABLE public.app_user SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'site')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'site') THEN
    ALTER TABLE public.site SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'user_sites')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'user_sites') THEN
    ALTER TABLE public.user_sites SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'view_permission')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'view_permission') THEN
    ALTER TABLE public.view_permission SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'role_permission')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'role_permission') THEN
    ALTER TABLE public.role_permission SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'audit_log')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'audit_log') THEN
    ALTER TABLE public.audit_log SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'audit_event')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'audit_event') THEN
    ALTER TABLE public.audit_event SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'password_reset_token')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'password_reset_token') THEN
    ALTER TABLE public.password_reset_token SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'backup_config')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'backup_config') THEN
    ALTER TABLE public.backup_config SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'backup_history')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'backup_history') THEN
    ALTER TABLE public.backup_history SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'commodity_type_config')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'commodity_type_config') THEN
    ALTER TABLE public.commodity_type_config SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'mfa_policy')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'mfa_policy') THEN
    ALTER TABLE public.mfa_policy SET SCHEMA auth;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'auth_session')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'auth' AND tablename = 'auth_session') THEN
    ALTER TABLE public.auth_session SET SCHEMA auth;
  END IF;
END $$;
