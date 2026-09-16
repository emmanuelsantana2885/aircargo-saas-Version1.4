package com.aircargo.authservice.command;

import com.aircargo.authservice.config.RolePermissionCatalog;
import com.aircargo.authservice.dto.LoginResponse;
import com.aircargo.authservice.dto.SiteDTO;
import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.repository.SiteRepository;
import com.aircargo.authservice.service.ActiveSessionTracker;
import com.aircargo.authservice.service.AuditService;
import com.aircargo.authservice.service.AuthSessionService;
import com.aircargo.authservice.service.MfaPolicyService;
import com.aircargo.authservice.service.MfaPolicyService.MfaEligibility;
import com.aircargo.authservice.service.MfaService;
import com.aircargo.common.auth.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command handler for authentication (write side of the CQRS split).
 * Owns all login business rules: account state checks, the failed-attempt
 * limit (5) with temporary lockout, password verification against the BCrypt
 * hash, MFA verification and JWT issuance. Every relevant fact is appended to
 * the audit event store.
 */
@Service
public class LoginCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginCommandHandler.class);

    /** Maximum consecutive failed login attempts before temporary lockout. */
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    /** How long an account stays locked after reaching the attempt limit. */
    public static final long LOCKOUT_MINUTES = 30;
    /** Máximo de códigos TOTP incorrectos consecutivos antes de bloquear MFA. */
    public static final int MAX_MFA_ATTEMPTS = 5;

    /** Generic, deliberately vague message for any bad-credential outcome. */
    public static final String MSG_INVALID_CREDENTIALS = "Email y/o contraseña incorrectos";

    /**
     * Hash de relleno (BCrypt cost 10) usado para ecualizar el timing del login:
     * la rama "usuario inexistente" ejecuta el mismo trabajo BCrypt que la rama
     * "contraseña incorrecta", de modo que un atacante no pueda distinguir emails
     * existentes por el tiempo de respuesta. El valor no corresponde a ningún
     * usuario real ni a ninguna contraseña válida.
     */
    static final String DUMMY_PASSWORD_HASH = "$2y$10$ROn8bl.CG2aNsXSN3rdl6.K0HDP/mTnVrxIlSQ/6nRBFn7vwqilHK";

    private final AppUserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ActiveSessionTracker sessionTracker;
    private final SiteRepository siteRepository;
    private final MfaService mfaService;
    private final MfaPolicyService mfaPolicyService;
    private final AuthSessionService authSessionService;
    /** Si true (default), TODO usuario debe tener MFA configurado para iniciar sesión. */
    private final boolean mfaMandatory;
    private final com.aircargo.authservice.service.UserStateRedisService statePublisher;

    public LoginCommandHandler(AppUserRepository userRepository, JwtUtil jwtUtil,
                               PasswordEncoder passwordEncoder, AuditService auditService,
                               ActiveSessionTracker sessionTracker, SiteRepository siteRepository,
                               MfaService mfaService, MfaPolicyService mfaPolicyService,
                               AuthSessionService authSessionService,
                               com.aircargo.authservice.service.UserStateRedisService statePublisher,
                               @org.springframework.beans.factory.annotation.Value("${app.mfa.mandatory:true}") boolean mfaMandatory) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.sessionTracker = sessionTracker;
        this.siteRepository = siteRepository;
        this.mfaService = mfaService;
        this.mfaPolicyService = mfaPolicyService;
        this.authSessionService = authSessionService;
        this.statePublisher = statePublisher;
        this.mfaMandatory = mfaMandatory;
    }

    @Transactional
    public LoginOutcome handle(LoginCommand command) {
        AppUser user = userRepository.findByEmail(command.email()).orElse(null);
        if (user == null) {
            // Same generic message as wrong-password: never reveal which emails exist.
            // Timing-harden: burn a real BCrypt compare so the unknown-user path
            // costs ~the same CPU as a wrong-password verification (Audit 5d).
            String probe = command.password() == null ? "" : command.password();
            passwordEncoder.matches(probe, DUMMY_PASSWORD_HASH);
            auditService.logLoginFailed(null, command.email(), 0, null, "UNKNOWN_USER", command.ipAddress());
            return LoginOutcome.failure(LoginOutcome.Status.INVALID_CREDENTIALS,
                    Map.of("error", MSG_INVALID_CREDENTIALS));
        }

        String passwordHash = user.getPasswordHash();
        boolean hasPasswordSet = passwordHash != null && !passwordHash.isBlank();

        if (hasPasswordSet) {
            if (command.password() == null || command.password().isBlank()) {
                return LoginOutcome.failure(LoginOutcome.Status.PASSWORD_REQUIRED,
                        Map.of("error", "Contraseña requerida"));
            }
            if (!passwordEncoder.matches(command.password(), passwordHash)) {
                // Wrong password: generic 401, never the account state.
                // (Audit 5d: el estado de la cuenta ya NO se revela antes de
                // verificar la credencial — antes un atacante distinguía emails
                // vivos por el 403 "Usuario inactivo"/"Cuenta bloqueada".)
                return registerFailedAttempt(user, command);
            }
        }

        // Account state is only disclosed AFTER a valid credential:
        // a correct password (or a password-less legacy account) reaches here.
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            return LoginOutcome.failure(LoginOutcome.Status.INACTIVE,
                    Map.of("error", "Usuario inactivo"));
        }

        // Temporary lockout after too many failed attempts
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            long minutesRemaining = Duration.between(OffsetDateTime.now(), user.getLockedUntil()).toMinutes();
            return LoginOutcome.failure(LoginOutcome.Status.LOCKED,
                    Map.of("error", "Cuenta bloqueada. Intente de nuevo en " + minutesRemaining + " minutos."));
        }

        // Manual admin block
        if (Boolean.TRUE.equals(user.getBlocked())) {
            return LoginOutcome.failure(LoginOutcome.Status.BLOCKED,
                    Map.of("error", "Account blocked. Contact your administrator."));
        }

        // Valid credential + account fully active: reset the failed-attempt counter
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        // MFA check — obligatorio para TODOS los usuarios (sin bypass por rol).
        boolean mfaRequired = mfaService.isMfaRequired(user);
        MfaEligibility mfaEligibility = mfaPolicyService.evaluate(user);
        boolean needsReenrollment = mfaMandatory && mfaEligibility != MfaEligibility.OK;

        if (mfaRequired) {
            if (Boolean.TRUE.equals(user.getMfaLocked())) {
                return LoginOutcome.failure(LoginOutcome.Status.MFA_LOCKED,
                        Map.of("error", "Cuenta bloqueada por intentos fallidos de MFA. Contacte al administrador."));
            }
            if (command.totpCode() == null || command.totpCode().isBlank()) {
                // Usuario tiene MFA habilitado pero no proporcionó código.
                // Si la política exige re-enrolamiento, devolvemos MFA_REQUIRED para pedir el código primero.
                // El re-enrolamiento se forzará tras verificar el TOTP válido.
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("mfaRequired", true);
                body.put("message", "Se requiere código de autenticación de dos factores");
                body.put("mfaReenrollmentNeeded", needsReenrollment);
                if (needsReenrollment) {
                    String reason = switch (mfaEligibility) {
                        case RESET_REQUIRED -> "reset";
                        case EXPIRED -> "expired";
                        default -> "required";
                    };
                    body.put("mfaReason", reason);
                }
                return LoginOutcome.failure(LoginOutcome.Status.MFA_REQUIRED, body);
            }
            if (!mfaService.verifyCode(user.getMfaSecret(), command.totpCode())) {
                // Contador de códigos TOTP incorrectos: bloquea el MFA al alcanzar
                // MAX_MFA_ATTEMPTS (equivalente operativo al lockout de contraseña).
                return registerMfaFailedAttempt(user, command);
            }
            user.setMfaFailedAttempts(0);
        } else if (needsReenrollment) {
            // Usuario NO tiene MFA habilitado pero la política lo exige (enrolamiento inicial o legacy sin MFA).
            String enrollToken = jwtUtil.generateEnrollToken(
                    user.getId().toString(), user.getRole().name(), user.getEmail(), user.getFullName());
            String reason = switch (mfaEligibility) {
                case RESET_REQUIRED -> "reset";
                case EXPIRED -> "expired";
                default -> "required";
            };
            String message = switch (mfaEligibility) {
                case RESET_REQUIRED -> "Por seguridad, la autenticación de dos factores fue reiniciada tras una actualización del sistema. Debe configurarla nuevamente.";
                case EXPIRED -> "Por seguridad, su configuración de dos factores caducó. Debe configurarla nuevamente para continuar.";
                default -> "Debe configurar la autenticación de dos factores (MFA) antes de continuar";
            };
            auditService.log(user.getId(), user.getEmail(), user.getFullName(),
                    com.aircargo.authservice.event.AuditEventType.MFA_REENROLLMENT_REQUIRED,
                    "USER", user.getId().toString(),
                    "{\"reason\":\"" + reason + "\"}", command.ipAddress());
            return LoginOutcome.failure(LoginOutcome.Status.MFA_ENROLLMENT_REQUIRED,
                    Map.of(
                            "mfaEnrollmentRequired", true,
                            "enrollToken", enrollToken,
                            "email", user.getEmail(),
                            "mfaReason", reason,
                            "message", message
                    ));
        }

        user.setLastLogin(OffsetDateTime.now());
        userRepository.save(user);
        statePublisher.publish(user.getId(), user.getTokensValidFrom(), user.getBlocked(), user.getIsActive());

        String airlineIdStr = user.getAirline() != null && user.getAirline().getId() != null
                ? user.getAirline().getId().toString() : "";

        String token = jwtUtil.generateToken(
                user.getId().toString(),
                user.getRole().name(),
                airlineIdStr,
                user.getEmail(),
                user.getFullName(),
                RolePermissionCatalog.codesFor(user.getRole())
        );
        String refreshToken = jwtUtil.generateRefreshToken(user.getId().toString());
        authSessionService.recordIssued(user.getId(), refreshToken, command.ipAddress(), null);

        auditService.logLogin(user.getId(), user.getEmail(), user.getFullName(), command.ipAddress());

        sessionTracker.recordHeartbeat(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(), user.getLastLogin());

        List<SiteDTO> userSites = resolveSites(user);

        // Si el usuario tenía MFA válido pero la política exige re-enrolamiento (reset/expired),
        // incluimos un enrollToken en la respuesta para que el frontend inicie el flujo de re-enrolamiento.
        boolean mfaReenrollmentNeeded = mfaRequired && needsReenrollment;
        String enrollToken = null;
        String mfaReason = null;
        if (mfaReenrollmentNeeded) {
            enrollToken = jwtUtil.generateEnrollToken(
                    user.getId().toString(), user.getRole().name(), user.getEmail(), user.getFullName());
            mfaReason = switch (mfaEligibility) {
                case RESET_REQUIRED -> "reset";
                case EXPIRED -> "expired";
                default -> "required";
            };
        }

        return LoginOutcome.success(new LoginResponse(
                token,
                refreshToken,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getAirline() != null ? user.getAirline().getId() : null,
                hasPasswordSet,
                userSites,
                Boolean.TRUE.equals(user.getMustChangePassword()),
                Boolean.TRUE.equals(user.getMfaEnabled()),
                RolePermissionCatalog.codesFor(user.getRole()),
                mfaReenrollmentNeeded,
                mfaReason,
                enrollToken
        ));
    }

    private LoginOutcome registerFailedAttempt(AppUser user, LoginCommand command) {
        int attempts = (user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0) + 1;
        user.setFailedLoginAttempts(attempts);

        auditService.logLoginFailed(user.getId(), user.getEmail(), attempts, user.getId(),
                "INVALID_PASSWORD", command.ipAddress());

        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            OffsetDateTime lockedUntil = OffsetDateTime.now().plusMinutes(LOCKOUT_MINUTES);
            user.setLockedUntil(lockedUntil);
            auditService.logAccountLocked(user.getId(), user.getEmail(), attempts, lockedUntil,
                    command.ipAddress());
            log.warn("Account locked for {} after {} failed attempts", user.getEmail(), attempts);
        }
        userRepository.save(user);

        return LoginOutcome.failure(LoginOutcome.Status.INVALID_CREDENTIALS,
                Map.of("error", MSG_INVALID_CREDENTIALS));
    }

    private LoginOutcome registerMfaFailedAttempt(AppUser user, LoginCommand command) {
        int attempts = (user.getMfaFailedAttempts() != null ? user.getMfaFailedAttempts() : 0) + 1;
        user.setMfaFailedAttempts(attempts);

        if (attempts >= MAX_MFA_ATTEMPTS) {
            mfaService.lockMfa(user.getId());
            auditService.log(user.getId(), user.getEmail(), user.getFullName(),
                    com.aircargo.authservice.event.AuditEventType.MFA_LOCKED,
                    "USER", user.getId().toString(),
                    "{\"reason\":\"max_totp_attempts\",\"attempts\":" + attempts + "}",
                    command.ipAddress());
            log.warn("MFA locked for {} after {} failed TOTP attempts", user.getEmail(), attempts);
            return LoginOutcome.failure(LoginOutcome.Status.MFA_LOCKED,
                    Map.of("error", "Cuenta bloqueada por intentos fallidos de MFA. Contacte al administrador."));
        }

        userRepository.save(user);
        auditService.log(user.getId(), user.getEmail(), user.getFullName(),
                com.aircargo.authservice.event.AuditEventType.MFA_INVALID,
                "USER", user.getId().toString(),
                "{\"failedAttempts\":" + attempts + "}", command.ipAddress());
        return LoginOutcome.failure(LoginOutcome.Status.MFA_INVALID,
                Map.of("error", "Código de autenticación inválido"));
    }

    private List<SiteDTO> resolveSites(AppUser user) {
        if (user.getRole() == UserRole.SUPER_USER && user.getSites().isEmpty()) {
            return siteRepository.findByIsActiveTrue().stream()
                    .map(SiteDTO::fromEntity)
                    .collect(Collectors.toList());
        }
        return user.getSites().stream()
                .map(SiteDTO::fromEntity)
                .collect(Collectors.toList());
    }

}
