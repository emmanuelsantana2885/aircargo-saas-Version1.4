package com.aircargo.mawbservice.service;

import com.aircargo.mawbservice.dto.HawbDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HawbService {
    List<HawbDTO> getByMawbId(UUID mawbId);
    Page<HawbDTO> getByMawbId(UUID mawbId, Pageable pageable);
    Optional<HawbDTO> getById(UUID id);
    Optional<HawbDTO> getByHawbNumber(String hawbNumber);
    HawbDTO create(HawbDTO dto);
    Optional<HawbDTO> update(UUID id, HawbDTO dto);
    boolean delete(UUID id);
}