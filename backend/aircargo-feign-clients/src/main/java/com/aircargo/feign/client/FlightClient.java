package com.aircargo.feign.client;

import com.aircargo.feign.dto.FlightDTO;
import com.aircargo.feign.dto.AirlineDTO;
import com.aircargo.feign.fallback.FlightClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "flight-service", url = "${flight-service.url:http://localhost:9093}",
        fallbackFactory = FlightClientFallbackFactory.class)
public interface FlightClient {

    @GetMapping("/api/flights/{id}")
    FlightDTO getFlightById(@PathVariable UUID id);

    @GetMapping("/api/flights/list")
    List<FlightDTO> getAllFlights();

    @PutMapping("/api/flights/{id}/status")
    FlightDTO updateFlightStatus(@PathVariable UUID id, @RequestBody String status);

    @GetMapping("/api/airlines/{id}")
    AirlineDTO getAirlineById(@PathVariable UUID id);

    @GetMapping("/api/airlines")
    List<AirlineDTO> getAllAirlines();
}
