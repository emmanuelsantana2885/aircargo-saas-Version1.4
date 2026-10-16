package com.aircargo.warehouseservice.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptCalcConfigValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) factory.close();
    }

    private static Set<String> paths(Object target) {
        return validator.validate(target).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static ReceiptCalcConfigDTO validConfig() {
        ReceiptCalcConfigDTO dto = new ReceiptCalcConfigDTO();
        dto.setDimFactorDom(194);
        dto.setDimFactorIntl(366);
        dto.setRoundUpKg(new BigDecimal("0.5"));
        dto.setMinChargeableLbs(new BigDecimal("100"));
        return dto;
    }

    @Test
    void validConfig_hasNoViolations() {
        assertTrue(validator.validate(validConfig()).isEmpty());
    }

    @Test
    void emptyConfig_hasNoViolations() {
        assertTrue(validator.validate(new ReceiptCalcConfigDTO()).isEmpty());
    }

    @Test
    void zeroDimFactor_isAllowed_meansDefault() {
        ReceiptCalcConfigDTO dto = new ReceiptCalcConfigDTO();
        dto.setDimFactorDom(0);
        dto.setDimFactorIntl(0);
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void negativeDimFactor_isRejected() {
        ReceiptCalcConfigDTO dto = validConfig();
        dto.setDimFactorDom(-1);
        assertTrue(paths(dto).contains("dimFactorDom"));
    }

    @Test
    void negativeRoundUp_isRejected() {
        ReceiptCalcConfigDTO dto = validConfig();
        dto.setRoundUpKg(new BigDecimal("-1"));
        assertTrue(paths(dto).contains("roundUpKg"));
    }

    @Test
    void negativeMinChargeable_isRejected() {
        ReceiptCalcConfigDTO dto = validConfig();
        dto.setMinChargeableLbs(new BigDecimal("-100"));
        assertTrue(paths(dto).contains("minChargeableLbs"));
    }
}
