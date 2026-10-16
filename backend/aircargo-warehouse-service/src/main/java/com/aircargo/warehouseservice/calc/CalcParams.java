package com.aircargo.warehouseservice.calc;

import java.math.BigDecimal;

/**
 * Parámetros efectivos de cálculo de un recibo, ya normalizados
 * (nunca null, divisores siempre positivos).
 */
public record CalcParams(
        ChargeableMethod method,
        int dimFactorDom,
        int dimFactorIntl,
        BigDecimal roundUpKg,
        BigDecimal roundUpLbs,
        BigDecimal minChargeableKg,
        BigDecimal minChargeableLbs) {

    public static final int DEFAULT_DIM_FACTOR_DOM = 194;
    public static final int DEFAULT_DIM_FACTOR_INTL = 366;

    /**
     * Normaliza los parámetros al construirse: nunca null y divisores siempre positivos.
     * Así el resto del motor puede asumir invariantes sin defensas repetidas.
     */
    public CalcParams {
        method = method != null ? method : ChargeableMethod.MAX;
        dimFactorDom = dimFactorDom > 0 ? dimFactorDom : DEFAULT_DIM_FACTOR_DOM;
        dimFactorIntl = dimFactorIntl > 0 ? dimFactorIntl : DEFAULT_DIM_FACTOR_INTL;
        roundUpKg = roundUpKg != null ? roundUpKg : BigDecimal.ZERO;
        roundUpLbs = roundUpLbs != null ? roundUpLbs : BigDecimal.ZERO;
        minChargeableKg = minChargeableKg != null ? minChargeableKg : BigDecimal.ZERO;
        minChargeableLbs = minChargeableLbs != null ? minChargeableLbs : BigDecimal.ZERO;
    }

    public static CalcParams defaults() {
        return new CalcParams(
                ChargeableMethod.MAX,
                DEFAULT_DIM_FACTOR_DOM,
                DEFAULT_DIM_FACTOR_INTL,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}