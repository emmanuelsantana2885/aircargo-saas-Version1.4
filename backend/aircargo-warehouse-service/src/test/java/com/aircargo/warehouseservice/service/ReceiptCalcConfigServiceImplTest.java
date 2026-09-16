package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.calc.CalcParams;
import com.aircargo.warehouseservice.calc.CalcParamsResolver;
import com.aircargo.warehouseservice.calc.ChargeableMethod;
import com.aircargo.warehouseservice.dto.ReceiptCalcConfigDTO;
import com.aircargo.warehouseservice.entity.ReceiptCalcConfig;
import com.aircargo.warehouseservice.repository.ReceiptCalcConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptCalcConfigServiceImplTest {

    @Mock
    private ReceiptCalcConfigRepository repository;

    @Mock
    private CalcParamsResolver resolver;

    @InjectMocks
    private ReceiptCalcConfigServiceImpl service;

    private ReceiptCalcConfig entity(UUID airlineId) {
        ReceiptCalcConfig c = new ReceiptCalcConfig();
        c.setId(UUID.randomUUID());
        c.setAirlineId(airlineId);
        c.setChargeableMethod(ChargeableMethod.MAX);
        c.setDimFactorDom(194);
        c.setDimFactorIntl(366);
        return c;
    }

    @Test
    void getAll_mapsEntitiesInRepositoryOrder() {
        ReceiptCalcConfig a = entity(UUID.randomUUID());
        ReceiptCalcConfig b = entity(null);
        when(repository.findAllByOrderByAirlineIdAscIdAsc()).thenReturn(List.of(a, b));

        List<ReceiptCalcConfigDTO> all = service.getAll();

        assertEquals(2, all.size());
        assertEquals(a.getId(), all.get(0).getId());
        assertNotNull(all.get(1).getChargeableMethod());
    }

    @Test
    void saveDefault_createsNewWhenMissing() {
        when(repository.findTopByAirlineIdIsNull()).thenReturn(Optional.empty());
        when(repository.save(any(ReceiptCalcConfig.class))).thenAnswer(inv -> {
            ReceiptCalcConfig e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        ReceiptCalcConfigDTO dto = new ReceiptCalcConfigDTO();
        dto.setDimFactorDom(140);
        dto.setDimFactorIntl(280);
        dto.setChargeableMethod("SUM");
        dto.setRoundUpKg(new BigDecimal("2"));

        ReceiptCalcConfigDTO saved = service.saveDefault(dto);

        ArgumentCaptor<ReceiptCalcConfig> captor = ArgumentCaptor.forClass(ReceiptCalcConfig.class);
        verify(repository).save(captor.capture());
        ReceiptCalcConfig entity = captor.getValue();
        assertNull(entity.getAirlineId());
        assertEquals(140, entity.getDimFactorDom());
        assertEquals(280, entity.getDimFactorIntl());
        assertEquals(ChargeableMethod.SUM, entity.getChargeableMethod());
        assertEquals(0, entity.getRoundUpKg().compareTo(new BigDecimal("2")));
        assertNotNull(saved.getId());
    }

    @Test
    void saveDefault_updatesExisting() {
        ReceiptCalcConfig existing = entity(null);
        when(repository.findTopByAirlineIdIsNull()).thenReturn(Optional.of(existing));
        when(repository.save(any(ReceiptCalcConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptCalcConfigDTO dto = new ReceiptCalcConfigDTO();
        dto.setDimFactorDom(150);

        service.saveDefault(dto);

        ArgumentCaptor<ReceiptCalcConfig> captor = ArgumentCaptor.forClass(ReceiptCalcConfig.class);
        verify(repository).save(captor.capture());
        assertEquals(150, captor.getValue().getDimFactorDom());
        assertEquals(existing.getId(), captor.getValue().getId());
    }

    @Test
    void saveForAirline_setsAirlineAndAppliesDto() {
        UUID airline = UUID.randomUUID();
        ReceiptCalcConfig existing = entity(null);
        when(repository.findTopByAirlineId(airline)).thenReturn(Optional.of(existing));
        when(repository.save(any(ReceiptCalcConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        ReceiptCalcConfigDTO dto = new ReceiptCalcConfigDTO();
        dto.setDimFactorIntl(399);
        dto.setChargeableMethod("DIM");

        ReceiptCalcConfigDTO saved = service.saveForAirline(airline, dto);

        assertEquals(airline, saved.getAirlineId());
        ArgumentCaptor<ReceiptCalcConfig> captor = ArgumentCaptor.forClass(ReceiptCalcConfig.class);
        verify(repository).save(captor.capture());
        assertEquals(airline, captor.getValue().getAirlineId());
        assertEquals(399, captor.getValue().getDimFactorIntl());
        assertEquals(ChargeableMethod.DIM, captor.getValue().getChargeableMethod());
    }

    @Test
    void saveForAirline_nullAirlineThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.saveForAirline(null, new ReceiptCalcConfigDTO()));
    }

    @Test
    void deleteForAirline_delegatesToRepository() {
        UUID airline = UUID.randomUUID();

        service.deleteForAirline(airline);

        verify(repository).deleteByAirlineId(airline);
    }

    @Test
    void deleteForAirline_nullThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteForAirline(null));
    }

    @Test
    void getDefault_returnsDefaultsWhenAbsent() {
        when(repository.findTopByAirlineIdIsNull()).thenReturn(Optional.empty());

        ReceiptCalcConfigDTO dto = service.getDefault();

        assertEquals(194, dto.getDimFactorDom());
        assertEquals(366, dto.getDimFactorIntl());
        assertEquals("MAX", dto.getChargeableMethod());
    }

    @Test
    void getByAirline_throwsWhenNoConfig() {
        UUID airline = UUID.randomUUID();
        when(repository.findTopByAirlineId(airline)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getByAirline(airline));
    }

    @Test
    void getByAirline_nullThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.getByAirline(null));
    }

    @Test
    void resolve_delegatesToResolver() {
        UUID airline = UUID.randomUUID();
        CalcParams expected = CalcParams.defaults();
        when(resolver.resolveProfile(airline)).thenReturn(expected);

        assertSame(expected, service.resolve(airline));
    }
}