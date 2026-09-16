-- Pivot to booking-owned schema: move the booking-service application table
-- from public into schema "booking".
-- Fully qualified SQL so resolution does not depend on the connection search_path.
CREATE SCHEMA IF NOT EXISTS booking;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'booking')
     AND NOT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'booking' AND tablename = 'booking') THEN
    ALTER TABLE public.booking SET SCHEMA booking;
  END IF;
END $$;