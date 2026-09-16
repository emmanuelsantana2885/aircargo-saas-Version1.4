package com.aircargo.common.auth;

import java.util.List;
import java.util.Set;

/**
 * Catálogo estático de permisos de la aplicación (RBAC por permisos).
 *
 * <p>Los permisos viajan como claim {@code permissions} en el JWT de acceso y se
 * convierten en {@code GrantedAuthority} en {@link JwtAuthFilter} (formato
 * {@code CAN_*} en el corazón de cada matcher de {@code SecurityConfig}).
 *
 * <p>La asignación rol → permisos es ESTÁTICA y vive en auth-service
 * ({@code com.aircargo.authservice.config.RolePermissionCatalog}): no hay tablas
 * de permiso en BD. La autoridad del rol se conserva además en el token como
 * fallback (un permiso {@code CAN_*} nunca coincide con una autoridad de rol).
 */
public final class Permissions {

    private Permissions() {
    }

    public static final String CAN_READ_DASHBOARD = "CAN_READ_DASHBOARD";
    public static final String CAN_READ_SITE = "CAN_READ_SITE";
    public static final String CAN_MANAGE_SITE = "CAN_MANAGE_SITE";

    public static final String CAN_READ_AIRLINE = "CAN_READ_AIRLINE";
    public static final String CAN_MANAGE_AIRLINE = "CAN_MANAGE_AIRLINE";

    public static final String CAN_READ_FLIGHT = "CAN_READ_FLIGHT";
    public static final String CAN_CREATE_FLIGHT = "CAN_CREATE_FLIGHT";
    public static final String CAN_UPDATE_FLIGHT = "CAN_UPDATE_FLIGHT";
    public static final String CAN_DELETE_FLIGHT = "CAN_DELETE_FLIGHT";
    public static final String CAN_READ_AIRCRAFT_TYPE = "CAN_READ_AIRCRAFT_TYPE";

    public static final String CAN_READ_BOOKING = "CAN_READ_BOOKING";
    public static final String CAN_CREATE_BOOKING = "CAN_CREATE_BOOKING";
    public static final String CAN_UPDATE_BOOKING = "CAN_UPDATE_BOOKING";
    public static final String CAN_DELETE_BOOKING = "CAN_DELETE_BOOKING";
    public static final String CAN_IMPORT_BOOKING = "CAN_IMPORT_BOOKING";

    public static final String CAN_READ_MAWB = "CAN_READ_MAWB";
    public static final String CAN_CREATE_MAWB = "CAN_CREATE_MAWB";
    public static final String CAN_UPDATE_MAWB = "CAN_UPDATE_MAWB";
    public static final String CAN_DELETE_MAWB = "CAN_DELETE_MAWB";
    public static final String CAN_MANAGE_MAWB_DOCS = "CAN_MANAGE_MAWB_DOCS";
    public static final String CAN_MANAGE_COMPLIANCE = "CAN_MANAGE_COMPLIANCE";
    public static final String CAN_MANAGE_LABEL_TEMPLATE = "CAN_MANAGE_LABEL_TEMPLATE";
    public static final String CAN_PRINT_LABEL = "CAN_PRINT_LABEL";
    public static final String CAN_PRINT_PALLET_LABEL = "CAN_PRINT_PALLET_LABEL";

    public static final String CAN_READ_RECEIPT = "CAN_READ_RECEIPT";
    public static final String CAN_CREATE_RECEIPT = "CAN_CREATE_RECEIPT";
    public static final String CAN_UPDATE_RECEIPT = "CAN_UPDATE_RECEIPT";
    public static final String CAN_DELETE_RECEIPT = "CAN_DELETE_RECEIPT";
    public static final String CAN_MANAGE_RECEIPT_CALC = "CAN_MANAGE_RECEIPT_CALC";

    public static final String CAN_READ_ULD = "CAN_READ_ULD";
    public static final String CAN_CREATE_ULD = "CAN_CREATE_ULD";
    public static final String CAN_UPDATE_ULD = "CAN_UPDATE_ULD";
    public static final String CAN_DELETE_ULD = "CAN_DELETE_ULD";
    public static final String CAN_SCAN_ULD = "CAN_SCAN_ULD";
    public static final String CAN_MANAGE_ULD_TYPE = "CAN_MANAGE_ULD_TYPE";

