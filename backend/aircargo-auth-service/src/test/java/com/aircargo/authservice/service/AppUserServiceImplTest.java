package com.aircargo.authservice.service;

import com.aircargo.authservice.dto.AppUserDTO;
import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.Site;
import com.aircargo.authservice.entity.UserRole;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.repository.SiteRepository;
import com.aircargo.common.entity.Airline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserServiceImplTest {

    @Mock
    private AppUserRepository repository;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private UserStateRedisService statePublisher;

    @InjectMocks
    private AppUserServiceImpl service;

    private AppUser sampleUser() {
        Airline airline = new Airline();
        airline.setId(UUID.randomUUID());
        return AppUser.builder()
                .id(UUID.randomUUID())
                .email("user@aircargo.com")
                .fullName("Test User")
                .role(UserRole.OPERATIONS)
                .airline(airline)
                .sites(new HashSet<>())
                .build();
    }

    @Test
    void create_forcesPasswordChangeAndClearsHash() {
        AppUserDTO dto = AppUserDTO.builder()
                .email("new@aircargo.com")
                .fullName("New User")
                .role(UserRole.WAREHOUSE_ASSISTANT)
                .airlineId(UUID.randomUUID())
                .build();

        AppUser saved = sampleUser();
        saved.setId(UUID.randomUUID());
        when(repository.save(any(AppUser.class))).thenReturn(saved);

        AppUserDTO result = service.create(dto);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getPasswordHash());
        assertTrue(captor.getValue().getMustChangePassword());
        assertNotNull(result);
        assertEquals(saved.getEmail(), result.getEmail());
    }

    @Test
    void getAll_filtersByAirlineId_whenProvided() {
        UUID airlineId = UUID.randomUUID();
        when(repository.findByAirlineId(airlineId)).thenReturn(List.of(sampleUser()));

        List<AppUserDTO> result = service.getAll(airlineId);

        assertEquals(1, result.size());
        verify(repository).findByAirlineId(airlineId);
        verify(repository, never()).findAll();
    }

    @Test
    void getAll_returnsAll_whenNoFilter() {
        when(repository.findAll()).thenReturn(List.of(sampleUser(), sampleUser()));

        List<AppUserDTO> result = service.getAll(null);

        assertEquals(2, result.size());
        verify(repository, never()).findByAirlineId(any());
    }

    @Test
    void getById_mapsEntity() {
        AppUser user = sampleUser();
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));

        Optional<AppUserDTO> result = service.getById(user.getId());

        assertTrue(result.isPresent());
        assertEquals(user.getEmail(), result.get().getEmail());
        assertEquals(user.getRole(), result.get().getRole());
    }

    @Test
    void update_preservesExistingFieldsWhenDtoPartial() {
        AppUser existing = sampleUser();
        existing.setEmail("keep@aircargo.com");
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUserDTO dto = AppUserDTO.builder().email("changed@aircargo.com").build();
        Optional<AppUserDTO> result = service.update(existing.getId(), dto);

        assertTrue(result.isPresent());
        assertEquals("changed@aircargo.com", result.get().getEmail());
        assertEquals(UserRole.OPERATIONS, result.get().getRole());
    }

    @Test
    void delete_returnsFalse_whenNotExists() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertFalse(service.delete(id));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void delete_returnsTrue_andDeletes_whenExists() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        assertTrue(service.delete(id));
        verify(repository).deleteById(id);
    }

    @Test
    void resetPassword_clearsHash_andForcesChange() {
        AppUser user = sampleUser();
        user.setPasswordHash("$2a$10$hashed");
        user.setMustChangePassword(false);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(repository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resetPassword(user.getId());

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(repository).save(captor.capture());
        assertNull(captor.getValue().getPasswordHash());
        assertTrue(captor.getValue().getMustChangePassword());
    }
}
