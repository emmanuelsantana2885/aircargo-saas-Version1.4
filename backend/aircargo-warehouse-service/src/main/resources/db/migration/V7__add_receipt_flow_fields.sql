-- Campos del flujo interactivo de recepción:
--   driver_signatures   → JSON con la lista de choferes/camiones que entregaron (una MAWB puede llegar en varios camiones)
--   operational_checks  → JSON con el checklist operativo (embalaje íntegro, documentación física, DUA)
--   cold_chain          → JSON con el seguimiento de temperatura (perecederos / cadena de frío)
--   dua_number          → número de DUA (Documento Único Administrativo) asociado
-- Idempotente y cualificado al schema del servicio (tras el pivot V5).
ALTER TABLE warehouse.warehouse_receipt
    ADD COLUMN IF NOT EXISTS driver_signatures TEXT,
    ADD COLUMN IF NOT EXISTS operational_checks TEXT,
    ADD COLUMN IF NOT EXISTS cold_chain TEXT,
    ADD COLUMN IF NOT EXISTS dua_number VARCHAR(50);

COMMENT ON COLUMN warehouse.warehouse_receipt.driver_signatures IS
    'JSON: choferes que entregaron la carga [{name, idNum, truckPlate, arrivedAt, sigUrl}]';
COMMENT ON COLUMN warehouse.warehouse_receipt.operational_checks IS
    'JSON: checklist operativo del recibo (embalaje íntegro, documentación física, DUA)';
COMMENT ON COLUMN warehouse.warehouse_receipt.cold_chain IS
    'JSON: seguimiento de cadena de frío {enabled, minC, maxC, readings:[{valueC, at}]}';
COMMENT ON COLUMN warehouse.warehouse_receipt.dua_number IS
    'Número de DUA (Documento Único Administrativo)';
