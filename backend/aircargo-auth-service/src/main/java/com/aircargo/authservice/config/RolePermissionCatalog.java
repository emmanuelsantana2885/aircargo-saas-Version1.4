package com.aircargo.authservice.config;

import com.aircargo.authservice.entity.UserRole;
import com.aircargo.common.auth.Permissions;

import java.util.*;

/**
 * Catálogo estático rol → permisos (sin tablas en BD).
 *
 * <p>Equivalencia preservada con los matchers previos de los 9
 * {@code SecurityConfig}: cada rol recibe exactamente los permisos que el
 * matcher actual le otorga (lecturas, escrituras y mutaciones especiales).
 * Las simplificaciones/ajustes requeridos se documentan aquí y en AGENTS.md.
 */
public final class RolePermissionCatalog {

    private RolePermissionCatalog() {
    }

    private static final List<String> ALL = Permissions.allCodesAsList();
    private static final Set<String> ALL_SET = Permissions.allCodes();

    private static final List<String> ALL_MINUS_SITE_LABEL;
    private static final List<String> READ_ONLY;
    private static final List<String> WAREHOUSE_ASSISTANT;
    private static final List<String> OPERATIONS;
    private static final List<String> TRAFFIC;
    private static final List<String> LOAD_PLANNER;
    private static final List<String> BI_USER;

