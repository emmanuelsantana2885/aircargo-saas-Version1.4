-- Elimina el sistema de plantillas de etiquetas (impresion CARGO/PALLET).
-- La tabla se creo en V3 y fue movida al schema "mawb" por V4.
-- Se resuelve en ambos schemas para ser idempotente y no depender del
-- estado del pivot (la tabla puede seguir en "public" si este nunca corrio).
-- Los indices (idx_label_template_type) caen con la tabla.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'mawb' AND tablename = 'label_template') THEN
    DROP TABLE mawb.label_template;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'label_template') THEN
    DROP TABLE public.label_template;
  END IF;
END $$;
