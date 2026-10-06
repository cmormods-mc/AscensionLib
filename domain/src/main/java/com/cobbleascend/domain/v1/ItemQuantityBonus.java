package com.cobbleascend.domain.v1;

import java.util.Random;

/**
 * The 777 Unique's loot bonus: +20% on the quantity of an item reward. A fraction of an item is settled by a seeded roll, so
 * 5 items become 6 and 1 item becomes 2 one time in five, and the same reward always comes out the same (a retry or a
 * restart cannot re-roll it). Applies to item rewards only: materials in the wallet are tuned separately and never scale.
 */
public final class ItemQuantityBonus {
    public static final int PERCENT = 20;

    private ItemQuantityBonus() {}

    /**
     * @param count the base quantity (at least 1)
     * @param seed  identifies this reward (player, item, source), so the rounding is stable
     */
    public static int scale(int count, String seed) {
        if (count < 1) return count;
        long scaledTimes100 = (long) count * (100 + PERCENT);
        int whole = (int) (scaledTimes100 / 100);
        int remainder = (int) (scaledTimes100 % 100);
        if (remainder > 0 && new Random(RewardSeed.of("item-quantity", seed, count)).nextInt(100) < remainder) whole++;
        return whole;
    }
}
