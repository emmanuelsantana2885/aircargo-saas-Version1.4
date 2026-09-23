package com.aircargo.feign.client;

import com.aircargo.feign.dto.UldDTO;
import com.aircargo.feign.dto.UldAwbDTO;
import com.aircargo.feign.fallback.UldClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "uld-service", url = "${uld-service.url:http://localhost:9097}",
        fallbackFactory = UldClientFallbackFactory.class)
public interface UldClient {

    @GetMapping("/api/ulds/{id}")
    UldDTO getUldById(@PathVariable UUID id);

    @GetMapping("/api/ulds")
    List<UldDTO> getUlds(@RequestParam(required = false) UUID airlineId,
                          @RequestParam(required = false) UUID flightId);

    @PostMapping("/api/ulds")
    UldDTO createUld(@RequestBody UldDTO dto);

    @PutMapping("/api/ulds/{id}")
    UldDTO updateUld(@PathVariable UUID id, @RequestBody UldDTO dto);

    @PostMapping("/api/ulds/{id}/release")
    UldDTO releaseFromFlight(@PathVariable UUID id);

    @GetMapping("/api/uld-awbs")
    List<UldAwbDTO> getUldAwbs(@RequestParam(required = false) UUID uldId,
                                @RequestParam(required = false) UUID mawbId);

    @PostMapping("/api/uld-awbs")
    UldAwbDTO createUldAwb(@RequestBody UldAwbDTO dto);
}
