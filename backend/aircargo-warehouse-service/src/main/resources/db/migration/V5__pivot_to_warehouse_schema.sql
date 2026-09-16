-- Pivot to warehouse-owned schema: move the warehouse-service application
-- tables from public into schema "warehouse".
-- Fully qualified SQL so resolution does not depend on the connection search_path.
CREATE SCHEMA IF NOT EXISTS warehouse;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'warehouse_receipt')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'warehouse' AND tablename = 'warehouse_receipt') THEN
    ALTER TABLE public.warehouse_receipt SET SCHEMA warehouse;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'receipt_piece')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'warehouse' AND tablename = 'receipt_piece') THEN
    ALTER TABLE public.receipt_piece SET SCHEMA warehouse;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'receipt_calc_config')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'warehouse' AND tablename = 'receipt_calc_config') THEN
    ALTER TABLE public.receipt_calc_config SET SCHEMA warehouse;
  END IF;
END $$;