package com.aircargo.loadplanningservice.service;

import com.aircargo.common.entity.CommodityType;
import com.aircargo.feign.client.FlightClient;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.feign.client.UldClient;
import com.aircargo.feign.dto.FlightDTO;
import com.aircargo.feign.dto.MawbDTO;
import com.aircargo.feign.dto.UldAwbDTO;
import com.aircargo.feign.dto.UldDTO;
import com.aircargo.common.event.FlightDepartedEvent;
import com.aircargo.loadplanningservice.config.RabbitConfig;
import com.aircargo.loadplanningservice.dto.LoadPlanningDTO;
import com.aircargo.loadplanningservice.dto.LoadPlanningUldDTO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LoadPlanningServiceImpl implements LoadPlanningService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LoadPlanningServiceImpl.class);

    private static final String FLIGHT_DEPARTED_KEY = "flight.departed";

    /**
     * Motivos validos para recuperar un load planning ya despachado. La
     * recuperacion es una excepcion: solo se autoriza ante un problema de
     * vuelo o una desviacion de la operacion, nunca como cierre normal.
     */
    private static final Set<String> ALLOWED_RECOVERY_EXCEPTION_TYPES =
            Set.of("FLIGHT_PROBLEM", "OPERATIONAL_DEVIATION", "OTHER");

    private final FlightClient flightClient;
    private final UldClient uldClient;
    private final MawbClient mawbClient;
    private final RabbitTemplate rabbitTemplate;
    private final WeightBalanceService weightBalanceService;

    public LoadPlanningServiceImpl(FlightClient flightClient,
                                    UldClient uldClient,
                                    MawbClient mawbClient,
                                    RabbitTemplate rabbitTemplate,
                                    WeightBalanceService weightBalanceService) {
        this.flightClient = flightClient;
        this.uldClient = uldClient;
        this.mawbClient = mawbClient;
        this.rabbitTemplate = rabbitTemplate;
        this.weightBalanceService = weightBalanceService;
    }

    /**
     * Overrides link commodity/status/destination with LIVE MAWB data (source of truth).
     * The uld_awb snapshot is kept in sync via mawb.updated events, but this read-time
     * enrichment guarantees load-planning reflects the current MAWB even right after
     * a Booking/MAWB edit or for links whose snapshot is still pending.
     */
    private UldAwbDTO enrichFromMawb(UldAwbDTO link) {
        try {
            MawbDTO mawb = null;
            if (link.getMawbId() != null) {
                mawb = mawbClient.getMawbById(link.getMawbId());
            }
            if (mawb == null && link.getMawbLabel() != null) {
                String canonical = normalizeAwb(link.getMawbLabel());
                if (canonical != null) {
                    mawb = mawbClient.getMawbByAwbNumber(canonical);
                }
            }
            if (mawb == null) return link;
            if (mawb.getCommodityType() != null) link.setDescription(mapCommodityType(mawb.getCommodityType()));
            if (mawb.getStatus() != null) link.setStatus(mawb.getStatus());
            if (mawb.getDestination() != null) link.setDestination(mawb.getDestination());
        } catch (Exception e) {
            log.warn("enrichFromMawb fallback (link {}) {}", link.getMawbLabel(), e.getMessage());
        }
        return link;
    }

    private String normalizeAwb(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[\\s\\-/_]", "");
        if (digits.length() != 11) return null;
        return digits.substring(0, 3) + "-" + digits.substring(3);
    }

    @Override
    @Cacheable(value = "load-plans", key = "#flightId")
    public Optional<LoadPlanningDTO> getByFlightId(UUID flightId) {
        try {
            FlightDTO flight = flightClient.getFlightById(flightId);
            if (flight == null) return Optional.empty();

            List<UldDTO> ulds = uldClient.getUlds(null, flightId);

            List<LoadPlanningUldDTO> uldDtos = ulds.stream()
                    .map(uld -> {
                        List<UldAwbDTO> awbs = uld.getAwbs();
                        if (awbs == null) {
                            awbs = uldClient.getUldAwbs(uld.getId(), null);
                        }
                        if (awbs != null) {
                            awbs = awbs.stream().map(this::enrichFromMawb).collect(Collectors.toList());
                        }

                        LoadPlanningUldDTO dto = new LoadPlanningUldDTO();
                        dto.setId(uld.getId());
                        dto.setUldNumber(uld.getUldNumber());
                        dto.setUldType(uld.getUldType());
                        dto.setPosition(uld.getPosition());
                        dto.setConfig(uld.getConfig());
                        dto.setSealNumber(uld.getSealNumber());
                        dto.setTareLbs(uld.getTareLbs());
                        dto.setGrossWeightLbs(uld.getGrossWeightLbs());
                        dto.setNetWeightLbs(uld.getNetWeightLbs());
                        dto.setStatus(uld.getStatus());
                        dto.setAwbs(awbs);
                        dto.setDestination(uld.getDestination());
                        dto.setLoadedBy(uld.getLoadedBy());
                        dto.setWeighedBy(uld.getWeighedBy());
                        dto.setConfirmedWith(uld.getConfirmedWith());
                        dto.setCompletedAt(uld.getCompletedAt() != null ? uld.getCompletedAt().toString() : null);
                        return dto;
                    })
                    .collect(Collectors.toList());

            String airlineName = null;
            String airlineCode = null;
            try {
                var airline = flightClient.getAirlineById(flight.getAirlineId());
                if (airline != null) {
                    airlineName = airline.getName();
                    airlineCode = airline.getCode() != null ? airline.getCode()
                            : (airline.getIataCode() != null ? airline.getIataCode() : null);
                }
            } catch (Exception ignored) {}

            LoadPlanningDTO result = new LoadPlanningDTO();
            result.setFlightId(flight.getId());
            result.setFlightNumber(flight.getFlightNumber());
            result.setOrigin(flight.getOrigin());
            result.setDestination(flight.getDestination());
            result.setAircraftReg(flight.getAircraftReg());
            result.setAircraftType(flight.getAircraftType());
            result.setFlightDate(flight.getFlightDate());
            result.setTotalPositions(flight.getTotalPositions());
            result.setMaxPayloadKg(flight.getMaxPayloadKg() != null
                    ? java.math.BigDecimal.valueOf(flight.getMaxPayloadKg()) : null);
            result.setUlds(uldDtos);
            result.setAirlineName(airlineName);
            result.setAirlineCode(airlineCode);
            try {
                result.setWeightBalance(weightBalanceService.compute(flight.getAircraftType(), uldDtos));
            } catch (Exception e) {
                log.warn("Weight & balance computation failed for flight {}: {}", flightId, e.getMessage());
            }
            return Optional.of(result);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    @CacheEvict(value = "load-plans", key = "#flightId")
    public LoadPlanningDTO closeLoadPlan(UUID flightId) {
        FlightDTO flight = flightClient.getFlightById(flightId);
        if (flight == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found: " + flightId);
        }

        flightClient.updateFlightStatus(flightId, "DEPARTED");
        publishFlightDeparted(flight);

        // Marca todas las MAWB del vuelo como DEPARTED antes de liberar los ULDs.
        List<UldDTO> ulds = uldClient.getUlds(null, flightId);
        for (UldDTO uld : ulds) {
            List<UldAwbDTO> links = uld.getAwbs();
            if (links == null) {
                links = uldClient.getUldAwbs(uld.getId(), null);
            }
            for (UldAwbDTO link : links) {
                if (link.getMawbId() != null) {
                    MawbDTO mawb = mawbClient.getMawbById(link.getMawbId());
                    if (mawb != null && !"DEPARTED".equals(mawb.getStatus())) {
                        mawbClient.updateMawbStatus(link.getMawbId(), "DEPARTED");
                    }
                }
            }
        }

        // Libera todos los ULDs en una sola operacion atomica. El ULD service
        // guarda un snapshot (vuelo + estado previo) por ULD para poder
        // recuperar el despacho si despues se levanta una excepcion operativa.
        try {
            Map<String, Object> dispatched = uldClient.dispatchFlight(flightId);
            log.info("Close load plan {}: dispatched ULDs -> {}", flightId, dispatched);
        } catch (Exception e) {
            log.warn("Failed to dispatch ULDs for flight {}: {}", flightId, e.getMessage());
        }

        return getByFlightId(flightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Load plan not found after close"));
    }

    @Override
    @CacheEvict(value = "load-plans", key = "#flightId")
    public LoadPlanningDTO holdLoadPlan(UUID flightId, String status, String reason) {
        FlightDTO flight = flightClient.getFlightById(flightId);
        if (flight == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found: " + flightId);
        }

        String targetStatus = (status == null || status.isBlank()) ? "DELAYED" : status.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"SCHEDULED".equals(targetStatus) && !"DELAYED".equals(targetStatus) && !"CANCELLED".equals(targetStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hold status must be SCHEDULED, DELAYED or CANCELLED");
        }

        String current = flight.getStatus();
        if (!"DEPARTED".equals(current)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only DEPARTED flights can be held (current status: " + current + ")");
        }

        log.info("Holding flight {} {} -> {} (reason: {})", flight.getFlightNumber(), flight.getId(),
                targetStatus, reason != null && !reason.isBlank() ? reason : "-");

        flightClient.updateFlightStatus(flightId, targetStatus);

        int reverted = 0;
        try {
            List<MawbDTO> mawbs = mawbClient.getMawbsByFlight(flightId);
            for (MawbDTO mawb : mawbs) {
                if (mawb.getId() == null) continue;
                if (!"DEPARTED".equals(mawb.getStatus())) continue;
                try {
                    mawbClient.updateMawbStatus(mawb.getId(), "MANIFESTED");
                    reverted++;
                } catch (Exception e) {
                    log.warn("Failed to revert MAWB {} to MANIFESTED: {}", mawb.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not revert MAWBs for flight {}: {}", flightId, e.getMessage());
        }
        log.info("Hold: reverted {} MAWB(s) to MANIFESTED for flight {}", reverted, flightId);

        return getByFlightId(flightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Load plan not found after hold"));
    }

    /**
     * Recupera un load planning ya despachado ante una excepcion operativa.
     * Es la unica via para reincorporar al plan los ULDs que el despacho libero:
     * exige un tipo de excepcion y un motivo, revierte el vuelo a SCHEDULED,
     * devuelve las MAWB a MANIFESTED y restaura todos los ULDs del vuelo con su
     * estado previo al despacho (mediante el snapshot guardado en el ULD service).
     */
    @Override
    @CacheEvict(value = "load-plans", key = "#flightId")
    public LoadPlanningDTO recoverLoadPlan(UUID flightId, String exceptionType, String reason) {
        FlightDTO flight = flightClient.getFlightById(flightId);
        if (flight == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found: " + flightId);
        }

        String type = (exceptionType == null || exceptionType.isBlank())
                ? null : exceptionType.trim().toUpperCase(Locale.ROOT);
        if (type == null || !ALLOWED_RECOVERY_EXCEPTION_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A valid exceptionType is required to recover a dispatched load plan "
                            + "(FLIGHT_PROBLEM, OPERATIONAL_DEVIATION, OTHER)");
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A reason is required to recover a dispatched load plan");
        }

        String current = flight.getStatus();
        if (!"DEPARTED".equals(current)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only DEPARTED flights can be recovered (current status: " + current + ")");
        }

        log.warn("Recovering dispatched load plan for flight {} {} - exception={} reason={}",
                flight.getFlightNumber(), flightId, type, reason.trim());

        flightClient.updateFlightStatus(flightId, "SCHEDULED");

        int reverted = 0;
        try {
            List<MawbDTO> mawbs = mawbClient.getMawbsByFlight(flightId);
            for (MawbDTO mawb : mawbs) {
                if (mawb.getId() == null) continue;
                if (!"DEPARTED".equals(mawb.getStatus())) continue;
                try {
                    mawbClient.updateMawbStatus(mawb.getId(), "MANIFESTED");
                    reverted++;
                } catch (Exception e) {
                    log.warn("Failed to revert MAWB {} to MANIFESTED: {}", mawb.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not revert MAWBs for flight {}: {}", flightId, e.getMessage());
        }

        int recoveredUlds = 0;
        try {
            Map<String, Object> recovered = uldClient.recoverFlight(flightId);
            Object count = recovered != null ? recovered.get("recovered") : null;
            recoveredUlds = count instanceof Number n ? n.intValue() : 0;
        } catch (Exception e) {
            log.warn("Failed to recover ULDs for flight {}: {}", flightId, e.getMessage());
        }

        log.info("Recovered load plan for flight {}: {} MAWB(s) reverted, {} ULD(s) restored",
                flightId, reverted, recoveredUlds);

        return getByFlightId(flightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Load plan not found after recovery"));
    }

    private void publishFlightDeparted(FlightDTO flight) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, FLIGHT_DEPARTED_KEY,
                    new FlightDepartedEvent(flight.getId(), flight.getFlightNumber(), flight.getAirlineId()));
            log.info("Published flight.departed event for flight {}", flight.getId());
        } catch (Exception e) {
            log.warn("Failed to publish flight.departed for flight {}: {}", flight.getId(), e.getMessage());
        }
    }

    private CommodityType mapCommodityType(String description) {
        if (description == null || description.isBlank()) {
            return CommodityType.GENERAL;
        }
        String normalized = description.trim().toUpperCase(java.util.Locale.ROOT).replace(" ", "_").replace("-", "_");
        try {
            return CommodityType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return CommodityType.GENERAL;
        }
    }
}
