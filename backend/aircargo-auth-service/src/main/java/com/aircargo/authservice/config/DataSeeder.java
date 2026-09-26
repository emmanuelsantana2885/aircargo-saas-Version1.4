package com.aircargo.authservice.config;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.RolePermission;
import com.aircargo.authservice.entity.Site;
import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.entity.ViewPermission;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.repository.AirlineRepository;
import com.aircargo.authservice.repository.RolePermissionRepository;
import com.aircargo.authservice.repository.SiteRepository;
import com.aircargo.authservice.repository.ViewPermissionRepository;
import com.aircargo.authservice.service.CommodityTypeService;
import com.aircargo.common.entity.Airline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import org.springframework.dao.DataAccessException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Seeds master data (airline, sites, users, site assignments, view/role permissions)
 * on startup so a fresh database is immediately usable.
 *
 * Idempotent: existing rows are never duplicated or overwritten.
 * Disabled under the "test" profile (H2 integration tests seed their own data).
 */
@Component
@Profile("!test")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final UUID UPS_AIRLINE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /**
     * flight.airline es single-writer de flight-service: auth NUNCA escribe ahí.
     * Solo resuelve la aerolínea UPS por lectura, reintentando brevemente por si
     * flight-service aún no ha aplicado su V1__init.sql (BD nueva arrancando en paralelo).
     */
    private static final int AIRLINE_RESOLVE_MAX_ATTEMPTS = 15;
    private static final long AIRLINE_RESOLVE_RETRY_MS = 1000L;

    private static final UUID SDQ_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STI_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID PUJ_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID MIA_ID = UUID.fromString("00000000-0000-0000-0000-000000000104");

    private final AirlineRepository airlineRepository;
    private final SiteRepository siteRepository;
    private final AppUserRepository appUserRepository;
    private final ViewPermissionRepository viewPermissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final CommodityTypeService commodityTypeService;

    public DataSeeder(AirlineRepository airlineRepository,
                      SiteRepository siteRepository,
                      AppUserRepository appUserRepository,
                      ViewPermissionRepository viewPermissionRepository,
                      RolePermissionRepository rolePermissionRepository,
                      CommodityTypeService commodityTypeService) {
        this.airlineRepository = airlineRepository;
        this.siteRepository = siteRepository;
        this.appUserRepository = appUserRepository;
        this.viewPermissionRepository = viewPermissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.commodityTypeService = commodityTypeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Airline ups = resolveUpsAirline();
        List<Site> sites = seedSites();
        Site sdq = sites.stream().filter(s -> "SDQ".equals(s.getCode())).findFirst().orElse(sites.get(0));
        if (ups != null) {
            seedUsers(ups, sdq);
        } else {
            log.error("DataSeeder: aerolínea UPS no resuelta en flight.airline — se omite el seed de usuarios (el ownership es de flight-service).");
        }
        seedViewPermissions();
        seedRolePermissions();
        seedCommodityTypes();
        log.info("DataSeeder: master data verified (airline, sites, users, permissions, commodity types)");
    }

    /**
     * READ-ONLY: auth ya no crea ni actualiza flight.airline (single-writer = flight-service).
     * Resuelve UPS por lectura con reintento (~15s) para tolerar el arranque paralelo en BD nueva.
     */
    private Airline resolveUpsAirline() {
        for (int attempt = 1; attempt <= AIRLINE_RESOLVE_MAX_ATTEMPTS; attempt++) {
            try {
                Optional<Airline> ups = airlineRepository.findByCode("UPS");
                if (ups.isPresent()) {
                    return ups.get();
                }
            } catch (DataAccessException e) {
                log.debug("DataSeeder: flight.airline aún no disponible (intento {}/{})", attempt, AIRLINE_RESOLVE_MAX_ATTEMPTS);
            }
            if (attempt < AIRLINE_RESOLVE_MAX_ATTEMPTS) {
                sleep(AIRLINE_RESOLVE_RETRY_MS);
            }
        }
        log.error("DataSeeder: aerolínea UPS no encontrada en flight.airline tras {} intentos (~{}s) — read-only, no se siembra.",
                AIRLINE_RESOLVE_MAX_ATTEMPTS, (AIRLINE_RESOLVE_MAX_ATTEMPTS * AIRLINE_RESOLVE_RETRY_MS) / 1000);
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DataSeeder: interrumpido durante la resolución de aerolínea", e);
        }
    }

    private List<Site> seedSites() {
        List<Site> existing = siteRepository.findAll();
        if (existing.size() >= 4) {
            return existing;
        }
        seedSite(SDQ_ID, "SDQ", "Santo Domingo", "DO");
        seedSite(STI_ID, "STI", "Santiago", "DO");
        seedSite(PUJ_ID, "PUJ", "Punta Cana", "DO");
        seedSite(MIA_ID, "MIA", "Miami", "US");
        return siteRepository.findAll();
    }

    private void seedSite(UUID id, String code, String name, String country) {
        if (siteRepository.existsByCode(code)) {
            return;
        }
        Site site = Site.builder()
                .id(id)
                .code(code)
                .name(name)
                .country(country)
                .isActive(true)
                .build();
        siteRepository.save(site);
        log.info("DataSeeder: seeded site {}", code);
    }

    private void seedUsers(Airline ups, Site sdq) {
        Map<String, UserRole> users = new LinkedHashMap<>();
        users.put("readonly@aircargo.com", UserRole.READ_ONLY);
        users.put("warehouse@aircargo.com", UserRole.WAREHOUSE_ASSISTANT);
        users.put("operations@aircargo.com", UserRole.OPERATIONS);
        users.put("traffic@aircargo.com", UserRole.TRAFFIC);
        users.put("loadplanner@aircargo.com", UserRole.LOAD_PLANNER);
        users.put("admin@aircargo.com", UserRole.ADMIN);
        users.put("supervisor@aircargo.com", UserRole.SUPER_USER);
        users.put("jsantos@rannik.com", UserRole.ADMIN);
        users.put("esantana@rannik.com", UserRole.SUPER_USER);
        users.put("dchestaro@rannik.com", UserRole.OPERATIONS);
        users.put("ilsantana@rannik.com", UserRole.WAREHOUSE_ASSISTANT);
        users.put("earellano@ups.com", UserRole.TRAFFIC);
        users.put("jcastrolopez@ups.com", UserRole.LOAD_PLANNER);
        users.put("bi@rannik.com", UserRole.BI_USER);

        users.forEach((email, role) -> {
            if (appUserRepository.existsByEmail(email)) {
                return;
            }
            String fullName = fullNameFor(email, role);
            AppUser user = AppUser.builder()
                    .airline(ups)
                    .email(email)
                    .fullName(fullName)
                    .role(role)
                    .passwordHash(null)
                    .mfaEnabled(false)
                    .mfaLocked(false)
                    .mustChangePassword(false)
                    .isActive(true)
                    .failedLoginAttempts(0)
                    .sites(new LinkedHashSet<>(Set.of(sdq)))
                    .build();
            appUserRepository.save(user);
            log.info("DataSeeder: seeded user {} ({})", email, role);
        });
    }

    private String fullNameFor(String email, UserRole role) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("readonly@aircargo.com", "Read Only User");
        names.put("warehouse@aircargo.com", "Warehouse Assistant");
        names.put("operations@aircargo.com", "Operations User");
        names.put("traffic@aircargo.com", "Traffic User");
        names.put("loadplanner@aircargo.com", "Load Planner");
        names.put("admin@aircargo.com", "Admin User");
        names.put("supervisor@aircargo.com", "Supervisor");
        names.put("jsantos@rannik.com", "Jose Santos");
        names.put("esantana@rannik.com", "Edward Santana");
        names.put("dchestaro@rannik.com", "Danny Chestaro");
        names.put("ilsantana@rannik.com", "Ilsa Santana");
        names.put("earellano@ups.com", "Eduardo Arellano");
        names.put("jcastrolopez@ups.com", "Jairo Castro");
        names.put("bi@rannik.com", "BI User");
        return names.getOrDefault(email, role.name());
    }

    private void seedViewPermissions() {
        if (viewPermissionRepository.count() > 0) {
            return;
        }
        Map<String, String[]> views = new LinkedHashMap<>();
        views.put("DASHBOARD", new String[]{"Dashboard", "Panel principal con resumen de operaciones", "PRINCIPAL"});
        views.put("BOOKINGS", new String[]{"Bookings", "Gestión de reservas y bookings", "PRINCIPAL"});
        views.put("RECEIPTS", new String[]{"Recibos de Almacén", "Emisión y consulta de recibos de bodega", "PRINCIPAL"});
        views.put("FLIGHTS", new String[]{"Vuelos", "Administración de vuelos y programación", "PRINCIPAL"});
        views.put("MAWBS", new String[]{"MAWBs", "Gestión de conocimientos aéreos maestros", "PRINCIPAL"});
        views.put("LOAD_PLANNING", new String[]{"Load Planning", "Planificación de carga y distribución", "PRINCIPAL"});
        views.put("ULDS", new String[]{"ULDs / Pallet Sheets", "Administración de contenedores y pallets", "PRINCIPAL"});
        views.put("HAWBS", new String[]{"HAWBs", "Gestión de conocimientos aéreos hijos", "OPERACIONES"});
        views.put("AIRLINES", new String[]{"Aerolíneas", "Administración de líneas aéreas y compañías", "OPERACIONES"});
        views.put("RAMP_MANIFEST", new String[]{"Manifiesto de Rampa", "Manifiesto de carga para operaciones de rampa", "OPERACIONES"});
        views.put("DIM_FACTOR", new String[]{"Factor Dimensional", "Configuración del factor dimensional para cálculos", "CONFIGURACION"});
        views.put("ULD_TYPE_CONFIG", new String[]{"Tipos de ULD", "Configuración de tipos de contenedores y pallets", "CONFIGURACION"});
        views.put("USERS", new String[]{"Usuarios", "Gestión de usuarios del sistema", "ADMINISTRACION"});
        views.put("AUDIT_LOG", new String[]{"Auditoría", "Consulta de bitácora de transacciones del sistema", "ADMINISTRACION"});
        views.put("ROLES", new String[]{"Roles y Permisos", "Administración de roles y permisos de acceso", "ADMINISTRACION"});
        views.put("SITES", new String[]{"Sitios / Aeropuertos", "Administración de códigos de sitio y aeropuertos", "ADMINISTRACION"});
        views.put("SETTINGS", new String[]{"Configuración Global", "Configuración general del sistema y parámetros", "ADMINISTRACION"});
        views.put("REPORTS", new String[]{"Reportes", "Generación y exportación de reportes operativos", "OPERACIONES"});
        views.put("EXPORTS", new String[]{"Exports", "Exportaciones y reportes", "ADMINISTRACION"});
        views.put("API_CATALOG", new String[]{"API Catalog", "Catálogo de endpoints de la API", "CONFIGURACION"});
        views.put("BI", new String[]{"BI", "Indicadores e inteligencia de negocio", "ADMINISTRACION"});

        views.forEach((code, data) -> {
            ViewPermission vp = ViewPermission.builder()
                    .code(code)
                    .name(data[0])
                    .description(data[1])
                    .category(data[2])
                    .isActive(true)
                    .build();
            viewPermissionRepository.save(vp);
        });
        log.info("DataSeeder: seeded {} view permissions", views.size());
    }

    private void seedRolePermissions() {
        if (rolePermissionRepository.count() > 0) {
            return;
        }
        Map<String, Set<String>> assignments = roleAssignments();

        assignments.forEach((role, codes) -> {
            codes.forEach(code -> {
                viewPermissionRepository.findByCode(code).ifPresent(vp -> {
                    RolePermission rp = RolePermission.builder()
                            .role(role)
                            .viewPermission(vp)
                            .canAccess(true)
                            .build();
                    rolePermissionRepository.save(rp);
                });
            });
        });
        log.info("DataSeeder: seeded role permissions for {} roles", assignments.size());
    }

    private Map<String, Set<String>> roleAssignments() {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        Set<String> all = Set.of(
                "DASHBOARD", "BOOKINGS", "RECEIPTS", "FLIGHTS", "MAWBS", "LOAD_PLANNING",
                "ULDS", "HAWBS", "AIRLINES", "RAMP_MANIFEST", "DIM_FACTOR", "ULD_TYPE_CONFIG",
                "USERS", "AUDIT_LOG", "ROLES", "SITES", "SETTINGS", "REPORTS", "EXPORTS",
                "API_CATALOG", "BI");

        map.put("READ_ONLY", all);
        map.put("WAREHOUSE_ASSISTANT", Set.of("DASHBOARD", "RECEIPTS"));
        map.put("OPERATIONS", Set.of("DASHBOARD", "FLIGHTS", "MAWBS", "LOAD_PLANNING", "ULDS", "HAWBS", "AIRLINES", "RAMP_MANIFEST", "REPORTS"));
        map.put("TRAFFIC", Set.of("DASHBOARD", "BOOKINGS", "MAWBS", "LOAD_PLANNING", "ULDS", "HAWBS", "AIRLINES", "REPORTS"));
        map.put("LOAD_PLANNER", Set.of("DASHBOARD", "FLIGHTS", "LOAD_PLANNING", "ULDS", "ULD_TYPE_CONFIG", "REPORTS"));
        Set<String> admin = new LinkedHashSet<>(all);
        admin.remove("SETTINGS");
        map.put("ADMIN", admin);
        map.put("SUPER_USER", all);
        return map;
    }

    /**
     * El catálogo de commodities tiene UNA sola definición de defaults: la de
     * CommodityTypeService (label + description + color canónicos). Antes este
     * seeder mantenía una lista duplicada de 18 códigos SIN description —el
     * texto que la UI muestra como tooltip—, y con otro formato de label
     * ("PERISHABLE" vs "Perishable", "SDQ-SDF" vs "SDQ\u2192SDF").
     * Delegar además en resetToDefaults() garantiza que un catálogo vacío se
     * rellene con los mismos textos que "Restaurar defaults" en Settings, y que
     * nunca se borre un código ajeno (upsert, no deleteAll).
     */
    private void seedCommodityTypes() {
        int restored = commodityTypeService.resetToDefaults();
        if (restored > 0) {
            log.info("DataSeeder: seeded {} commodity types desde los defaults canónicos", restored);
        }
    }

}
