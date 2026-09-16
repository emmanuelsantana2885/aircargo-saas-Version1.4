package com.aircargo.feign.fallback;

import com.aircargo.feign.client.UldClient;
import com.aircargo.feign.dto.UldDTO;
import com.aircargo.feign.dto.UldAwbDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class UldClientFallbackFactory implements FallbackFactory<UldClient> {

    private static final Logger log = LoggerFactory.getLogger(UldClientFallbackFactory.class);

    @Override
    public UldClient create(Throwable cause) {
        return new UldClient() {
            @Override
            public UldDTO getUldById(UUID id) {
                log.warn("UldClient fallback: getUldById({}) — {}", id, cause.getMessage());
                return null;
            }

            @Override
            public List<UldDTO> getUlds(UUID airlineId, UUID flightId) {
                log.warn("UldClient fallback: getUlds(airline={},flight={}) — {}", airlineId, flightId, cause.getMessage());
                return Collections.emptyList();
            }

            @Override
            public UldDTO createUld(UldDTO dto) {
                log.error("UldClient fallback: createUld — writes cannot degrade", cause);
                throw new RuntimeException("ULD service unavailable: " + cause.getMessage(), cause);
            }

            @Override
            public UldDTO updateUld(UUID id, UldDTO dto) {
                log.error("UldClient fallback: updateUld({}) — writes cannot degrade", id, cause);
                throw new RuntimeException("ULD service unavailable: " + cause.getMessage(), cause);
            }

            @Override
            public List<UldAwbDTO> getUldAwbs(UUID uldId, UUID mawbId) {
                log.warn("UldClient fallback: getUldAwbs(uld={},mawb={}) — {}", uldId, mawbId, cause.getMessage());
                return Collections.emptyList();
            }

            @Override
            public UldAwbDTO createUldAwb(UldAwbDTO dto) {
                log.error("UldClient fallback: createUldAwb — writes cannot degrade", cause);
                throw new RuntimeException("ULD service unavailable: " + cause.getMessage(), cause);
            }
        };
    }
}
