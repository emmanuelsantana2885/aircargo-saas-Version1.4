package com.aircargo.authservice.service;

import com.aircargo.authservice.dto.AppUserDTO;
import com.aircargo.authservice.entity.AppUser;
import com.aircargo.authservice.entity.Site;
import com.aircargo.authservice.repository.AppUserRepository;
import com.aircargo.authservice.repository.SiteRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AppUserServiceImpl implements AppUserService {

    private final AppUserRepository repository;
    private final SiteRepository siteRepository;
    private final UserStateRedisService statePublisher;

    public AppUserServiceImpl(AppUserRepository repository, SiteRepository siteRepository,
                              UserStateRedisService statePublisher) {
        this.repository = repository;
        this.siteRepository = siteRepository;
        this.statePublisher = statePublisher;
    }

    @Override
    @Cacheable(value = "users", key = "#airlineId != null ? #airlineId : 'all'")
    public List<AppUserDTO> getAll(UUID airlineId) {
        List<AppUser> results = airlineId != null ? repository.findByAirlineId(airlineId) : repository.findAll();
        return results.stream().map(AppUserDTO::fromEntity).collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "users", key = "#id")
    public Optional<AppUserDTO> getById(UUID id) {
        return repository.findById(id).map(AppUserDTO::fromEntity);
    }

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public AppUserDTO create(AppUserDTO dto) {
        AppUser e = AppUserDTO.toEntity(dto);
        e.setPasswordHash(null);
        e.setMustChangePassword(true);
        if (dto.getSiteIds() != null) {
            Set<Site> sites = dto.getSiteIds().stream()
                    .map(siteRepository::findById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toSet());
            e.setSites(sites);
        }
        AppUser saved = repository.save(e);
        statePublisher.publish(saved.getId(), saved.getTokensValidFrom(), saved.getBlocked(), saved.getIsActive());
        return AppUserDTO.fromEntity(saved);
    }

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public Optional<AppUserDTO> update(UUID id, AppUserDTO dto) {
        return repository.findById(id)
                .map(existing -> {
                    if (dto.getEmail() != null) existing.setEmail(dto.getEmail());
                    if (dto.getFullName() != null) existing.setFullName(dto.getFullName());
                    if (dto.getRole() != null) existing.setRole(dto.getRole());
                    existing.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : existing.getIsActive());
                    if (dto.getAirlineId() != null) {
                        com.aircargo.common.entity.Airline airline = new com.aircargo.common.entity.Airline();
                        airline.setId(dto.getAirlineId());
                        existing.setAirline(airline);
                    }
                    if (dto.getSiteIds() != null) {
                        Set<Site> sites = dto.getSiteIds().stream()
                                .map(siteRepository::findById)
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .collect(Collectors.toSet());
                        existing.setSites(sites);
                    }
                    AppUser updated = repository.save(existing);
                    statePublisher.publish(updated.getId(), updated.getTokensValidFrom(),
                            updated.getBlocked(), updated.getIsActive());
                    return updated;
                })
                .map(AppUserDTO::fromEntity);
    }

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public boolean delete(UUID id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        statePublisher.evict(id);
        return true;
    }

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public void resetPassword(UUID id) {
        repository.findById(id).ifPresent(user -> {
            user.setPasswordHash(null);
            user.setMustChangePassword(true);
            repository.save(user);
        });
    }
}
