package com.aircargo.loadplanningservice.wb;

import java.util.Map;

/**
 * Weight &amp; balance reference data for a single aircraft type. All weights are pounds
 * and all arms are inches from the reference datum, matching the field weights used
 * elsewhere in load planning.
 *
 * <p>Values are placeholder data until real operator figures are filed; the top-level
 * {@code _meta.placeholder} flag (see {@link WbConfigFile}) drives the UI warning.</p>
 */
public class AircraftWbConfig {

    public String label;
    public boolean configured;

    public Double downWeightLbs;
    public Double downWeightArmIn;

    public Double lemacIn;
    public Double macIn;

    public Double cgForwardLimitPctMac;
    public Double cgAftLimitPctMac;

    public Double maxZeroFuelWeightLbs;
    public Double maxTakeoffWeightLbs;

    public WbMainDeck mainDeck;
    public Map<String, WbStation> stations;

    public Double fuelLbs;
    public Double fuelArmIn;
}
