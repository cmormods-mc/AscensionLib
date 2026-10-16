package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.Rarity;
import java.util.random.RandomGenerator;

/**
 * The rarity weights of a profile zone tier (docs/ALL-WILD-PROFILES-DESIGN.md). Tier 1 is the ordinary table, exactly. A higher tier tilts it:
 * each rarity's weight is multiplied by {@code exp(tilt x (tier - 1) x index)}, index 0 for Common to 5 for Mythic, so every step toward rarer is made
 * more likely at the same pace; the result is scaled to {@link #SCALE} tickets. An explicit list in the settings replaces the tilt for its tier.
 *
 * <p>Pure and deterministic, so a Scouter preview and the capture (both drawn from a stream fixed by the Pokemon's ID, the tier and the server secret)
 * always agree.
 */
public final class ZoneRarity {
    /** Tickets in a tilted table: fine enough that the rarest tier-1 odds (0.1 percent) keep their shape. */
    public static final int SCALE = 1_000_000;

    private ZoneRarity() {}

    /** The weights for a tier, Common to Mythic. {@code ordinary} is the base table's weights in the same order. */
    public static int[] weights(int tier, int[] ordinary, ZoneSettings settings) {
        if (tier < 1) throw new IllegalArgumentException("Zone tier must be at least 1");
        var explicit = settings.rarityWeights().get(tier);
        if (explicit != null) return explicit.stream().mapToInt(Integer::intValue).toArray();
        if (tier == 1 || settings.rarityTilt() == 0) return ordinary.clone();
        double[] raw = new double[ordinary.length];
        double total = 0;
        for (int i = 0; i < raw.length; i++) {
            raw[i] = ordinary[i] * Math.exp(settings.rarityTilt() * (tier - 1) * i);
            total += raw[i];
        }
        // Largest-remainder rounding to exactly SCALE tickets.
        int[] out = new int[raw.length];
        double[] remainder = new double[raw.length];
        int given = 0;
        for (int i = 0; i < raw.length; i++) {
            double exact = raw[i] / total * SCALE;
            out[i] = (int) Math.floor(exact);
            remainder[i] = exact - out[i];
            given += out[i];
        }
        while (given < SCALE) {
            int best = 0;
            for (int i = 1; i < raw.length; i++) if (remainder[i] > remainder[best]) best = i;
            out[best]++;
            remainder[best] = -1;
            given++;
        }
        return out;
    }

    /** One weighted draw. With the ordinary weights this is the same draw as {@code Rules.rollRarity}. */
    public static Rarity roll(RandomGenerator random, int[] weights) {
        long total = 0;
        for (int w : weights) total += w;
        if (total <= 0) throw new IllegalArgumentException("Weights must add up to more than 0");
        int ticket = random.nextInt((int) total);
        Rarity[] rarities = Rarity.values();
        for (int i = 0; i < weights.length; i++) {
            ticket -= weights[i];
            if (ticket < 0) return rarities[i];
        }
        throw new IllegalStateException("Unreachable weighted draw");
    }

    /** The odds as percentages, for tests, the guide and the owner's table. */
    public static double[] percentages(int[] weights) {
        double total = 0;
        for (int w : weights) total += w;
        double[] out = new double[weights.length];
        for (int i = 0; i < out.length; i++) out[i] = weights[i] / total * 100;
        return out;
    }
}
