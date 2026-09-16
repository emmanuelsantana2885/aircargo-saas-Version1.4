-- Pivot to mawb-owned schema: move the mawb-service application tables from
-- public into schema "mawb".
-- Fully qualified SQL so resolution does not depend on the connection search_path.
CREATE SCHEMA IF NOT EXISTS mawb;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'mawb')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'mawb' AND tablename = 'mawb') THEN
    ALTER TABLE public.mawb SET SCHEMA mawb;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'hawb')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'mawb' AND tablename = 'hawb') THEN
    ALTER TABLE public.hawb SET SCHEMA mawb;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'dua_record')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'mawb' AND tablename = 'dua_record') THEN
    ALTER TABLE public.dua_record SET SCHEMA mawb;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'label_template')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'mawb' AND tablename = 'label_template') THEN
    ALTER TABLE public.label_template SET SCHEMA mawb;
  END IF;
END $$;