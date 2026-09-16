-- Pivot to uld-owned schema: move the uld-service application tables from
-- public into schema "uld".
-- Fully qualified SQL so resolution does not depend on the connection search_path.
CREATE SCHEMA IF NOT EXISTS uld;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'uld')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'uld' AND tablename = 'uld') THEN
    ALTER TABLE public.uld SET SCHEMA uld;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'uld_awb')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'uld' AND tablename = 'uld_awb') THEN
    ALTER TABLE public.uld_awb SET SCHEMA uld;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'uld_flight_operator')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'uld' AND tablename = 'uld_flight_operator') THEN
    ALTER TABLE public.uld_flight_operator SET SCHEMA uld;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'uld_piece')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'uld' AND tablename = 'uld_piece') THEN
    ALTER TABLE public.uld_piece SET SCHEMA uld;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'uld_type_catalog')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'uld' AND tablename = 'uld_type_catalog') THEN
    ALTER TABLE public.uld_type_catalog SET SCHEMA uld;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'uld_type_config')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'uld' AND tablename = 'uld_type_config') THEN
    ALTER TABLE public.uld_type_config SET SCHEMA uld;
  END IF;
END $$;