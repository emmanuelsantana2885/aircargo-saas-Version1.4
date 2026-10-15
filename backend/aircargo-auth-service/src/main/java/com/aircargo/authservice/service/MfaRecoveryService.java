package com.aircargo.authservice.service;

import com.aircargo.authservice.entity.MfaRecoveryCode;
import com.aircargo.authservice.repository.MfaRecoveryCodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recuperación de acceso al autenticador (MFA) mediante un código OTP de 6
 * dígitos enviado por correo. Solo se guarda el SHA-256 del código; el valor
 * crudo viaja únicamente en el email. Los códigos son de un solo uso, expiran
 * y admiten un número máximo de intentos fallidos.
 */
@Service
public class MfaRecoveryService {

    /** Intentos fallidos permitidos antes de invalidar el código activo. */
    public static final int MAX_ATTEMPTS = 5;

    private final MfaRecoveryCodeRepository repository;
    private final MailService mailService;
    private final int expiryMinutes;
    private final boolean exposeCode;
    private final SecureRandom random = new SecureRandom();

    public MfaRecoveryService(MfaRecoveryCodeRepository repository,
                              MailService mailService,
                              @Value("${app.mfa.recovery.expiry-minutes:10}") int expiryMinutes,
                              @Value("${app.mfa.recovery.expose-code:false}") boolean exposeCode) {
        this.repository = repository;
        this.mailService = mailService;
        this.expiryMinutes = expiryMinutes;
        this.exposeCode = exposeCode;
    }

    /**
     * True si el código de recuperación puede entregarse por correo. Cuando es
     * false (SMTP desactivado/sin configurar) el código solo queda en el log y
     * la recuperación debe resolverse con un restablecimiento por administrador.
     */
    public boolean isDeliveryAvailable() {
        return mailService.isDeliveryAvailable();
    }

    /**
     * Genera un código nuevo, invalida los pendientes anteriores del usuario y
     * lo envía por correo. Devuelve el código crudo SOLO si
     * {@code app.mfa.recovery.expose-code=true} (útil en desarrollo/tests).
     */
    @Transactional
    public Optional<String> createCode(UUID userId, String email) {
        for (MfaRecoveryCode pending : repository.findByUserIdAndUsedAtIsNull(userId)) {
            pending.setUsedAt(OffsetDateTime.now());
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        MfaRecoveryCode entity = new MfaRecoveryCode();
        entity.setUserId(userId);
        entity.setCodeHash(PasswordResetService.sha256Hex(code));
        entity.setExpiresAt(OffsetDateTime.now().plusMinutes(expiryMinutes));
        repository.save(entity);

        mailService.sendMfaRecoveryCode(email, code, expiryMinutes);
        return exposeCode ? Optional.of(code) : Optional.empty();
    }

    /**
     * Verifica el código. Si no coincide, incrementa los intentos (invalidando
     * el código al llegar al máximo). Si coincide y sigue vigente, lo marca como
     * usado y devuelve true.
     */
    @Transactional
    public boolean confirmCode(UUID userId, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String hash = PasswordResetService.sha256Hex(code.trim());
        List<MfaRecoveryCode> pending = repository.findByUserIdAndUsedAtIsNull(userId);
        if (pending.isEmpty()) {
            return false;
        }
        MfaRecoveryCode match = pending.stream()
                .filter(c -> c.getCodeHash().equals(hash))
                .findFirst()
                .orElse(null);

        OffsetDateTime now = OffsetDateTime.now();
        if (match == null) {
            for (MfaRecoveryCode c : pending) {
                if (c.getAttempts() < MAX_ATTEMPTS) {
                    c.setAttempts(c.getAttempts() + 1);
                }
            }
            return false;
        }
        if (match.getAttempts() >= MAX_ATTEMPTS || match.getExpiresAt().isBefore(now)) {
            return false;
        }
        match.setUsedAt(now);
        return true;
    }
}
