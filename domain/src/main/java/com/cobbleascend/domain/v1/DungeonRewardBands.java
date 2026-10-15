package com.cobbleascend.domain.v1;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * What a finished dungeon run pays (ascensionDungeons; numbers provisional pending balance approval). Flat per participant, so a party
 * is never rewarded for who dealt the damage; only the chance rolls differ per player. Dust (arena 10, boss 12 per tier, lowered 2026-10-15 so the Tower out-earns it) pays for the run's depth (its tier) and for every
 * arena raid won; Facets come from arena wins and a defeated boss; one Unique Fragment only from a boss and only sometimes (35 percent, a lower rate than the Tower). It pays no Ascension Cores, the same
 * rule as Trials: the Tower stays the only core source.
 *
 * <p>Rolls are seeded by (run, player, tier), so a repeated callback computes identical amounts and {@code EncounterRewards} never sees a
 * conflict.
 */
public record DungeonRewardBands(int tierDust, int arenaDust, int arenaDustTierPercent, int bossDustPerTier, int arenaFacetPercent,
                                 int bossFragmentPercent) {
    public static final int MAX_TIER = 10;
    public static final int MAX_ARENAS = 16;

    public static final DungeonRewardBands DEFAULTS = new DungeonRewardBands(10, 10, 10, 12, 35, 35);

    public DungeonRewardBands {
        if (tierDust < 0 || arenaDust < 0 || arenaDustTierPercent < 0 || bossDustPerTier < 0)
            throw new IllegalArgumentException("Dust amounts must be nonnegative");
        if (arenaFacetPercent < 0 || arenaFacetPercent > 100 || bossFragmentPercent < 0 || bossFragmentPercent > 100)
            throw new IllegalArgumentException("Chances must be 0-100 percent");
    }

    /**
     * @param runId       the run's own ID (never reused)
     * @param player      the participant
     * @param tier        the run's zone tier, 1..{@link #MAX_TIER}
     * @param arenaWins   arena raids the party won, 0..{@link #MAX_ARENAS}
     * @param bossDefeated whether the run's final boss was beaten
     * @return nonzero amounts, possibly only dust
     */
    public Map<MaterialId, Long> payout(String runId, UUID player, int tier, int arenaWins, boolean bossDefeated) {
        if (tier < 1 || tier > MAX_TIER) throw new IllegalArgumentException("Tier must be 1-" + MAX_TIER);
        if (arenaWins < 0 || arenaWins > MAX_ARENAS) throw new IllegalArgumentException("Arena wins must be 0-" + MAX_ARENAS);
        double dust = (double) tierDust * tier
                + arenaWins * arenaDust * (1.0 + arenaDustTierPercent / 100.0 * (tier - 1))
                + (bossDefeated ? (double) bossDustPerTier * tier : 0);
        var random = new Random(RewardSeed.of("dungeon-reward", runId, player, tier));
        long facets = bossDefeated ? 1 : 0;
        for (int win = 0; win < arenaWins; win++) if (random.nextInt(100) < arenaFacetPercent) facets++;
        var result = new EnumMap<MaterialId, Long>(MaterialId.class);
        long roundedDust = Math.round(dust);
        if (roundedDust > 0) result.put(MaterialId.RESONANCE_DUST, roundedDust);
        if (facets > 0) result.put(MaterialId.FACET, facets);
        if (bossDefeated && random.nextInt(100) < bossFragmentPercent) result.put(MaterialId.UNIQUE_FRAGMENT, 1L);
        return Collections.unmodifiableMap(result);
    }
}
