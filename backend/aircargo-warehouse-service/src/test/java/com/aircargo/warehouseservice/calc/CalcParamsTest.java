package com.aircargo.warehouseservice.calc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CalcParamsTest {

    @Test
    void nullMethodDefaultsToMax() {
        CalcParams p = new CalcParams(null, 194, 366, null, null, null, null);
        assertSame(ChargeableMethod.MAX, p.method());
    }

    @Test
    void nonPositiveDimFactorsFallBackToDefaults() {
        CalcParams p = new CalcParams(ChargeableMethod.MAX, 0, -5, null, null, null, null);
        assertEquals(CalcParams.DEFAULT_DIM_FACTOR_DOM, p.dimFactorDom());
        assertEquals(CalcParams.DEFAULT_DIM_FACTOR_INTL, p.dimFactorIntl());
    }

    @Test
    void nullBigDecimalsNormalizeToZero() {
        CalcParams p = new CalcParams(null, 194, 366, null, null, null, null);
        assertEquals(BigDecimal.ZERO, p.roundUpKg());
        assertEquals(BigDecimal.ZERO, p.roundUpLbs());
        assertEquals(BigDecimal.ZERO, p.minChargeableKg());
        assertEquals(BigDecimal.ZERO, p.minChargeableLbs());
    }

    @Test
    void validValuesArePreserved() {
        CalcParams p = new CalcParams(ChargeableMethod.SUM, 150, 300,
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("5"), new BigDecimal("10"));
        assertEquals(ChargeableMethod.SUM, p.method());
        assertEquals(150, p.dimFactorDom());
        assertEquals(300, p.dimFactorIntl());
        assertEquals(0, new BigDecimal("1").compareTo(p.roundUpKg()));
        assertEquals(0, new BigDecimal("2").compareTo(p.roundUpLbs()));
        assertEquals(0, new BigDecimal("5").compareTo(p.minChargeableKg()));
        assertEquals(0, new BigDecimal("10").compareTo(p.minChargeableLbs()));
    }

    @Test
    void defaultsEqualsResolvedDefaults() {
        assertEquals(CalcParams.defaults(), new CalcParams(null, 0, 0, null, null, null, null));
    }
}
