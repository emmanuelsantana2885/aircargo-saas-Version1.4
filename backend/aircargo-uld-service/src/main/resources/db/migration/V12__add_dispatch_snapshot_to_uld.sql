-- Snapshot de despacho para poder RECUPERAR un load planning despachado.
-- Al cerrar (despachar) un vuelo, los ULDs se liberan de flight_id para que
-- vuelvan al pool reutilizable. Para poder recuperarlos si se crea una
-- excepción (problema de vuelo / desviación operativa) se guarda aquí:
--   * dispatched_flight_id  -> vuelo del que fueron liberados
--   * status_before_dispatch -> estado que tenían antes del despacho
--
-- Después del pivot (V11) la tabla vive en el schema "uld": SQL totalmente
-- cualificado para no depender del search_path de la conexión.
-- IDEMPOTENTE.

ALTER TABLE uld.uld ADD COLUMN IF NOT EXISTS dispatched_flight_id UUID;
ALTER TABLE uld.uld ADD COLUMN IF NOT EXISTS status_before_dispatch VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_uld_dispatched_flight
    ON uld.uld (dispatched_flight_id);

COMMENT ON COLUMN uld.uld.dispatched_flight_id IS
    'Vuelo del que este ULD fue liberado al despachar (permite recuperar el load planning)';
COMMENT ON COLUMN uld.uld.status_before_dispatch IS
    'Estado del ULD antes del despacho; se restaura al recuperar el load planning';
