-- Estado del recibo: DRAFT (borrador guardado) | EMITTED (emitido/final).
-- Espejo raíz de la migración V6 del warehouse-service. Idempotente.
ALTER TABLE warehouse.warehouse_receipt
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'EMITTED';

CREATE INDEX IF NOT EXISTS idx_warehouse_receipt_status
    ON warehouse.warehouse_receipt (status);

COMMENT ON COLUMN warehouse.warehouse_receipt.status IS
    'Estado del recibo: DRAFT = borrador guardado (no cuenta como recibido); EMITTED = recibo emitido/final';
