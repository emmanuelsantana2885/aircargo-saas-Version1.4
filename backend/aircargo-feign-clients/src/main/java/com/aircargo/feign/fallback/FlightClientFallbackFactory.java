package com.aircargo.feign.fallback;

import com.aircargo.feign.client.FlightClient;
import com.aircargo.feign.dto.FlightDTO;
import com.aircargo.feign.dto.AirlineDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class FlightClientFallbackFactory implements FallbackFactory<FlightClient> {

    private static final Logger log = LoggerFactory.getLogger(FlightClientFallbackFactory.class);

    @Override
    public FlightClient create(Throwable cause) {
        return new FlightClient() {
            @Override
            public FlightDTO getFlightById(UUID id) {
                log.warn("FlightClient fallback: getFlightById({}) — {}", id, cause.getMessage());
                return null;
            }

            @Override
            public List<FlightDTO> getAllFlights() {
                log.warn("FlightClient fallback: getAllFlights — {}", cause.getMessage());
                return Collections.emptyList();
            }

            @Override
            public FlightDTO updateFlightStatus(UUID id, String status) {
                log.error("FlightClient fallback: updateFlightStatus({},{}) — writes cannot degrade", id, status, cause);
                throw new RuntimeException("Flight service unavailable: " + cause.getMessage(), cause);
            }

            @Override
            public AirlineDTO getAirlineById(UUID id) {
                log.warn("FlightClient fallback: getAirlineById({}) — {}", id, cause.getMessage());
                return null;
            }

            @Override
            public List<AirlineDTO> getAllAirlines() {
                log.warn("FlightClient fallback: getAllAirlines — {}", cause.getMessage());
                return Collections.emptyList();
            }
        };
    }
}
