package com.aircargo.warehouseservice.calc;

import com.aircargo.warehouseservice.dto.WarehouseReceiptDTO;
import com.aircargo.warehouseservice.entity.ReceiptCalcConfig;
import com.aircargo.warehouseservice.entity.WarehouseReceipt;
import com.aircargo.warehouseservice.repository.ReceiptCalcConfigRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Resuelve los parámetros efectivos de cálculo para un recibo.
 * Precedencia por parámetro: DTO (request) &gt; entidad existente &gt; perfil de BD
 * (aerolínea o default global) &gt; constantes.
 */
@Component
public class CalcParamsResolver {

    private final ReceiptCalcConfigRepository configRepository;

    public CalcParamsResolver(ReceiptCalcConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public CalcParams resolveFor(WarehouseReceiptDTO dto, WarehouseReceipt existing) {
        UUID airlineId = dto != null && dto.getAirlineId() != null
                ? dto.getAirlineId()
                : (existing != null ? existing.getAirlineId() : null);

        CalcParams profile = resolveProfile(airlineId);

        return new CalcParams(
                method(dtoVal(dto, existing, d -> d.getChargeableMethod(), e -> e.getChargeableMethod(), profile.method().name()),
                        profile.method()),
                intV(dtoVal(dto, existing, d -> d.getDimFactorDom(), e -> e.getDimFactorDom(), profile.dimFactorDom()),
                        CalcParams.DEFAULT_DIM_FACTOR_DOM),
                intV(dtoVal(dto, existing, d -> d.getDimFactorIntl(), e -> e.getDimFactorIntl(), profile.dimFactorIntl()),
                        CalcParams.DEFAULT_DIM_FACTOR_INTL),
                decV(dtoVal(dto, existing, d -> d.getRoundUpKg(), e -> e.getRoundUpKg(), profile.roundUpKg())),
                decV(dtoVal(dto, existing, d -> d.getRoundUpLbs(), e -> e.getRoundUpLbs(), profile.roundUpLbs())),
                decV(dtoVal(dto, existing, d -> d.getMinChargeableKg(), e -> e.getMinChargeableKg(), profile.minChargeableKg())),
                decV(dtoVal(dto, existing, d -> d.getMinChargeableLbs(), e -> e.getMinChargeableLbs(), profile.minChargeableLbs())));
    }

    public CalcParams resolveProfile(UUID airlineId) {
        ReceiptCalcConfig config = configRepository.findTopByAirlineId(airlineId)
                .orElseGet(() -> configRepository.findTopByAirlineIdIsNull().orElse(null));
        return paramsFromConfig(config);
    }

    private CalcParams paramsFromConfig(ReceiptCalcConfig config) {
        if (config == null) return CalcParams.defaults();
        return new CalcParams(
                config.getChargeableMethod() != null ? config.getChargeableMethod() : ChargeableMethod.MAX,
                intV(config.getDimFactorDom(), CalcParams.DEFAULT_DIM_FACTOR_DOM),
                intV(config.getDimFactorIntl(), CalcParams.DEFAULT_DIM_FACTOR_INTL),
                decV(config.getRoundUpKg()),
                decV(config.getRoundUpLbs()),
                decV(config.getMinChargeableKg()),
                decV(config.getMinChargeableLbs()));
    }

    private ChargeableMethod method(String candidate, ChargeableMethod fallback) {
        if (candidate == null || candidate.isBlank()) return fallback;
        return ChargeableMethod.lookup(candidate);
    }

    private int intV(Integer value, int fallback) {
        if (value == null || value <= 0) return fallback;
        return value;
    }

    private BigDecimal decV(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private <T> T dtoVal(WarehouseReceiptDTO dto, WarehouseReceipt existing,
                         java.util.function.Function<WarehouseReceiptDTO, T> dtoGetter,
                         java.util.function.Function<WarehouseReceipt, T> entityGetter,
                         T profileValue) {
        if (dto != null) {
            T v = dtoGetter.apply(dto);
            if (v != null) return v;
        }
        if (existing != null) {
            T v = entityGetter.apply(existing);
            if (v != null) return v;
        }
        return profileValue;
    }
}