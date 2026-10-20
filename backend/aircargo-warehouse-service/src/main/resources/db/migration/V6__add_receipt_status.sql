-- Estado del recibo: DRAFT (borrador guardado) | EMITTED (emitido/final).
-- Los recibos previos quedan EMITTED por defecto. Idempotente y cualificado al
-- schema del servicio (tras el pivot V5).
ALTER TABLE warehouse.warehouse_receipt
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'EMITTED';

CREATE INDEX IF NOT EXISTS idx_warehouse_receipt_status
    ON warehouse.warehouse_receipt (status);

COMMENT ON COLUMN warehouse.warehouse_receipt.status IS
    'Estado del recibo: DRAFT = borrador guardado (no cuenta como recibido); EMITTED = recibo emitido/final';
