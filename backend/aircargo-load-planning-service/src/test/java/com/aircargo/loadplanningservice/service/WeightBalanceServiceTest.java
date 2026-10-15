package com.aircargo.loadplanningservice.service;

import com.aircargo.loadplanningservice.dto.LoadPlanningUldDTO;
import com.aircargo.loadplanningservice.dto.WeightBalanceDTO;
import com.aircargo.loadplanningservice.wb.AircraftWbConfig;
import com.aircargo.loadplanningservice.wb.WbConfigFile;
import com.aircargo.loadplanningservice.wb.WbMainDeck;
import com.aircargo.loadplanningservice.wb.WbStation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightBalanceServiceTest {

    /** Aircraft with neat numbers for exact assertions. */
    private AircraftWbConfig testAircraft() {
        AircraftWbConfig ac = new AircraftWbConfig();
        ac.label = "TEST-300F";
        ac.configured = true;
        ac.downWeightLbs = 1000.0;
        ac.downWeightArmIn = 100.0;
        ac.lemacIn = 0.0;
        ac.macIn = 100.0;
        ac.cgForwardLimitPctMac = 0.0;
        ac.cgAftLimitPctMac = 200.0;
        ac.maxZeroFuelWeightLbs = 3000.0;
        ac.maxTakeoffWeightLbs = 4000.0;
        ac.fuelLbs = 0.0;

        WbMainDeck deck = new WbMainDeck();
        deck.positions = List.of("P1", "P2", "P3");
        deck.armStartIn = 100.0;
        deck.armEndIn = 300.0;
        ac.mainDeck = deck;

        Map<String, WbStation> stations = new HashMap<>();
        WbStation belly = new WbStation();
        belly.armIn = 50.0;
        belly.maxWeightLbs = 3200.0;
        stations.put("10L", belly);
        ac.stations = stations;
        return ac;
    }

    private WeightBalanceService service() {
        WbConfigFile file = new WbConfigFile();
        file._meta = new HashMap<>();
        file._meta.put("placeholder", Boolean.TRUE);
        Map<String, AircraftWbConfig> aircraft = new HashMap<>();
        aircraft.put("TEST", testAircraft());
        file.aircraft = aircraft;
        return new WeightBalanceService(file);
    }

    private LoadPlanningUldDTO uld(String position, double grossLbs) {
        LoadPlanningUldDTO dto = new LoadPlanningUldDTO();
        dto.setPosition(position);
        dto.setGrossWeightLbs(BigDecimal.valueOf(grossLbs));
        return dto;
    }

    @Test
    void loadDefault_loadsResourceAndHasB767() {
        WbConfigFile file = WeightBalanceService.loadDefault();
        assertNotNull(file);
        assertNotNull(file.aircraft);
        assertTrue(file.aircraft.containsKey("B767"));
        assertNotNull(file.aircraft.get("B767").mainDeck);
    }

    @Test
    void armFor_mainDeckInterpolatesLinearly() {
        WeightBalanceService svc = service();
        AircraftWbConfig ac = testAircraft();
        assertEquals(100.0, svc.armFor("P1", ac), 0.001);
        assertEquals(200.0, svc.armFor("P2", ac), 0.001);
        assertEquals(300.0, svc.armFor("P3", ac), 0.001);
        assertEquals(200.0, svc.armFor(" p2 ", ac), 0.001);
    }

    @Test
    void armFor_prefersExplicitStationOverDeck() {
        WeightBalanceService svc = service();
        assertEquals(50.0, svc.armFor("10L", testAircraft()), 0.001);
    }

    @Test
    void armFor_unknownPositionReturnsNull() {
        WeightBalanceService svc = service();
        assertNull(svc.armFor("ZZ", testAircraft()));
        assertNull(svc.armFor(null, testAircraft()));
        assertNull(svc.armFor("  ", testAircraft()));
    }

    @Test
    void compute_unknownAircraftReportsNotConfigured() {
        WeightBalanceDTO dto = service().compute("A380", List.of(uld("P1", 1000)));
        assertFalse(dto.getConfigured());
        assertEquals(0, dto.getMappedUldCount());
        assertFalse(dto.getWarnings().isEmpty());
    }

    @Test
    void compute_producesCgAndAnnotatesStationArm() {
        LoadPlanningUldDTO u = uld("P2", 1000); // arm 200
        WeightBalanceDTO dto = service().compute("TEST", List.of(u));

        assertTrue(dto.getConfigured());
        assertEquals("TEST-300F", dto.getAircraftLabel());
        assertEquals(1000.0, dto.getDownWeightLbs(), 0.001);
        assertEquals(1000.0, dto.getPayloadLbs(), 0.001);
        assertEquals(2000.0, dto.getZeroFuelWeightLbs(), 0.001);
        assertEquals(1, dto.getMappedUldCount());
        // CG = (1000*100 + 1000*200) / 2000 = 150 ; %MAC = (150-0)/100*100 = 150
        assertEquals(150.0, dto.getCgArmIn(), 0.001);
        assertEquals(150.0, dto.getCgPercentMac(), 0.001);
        assertTrue(dto.getWithinEnvelope());
        assertEquals(200.0, u.getStationArmIn(), 0.001);
    }

    @Test
    void compute_flagsOutOfEnvelopeAndWarns() {
        // heavy aft load pushes CG past the aft limit (200)
        LoadPlanningUldDTO aft = uld("P3", 5000); // arm 300
        WeightBalanceDTO dto = service().compute("TEST", List.of(aft));

        assertFalse(dto.getWithinEnvelope());
        assertTrue(dto.getCgPercentMac() > 200.0);
        assertTrue(dto.getWarnings().stream().anyMatch(w -> w.contains("envolvente")));
    }

    @Test
    void compute_reportsUnmappedPositions() {
        LoadPlanningUldDTO stray = uld("ZZ", 500);
        WeightBalanceDTO dto = service().compute("TEST", List.of(stray));
        assertTrue(dto.getUnmappedPositions().contains("ZZ"));
        assertEquals(0, dto.getMappedUldCount());
    }

    @Test
    void compute_fallsBackToTareWeight() {
        LoadPlanningUldDTO u = new LoadPlanningUldDTO();
        u.setPosition("P1");
        u.setTareLbs(BigDecimal.valueOf(250));
        WeightBalanceDTO dto = service().compute("TEST", List.of(u));
        assertEquals(250.0, dto.getPayloadLbs(), 0.001);
    }

    @Test
    void compute_skipsOpenUldsFromUnmappedWarnings() {
        LoadPlanningUldDTO open = uld(null, 0);
        open.setStatus("OPEN");
        WeightBalanceDTO dto = service().compute("TEST", List.of(open));
        assertTrue(dto.getUnmappedPositions().isEmpty());
        assertEquals(0, dto.getMappedUldCount());
    }

    @Test
    void compute_setsPlaceholderFromMeta() {
        WeightBalanceDTO dto = service().compute("TEST", new ArrayList<>());
        assertTrue(dto.getPlaceholder());
        assertTrue(dto.getConfigured());
        // no ULDs -> CG sits at DOW arm -> 100 ; %MAC = 100
        assertEquals(100.0, dto.getCgPercentMac(), 0.001);
    }
}
