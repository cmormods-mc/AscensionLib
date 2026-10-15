package com.cobbleascend.domain.v1;

import java.util.UUID;

/**
 * Chance (percent, provisional pending balance approval) that a reward drops one Ascension Sigil, the rare loot that gives a Pokemon
 * with no profile one. Towers pay it on a boss victory and dungeon runs per tier. Rolled with a seed from (source, encounter, player), so a retried settlement
 * recomputes the same winners.
 */
public record SigilDrops(int towerBasePercent, int towerMaxPercent, int dungeonBasePercent, int dungeonMaxPercent) {
    /** Tower: 2 percent plus 1 per 10 boss floors, at most 8. Dungeon: 2 percent at tier 1 plus 1 per tier, at most 10. */
    public static final SigilDrops DEFAULTS = new SigilDrops(2, 8, 2, 10);

    public SigilDrops {
        if (towerBasePercent < 0 || towerMaxPercent < towerBasePercent || towerMaxPercent > 100
                || dungeonBasePercent < 0 || dungeonMaxPercent < dungeonBasePercent || dungeonMaxPercent > 100)
            throw new IllegalArgumentException("Drop chances must be 0-100 and a maximum must not be below its base");
    }

    public int towerPercent(int bossFloor) {
        return (int) Math.min(towerMaxPercent, towerBasePercent + Math.max(0, bossFloor) / 10L);
    }

    /** Tier 1 is the shallowest. */
    public int dungeonPercent(int tier) {
        return (int) Math.min(dungeonMaxPercent, dungeonBasePercent + Math.max(0, tier - 1) * 1L);
    }

    public boolean rollDungeon(String runId, UUID player, int tier) {
        return roll("dungeon", runId, player, dungeonPercent(tier));
    }

    public boolean rollTower(String encounterId, UUID player, int bossFloor) {
        return roll("tower", encounterId, player, towerPercent(bossFloor));
    }

    private static boolean roll(String source, String id, UUID player, int percent) {
        return new java.util.Random(RewardSeed.of("sigil-drop", source, id, player)).nextInt(100) < percent;
    }
}