    static {
        ALL_MINUS_SITE_LABEL = build(ALL_SET, Set.of(
                Permissions.CAN_MANAGE_SITE,
                Permissions.CAN_MANAGE_LABEL_TEMPLATE));

        READ_ONLY = build(Set.of(
                Permissions.CAN_READ_DASHBOARD,
                Permissions.CAN_READ_AIRLINE,
                Permissions.CAN_READ_FLIGHT,
                Permissions.CAN_READ_AIRCRAFT_TYPE,
                Permissions.CAN_READ_BOOKING,
                Permissions.CAN_READ_MAWB,
                Permissions.CAN_READ_ULD,
                Permissions.CAN_READ_LOAD_PLAN,
                Permissions.CAN_READ_BI,
                Permissions.CAN_READ_NOTIFICATION));

        WAREHOUSE_ASSISTANT = build(Set.of(
                Permissions.CAN_READ_DASHBOARD,
                Permissions.CAN_READ_AIRLINE,
                Permissions.CAN_READ_FLIGHT,
                Permissions.CAN_READ_AIRCRAFT_TYPE,
                Permissions.CAN_READ_MAWB,
                Permissions.CAN_READ_ULD,
                Permissions.CAN_READ_RECEIPT,
                Permissions.CAN_READ_BI,
                Permissions.CAN_READ_NOTIFICATION,
                Permissions.CAN_CREATE_RECEIPT,
                Permissions.CAN_UPDATE_RECEIPT,
                Permissions.CAN_DELETE_RECEIPT,
                Permissions.CAN_MANAGE_MAWB_DOCS,
                Permissions.CAN_PRINT_LABEL));

        OPERATIONS = build(Set.of(
                Permissions.CAN_READ_DASHBOARD,
                Permissions.CAN_READ_AIRLINE,
                Permissions.CAN_READ_FLIGHT,
                Permissions.CAN_READ_AIRCRAFT_TYPE,
                Permissions.CAN_READ_MAWB,
                Permissions.CAN_READ_ULD,
                Permissions.CAN_READ_LOAD_PLAN,
                Permissions.CAN_CREATE_FLIGHT,
                Permissions.CAN_UPDATE_FLIGHT,
                Permissions.CAN_DELETE_FLIGHT,
                Permissions.CAN_CREATE_MAWB,
                Permissions.CAN_UPDATE_MAWB,
                Permissions.CAN_DELETE_MAWB,
                Permissions.CAN_MANAGE_MAWB_DOCS,
                Permissions.CAN_PRINT_LABEL,
                Permissions.CAN_CREATE_ULD,
                Permissions.CAN_UPDATE_ULD,
                Permissions.CAN_DELETE_ULD,
                Permissions.CAN_SCAN_ULD,
                Permissions.CAN_PRINT_PALLET_LABEL,
                Permissions.CAN_CREATE_LOAD_PLAN,
                Permissions.CAN_ASSIGN_LOAD_PLAN,
                Permissions.CAN_CLOSE_LOAD_PLAN,
                Permissions.CAN_EXPORT_LOAD_PLAN,
                Permissions.CAN_EXPORT_PALLET_SHEETS,
                Permissions.CAN_IMPORT_RAMP_MANIFEST));

        TRAFFIC = build(Set.of(
                Permissions.CAN_READ_DASHBOARD,
                Permissions.CAN_READ_AIRLINE,
                Permissions.CAN_READ_FLIGHT,
                Permissions.CAN_READ_AIRCRAFT_TYPE,
                Permissions.CAN_READ_MAWB,
                Permissions.CAN_READ_ULD,
                Permissions.CAN_READ_LOAD_PLAN,
                Permissions.CAN_READ_BOOKING,
                Permissions.CAN_CREATE_BOOKING,
                Permissions.CAN_UPDATE_BOOKING,
                Permissions.CAN_IMPORT_BOOKING,
                Permissions.CAN_CREATE_MAWB,
                Permissions.CAN_UPDATE_MAWB,
                Permissions.CAN_DELETE_MAWB,
                Permissions.CAN_MANAGE_MAWB_DOCS,
                Permissions.CAN_PRINT_LABEL,
                Permissions.CAN_CREATE_ULD,
                Permissions.CAN_UPDATE_ULD,
                Permissions.CAN_DELETE_ULD,
                Permissions.CAN_SCAN_ULD,
                Permissions.CAN_PRINT_PALLET_LABEL,
                Permissions.CAN_CREATE_LOAD_PLAN,
                Permissions.CAN_ASSIGN_LOAD_PLAN,
                Permissions.CAN_CLOSE_LOAD_PLAN,
                Permissions.CAN_EXPORT_LOAD_PLAN,
                Permissions.CAN_EXPORT_PALLET_SHEETS,
                Permissions.CAN_IMPORT_RAMP_MANIFEST));

        LOAD_PLANNER = build(Set.of(
                Permissions.CAN_READ_DASHBOARD,
                Permissions.CAN_READ_AIRLINE,
                Permissions.CAN_READ_FLIGHT,
                Permissions.CAN_READ_AIRCRAFT_TYPE,
                Permissions.CAN_READ_MAWB,
                Permissions.CAN_READ_ULD,
                Permissions.CAN_READ_LOAD_PLAN,
                Permissions.CAN_READ_BOOKING,
                Permissions.CAN_CREATE_BOOKING,
                Permissions.CAN_UPDATE_BOOKING,
                Permissions.CAN_IMPORT_BOOKING,
                Permissions.CAN_CREATE_ULD,
                Permissions.CAN_UPDATE_ULD,
                Permissions.CAN_DELETE_ULD,
                Permissions.CAN_SCAN_ULD,
                Permissions.CAN_PRINT_PALLET_LABEL,
                Permissions.CAN_CREATE_LOAD_PLAN,
                Permissions.CAN_ASSIGN_LOAD_PLAN,
                Permissions.CAN_CLOSE_LOAD_PLAN,
                Permissions.CAN_EXPORT_LOAD_PLAN,
                Permissions.CAN_EXPORT_PALLET_SHEETS,
                Permissions.CAN_IMPORT_RAMP_MANIFEST));

        BI_USER = build(Set.of(
                Permissions.CAN_READ_DASHBOARD,
                Permissions.CAN_READ_AIRLINE,
                Permissions.CAN_READ_FLIGHT,
                Permissions.CAN_READ_AIRCRAFT_TYPE,
                Permissions.CAN_READ_MAWB,
                Permissions.CAN_READ_ULD,
                Permissions.CAN_READ_BOOKING,
                Permissions.CAN_READ_BI,
                Permissions.CAN_READ_REPORT));
    }

    /** Devuelve la lista de permisos CAN_* para el rol dado (lista inmutable). */
    public static List<String> codesFor(UserRole role) {
        return switch (role) {
            case SUPER_USER -> ALL;
            case ADMIN -> ALL_MINUS_SITE_LABEL;
            case READ_ONLY -> RolePermissionCatalog.READ_ONLY;
            case WAREHOUSE_ASSISTANT -> RolePermissionCatalog.WAREHOUSE_ASSISTANT;
            case OPERATIONS -> RolePermissionCatalog.OPERATIONS;
            case TRAFFIC -> RolePermissionCatalog.TRAFFIC;
            case LOAD_PLANNER -> RolePermissionCatalog.LOAD_PLANNER;
            case BI_USER -> RolePermissionCatalog.BI_USER;
        };
    }

    private static List<String> build(Set<String> codes) {
        return List.copyOf(codes);
    }

    private static List<String> build(Set<String> from, Set<String> remove) {
        Set<String> result = new LinkedHashSet<>(from);
        result.removeAll(remove);
        return List.copyOf(result);
    }
}
