package com.aircargo.loadplanningservice.wb;

import java.util.Map;

/**
 * Root of {@code wb/aircraft-wb.json}.
 *
 * <pre>
 * {
 *   "_meta": { "placeholder": true, "units": "lbs / inches", "note": "..." },
 *   "aircraft": { "B767": { ... }, ... }
 * }
 * </pre>
 *
 * Lookup keys are normalized to upper case.
 */
public class WbConfigFile {

    public Map<String, Object> _meta;
    public Map<String, AircraftWbConfig> aircraft;
}
