-- Pivot to auth-owned schema: move the auth-service application tables from
-- public into schema "auth". Only tables are moved; enum types stay in public.
-- Fully qualified SQL so resolution does not depend on the connection search_path.
CREATE SCHEMA IF NOT EXISTS auth;

ALTER TABLE public.app_user SET SCHEMA auth;
ALTER TABLE public.site SET SCHEMA auth;
ALTER TABLE public.user_sites SET SCHEMA auth;
ALTER TABLE public.view_permission SET SCHEMA auth;
ALTER TABLE public.role_permission SET SCHEMA auth;
ALTER TABLE public.audit_log SET SCHEMA auth;
ALTER TABLE public.audit_event SET SCHEMA auth;
ALTER TABLE public.password_reset_token SET SCHEMA auth;
ALTER TABLE public.backup_config SET SCHEMA auth;
ALTER TABLE public.backup_history SET SCHEMA auth;
ALTER TABLE public.commodity_type_config SET SCHEMA auth;
ALTER TABLE public.mfa_policy SET SCHEMA auth;
ALTER TABLE public.auth_session SET SCHEMA auth;