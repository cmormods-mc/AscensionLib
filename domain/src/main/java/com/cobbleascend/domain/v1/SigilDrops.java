package com.cobbleascend.domain.v1;

import java.util.UUID;

/**
 * Chance (percent, provisional pending balance approval) that a reward drops one Ascension Sigil, the rare loot that gives a Pokemon
 * with no profile one. Towers pay it on a boss victory. Rolled with a seed from (source, encounter, player), so a retried settlement
 * recomputes the same winners.
 */
public record SigilDrops(int towerBasePercent, int towerMaxPercent) {
    /** Tower: 2 percent plus 1 per 10 boss floors, at most 8. */
    public static final SigilDrops DEFAULTS = new SigilDrops(2, 8);

    public SigilDrops {
        if (towerBasePercent < 0 || towerMaxPercent < towerBasePercent || towerMaxPercent > 100)
            throw new IllegalArgumentException("Drop chances must be 0-100 and a maximum must not be below its base");
    }

    public int towerPercent(int bossFloor) {
        return (int) Math.min(towerMaxPercent, towerBasePercent + Math.max(0, bossFloor) / 10L);
    }

    public boolean rollTower(String encounterId, UUID player, int bossFloor) {
        return roll("tower", encounterId, player, towerPercent(bossFloor));
    }

    private static boolean roll(String source, String id, UUID player, int percent) {
        return new java.util.Random(RewardSeed.of("sigil-drop", source, id, player)).nextInt(100) < percent;
    }
}
