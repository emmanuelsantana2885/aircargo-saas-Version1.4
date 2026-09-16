package com.aircargo.warehouseservice.calc;

import com.aircargo.warehouseservice.dto.WarehouseReceiptDTO;
import com.aircargo.warehouseservice.entity.ReceiptCalcConfig;
import com.aircargo.warehouseservice.entity.WarehouseReceipt;
import com.aircargo.warehouseservice.repository.ReceiptCalcConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalcParamsResolverTest {

    @Mock
    private ReceiptCalcConfigRepository repository;

    @InjectMocks
    private CalcParamsResolver resolver;

    private ReceiptCalcConfig config(UUID airlineId, int dom, int intl, ChargeableMethod method, String roundUpKg, String minLbs) {
        ReceiptCalcConfig c = new ReceiptCalcConfig();
        c.setAirlineId(airlineId);
        c.setDimFactorDom(dom);
        c.setDimFactorIntl(intl);
        c.setChargeableMethod(method);
        c.setRoundUpKg(new BigDecimal(roundUpKg));
        c.setMinChargeableLbs(new BigDecimal(minLbs));
        return c;
    }

    @Test
    void resolveProfile_usesAirlineConfig() {
        UUID airline = UUID.randomUUID();
        when(repository.findTopByAirlineId(airline))
                .thenReturn(Optional.of(config(airline, 150, 300, ChargeableMethod.SUM, "1", "5")));

        CalcParams p = resolver.resolveProfile(airline);

        assertEquals(150, p.dimFactorDom());
        assertEquals(300, p.dimFactorIntl());
        assertEquals(ChargeableMethod.SUM, p.method());
        assertEquals(0, p.roundUpKg().compareTo(BigDecimal.ONE));
        assertEquals(0, p.minChargeableLbs().compareTo(new BigDecimal("5")));
    }

    @Test
    void resolveProfile_fallsBackToGlobalDefault() {
        UUID airline = UUID.randomUUID();
        when(repository.findTopByAirlineId(airline)).thenReturn(Optional.empty());
        when(repository.findTopByAirlineIdIsNull())
                .thenReturn(Optional.of(config(null, 160, 320, ChargeableMethod.DIM, "0", "0")));

        CalcParams p = resolver.resolveProfile(airline);

        assertEquals(160, p.dimFactorDom());
        assertEquals(320, p.dimFactorIntl());
        assertEquals(ChargeableMethod.DIM, p.method());
    }

    @Test
    void resolveProfile_returnsDefaultsWhenNoConfigAtAll() {
        UUID airline = UUID.randomUUID();
        when(repository.findTopByAirlineId(airline)).thenReturn(Optional.empty());
        when(repository.findTopByAirlineIdIsNull()).thenReturn(Optional.empty());

        assertEquals(CalcParams.defaults(), resolver.resolveProfile(airline));
    }

    @Test
    void resolveFor_dtoOverridesProfile() {
        UUID airline = UUID.randomUUID();
        WarehouseReceiptDTO dto = new WarehouseReceiptDTO();
        dto.setAirlineId(airline);
        dto.setDimFactorDom(100);
        dto.setDimFactorIntl(250);
        dto.setChargeableMethod("SCALE");
        dto.setRoundUpLbs(new BigDecimal("7"));
        when(repository.findTopByAirlineId(airline))
                .thenReturn(Optional.of(config(airline, 194, 366, ChargeableMethod.MAX, "0", "0")));

        CalcParams p = resolver.resolveFor(dto, null);

        assertEquals(100, p.dimFactorDom());
        assertEquals(250, p.dimFactorIntl());
        assertEquals(ChargeableMethod.SCALE, p.method());
        assertEquals(0, p.roundUpLbs().compareTo(new BigDecimal("7")));
    }

    @Test
    void resolveFor_existingUsedWhenDtoMissing() {
        UUID airline = UUID.randomUUID();
        WarehouseReceipt existing = new WarehouseReceipt();
        existing.setAirlineId(airline);
        existing.setDimFactorDom(77);
        existing.setDimFactorIntl(388);
        existing.setChargeableMethod("SCALE");
        when(repository.findTopByAirlineId(airline)).thenReturn(Optional.empty());
        when(repository.findTopByAirlineIdIsNull())
                .thenReturn(Optional.of(config(null, 194, 366, ChargeableMethod.MAX, "0", "0")));

        CalcParams p = resolver.resolveFor(null, existing);

        assertEquals(77, p.dimFactorDom());
        assertEquals(388, p.dimFactorIntl());
        assertEquals(ChargeableMethod.SCALE, p.method());
    }

    @Test
    void resolveFor_invalidDtoValuesFallBackToSafe() {
        UUID airline = UUID.randomUUID();
        WarehouseReceiptDTO dto = new WarehouseReceiptDTO();
        dto.setAirlineId(airline);
        dto.setChargeableMethod("BOGUS");
        dto.setDimFactorDom(-5);
        dto.setDimFactorIntl(0);
        when(repository.findTopByAirlineId(airline))
                .thenReturn(Optional.of(config(airline, 194, 366, ChargeableMethod.MAX, "0", "0")));

        CalcParams p = resolver.resolveFor(dto, null);

        assertEquals(ChargeableMethod.MAX, p.method());
        assertEquals(194, p.dimFactorDom());
        assertEquals(366, p.dimFactorIntl());
    }
}