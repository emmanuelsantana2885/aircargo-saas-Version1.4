package com.aircargo.authservice.service;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.MfaPolicy;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.repository.MfaPolicyRepository;
import com.aircargo.authservice.service.MfaPolicyService.MfaEligibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Política de MFA: evaluación OK/REQUIRED/RESET_REQUIRED/EXPIRED y sincronización
 * de los valores de config (reset_on_startup, max_age_days) sobre el singleton.
 */
@ExtendWith(MockitoExtension.class)
class MfaPolicyServiceTest {

    private static final int CONFIG_MAX_AGE_DAYS = 7;
    private static final boolean CONFIG_RESET_ON_STARTUP = false;

    @Mock
    private MfaPolicyRepository policyRepository;

    @Mock
    private AppUserRepository userRepository;

    private MfaPolicyService service() {
        return new MfaPolicyService(policyRepository, userRepository,
                CONFIG_MAX_AGE_DAYS, CONFIG_RESET_ON_STARTUP);
    }

    private MfaPolicy policy(OffsetDateTime lastResetAt, Integer maxAgeDays, Boolean resetOnStartup) {
        return MfaPolicy.builder()
                .id(1)
                .lastResetAt(lastResetAt)
                .maxAgeDays(maxAgeDays)
                .resetOnStartup(resetOnStartup)
                .build();
    }

    private AppUser user(Boolean mfaEnabled, OffsetDateTime enrolledAt) {
        return AppUser.builder()
                .mfaEnabled(mfaEnabled)
                .mfaEnrolledAt(enrolledAt)
                .build();
    }

    @Test
    void evaluate_usuarioNulo_esOk() {
        assertEquals(MfaEligibility.OK, service().evaluate(null));
        verifyNoInteractions(policyRepository);
    }

    @Test
    void evaluate_sinMfaHabilitado_requiereEnrolar() {
        AppUser u = user(false, null);
        assertEquals(MfaEligibility.REQUIRED, service().evaluate(u));
    }

    @Test
    void evaluate_sinTimestampDeEnrolamiento_requiereEnrolar() {
        AppUser u = user(true, null);
        assertEquals(MfaEligibility.REQUIRED, service().evaluate(u));
    }

    @Test
    void evaluate_enrolamientoReciente_esOk() {
        when(policyRepository.findById(1)).thenReturn(Optional.of(
                policy(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3650), CONFIG_MAX_AGE_DAYS, CONFIG_RESET_ON_STARTUP)));

        AppUser u = user(true, OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
        assertEquals(MfaEligibility.OK, service().evaluate(u));
    }

    @Test
    void evaluate_enrolamientoMayorQueMaxAge_expirado() {
        when(policyRepository.findById(1)).thenReturn(Optional.of(
                policy(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3650), CONFIG_MAX_AGE_DAYS, CONFIG_RESET_ON_STARTUP)));

        AppUser u = user(true, OffsetDateTime.now(ZoneOffset.UTC).minusDays(8));
        assertEquals(MfaEligibility.EXPIRED, service().evaluate(u));
    }

    @Test
    void evaluate_enrolamientoAnteriorAlUltimoReset_requiereReset() {
        when(policyRepository.findById(1)).thenReturn(Optional.of(
                policy(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), CONFIG_MAX_AGE_DAYS, CONFIG_RESET_ON_STARTUP)));

        AppUser u = user(true, OffsetDateTime.now(ZoneOffset.UTC).minusHours(3));
        assertEquals(MfaEligibility.RESET_REQUIRED, service().evaluate(u));
    }

    @Test
    void evaluate_maxAgeNulo_caeAlDefaultConfigurado_noAUnDia() {
        // Fila con max_age_days NULL (insertada a mano): debe caer al default de config
        // (7), no a 1. Un enrolamiento de 3 días es OK con 7 y EXPIRED con 1.
        when(policyRepository.findById(1)).thenReturn(Optional.of(
                policy(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3650), null, CONFIG_RESET_ON_STARTUP)));

        AppUser u = user(true, OffsetDateTime.now(ZoneOffset.UTC).minusDays(3));
        assertEquals(MfaEligibility.OK, service().evaluate(u));
    }

    @Test
    void policy_sincronizaResetOnStartupYMaxAgeDesdeConfig() {
        when(policyRepository.findById(1)).thenReturn(Optional.of(
                policy(OffsetDateTime.now(ZoneOffset.UTC), 3, true)));

        MfaPolicy result = service().policy();

        assertEquals(CONFIG_RESET_ON_STARTUP, result.getResetOnStartup());
        assertEquals(CONFIG_MAX_AGE_DAYS, result.getMaxAgeDays());

        ArgumentCaptor<MfaPolicy> captor = ArgumentCaptor.forClass(MfaPolicy.class);
        verify(policyRepository).save(captor.capture());
        assertEquals(CONFIG_MAX_AGE_DAYS, captor.getValue().getMaxAgeDays());
    }

    @Test
    void policy_sinCambios_noVuelveAGuardar() {
        when(policyRepository.findById(1)).thenReturn(Optional.of(
                policy(OffsetDateTime.now(ZoneOffset.UTC), CONFIG_MAX_AGE_DAYS, CONFIG_RESET_ON_STARTUP)));

        service().policy();

        verify(policyRepository, never()).save(any());
    }

    @Test
    void policy_sinFila_creaSingletonConDefaults() {
        when(policyRepository.findById(1)).thenReturn(Optional.empty());

        MfaPolicy created = service().policy();

        assertEquals(1, created.getId());
        assertEquals(CONFIG_MAX_AGE_DAYS, created.getMaxAgeDays());
        assertEquals(CONFIG_RESET_ON_STARTUP, created.getResetOnStartup());
        assertNotNull(created.getLastResetAt());
        verify(policyRepository).save(created);
    }
}
