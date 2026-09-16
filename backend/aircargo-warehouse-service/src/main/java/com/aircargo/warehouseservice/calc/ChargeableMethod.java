package com.aircargo.warehouseservice.calc;

import java.math.BigDecimal;

/**
 * Estrategia de base para el peso cobrable de cada pieza:
 * <ul>
 *   <li>MAX   — máx(peso balanza, peso dimensional)</li>
 *   <li>SUM   — balanza + dimensional</li>
 *   <li>SCALE — solo balanza</li>
 *   <li>DIM   — solo dimensional</li>
 * </ul>
 * Independiente en kg y lbs.
 */
public enum ChargeableMethod {
    MAX, SUM, SCALE, DIM;

    public BigDecimal base(BigDecimal scale, BigDecimal dim) {
        BigDecimal s = scale != null ? scale : BigDecimal.ZERO;
        BigDecimal d = dim != null ? dim : BigDecimal.ZERO;
        switch (this) {
            case SUM:   return s.add(d);
            case SCALE: return s;
            case DIM:   return d;
            default:    return s.max(d);
        }
    }

    public static ChargeableMethod lookup(String value) {
        if (value == null || value.isBlank()) return MAX;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MAX;
        }
    }
}