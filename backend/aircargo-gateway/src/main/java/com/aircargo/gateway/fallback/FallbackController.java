package com.aircargo.gateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Handler for the circuit-breaker fallback URIs (forward:/fallback/{service})
 * configured in RouteConfig. Returns a consistent 503 JSON instead of a 404
 * when an upstream service is unavailable.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping(path = "/{service}", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS
    })
    public Mono<ResponseEntity<Map<String, Object>>> fallback(@PathVariable("service") String service) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Service temporarily unavailable. Please try again later.",
                "status", 503,
                "service", service == null || service.isBlank() ? "unknown" : service
        )));
    }
}