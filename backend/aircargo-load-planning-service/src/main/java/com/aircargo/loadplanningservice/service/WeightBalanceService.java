package com.aircargo.loadplanningservice.service;

import com.aircargo.loadplanningservice.dto.LoadPlanningUldDTO;
import com.aircargo.loadplanningservice.dto.WeightBalanceDTO;
import com.aircargo.loadplanningservice.wb.AircraftWbConfig;
import com.aircargo.loadplanningservice.wb.WbConfigFile;
import com.aircargo.loadplanningservice.wb.WbMainDeck;
import com.aircargo.loadplanningservice.wb.WbStation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes weight &amp; balance (CG in %MAC) for a flight load plan.
 *
 * <p>Reference data lives in {@code wb/aircraft-wb.json} (placeholder until real
 * operator/AFM figures are filed). The service never throws: if data is missing or
 * the aircraft is not configured it returns a DTO with {@code configured=false} so
 * the UI can simply hide the panel.</p>
 *
 * <p>Reporting weights are pounds, arms are inches from the reference datum. Fuel is
 * not modelled (placeholder {@code fuelLbs=0}); DOW/payload/ZFW/TOW are exposed so the
 * formula can be completed once real figures arrive.</p>
 */
@Service
public class WeightBalanceService {

    private static final Logger log = LoggerFactory.getLogger(WeightBalanceService.class);
    private static final String RESOURCE = "/wb/aircraft-wb.json";

    private final WbConfigFile config;

    public WeightBalanceService() {
        this(loadDefault());
    }

    WeightBalanceService(WbConfigFile config) {
        this.config = config;
    }

    static WbConfigFile loadDefault() {
        try (InputStream is = WeightBalanceService.class.getResourceAsStream(RESOURCE)) {
            if (is == null) {
                log.warn("W&B config {} not found on classpath; CG panel will be hidden", RESOURCE);
                return new WbConfigFile();
            }
            return new ObjectMapper().readValue(is, WbConfigFile.class);
        } catch (Exception e) {
            log.warn("Failed to load W&B config {}: {}", RESOURCE, e.getMessage());
            return new WbConfigFile();
        }
    }

