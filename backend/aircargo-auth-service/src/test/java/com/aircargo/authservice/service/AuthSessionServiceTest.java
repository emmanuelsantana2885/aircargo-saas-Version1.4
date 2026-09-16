package com.aircargo.authservice.service;

import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.AuthSession;
import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.event.AuditEventType;
import com.aircargo.authservice.repository.AuthSessionRepository;
import com.aircargo.common.auth.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de la cadena de rotación de refresh tokens (sesión 5):
 * rotación atómica, adopción de tokens legacy, gracia multi-pestaña,
 * robo (revocación de familia), registro en login y purga programada.
 */
class AuthSessionServiceTest {

    private static final String SECRET = "auth-session-test-secret-0123456789abcdefghijklmnopqrstuvwxyz";

    @Mock
    private AuthSessionRepository repository;
    @Mock
    private TokenRevocationService tokenRevocationService;
    @Mock
    private AuditService auditService;

    private JwtUtil jwtUtil;
    private AuthSessionService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jwtUtil = new JwtUtil(SECRET, 60_000L, true);
        service = new AuthSessionService(repository, jwtUtil, tokenRevocationService, auditService);
        user = AppUser.builder()
                .id(UUID.randomUUID())
                .email("rotate@aircargo.com")
                .fullName("Rotate User")
                .role(UserRole.OPERATIONS)
                .build();
    }

    private String newRefreshToken() {
        return jwtUtil.generateRefreshToken(user.getId().toString());
    }

    private AuthSession rowWith(String token, boolean rotated, boolean graceUsed, boolean revoked) {
        AuthSession row = new AuthSession();
        row.setUserId(user.getId());
        row.setTokenHash(PasswordResetService.sha256Hex(token));
        row.setIssuedAt(OffsetDateTime.now());
        row.setExpiresAt(OffsetDateTime.now().plusSeconds(60));
        row.setRevoked(revoked);
        row.setGraceUsed(graceUsed);
        if (rotated) {
            row.setRotatedAt(OffsetDateTime.now());
        }
        return row;
    }

    @Test
    void processRefresh_tokenVigente_rotaAtomicamenteSinRevocar() {
        String t1 = newRefreshToken();
        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.of(rowWith(t1, false, false, false)));
        when(repository.rotate(any(), any(), any())).thenReturn(1);

        AuthSessionService.RefreshResult result =
                service.processRefresh(user, t1, "127.0.0.1", "test-agent");

        assertEquals(AuthSessionService.Status.ROTATED, result.status());
        assertFalse(result.newRefreshToken().isBlank());
        verify(repository).rotate(eq(PasswordResetService.sha256Hex(t1)), any(), any());
        ArgumentCaptor<AuthSession> successor = ArgumentCaptor.forClass(AuthSession.class);
        verify(repository).save(successor.capture());
        assertEquals(user.getId(), successor.getValue().getUserId());
        // El sucesor se guardó con el hash del nuevo token, no el valor crudo.
        assertEquals(PasswordResetService.sha256Hex(result.newRefreshToken()),
                successor.getValue().getTokenHash());
        verify(tokenRevocationService, never()).bump(any());
        verify(repository, never()).revokeAllForUser(any());
    }

    @Test
    void processRefresh_sinFila_adoptaLegacyYRota() {
        String legacy = newRefreshToken();
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        AuthSessionService.RefreshResult result =
                service.processRefresh(user, legacy, "127.0.0.1", null);

        assertEquals(AuthSessionService.Status.ROTATED, result.status());
        ArgumentCaptor<AuthSession> saves = ArgumentCaptor.forClass(AuthSession.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saves.capture());
        // 1ª fila = la adoptada (LEGACY); 2ª = el sucesor.
        AuthSession adopted = saves.getAllValues().get(0);
        assertEquals("LEGACY", adopted.getCreatedBy());
        assertEquals(PasswordResetService.sha256Hex(legacy), adopted.getTokenHash());
        assertEquals(user.getId(), adopted.getUserId());
    }

    @Test
    void processRefresh_filaRevocada_rechazaSinDegradar() {
        String t = newRefreshToken();
        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.of(rowWith(t, true, true, true)));

        AuthSessionService.RefreshResult result =
                service.processRefresh(user, t, "127.0.0.1", null);

        assertEquals(AuthSessionService.Status.REJECTED, result.status());
        assertNull(result.newRefreshToken());
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
        verify(tokenRevocationService, never()).bump(any());
        verify(auditService, never()).log(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void processRefresh_primerReuso_rotaEnGraciaSinRevocarFamilia() {
        String t1 = newRefreshToken();
        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.of(rowWith(t1, true, false, false)));

        AuthSessionService.RefreshResult result =
                service.processRefresh(user, t1, "127.0.0.1", null);

        assertEquals(AuthSessionService.Status.GRACE_ROTATED, result.status());
        assertFalse(result.newRefreshToken().isBlank());
        verify(tokenRevocationService, never()).bump(any());
        verify(repository, never()).revokeAllForUser(any());
        verify(auditService).log(eq(user.getId()), eq(user.getEmail()), eq(user.getFullName()),
                eq(AuditEventType.REFRESH_TOKEN_GRACE_ROTATED), eq("USER"), eq(user.getId().toString()),
                any(), eq("127.0.0.1"));
        // La fila en gracia se marcó y luego se insertó el sucesor (2 saves).
        ArgumentCaptor<AuthSession> saved = ArgumentCaptor.forClass(AuthSession.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertTrue(Boolean.TRUE.equals(saved.getAllValues().get(0).getGraceUsed()));
        assertFalse(Boolean.TRUE.equals(saved.getAllValues().get(1).getGraceUsed()));
    }

    @Test
    void processRefresh_segundoReuso_detectaRoboYRevocaFamilia() {
        String t1 = newRefreshToken();
        when(repository.findByTokenHash(any()))
                .thenReturn(Optional.of(rowWith(t1, true, true, false)));

        AuthSessionService.RefreshResult result =
                service.processRefresh(user, t1, "127.0.0.1", null);

        assertEquals(AuthSessionService.Status.THEFT_REVOKED, result.status());
        assertNull(result.newRefreshToken());
        verify(tokenRevocationService).bump(user.getId());
        verify(repository).revokeAllForUser(user.getId());
        verify(auditService).log(eq(user.getId()), eq(user.getEmail()), eq(user.getFullName()),
                eq(AuditEventType.REFRESH_TOKEN_REUSE), eq("USER"), eq(user.getId().toString()),
                any(), eq("127.0.0.1"));
    }

    @Test
    void recordIssued_guardaHashYNoElTokenCrudo() {
        String rt = newRefreshToken();
        service.recordIssued(user.getId(), rt, "127.0.0.1", "unit-agent");

        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(repository).saveAndFlush(captor.capture());
        AuthSession saved = captor.getValue();
        assertEquals(PasswordResetService.sha256Hex(rt), saved.getTokenHash());
        assertFalse(saved.getTokenHash().contains(rt));
        assertEquals("LOGIN", saved.getCreatedBy());
        assertEquals("unit-agent", saved.getUserAgent());
    }

    @Test
    void recordIssued_tokenNuloORojo_noGuarda() {
        service.recordIssued(user.getId(), null, "127.0.0.1", null);
        service.recordIssued(user.getId(), "   ", "127.0.0.1", null);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void purgeExpired_eliminaSoloFilasVencidas() {
        when(repository.deleteByExpiresAtBefore(any())).thenReturn(3);
        service.purgeExpired();
        verify(repository).deleteByExpiresAtBefore(org.mockito.ArgumentMatchers.argThat(
                cutoff -> cutoff.isBefore(OffsetDateTime.now())));
    }
}