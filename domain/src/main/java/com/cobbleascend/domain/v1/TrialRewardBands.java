package com.cobbleascend.domain.v1;

import java.util.EnumMap;
import java.util.Map;

/**
 * What a won Trial pays (decided 2026-10-05, numbers provisional): a Trial is a floor-limited CobbleTowers run, and its rank
 * (1-3) is the difficulty of the floors it covers. Flat per player, victory only, no chances and no Scouters, Cores, Unique
 * Fragments or Catalysts in this first version: Cores stay a Tower boss reward until Core pity and a daily budget exist.
 *
 * <p>The amounts are the three-rank table of {@code ECONOMY.md}. There is no entry gate and no daily budget yet, so a farmed
 * rank-3 Trial pays 16 dust and 2 facets per clear; if a live test shows that out-earning the Towers, shrink these before adding
 * the budget.
 */
public final class TrialRewardBands {
    public static final int MIN_RANK = 1;
    public static final int MAX_RANK = 3;

    private static final int[] DUST = {6, 10, 16};
    private static final int[] FACETS = {0, 1, 2};

    private TrialRewardBands() {}

    /** The rank a trial of {@code floorLimit} floors has, matching the Towers scouting tiers: 1-4 rank 1, 5-9 rank 2, 10+ rank 3. */
    public static int rankForFloorLimit(int floorLimit) {
        if (floorLimit < 1) throw new IllegalArgumentException("A trial has at least one floor");
        return floorLimit < 5 ? 1 : floorLimit < 10 ? 2 : 3;
    }

    /** The materials a victory of this rank pays to each player. */
    public static Map<MaterialId, Long> payout(int rank) {
        if (rank < MIN_RANK || rank > MAX_RANK) throw new IllegalArgumentException("Trial rank must be 1-3");
        var result = new EnumMap<MaterialId, Long>(MaterialId.class);
        result.put(MaterialId.RESONANCE_DUST, (long) DUST[rank - 1]);
        if (FACETS[rank - 1] > 0) result.put(MaterialId.FACET, (long) FACETS[rank - 1]);
        return java.util.Collections.unmodifiableMap(result);
    }
}
