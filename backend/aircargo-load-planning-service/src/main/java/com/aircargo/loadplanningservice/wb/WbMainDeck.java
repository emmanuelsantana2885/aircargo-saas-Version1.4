package com.aircargo.loadplanningservice.wb;

import java.util.List;

/**
 * Main-deck pallet positions. The balance arm of each listed position is interpolated
 * linearly between {@link #armStartIn} (first position) and {@link #armEndIn} (last
 * position). This avoids enumerating every station while keeping the arm data accurate
 * once real figures are entered.
 */
public class WbMainDeck {

    public List<String> positions;
    public Double armStartIn;
    public Double armEndIn;
}
