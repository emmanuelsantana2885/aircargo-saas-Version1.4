package com.aircargo.loadplanningservice.wb;

/**
 * Explicitly-mapped loading station: a position code (e.g. "10L", "13") with its
 * balance arm from the reference datum (inches) and optional structural weight limit.
 *
 * <p>Fields are public on purpose: Jackson's default field visibility is PUBLIC_ONLY,
 * so this maps directly without getters/setters.</p>
 */
public class WbStation {

    public Double armIn;
    public Double maxWeightLbs;
}
