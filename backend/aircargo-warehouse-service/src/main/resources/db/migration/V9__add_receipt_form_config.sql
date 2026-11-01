-- Ajustes del formulario de recibo: qué campos reservados están habilitados.
-- Se mantiene una única fila (ajuste global) que solo un SUPER_USER puede cambiar.
CREATE TABLE IF NOT EXISTS warehouse.receipt_form_config (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seal_number_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    dock_bay_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by          UUID,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO warehouse.receipt_form_config (id)
SELECT gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM warehouse.receipt_form_config);

-- Valores del recibo para los campos antes reservados (solo se capturan cuando
-- el ajuste correspondiente está habilitado; quedan NULL mientras no se usen).
ALTER TABLE warehouse.warehouse_receipt
    ADD COLUMN IF NOT EXISTS seal_number VARCHAR(64);
ALTER TABLE warehouse.warehouse_receipt
    ADD COLUMN IF NOT EXISTS dock_bay VARCHAR(64);

COMMENT ON TABLE warehouse.receipt_form_config IS
    'Ajustes globales del formulario de recibo (campos reservados habilitados por un SUPER_USER)';
COMMENT ON COLUMN warehouse.warehouse_receipt.seal_number IS
    'Sello / precinto de seguridad capturado en la recepción (opcional, requiere el ajuste habilitado)';
COMMENT ON COLUMN warehouse.warehouse_receipt.dock_bay IS
    'Muelle / bahía asignada a la recepción (opcional, requiere el ajuste habilitado)';
