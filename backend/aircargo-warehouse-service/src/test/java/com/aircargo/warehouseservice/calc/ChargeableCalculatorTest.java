package com.aircargo.warehouseservice.calc;

import com.aircargo.warehouseservice.dto.ReceiptPieceDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChargeableCalculatorTest {

    private ReceiptPieceDTO piece(Integer pieces, String dim, String scaleLbs, String scaleKg) {
        ReceiptPieceDTO p = new ReceiptPieceDTO();
        p.setPieces(pieces);
        if (dim != null) {
            BigDecimal d = new BigDecimal(dim);
            p.setLengthIn(d);
            p.setWidthIn(d);
            p.setHeightIn(d);
        }
        if (scaleLbs != null) p.setScaleWeightLbs(new BigDecimal(scaleLbs));
        if (scaleKg != null) p.setScaleWeightKg(new BigDecimal(scaleKg));
        return p;
    }

    private CalcParams params(ChargeableMethod method, String roundUpKg, String roundUpLbs,
                              String minKg, String minLbs) {
        return new CalcParams(method, 194, 366,
                new BigDecimal(roundUpKg), new BigDecimal(roundUpLbs),
                new BigDecimal(minKg), new BigDecimal(minLbs));
    }

    @Test
    void roundUp_ceilingsToStepMultiples() {
        assertEquals(0, new BigDecimal("40").compareTo(ChargeableCalculator.roundUp(new BigDecimal("37.4"), new BigDecimal("5"))));
        assertEquals(0, new BigDecimal("40").compareTo(ChargeableCalculator.roundUp(new BigDecimal("40"), new BigDecimal("5"))));
    }

    @Test
    void roundUp_nullOrNonPositiveStepReturnsValue() {
        assertEquals(0, ChargeableCalculator.roundUp(new BigDecimal("37.4"), null).compareTo(new BigDecimal("37.4")));
        assertEquals(0, ChargeableCalculator.roundUp(new BigDecimal("37.4"), BigDecimal.ZERO).compareTo(new BigDecimal("37.4")));
        assertEquals(BigDecimal.ZERO, ChargeableCalculator.roundUp(null, new BigDecimal("5")));
    }

    @Test
    void applyMin_enforcesMinimumOnlyAscending() {
        assertEquals(0, ChargeableCalculator.applyMin(new BigDecimal("30"), new BigDecimal("50")).compareTo(new BigDecimal("50")));
        assertEquals(0, ChargeableCalculator.applyMin(new BigDecimal("60"), new BigDecimal("50")).compareTo(new BigDecimal("60")));
        assertEquals(0, ChargeableCalculator.applyMin(new BigDecimal("60"), BigDecimal.ZERO).compareTo(new BigDecimal("60")));
    }

    @Test
    void volume_computedFromDimsTimesPieces() {
        assertEquals(0, new BigDecimal("16000").compareTo(ChargeableCalculator.volume(piece(2, "20", null, null))));
    }

    @Test
    void volume_nullWhenDimsMissing() {
        assertNull(ChargeableCalculator.volume(piece(1, null, "50", null)));
    }

    @Test
    void base_choosesPerMethod() {
        BigDecimal scale = new BigDecimal("50");
        BigDecimal dim = new BigDecimal("41.24");
        assertEquals(0, ChargeableMethod.MAX.base(scale, dim).compareTo(new BigDecimal("50")));
        assertEquals(0, ChargeableMethod.SUM.base(scale, dim).compareTo(new BigDecimal("91.24")));
        assertEquals(scale, ChargeableMethod.SCALE.base(scale, dim));
        assertEquals(dim, ChargeableMethod.DIM.base(scale, dim));
        assertEquals(BigDecimal.ZERO, ChargeableMethod.MAX.base(null, null));
    }

    @Test
    void apply_maxScaleWins_keepsUnitsConsistent() {
        ReceiptPieceDTO p = piece(1, "20", "50", null);
        ChargeableCalculator.apply(p, CalcParams.defaults());
        assertEquals(0, new BigDecimal("41.24").compareTo(p.getDimWeightLbs()));
        assertEquals(0, new BigDecimal("21.858").compareTo(p.getDimWeightKg()));
        assertEquals(0, new BigDecimal("50").compareTo(p.getChargeableLbs()));
        assertEquals(0, new BigDecimal("22.680").compareTo(p.getChargeableKg()));
    }

    @Test
    void apply_maxDimWins_usesLinearKg() {
        ReceiptPieceDTO p = piece(1, "20", "20", "9.072");
        ChargeableCalculator.apply(p, CalcParams.defaults());
        assertEquals(0, new BigDecimal("41.24").compareTo(p.getChargeableLbs()));
        assertEquals(0, new BigDecimal("18.706").compareTo(p.getChargeableKg()));
    }

    @Test
    void apply_scaleMethod_ignoresDim() {
        ReceiptPieceDTO p = piece(1, "20", "50", null);
        ChargeableCalculator.apply(p, params(ChargeableMethod.SCALE, "0", "0", "0", "0"));
        assertEquals(0, new BigDecimal("50").compareTo(p.getChargeableLbs()));
        assertEquals(0, new BigDecimal("22.680").compareTo(p.getChargeableKg()));
    }

    @Test
    void apply_dimMethod_ignoresScale() {
        ReceiptPieceDTO p = piece(1, "20", "50", null);
        ChargeableCalculator.apply(p, params(ChargeableMethod.DIM, "0", "0", "0", "0"));
        assertEquals(0, new BigDecimal("41.24").compareTo(p.getChargeableLbs()));
        assertEquals(0, new BigDecimal("18.706").compareTo(p.getChargeableKg()));
    }

    @Test
    void apply_sumMethod_addsBothUnits() {
        ReceiptPieceDTO p = piece(1, "20", "50", null);
        ChargeableCalculator.apply(p, params(ChargeableMethod.SUM, "0", "0", "0", "0"));
        assertEquals(0, new BigDecimal("91.24").compareTo(p.getChargeableLbs()));
        assertEquals(0, new BigDecimal("41.386").compareTo(p.getChargeableKg()));
    }

    @Test
    void apply_roundUpAndMinAppliedPerPiece() {
        ReceiptPieceDTO p = piece(1, "20", "2", null);
        ChargeableCalculator.apply(p, params(ChargeableMethod.MAX, "10", "10", "5", "5"));
        assertEquals(0, new BigDecimal("50").compareTo(p.getChargeableLbs()));
        assertEquals(0, new BigDecimal("22.680").compareTo(p.getChargeableKg()));
    }

    @Test
    void apply_minChargeableRaisesSmallShipment() {
        ReceiptPieceDTO p = piece(1, null, "2", null);
        ChargeableCalculator.apply(p, params(ChargeableMethod.MAX, "0", "0", "100", "100"));
        assertEquals(0, new BigDecimal("100").compareTo(p.getChargeableLbs()));
        assertEquals(0, new BigDecimal("100").compareTo(p.getChargeableKg()));
    }

    @Test
    void totals_sumsAndScalesOnTotal() {
        ReceiptPieceDTO p1 = piece(1, null, "50", null);
        ReceiptPieceDTO p2 = piece(1, null, "50", null);
        ChargeableCalculator.apply(p1, CalcParams.defaults());
        ChargeableCalculator.apply(p2, CalcParams.defaults());

        ChargeableCalculator.CalcTotals t = ChargeableCalculator.totals(List.of(p1, p2), CalcParams.defaults());

        assertEquals(2, t.pieceCount());
        assertEquals(0, new BigDecimal("100.00").compareTo(t.actualWeightLbs()));
        assertEquals(0, new BigDecimal("45.360").compareTo(t.actualWeightKg()));
        assertEquals(0, new BigDecimal("100.00").compareTo(t.chargeableWeightLbs()));
        assertEquals(0, new BigDecimal("45.360").compareTo(t.chargeableWeightKg()));
    }

    @Test
    void totals_appliesRoundUpOnAggregate() {
        ReceiptPieceDTO p = piece(1, null, "50", null);
        CalcParams setup = params(ChargeableMethod.MAX, "50", "50", "0", "0");
        ChargeableCalculator.apply(p, setup);

        ChargeableCalculator.CalcTotals t = ChargeableCalculator.totals(List.of(p), setup);

        assertEquals(1, t.pieceCount());
        assertEquals(0, new BigDecimal("50.00").compareTo(t.chargeableWeightLbs()));
        assertEquals(0, new BigDecimal("50.000").compareTo(t.chargeableWeightKg()));
    }

    @Test
    void totals_emptyReturnsZeros() {
        ChargeableCalculator.CalcTotals t = ChargeableCalculator.totals(List.of(), CalcParams.defaults());
        assertEquals(0, t.pieceCount());
        assertEquals(BigDecimal.ZERO, t.actualWeightLbs());
        assertEquals(BigDecimal.ZERO, t.chargeableWeightKg());
    }
}