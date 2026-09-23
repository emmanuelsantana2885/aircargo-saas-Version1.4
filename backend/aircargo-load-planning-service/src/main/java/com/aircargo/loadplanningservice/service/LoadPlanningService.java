package com.aircargo.loadplanningservice.service;

import com.aircargo.loadplanningservice.dto.LoadPlanningDTO;

import java.util.Optional;
import java.util.UUID;

public interface LoadPlanningService {
    Optional<LoadPlanningDTO> getByFlightId(UUID flightId);
    LoadPlanningDTO closeLoadPlan(UUID flightId);
    LoadPlanningDTO holdLoadPlan(UUID flightId, String status, String reason);
}
