-- Pivot to per-service schemas (combined mirror of the six per-service pivot
-- migrations). Moves each service's application tables from public into its own
-- schema. Idempotent: each ALTER runs only if the table is still in public and
-- not already present in the target schema (allows reruns and fresh databases).
-- Only tables are moved; enum types stay in public. Fully qualified SQL.
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS flight;
CREATE SCHEMA IF NOT EXISTS booking;
CREATE SCHEMA IF NOT EXISTS mawb;
CREATE SCHEMA IF NOT EXISTS warehouse;
CREATE SCHEMA IF NOT EXISTS uld;

DO $$
BEGIN
  -- auth
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='app_user') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='app_user') THEN ALTER TABLE public.app_user SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='site') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='site') THEN ALTER TABLE public.site SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='user_sites') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='user_sites') THEN ALTER TABLE public.user_sites SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='view_permission') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='view_permission') THEN ALTER TABLE public.view_permission SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='role_permission') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='role_permission') THEN ALTER TABLE public.role_permission SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='audit_log') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='audit_log') THEN ALTER TABLE public.audit_log SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='audit_event') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='audit_event') THEN ALTER TABLE public.audit_event SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='password_reset_token') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='password_reset_token') THEN ALTER TABLE public.password_reset_token SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='backup_config') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='backup_config') THEN ALTER TABLE public.backup_config SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='backup_history') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='backup_history') THEN ALTER TABLE public.backup_history SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='commodity_type_config') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='commodity_type_config') THEN ALTER TABLE public.commodity_type_config SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='mfa_policy') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='mfa_policy') THEN ALTER TABLE public.mfa_policy SET SCHEMA auth; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='auth_session') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='auth' AND tablename='auth_session') THEN ALTER TABLE public.auth_session SET SCHEMA auth; END IF;

  -- flight
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='airline') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='flight' AND tablename='airline') THEN ALTER TABLE public.airline SET SCHEMA flight; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='flight') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='flight' AND tablename='flight') THEN ALTER TABLE public.flight SET SCHEMA flight; END IF;

  -- booking
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='booking') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='booking' AND tablename='booking') THEN ALTER TABLE public.booking SET SCHEMA booking; END IF;

  -- mawb
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='mawb') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='mawb' AND tablename='mawb') THEN ALTER TABLE public.mawb SET SCHEMA mawb; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='hawb') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='mawb' AND tablename='hawb') THEN ALTER TABLE public.hawb SET SCHEMA mawb; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='dua_record') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='mawb' AND tablename='dua_record') THEN ALTER TABLE public.dua_record SET SCHEMA mawb; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='label_template') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='mawb' AND tablename='label_template') THEN ALTER TABLE public.label_template SET SCHEMA mawb; END IF;

  -- warehouse
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='warehouse_receipt') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='warehouse' AND tablename='warehouse_receipt') THEN ALTER TABLE public.warehouse_receipt SET SCHEMA warehouse; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='receipt_piece') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='warehouse' AND tablename='receipt_piece') THEN ALTER TABLE public.receipt_piece SET SCHEMA warehouse; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='receipt_calc_config') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='warehouse' AND tablename='receipt_calc_config') THEN ALTER TABLE public.receipt_calc_config SET SCHEMA warehouse; END IF;

  -- uld
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='uld') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='uld' AND tablename='uld') THEN ALTER TABLE public.uld SET SCHEMA uld; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='uld_awb') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='uld' AND tablename='uld_awb') THEN ALTER TABLE public.uld_awb SET SCHEMA uld; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='uld_flight_operator') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='uld' AND tablename='uld_flight_operator') THEN ALTER TABLE public.uld_flight_operator SET SCHEMA uld; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='uld_piece') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='uld' AND tablename='uld_piece') THEN ALTER TABLE public.uld_piece SET SCHEMA uld; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='uld_type_catalog') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='uld' AND tablename='uld_type_catalog') THEN ALTER TABLE public.uld_type_catalog SET SCHEMA uld; END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='uld_type_config') AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='uld' AND tablename='uld_type_config') THEN ALTER TABLE public.uld_type_config SET SCHEMA uld; END IF;
END $$;