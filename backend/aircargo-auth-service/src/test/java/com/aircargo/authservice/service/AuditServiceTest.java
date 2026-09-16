package com.aircargo.authservice.service;

import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.event.AuditEvent;
import com.aircargo.authservice.event.AuditEventStore;
import com.aircargo.common.entity.Airline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventStore eventStore;

    private AuditService service() {
        return new AuditService(eventStore);
    }

    private com.aircargo.authservice.entity.AppUser sampleUser() {
        Airline airline = new Airline();
        airline.setId(UUID.randomUUID());
        return com.aircargo.authservice.entity.AppUser.builder()
                .id(UUID.randomUUID())
                .email("user@aircargo.com")
                .fullName("Test User")
                .role(UserRole.OPERATIONS)
                .airline(airline)
                .passwordHash("super-secret-hash")
                .mfaSecret("super-secret-totp")
                .blocked(false)
                .isActive(true)
                .build();
    }

    @Test
    void snapshotIncludesIdentityButNeverSecrets() {
        String snap = AuditService.snapshot(sampleUser());
        assertNotNull(snap);
        assertTrue(snap.contains("\"email\":\"user@aircargo.com\""));
        assertTrue(snap.contains("\"role\":\"OPERATIONS\""));
        assertFalse(snap.contains("super-secret-hash"));
        assertFalse(snap.contains("super-secret-totp"));
        assertNotEquals(snap, AuditService.snapshot(null));
        assertEquals("null", String.valueOf(AuditService.snapshot(null)));
    }

    @Test
    void eightArgLogDelegatesWithNullBeforeAfter() {
        UUID actor = UUID.randomUUID();
        service().log(actor, "admin@aircargo.com", "Admin", "USER_DELETED", "USER",
                actor.toString(), null, "127.0.0.1");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventStore).append(org.mockito.ArgumentMatchers.eq(actor),
                org.mockito.ArgumentMatchers.eq("admin@aircargo.com"),
                org.mockito.ArgumentMatchers.eq("Admin"),
                org.mockito.ArgumentMatchers.eq("USER_DELETED"),
                org.mockito.ArgumentMatchers.eq("USER"),
                org.mockito.ArgumentMatchers.eq(actor.toString()),
                org.mockito.ArgumentMatchers.eq((String) null),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"));
    }

    @Test
    void diffLogPropagatesBeforeAndAfterSnapshots() {
        com.aircargo.authservice.entity.AppUser before = sampleUser();
        com.aircargo.authservice.entity.AppUser after = sampleUser();
        after.setBlocked(true);

        UUID actor = UUID.randomUUID();
        service().log(actor, "admin@aircargo.com", "Admin", "USER_BLOCKED", "USER",
                before.getId().toString(), "Blocked user",
                AuditService.snapshot(before), AuditService.snapshot(after), "127.0.0.1");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventStore).append(org.mockito.ArgumentMatchers.eq(actor),
                org.mockito.ArgumentMatchers.eq("admin@aircargo.com"),
                org.mockito.ArgumentMatchers.eq("Admin"),
                org.mockito.ArgumentMatchers.eq("USER_BLOCKED"),
                org.mockito.ArgumentMatchers.eq("USER"),
                org.mockito.ArgumentMatchers.eq(before.getId().toString()),
                org.mockito.ArgumentMatchers.eq("Blocked user"),
                org.mockito.ArgumentMatchers.contains("\"blocked\":false"),
                org.mockito.ArgumentMatchers.contains("\"blocked\":true"),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"));
    }
}