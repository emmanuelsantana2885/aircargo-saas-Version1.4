package com.aircargo.authservice.service;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.repository.AppUserRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class MfaService {

    private static final String ISSUER = "AirCargo";

    private final AppUserRepository userRepository;
    private final TokenRevocationService tokenRevocationService;
    private final SecretGenerator secretGenerator;
    private final TimeProvider timeProvider;
    private final CodeGenerator codeGenerator;
    private final DefaultCodeVerifier codeVerifier;

    public MfaService(AppUserRepository userRepository, TokenRevocationService tokenRevocationService) {
        this.userRepository = userRepository;
        this.tokenRevocationService = tokenRevocationService;
        this.secretGenerator = new DefaultSecretGenerator();
        this.timeProvider = new SystemTimeProvider();
        this.codeGenerator = new DefaultCodeGenerator();
        this.codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        this.codeVerifier.setTimePeriod(30);
        this.codeVerifier.setAllowedTimePeriodDiscrepancy(1);
    }

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String getOtpAuthUrl(String email, String secret) {
        return "otpauth://totp/" + ISSUER + ":" + email
                + "?secret=" + secret
                + "&issuer=" + ISSUER
                + "&algorithm=SHA1"
                + "&digits=6"
                + "&period=30";
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) return false;
        return codeVerifier.isValidCode(secret, code);
    }

    public void enableMfa(UUID userId, String secret) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setMfaSecret(secret);
            user.setMfaEnabled(true);
            user.setMfaLocked(false);
            user.setMfaFailedAttempts(0);
            user.setMfaEnrolledAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
            userRepository.save(user);
        });
    }

    public void disableMfa(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setMfaSecret(null);
            user.setMfaEnabled(false);
            user.setMfaLocked(false);
            user.setMfaFailedAttempts(0);
            user.setMfaEnrolledAt(null);
            userRepository.save(user);
        });
    }

    /**
     * Reinicia el MFA de un usuario de forma atómica: deshabilita el MFA y revoca
     * centralmente todas sus sesiones (bump de tokens_valid_from + publicación en
     * Redis para el filtro por-request).
     *
     * Es la única vía de los endpoints de reset: self (/mfa/reset), admin en
     * /auth/mfa/reset/{userId}, admin en /users/{id}/mfa/reset y la confirmación
     * de recuperación por correo. Fuerza re-enrolamiento en el próximo login.
     */
    @Transactional
    public void resetUserMfa(UUID userId) {
        disableMfa(userId);
        tokenRevocationService.bump(userId);
    }

    public void lockMfa(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setMfaLocked(true);
            userRepository.save(user);
        });
    }

    public void unlockMfa(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setMfaLocked(false);
            user.setMfaFailedAttempts(0);
            userRepository.save(user);
        });
    }

    public boolean isMfaRequired(AppUser user) {
        return Boolean.TRUE.equals(user.getMfaEnabled());
    }
}
