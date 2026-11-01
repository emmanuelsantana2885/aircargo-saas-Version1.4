-- Espejo raíz de la migración del warehouse-service
-- (backend/aircargo-warehouse-service/.../V8__add_received_temperature.sql).
-- Temperatura de recepción de la cadena de frío / perecederos.
ALTER TABLE warehouse.warehouse_receipt
    ADD COLUMN IF NOT EXISTS received_temp_c NUMERIC(6, 2);

ALTER TABLE warehouse.warehouse_receipt
    ADD COLUMN IF NOT EXISTS cold_chain_ok BOOLEAN;

COMMENT ON COLUMN warehouse.warehouse_receipt.received_temp_c IS
    'Temperatura (°C) registrada en el momento de la recepción para carga refrigerada / perecederos';
COMMENT ON COLUMN warehouse.warehouse_receipt.cold_chain_ok IS
    'Resultado de la verificación de cadena de frío en recepción (true = sin excursión)';
