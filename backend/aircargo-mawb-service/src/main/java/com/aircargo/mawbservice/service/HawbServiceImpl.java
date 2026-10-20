package com.aircargo.mawbservice.service;

import com.aircargo.mawbservice.dto.HawbDTO;
import com.aircargo.mawbservice.entity.Hawb;
import com.aircargo.mawbservice.entity.MawbStatus;
import com.aircargo.mawbservice.repository.HawbRepository;
import com.aircargo.mawbservice.repository.MawbRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HawbServiceImpl implements HawbService {

    private final HawbRepository hawbRepository;
    private final MawbRepository mawbRepository;

    public HawbServiceImpl(HawbRepository hawbRepository, MawbRepository mawbRepository) {
        this.hawbRepository = hawbRepository;
        this.mawbRepository = mawbRepository;
    }

    @Override
    @Cacheable(value = "hawbs", key = "#mawbId")
    public List<HawbDTO> getByMawbId(UUID mawbId) {
        return hawbRepository.findByMawbId(mawbId).stream()
                .map(HawbDTO::fromEntity)
                .toList();
    }

    @Override
    @Cacheable(value = "hawbs", key = "{#mawbId, #pageable.pageNumber, #pageable.pageSize}")
    public Page<HawbDTO> getByMawbId(UUID mawbId, Pageable pageable) {
        return hawbRepository.findByMawbId(mawbId, pageable)
                .map(HawbDTO::fromEntity);
    }

    @Override
    @Cacheable(value = "hawbs", key = "#id")
    public Optional<HawbDTO> getById(UUID id) {
        return hawbRepository.findById(id).map(HawbDTO::fromEntity);
    }

    @Override
    @Cacheable(value = "hawbs", key = "'num:' + #hawbNumber")
    public Optional<HawbDTO> getByHawbNumber(String hawbNumber) {
        if (hawbNumber == null || hawbNumber.isBlank()) return Optional.empty();
        return Optional.ofNullable(hawbRepository.findByHawbNumber(hawbNumber.trim()))
                .map(HawbDTO::fromEntity);
    }

    @Override
    @CacheEvict(value = "hawbs", allEntries = true)
    public HawbDTO create(HawbDTO dto) {
        Hawb entity = HawbDTO.toEntity(dto);
        if (entity.getAirlineId() == null && dto.getMawbId() != null) {
            mawbRepository.findById(dto.getMawbId())
                    .ifPresent(mawb -> entity.setAirlineId(mawb.getAirlineId()));
        }
        if (entity.getAirlineId() == null) {
            throw new IllegalArgumentException("airlineId es requerido (no se pudo derivar del MAWB " + dto.getMawbId() + ")");
        }
        Hawb saved = hawbRepository.save(entity);
        return HawbDTO.fromEntity(saved);
    }

    @Override
    @CacheEvict(value = "hawbs", allEntries = true)
    public Optional<HawbDTO> update(UUID id, HawbDTO dto) {
        return hawbRepository.findById(id)
                .map(existing -> {
                    if (dto.getMawbId() != null) existing.setMawbId(dto.getMawbId());
                    if (dto.getAirlineId() != null) existing.setAirlineId(dto.getAirlineId());
                    if (dto.getHawbNumber() != null) existing.setHawbNumber(dto.getHawbNumber());
                    if (dto.getConsigneeName() != null) existing.setConsigneeName(dto.getConsigneeName());
                    if (dto.getDestination() != null) existing.setDestination(dto.getDestination());
                    if (dto.getPieces() != null) existing.setPieces(dto.getPieces());
                    if (dto.getWeightKg() != null) existing.setWeightKg(dto.getWeightKg());
                    if (dto.getCommodityType() != null) existing.setCommodityType(dto.getCommodityType());
                    if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
                    if (dto.getNotes() != null) existing.setNotes(dto.getNotes());
                    return hawbRepository.save(existing);
                })
                .map(HawbDTO::fromEntity);
    }

    @Override
    @CacheEvict(value = "hawbs", allEntries = true)
    public boolean delete(UUID id) {
        if (!hawbRepository.existsById(id)) return false;
        hawbRepository.deleteById(id);
        return true;
    }
}