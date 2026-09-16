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