    public static final String CAN_READ_LOAD_PLAN = "CAN_READ_LOAD_PLAN";
    public static final String CAN_CREATE_LOAD_PLAN = "CAN_CREATE_LOAD_PLAN";
    public static final String CAN_ASSIGN_LOAD_PLAN = "CAN_ASSIGN_LOAD_PLAN";
    public static final String CAN_CLOSE_LOAD_PLAN = "CAN_CLOSE_LOAD_PLAN";
    public static final String CAN_EXPORT_LOAD_PLAN = "CAN_EXPORT_LOAD_PLAN";
    public static final String CAN_EXPORT_PALLET_SHEETS = "CAN_EXPORT_PALLET_SHEETS";
    public static final String CAN_IMPORT_RAMP_MANIFEST = "CAN_IMPORT_RAMP_MANIFEST";

    public static final String CAN_READ_BI = "CAN_READ_BI";
    public static final String CAN_READ_REPORT = "CAN_READ_REPORT";

    public static final String CAN_READ_NOTIFICATION = "CAN_READ_NOTIFICATION";
    public static final String CAN_MANAGE_NOTIFICATION = "CAN_MANAGE_NOTIFICATION";

    public static final String CAN_MANAGE_USER = "CAN_MANAGE_USER";
    public static final String CAN_MANAGE_ROLES = "CAN_MANAGE_ROLES";
    public static final String CAN_READ_AUDIT = "CAN_READ_AUDIT";
    public static final String CAN_MANAGE_COMMODITY_TYPE = "CAN_MANAGE_COMMODITY_TYPE";
    public static final String CAN_MANAGE_BACKUP = "CAN_MANAGE_BACKUP";

    /** Todos los códigos del catálogo, como Set inmutable. */
    public static Set<String> allCodes() {
        return Set.copyOf(allCodesAsList());
    }

    /** Todos los códigos del catálogo, como List inmutable (orden estable). */
    public static List<String> allCodesAsList() {
        return List.of(
                CAN_READ_DASHBOARD,
                CAN_READ_SITE, CAN_MANAGE_SITE,
                CAN_READ_AIRLINE, CAN_MANAGE_AIRLINE,
                CAN_READ_FLIGHT, CAN_CREATE_FLIGHT, CAN_UPDATE_FLIGHT, CAN_DELETE_FLIGHT,
                CAN_READ_AIRCRAFT_TYPE,
                CAN_READ_BOOKING, CAN_CREATE_BOOKING, CAN_UPDATE_BOOKING, CAN_DELETE_BOOKING,
                CAN_IMPORT_BOOKING,
                CAN_READ_MAWB, CAN_CREATE_MAWB, CAN_UPDATE_MAWB, CAN_DELETE_MAWB,
                CAN_MANAGE_MAWB_DOCS, CAN_MANAGE_COMPLIANCE, CAN_MANAGE_LABEL_TEMPLATE,
                CAN_PRINT_LABEL, CAN_PRINT_PALLET_LABEL,
                CAN_READ_RECEIPT, CAN_CREATE_RECEIPT, CAN_UPDATE_RECEIPT, CAN_DELETE_RECEIPT,
                CAN_MANAGE_RECEIPT_CALC,
                CAN_READ_ULD, CAN_CREATE_ULD, CAN_UPDATE_ULD, CAN_DELETE_ULD, CAN_SCAN_ULD,
                CAN_MANAGE_ULD_TYPE,
                CAN_READ_LOAD_PLAN, CAN_CREATE_LOAD_PLAN, CAN_ASSIGN_LOAD_PLAN,
                CAN_CLOSE_LOAD_PLAN, CAN_EXPORT_LOAD_PLAN, CAN_EXPORT_PALLET_SHEETS,
                CAN_IMPORT_RAMP_MANIFEST,
                CAN_READ_BI, CAN_READ_REPORT,
                CAN_READ_NOTIFICATION, CAN_MANAGE_NOTIFICATION,
                CAN_MANAGE_USER, CAN_MANAGE_ROLES, CAN_READ_AUDIT,
                CAN_MANAGE_COMMODITY_TYPE, CAN_MANAGE_BACKUP
        );
    }
}