    /**
     * Computes CG for the given aircraft type and ULD list. As a side effect each
     * mapped ULD is annotated with its resolved {@code stationArmIn} so the board can
     * display the arm per position.
     */
    public WeightBalanceDTO compute(String aircraftType, List<LoadPlanningUldDTO> ulds) {
        WeightBalanceDTO dto = new WeightBalanceDTO();
        dto.setWarnings(new ArrayList<>());
        dto.setUnmappedPositions(new ArrayList<>());
        dto.setPlaceholder(isPlaceholder());

        AircraftWbConfig ac = resolve(aircraftType);
        if (ac == null || !ac.configured) {
            dto.setConfigured(false);
            dto.setAircraftLabel(ac != null ? ac.label : null);
            dto.setMappedUldCount(0);
            dto.getWarnings().add("Sin datos de peso y balance para el tipo de aeronave "
                    + (aircraftType == null ? "(desconocido)" : aircraftType));
            return dto;
        }

        dto.setConfigured(true);
        dto.setAircraftLabel(ac.label);
        dto.setCgForwardLimitPctMac(ac.cgForwardLimitPctMac);
        dto.setCgAftLimitPctMac(ac.cgAftLimitPctMac);

        double dow = nz(ac.downWeightLbs);
        double payload = 0.0;
        double moment = dow * nz(ac.downWeightArmIn);
        int mapped = 0;

        if (ulds != null) {
            for (LoadPlanningUldDTO uld : ulds) {
                Double arm = armFor(uld.getPosition(), ac);
                double weight = weightOf(uld);
                if (arm == null) {
                    String pos = uld.getPosition();
                    if (pos != null && !pos.isBlank() && !"OPEN".equalsIgnoreCase(uld.getStatus())) {
                        String key = pos.trim().toUpperCase(Locale.ROOT);
                        if (!dto.getUnmappedPositions().contains(key)) {
                            dto.getUnmappedPositions().add(key);
                        }
                    }
                    continue;
                }
                uld.setStationArmIn(round2(arm));
                payload += weight;
                moment += weight * arm;
                mapped++;
            }
        }

        double fuel = nz(ac.fuelLbs);
        double zfw = dow + payload;
        double tow = zfw + fuel;
        double totalWeight = tow > 0 ? tow : zfw;

        if (totalWeight <= 0) {
            dto.setConfigured(false);
            dto.getWarnings().add("Peso total no calculable (DOW/carga a cero)");
            return dto;
        }

        double cgArm = moment / totalWeight;
        double pctMac = ac.macIn != null && ac.macIn != 0 && ac.lemacIn != null
                ? (cgArm - ac.lemacIn) / ac.macIn * 100.0
                : 0.0;

        dto.setDownWeightLbs(round2(dow));
        dto.setPayloadLbs(round2(payload));
        dto.setZeroFuelWeightLbs(round2(zfw));
        dto.setTakeoffWeightLbs(round2(tow));
        dto.setCgArmIn(round2(cgArm));
        dto.setCgPercentMac(round2(pctMac));
        dto.setMappedUldCount(mapped);

        boolean cgOk = ac.cgForwardLimitPctMac == null || ac.cgAftLimitPctMac == null
                || (pctMac >= ac.cgForwardLimitPctMac && pctMac <= ac.cgAftLimitPctMac);
        boolean zfwOk = ac.maxZeroFuelWeightLbs == null || zfw <= ac.maxZeroFuelWeightLbs;
        boolean towOk = ac.maxTakeoffWeightLbs == null || tow <= ac.maxTakeoffWeightLbs;
        dto.setWithinEnvelope(cgOk && zfwOk && towOk);

        if (!cgOk) {
            dto.getWarnings().add("CG fuera de envolvente");
        }
        if (!zfwOk) {
            dto.getWarnings().add("ZFW excede MZFW");
        }
        if (!towOk) {
            dto.getWarnings().add("TOW excede MTOW");
        }
        if (!dto.getUnmappedPositions().isEmpty()) {
            dto.getWarnings().add("Posiciones sin arm conocido: " + String.join(", ", dto.getUnmappedPositions()));
        }
        return dto;
    }

    private AircraftWbConfig resolve(String aircraftType) {
        if (config == null || config.aircraft == null || aircraftType == null || aircraftType.isBlank()) {
            return null;
        }
        return config.aircraft.get(aircraftType.trim().toUpperCase(Locale.ROOT));
    }

    private boolean isPlaceholder() {
        if (config == null || config._meta == null) return false;
        return Boolean.TRUE.equals(config._meta.get("placeholder"));
    }

    /** Resolves the balance arm for a position code: explicit station first, then main-deck interpolation. */
    Double armFor(String position, AircraftWbConfig ac) {
        if (position == null || position.isBlank() || ac == null) return null;
        String key = position.trim().toUpperCase(Locale.ROOT);

        Map<String, WbStation> stations = ac.stations;
        if (stations != null) {
            WbStation station = stations.get(key);
            if (station != null) return station.armIn;
        }

        WbMainDeck deck = ac.mainDeck;
        if (deck != null && deck.positions != null && !deck.positions.isEmpty()
                && deck.armStartIn != null && deck.armEndIn != null) {
            int idx = -1;
            for (int i = 0; i < deck.positions.size(); i++) {
                if (deck.positions.get(i) != null && deck.positions.get(i).equalsIgnoreCase(key)) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                int last = deck.positions.size() - 1;
                if (last <= 0) return deck.armStartIn;
                return deck.armStartIn + (idx / (double) last) * (deck.armEndIn - deck.armStartIn);
            }
        }
        return null;
    }

    private double weightOf(LoadPlanningUldDTO uld) {
        if (uld == null) return 0.0;
        Double gross = toDouble(uld.getGrossWeightLbs());
        if (gross != null) return gross;
        Double tare = toDouble(uld.getTareLbs());
        return tare != null ? tare : 0.0;
    }

    private static Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private static double nz(Double value) {
        return value != null ? value : 0.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
