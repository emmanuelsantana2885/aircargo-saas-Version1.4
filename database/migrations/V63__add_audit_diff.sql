-- Audit diff capture (auth-service V25 parity). Adds pre/post-change state
-- columns for the 4 dangerous user mutations (create/update/delete/block) on
-- the shared audit_log/audit_event tables.

ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS before_value TEXT;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS after_value TEXT;

ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS before_value TEXT;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS after_value TEXT;