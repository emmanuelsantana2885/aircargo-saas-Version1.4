package com.aircargo.feign.fallback;

import com.aircargo.feign.client.MawbClient;
import com.aircargo.feign.dto.MawbDTO;
import com.aircargo.feign.dto.HawbDTO;
import com.aircargo.feign.dto.LabelTemplateDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class MawbClientFallbackFactory implements FallbackFactory<MawbClient> {

    private static final Logger log = LoggerFactory.getLogger(MawbClientFallbackFactory.class);

    @Override
    public MawbClient create(Throwable cause) {
        return new MawbClient() {
            @Override
            public MawbDTO getMawbById(UUID id) {
                log.warn("MawbClient fallback: getMawbById({}) — {}", id, cause.getMessage());
                return null;
            }

            @Override
            public MawbDTO getMawbByAwbNumber(String awbNumber) {
                log.warn("MawbClient fallback: getMawbByAwbNumber({}) — {}", awbNumber, cause.getMessage());
                return null;
            }

            @Override
            public List<MawbDTO> getMawbsByFlight(UUID flightId) {
                log.warn("MawbClient fallback: getMawbsByFlight({}) — {}", flightId, cause.getMessage());
                return Collections.emptyList();
            }

            @Override
            public List<HawbDTO> getHawbsByMawb(UUID mawbId) {
                log.warn("MawbClient fallback: getHawbsByMawb({}) — {}", mawbId, cause.getMessage());
                return Collections.emptyList();
            }

            @Override
            public HawbDTO getHawbById(UUID id) {
                log.warn("MawbClient fallback: getHawbById({}) — {}", id, cause.getMessage());
                return null;
            }

            @Override
            public MawbDTO createMawb(MawbDTO dto) {
                log.error("MawbClient fallback: createMawb — writes cannot degrade", cause);
                throw new RuntimeException("Mawb service unavailable: " + cause.getMessage(), cause);
            }

            @Override
            public MawbDTO updateMawb(UUID id, MawbDTO dto) {
                log.error("MawbClient fallback: updateMawb({}) — writes cannot degrade", id, cause);
                throw new RuntimeException("Mawb service unavailable: " + cause.getMessage(), cause);
            }

            @Override
            public MawbDTO updateMawbStatus(UUID id, String status) {
                log.error("MawbClient fallback: updateMawbStatus({},{}) — writes cannot degrade", id, status, cause);
                throw new RuntimeException("Mawb service unavailable: " + cause.getMessage(), cause);
            }

            @Override
            public List<LabelTemplateDTO> getLabelTemplates(String type) {
                log.warn("MawbClient fallback: getLabelTemplates({}) — {}", type, cause.getMessage());
                return Collections.emptyList();
            }

            @Override
            public LabelTemplateDTO getLabelTemplateById(UUID id) {
                log.warn("MawbClient fallback: getLabelTemplateById({}) — {}", id, cause.getMessage());
                return null;
            }
        };
    }
}
