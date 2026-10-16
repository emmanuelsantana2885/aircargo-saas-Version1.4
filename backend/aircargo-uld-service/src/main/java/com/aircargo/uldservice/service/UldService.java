package com.aircargo.uldservice.service;

import com.aircargo.common.dto.PageResponse;
import com.aircargo.uldservice.dto.UldDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UldService {
    List<UldDTO> getAll(UUID airlineId, UUID flightId);
    PageResponse<UldDTO> getAll(UUID airlineId, UUID flightId, int page, int size);
    Optional<UldDTO> getById(UUID id);
    UldDTO create(UldDTO dto);
    Optional<UldDTO> update(UUID id, UldDTO dto);
    Optional<UldDTO> update(UUID id, UldDTO dto, boolean enforceOperatorMandatory);
    UldDTO assignFlight(UUID id, UUID flightId);
    UldDTO transferUld(UUID uldId, UUID destinationFlightId, String reason);
    boolean delete(UUID id);

    /**
     * Despacha todos los ULDs de un vuelo: guarda la snapshot (vuelo + estado previo),
     * los marca LOADED y les quita el flightId para que puedan reutilizarse en otro vuelo.
     * @return número de ULDs despachados.
     */
    int dispatchFlight(UUID flightId);

    /**
     * Recupera (revierte el despacho de) todos los ULDs de un vuelo: les devuelve el flightId
     * y el estado previo al despacho, y limpia la snapshot.
     * @return número de ULDs recuperados.
     */
    int recoverFlight(UUID flightId);
}
