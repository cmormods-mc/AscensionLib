package com.cobbleascend.domain.v1;

import java.util.Objects;

/**
 * What an encounter declares for one enemy. A Unique is fixed by the encounter (never randomly drawn) and only
 * bosses may carry one, so it is a learnable signature mechanic rather than a surprise.
 */
public record EnemySpec(EnemyTier tier, boolean boss, String uniqueId) {
    public EnemySpec {
        Objects.requireNonNull(tier);
        if (uniqueId != null && !boss) throw new IllegalArgumentException("Only boss enemies may carry a Unique");
    }

    public static EnemySpec regular(EnemyTier tier) { return new EnemySpec(tier, false, null); }

    public static EnemySpec boss(EnemyTier tier, String uniqueId) { return new EnemySpec(tier, true, uniqueId); }
}
