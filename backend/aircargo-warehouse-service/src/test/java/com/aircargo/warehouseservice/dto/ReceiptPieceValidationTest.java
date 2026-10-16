package com.aircargo.warehouseservice.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptPieceValidationTest {

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

    private static ReceiptPieceDTO validPiece() {
        ReceiptPieceDTO p = new ReceiptPieceDTO();
        p.setPieces(1);
        p.setLengthIn(new BigDecimal("20"));
        p.setWidthIn(new BigDecimal("20"));
        p.setHeightIn(new BigDecimal("20"));
        p.setScaleWeightLbs(new BigDecimal("50"));
        return p;
    }

    @Test
    void validPiece_hasNoViolations() {
        assertTrue(validator.validate(validPiece()).isEmpty());
    }

    @Test
    void negativePieces_isRejected() {
        ReceiptPieceDTO p = validPiece();
        p.setPieces(-1);
        assertTrue(paths(p).contains("pieces"));
    }

    @Test
    void negativeDimension_isRejected() {
        ReceiptPieceDTO p = validPiece();
        p.setLengthIn(new BigDecimal("-5"));
        assertTrue(paths(p).contains("lengthIn"));
    }

    @Test
    void negativeScaleWeight_isRejected() {
        ReceiptPieceDTO p = validPiece();
        p.setScaleWeightLbs(new BigDecimal("-50"));
        assertTrue(paths(p).contains("scaleWeightLbs"));
    }

    @Test
    void cascadeFromReceipt_rejectsNegativeNestedPiece() {
        WarehouseReceiptDTO receipt = new WarehouseReceiptDTO();
        ReceiptPieceDTO bad = validPiece();
        bad.setPieces(-3);
        receipt.setPieces(List.of(bad));

        Set<String> paths = paths(receipt);
        assertTrue(paths.contains("pieces[0].pieces"),
                "expected cascade violation on pieces[0].pieces but was " + paths);
    }

    @Test
    void negativeReceiptWeight_isRejected() {
        WarehouseReceiptDTO receipt = new WarehouseReceiptDTO();
        receipt.setChargeableWeightKg(new BigDecimal("-1"));
        assertTrue(paths(receipt).contains("chargeableWeightKg"));
    }

    @Test
    void negativeReceiptPieceCount_isRejected() {
        WarehouseReceiptDTO receipt = new WarehouseReceiptDTO();
        receipt.setPieceCount(-2);
        assertTrue(paths(receipt).contains("pieceCount"));
    }

    @Test
    void validReceiptWithPieces_hasNoViolations() {
        WarehouseReceiptDTO receipt = new WarehouseReceiptDTO();
        receipt.setPieceCount(1);
        receipt.setChargeableWeightKg(new BigDecimal("22.680"));
        receipt.setPieces(List.of(validPiece()));
        assertEquals(0, validator.validate(receipt).size());
    }
}
