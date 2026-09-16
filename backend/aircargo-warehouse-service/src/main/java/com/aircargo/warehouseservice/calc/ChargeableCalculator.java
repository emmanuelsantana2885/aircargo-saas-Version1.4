package com.aircargo.warehouseservice.calc;

import com.aircargo.warehouseservice.dto.ReceiptPieceDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Motor de cálculo de pesos para recibos de bodega.
 * Modelo unificado de 2 divisores: kg usa dimFactorIntl (366), lbs usa dimFactorDom (194).
 * El cobrable por pieza se redondea al alza según round-up y nunca baja del mínimo.
 * Los totales aplican el mismo redondeo/mínimo sobre la suma de cobrables por pieza.
 */
public final class ChargeableCalculator {

    public static final BigDecimal KGS_PER_LB = BigDecimal.valueOf(0.45359237);
    public static final BigDecimal LBS_PER_KG = BigDecimal.valueOf(2.20462);

    private ChargeableCalculator() {
    }

    public static BigDecimal roundUp(BigDecimal value, BigDecimal step) {
        if (value == null) return BigDecimal.ZERO;
        if (step == null || step.signum() <= 0) return value;
        return value.divide(step, 0, RoundingMode.CEILING).multiply(step);
    }

    public static BigDecimal applyMin(BigDecimal value, BigDecimal min) {
        if (value == null) return BigDecimal.ZERO;
        if (min != null && min.signum() > 0 && value.compareTo(min) < 0) return min;
        return value;
    }

    public static BigDecimal volume(ReceiptPieceDTO piece) {
        if (piece == null || piece.getLengthIn() == null || piece.getWidthIn() == null || piece.getHeightIn() == null) {
            return null;
        }
        BigDecimal pieces = piece.getPieces() != null ? BigDecimal.valueOf(piece.getPieces()) : BigDecimal.ONE;
        return piece.getLengthIn().multiply(piece.getWidthIn()).multiply(piece.getHeightIn()).multiply(pieces);
    }

    /** Aplica el cálculo completo a una pieza (dim, cobrable kg/lbs) mutando el DTO. */
    public static void apply(ReceiptPieceDTO piece, CalcParams params) {
        BigDecimal scaleLbs = piece.getScaleWeightLbs() != null ? piece.getScaleWeightLbs() : BigDecimal.ZERO;
        BigDecimal scaleKg = piece.getScaleWeightKg() != null ? piece.getScaleWeightKg()
                : scaleLbs.divide(LBS_PER_KG, 3, RoundingMode.HALF_UP);

        BigDecimal dimWeightLbs = null;
        BigDecimal dimWeightKg = null;
        BigDecimal volume = volume(piece);
        if (volume != null) {
            dimWeightLbs = volume.divide(BigDecimal.valueOf(params.dimFactorDom()), 2, RoundingMode.HALF_UP);
            dimWeightKg = volume.divide(BigDecimal.valueOf(params.dimFactorIntl()), 3, RoundingMode.HALF_UP);
        }
        piece.setDimWeightLbs(dimWeightLbs);
        piece.setDimWeightKg(dimWeightKg);

        BigDecimal dimLbs = dimWeightLbs != null ? dimWeightLbs : BigDecimal.ZERO;
        BigDecimal dimKg = dimWeightKg != null ? dimWeightKg : BigDecimal.ZERO;

        BigDecimal chargeableLbs = params.method().base(scaleLbs, dimLbs);
        BigDecimal chargeableKg = params.method().base(scaleKg, dimKg);

        // round-up → mínimo, por pieza (linear lbs ↔ kg para no desincronizar)
        BigDecimal roundedLbs = roundUp(applyMin(chargeableLbs, params.minChargeableLbs()), params.roundUpLbs());
        BigDecimal roundedKg = chargeableLbs.equals(scaleLbs) && chargeableKg.equals(scaleKg)
                ? roundUp(applyMin(chargeableKg, params.minChargeableKg()), params.roundUpKg())
                : roundedLbs.multiply(KGS_PER_LB).setScale(3, RoundingMode.HALF_UP);

        piece.setChargeableLbs(roundedLbs);
        piece.setChargeableKg(roundedKg);
    }

    /** Calcula los totales del recibo a partir de las piezas ya procesadas. */
    public static CalcTotals totals(List<ReceiptPieceDTO> pieces, CalcParams params) {
        if (pieces == null || pieces.isEmpty()) {
            return new CalcTotals(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        int pieceCount = pieces.stream().mapToInt(p -> p.getPieces() != null ? p.getPieces() : 1).sum();

        BigDecimal totalScaleLbs = pieces.stream()
                .map(p -> p.getScaleWeightLbs() != null ? p.getScaleWeightLbs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalScaleKg = pieces.stream()
                .map(p -> p.getScaleWeightKg() != null ? p.getScaleWeightKg()
                        : (p.getScaleWeightLbs() != null
                            ? p.getScaleWeightLbs().divide(LBS_PER_KG, 3, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal chargeableLbs = pieces.stream()
                .map(p -> p.getChargeableLbs() != null ? p.getChargeableLbs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal chargeableKg = pieces.stream()
                .map(p -> p.getChargeableKg() != null ? p.getChargeableKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // round-up → mínimo aplicado sobre el total cobrable
        return new CalcTotals(
                pieceCount,
                totalScaleLbs.setScale(2, RoundingMode.HALF_UP),
                totalScaleKg.setScale(3, RoundingMode.HALF_UP),
                roundUp(applyMin(chargeableLbs, params.minChargeableLbs()), params.roundUpLbs())
                        .setScale(2, RoundingMode.HALF_UP),
                roundUp(applyMin(chargeableKg, params.minChargeableKg()), params.roundUpKg())
                        .setScale(3, RoundingMode.HALF_UP));
    }

    public record CalcTotals(int pieceCount,
                             BigDecimal actualWeightLbs,
                             BigDecimal actualWeightKg,
                             BigDecimal chargeableWeightLbs,
                             BigDecimal chargeableWeightKg) {
    }
}