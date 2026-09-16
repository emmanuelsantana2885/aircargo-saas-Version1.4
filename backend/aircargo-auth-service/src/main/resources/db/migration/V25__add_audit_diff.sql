-- Audit diff capture (4d): external_text columns for pre/post-change state on
-- the 4 dangerous user mutations (create/update/delete/block) and user events.
-- TEXT columns: diff snapshots are JSON with dynamic content (never indexed).

ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS before_value TEXT;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS after_value TEXT;

ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS before_value TEXT;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS after_value TEXT;