-- Snapshot de despacho para poder RECUPERAR un load planning despachado
-- (espejo raíz de la migración V12 de aircargo-uld-service).
--
-- Al despachar un vuelo los ULDs se liberan de flight_id para reutilizarse;
-- estas columnas recuerdan de qué vuelo provienen y su estado previo, de modo
-- que la recuperación (solo ante una excepción documentada) pueda reasignarlos
-- al vuelo original.
--
-- La tabla vive en el schema "uld"; SQL totalmente cualificado.
-- IDEMPOTENTE.

ALTER TABLE uld.uld ADD COLUMN IF NOT EXISTS dispatched_flight_id UUID;
ALTER TABLE uld.uld ADD COLUMN IF NOT EXISTS status_before_dispatch VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_uld_dispatched_flight
    ON uld.uld (dispatched_flight_id);

COMMENT ON COLUMN uld.uld.dispatched_flight_id IS
    'Vuelo del que este ULD fue liberado al despachar (permite recuperar el load planning)';
COMMENT ON COLUMN uld.uld.status_before_dispatch IS
    'Estado del ULD antes del despacho; se restaura al recuperar el load planning';
