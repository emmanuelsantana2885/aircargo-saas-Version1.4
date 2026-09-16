package com.aircargo.common.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final long REVOCATION_CACHE_MS = 30_000;
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login", "/api/auth/set-password", "/api/auth/set-password-token",
            "/api/auth/reset-password/", "/api/auth/refresh", "/api/auth/heartbeat",
            "/api/auth/mfa/enroll/"
    );

    private final JwtUtil jwtUtil;
    // Opcional: habilita revocación central por-request (tokens_valid_from / blocked / is_active).
    // Redis es la fuente preferida (snapshot cache aircargo:user-state:<userId> publicado por auth);
    // el JDBC sobre auth.app_user es el fallback fail-open cuando no hay Redis.
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final Map<UUID, UserState> stateCache = new ConcurrentHashMap<>();

    private static final String USER_STATE_KEY_PREFIX = "aircargo:user-state:";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record UserState(boolean blocked, boolean active, OffsetDateTime tokensValidFrom, long loadedAtMs) {}

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this(jwtUtil, null, null);
    }

    public JwtAuthFilter(JwtUtil jwtUtil, JdbcTemplate jdbcTemplate) {
        this(jwtUtil, jdbcTemplate, null);
    }

    public JwtAuthFilter(JwtUtil jwtUtil, JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jwtUtil = jwtUtil;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // Endpoints públicos: skip JWT validation (permitAll en SecurityConfig se encarga)
        if (PUBLIC_PATHS.stream().anyMatch(uri::startsWith)) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            log.debug("NO token for {} {}", method, uri);
            chain.doFilter(request, response);
            return;
        }

        try {
            if (jwtUtil.isRevoked(token)) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response, "Token revoked");
                return;
            }
            Claims claims = jwtUtil.parseToken(token);
            String tokenType = claims.get("tokenType", String.class);
            // Solo tokens de acceso/servicio pueden autenticar APIs. Un token
            // "enroll" o "refresh" jamás debe autorizar endpoints protegidos.
            if (tokenType != null && !"access".equals(tokenType) && !"service".equals(tokenType)) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response, "Token type not allowed for API access");
                return;
            }
            String role = claims.get("role", String.class);
            if (role == null) {
                SecurityContextHolder.clearContext();
                writeUnauthorized(response, "Invalid token (no role)");
                return;
            }
            String userId = claims.getSubject();
            String airlineId = claims.get("airlineId", String.class);
            String email = claims.get("email", String.class);
            String fullName = claims.get("fullName", String.class);

            // Revocación central por-request (solo servicios con BD o Redis; caché 30s).
            // Los tokens de servicio (subject "service:...") no son usuarios: se saltan
            // el chequeo, que requiere un UUID real (UUID.fromString lanzaría).
            if ((jdbcTemplate != null || redisTemplate != null) && userId != null && !userId.startsWith("service:")) {
                try {
                    if (isStale(UUID.fromString(userId), claims)) {
                        log.info("Session REVOKED for {} {}", method, uri);
                        SecurityContextHolder.clearContext();
                        writeUnauthorized(response, "Session revoked");
                        return;
                    }
                } catch (IllegalArgumentException e) {
                    log.debug("Subject no-UUID, omitting revocation check for {} {}", method, uri);
                }
            }

            UserPrincipal principal = new UserPrincipal(userId, role, airlineId, email, fullName);
            SimpleGrantedAuthority roleAuthority = new SimpleGrantedAuthority(role);
            List<org.springframework.security.core.GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(roleAuthority);
            Object rawPerms = claims.get("permissions");
            if (rawPerms instanceof List<?> list) {
                for (Object p : list) {
                    if (p instanceof String s && !s.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(s));
                    }
                }
            }
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            log.info("JWT INVALID for {} {}: {}", method, uri, e.getMessage());
            SecurityContextHolder.clearContext();
            writeUnauthorized(response, "Token expired or invalid");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Bearer header tiene prioridad; fallback a cookie httpOnly. */
    static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return CookieAuthSupport.extractToken(request, CookieAuthSupport.ACCESS_COOKIE);
    }

    private boolean isStale(UUID userId, Claims claims) {
        UserState s = stateCache.compute(userId, (k, prev) -> {
            if (prev != null && System.currentTimeMillis() - prev.loadedAtMs() < REVOCATION_CACHE_MS) {
                return prev;
            }
            return loadState(userId, prev);
        });
        if (s.blocked() || !s.active()) return true;
        java.util.Date iat = claims.getIssuedAt();
        return iat != null && s.tokensValidFrom() != null
                && OffsetDateTime.ofInstant(iat.toInstant(), java.time.ZoneOffset.UTC).isBefore(s.tokensValidFrom());
    }

    /**
     * Carga best-effort del estado de revocación del usuario:
     * 1º Redis (snapshot aircargo:user-state:<uuid> publicado por auth-service — sin carga a la BD),
     * 2º JDBC calificado contra auth.app_user (fallback fail-open cuando Redis no responde),
     * y si ninguna fuente está disponible se permite el paso (comportamiento pre-revocación).
     */
    private UserState loadState(UUID userId, UserState prev) {
        if (redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get(USER_STATE_KEY_PREFIX + userId);
                if (json != null) {
                    JsonNode n = MAPPER.readTree(json);
                    Long epoch = n.has("tokensValidFrom") && !n.get("tokensValidFrom").isNull()
                            ? n.get("tokensValidFrom").asLong() : null;
                    OffsetDateTime vf = epoch != null
                            ? OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC) : null;
                    return new UserState(
                            n.has("blocked") && n.get("blocked").asBoolean(false),
                            !n.has("active") || n.get("active").asBoolean(true),
                            vf,
                            System.currentTimeMillis());
                }
            } catch (Exception e) {
                log.warn("Redis user-state de {} ilegible (fallback SQL): {}", userId, e.getMessage());
            }
        }
        if (jdbcTemplate != null) {
            try {
                return jdbcTemplate.queryForObject(
                        "SELECT blocked, is_active, COALESCE(tokens_valid_from, TIMESTAMP '1970-01-01 00:00:00+00') FROM auth.app_user WHERE id = ?",
                        (rs, n) -> new UserState(rs.getBoolean(1), rs.getBoolean(2),
                                rs.getObject(3, OffsetDateTime.class), System.currentTimeMillis()),
                        userId);
            } catch (Exception e) {
                log.warn("No se pudo leer estado de usuario {}: {}", userId, e.getMessage());
            }
        }
        return prev != null ? prev : new UserState(false, true, null, System.currentTimeMillis());
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
        response.getWriter().flush();
    }
}
