-- Perfil de cálculo de recibos por aerolínea (NULL = default global)
CREATE TABLE IF NOT EXISTS receipt_calc_config (
    id               UUID PRIMARY KEY,
    airline_id       UUID UNIQUE,
    dim_factor_dom   INTEGER NOT NULL DEFAULT 194,
    dim_factor_intl  INTEGER NOT NULL DEFAULT 366,
    chargeable_method VARCHAR(10) NOT NULL DEFAULT 'MAX',
    round_up_kg      NUMERIC(10, 3) NOT NULL DEFAULT 0,
    round_up_lbs     NUMERIC(10, 3) NOT NULL DEFAULT 0,
    min_chargeable_kg  NUMERIC(10, 3) NOT NULL DEFAULT 0,
    min_chargeable_lbs NUMERIC(10, 3) NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ DEFAULT now(),
    updated_at       TIMESTAMPTZ DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_receipt_calc_default
    ON receipt_calc_config ((airline_id IS NULL)) WHERE airline_id IS NULL;

-- Parámetros resueltos persistidos en cada recibo (histórico conservado)
ALTER TABLE warehouse_receipt ADD COLUMN IF NOT EXISTS chargeable_method VARCHAR(10);
ALTER TABLE warehouse_receipt ADD COLUMN IF NOT EXISTS round_up_kg NUMERIC(10, 3) DEFAULT 0;
ALTER TABLE warehouse_receipt ADD COLUMN IF NOT EXISTS round_up_lbs NUMERIC(10, 3) DEFAULT 0;
ALTER TABLE warehouse_receipt ADD COLUMN IF NOT EXISTS min_chargeable_kg NUMERIC(10, 3) DEFAULT 0;
ALTER TABLE warehouse_receipt ADD COLUMN IF NOT EXISTS min_chargeable_lbs NUMERIC(10, 3) DEFAULT 0;

-- Fila default global única (se crea solo si no existe; nunca sobrescribe cambios del admin)
INSERT INTO receipt_calc_config (id)
SELECT gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM receipt_calc_config WHERE airline_id IS NULL);