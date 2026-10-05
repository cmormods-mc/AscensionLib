package com.cobbleascend.domain.v1;

import java.util.random.RandomGenerator;

/** Inclusive integer roll range (percent) for one affix rank. */
public record RankBand(int min, int max) {
    public RankBand {
        if (min < 0 || max < min || max > 100) throw new IllegalArgumentException("Invalid rank band");
    }

    public boolean contains(int value) { return value >= min && value <= max; }

    public int roll(RandomGenerator random) { return random.nextInt(min, max + 1); }
}
