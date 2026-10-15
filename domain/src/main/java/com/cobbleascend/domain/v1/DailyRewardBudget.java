package com.cobbleascend.domain.v1;

import java.util.EnumMap;
import java.util.Map;

/**
 * The daily full-reward budget (docs/TOWER-LOOT-DESIGN.md 5.5b, owner decision 4): a player's first {@code fullPerDay} paid chests of a UTC day pay in
 * full, and after that the Resonance Dust of a chest drops to {@code reducedDustPercent} percent. Only dust is cut, so a Core, a Catalyst or a Mark from a
 * hard run is never taken away; the point is that grinding the easiest repeatable chest all day stops being the best use of the day. Both numbers are
 * data, not code.
 */
public record DailyRewardBudget(int fullPerDay, int reducedDustPercent) {
    public static final DailyRewardBudget DEFAULTS = new DailyRewardBudget(20, 25);

    public DailyRewardBudget {
        if (fullPerDay < 0) throw new IllegalArgumentException("The daily budget cannot be negative");
        if (reducedDustPercent < 0 || reducedDustPercent > 100) throw new IllegalArgumentException("A reduced share is 0-100 percent");
    }

    /** Whether a player who has already been paid {@code used} budgeted chests today gets this one reduced. */
    public boolean reduces(int used) { return used >= fullPerDay; }

    /** The chest with its dust cut to the reduced share (at least 1 when it had any); everything else unchanged. */
    public Map<MaterialId, Long> reduced(Map<MaterialId, Long> materials) {
        var out = new EnumMap<MaterialId, Long>(MaterialId.class);
        out.putAll(materials);
        Long dust = materials.get(MaterialId.RESONANCE_DUST);
        if (dust != null && dust > 0) {
            long cut = Math.round(dust * reducedDustPercent / 100.0);
            if (cut < 1 && reducedDustPercent > 0) cut = 1;
            if (cut < 1) out.remove(MaterialId.RESONANCE_DUST); else out.put(MaterialId.RESONANCE_DUST, cut);
        }
        return java.util.Collections.unmodifiableMap(out);
    }
}
