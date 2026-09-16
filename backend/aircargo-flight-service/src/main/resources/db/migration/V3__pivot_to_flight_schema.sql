-- Pivot to flight-owned schema: move the flight-service application tables
-- (including the shared airline table) from public into schema "flight".
-- Fully qualified SQL so resolution does not depend on the connection search_path.
CREATE SCHEMA IF NOT EXISTS flight;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'airline')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'flight' AND tablename = 'airline') THEN
    ALTER TABLE public.airline SET SCHEMA flight;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'flight')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'flight' AND tablename = 'flight') THEN
    ALTER TABLE public.flight SET SCHEMA flight;
  END IF;
END $$;