package com.cobbleascend.domain.v1;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * What a CobbleTowers boss victory pays (decided 2026-10-05, numbers provisional). Everyone in the run gets the same
 * band (flat per player); only the chance rolls differ per player. Dust pays on every floor, facets, cores and
 * Unique Fragments only on boss floors.
 *
 * <p>A payout covers one segment: the non-boss floors cleared since the previous boss plus the boss itself, so a
 * run that fails before a boss pays nothing. Rolls are seeded by (encounter, player, boss floor), so a repeated
 * callback computes identical amounts and {@code EncounterRewards} never sees a conflict.
 *
 * <p>Sized for about 14 runs to floor 10 a week (2 free entry items a day): about 35 dust, 1.5 facets and 0.15 cores
 * per run, against 370 dust, 17 facets and 2 cores for one Pokemon from common to legendary.
 */
public record TowerRewardBands(double floorDust, double floorGrowth, int firstBossDust, int milestoneBossFloor,
                               int milestoneBossDust, int firstBossFacetPercent, int bossFacetPercent,
                               int bossCorePermille, int bossFragmentPercent) {
    public static final TowerRewardBands DEFAULTS = new TowerRewardBands(1.6, 1.08, 5, 10, 12, 50, 100, 75, 5);

    public TowerRewardBands {
        if (floorDust < 0 || floorGrowth < 1 || firstBossDust < 0 || milestoneBossFloor < 1 || milestoneBossDust < 0)
            throw new IllegalArgumentException("Dust amounts must be nonnegative, growth at least 1, floor at least 1");
        if (!percent(firstBossFacetPercent) || !percent(bossFacetPercent) || !percent(bossFragmentPercent)
                || bossCorePermille < 0 || bossCorePermille > 1000)
            throw new IllegalArgumentException("Chances must be 0-100 percent (cores 0-1000 permille)");
    }

    private static boolean percent(int value) { return value >= 0 && value <= 100; }

    /**
     * @param encounterId the boss encounter's ID (never reused)
     * @param player      the participant
     * @param fromFloor   first floor of the segment (the floor after the previous boss, or 1)
     * @param bossFloor   the boss floor just cleared; floors from..boss-1 are ordinary
     * @return nonzero material amounts, possibly only dust
     */
    public Map<MaterialId, Long> payout(String encounterId, UUID player, int fromFloor, int bossFloor) {
        if (fromFloor < 1 || bossFloor < fromFloor) throw new IllegalArgumentException("Bad floor range");
        double dust = bossDust(bossFloor);
        for (int floor = fromFloor; floor < bossFloor; floor++) dust += floorDust * Math.pow(floorGrowth, floor - 1);
        var random = new Random(RewardSeed.of("tower-reward", encounterId, player, bossFloor));
        var result = new EnumMap<MaterialId, Long>(MaterialId.class);
        long roundedDust = Math.round(dust);
        if (roundedDust > 0) result.put(MaterialId.RESONANCE_DUST, roundedDust);
        boolean first = bossFloor < milestoneBossFloor;
        if (random.nextInt(100) < (first ? firstBossFacetPercent : bossFacetPercent)) result.put(MaterialId.FACET, 1L);
        if (random.nextInt(1000) < bossCorePermille) result.put(MaterialId.ASCENSION_CORE, 1L);
        if (random.nextInt(100) < bossFragmentPercent) result.put(MaterialId.UNIQUE_FRAGMENT, 1L);
        return java.util.Collections.unmodifiableMap(result);
    }

    /** Boss dust: a flat amount before the milestone floor, then growing with depth from the milestone amount. */
    double bossDust(int bossFloor) {
        return bossFloor < milestoneBossFloor
                ? firstBossDust
                : milestoneBossDust * Math.pow(floorGrowth, bossFloor - milestoneBossFloor);
    }
}
