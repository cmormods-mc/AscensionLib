package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.Rarity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * A difficulty tier an encounter declares before entry: which rarities its enemies can have, and how many
 * milestone-style rank credits each enemy spends on its slots. Everything else (slots, bands, caps) is the same
 * rule set players use, so difficulty scales through rarity and ranks only.
 */
public record EnemyTier(String id, List<RarityWeight> rarities, int rankCredits) {
    public record RarityWeight(Rarity rarity, int weight) {
        public RarityWeight {
            Objects.requireNonNull(rarity);
            if (weight < 1 || weight > 10000) throw new IllegalArgumentException("Invalid rarity weight");
        }
    }

    public EnemyTier {
        Objects.requireNonNull(id);
        if (!id.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid tier ID");
        var ordered = new ArrayList<>(rarities);
        ordered.sort(Comparator.comparing(RarityWeight::rarity));
        rarities = List.copyOf(ordered);
        if (rarities.isEmpty()) throw new IllegalArgumentException("A tier needs at least one rarity");
        var seen = EnumSet.noneOf(Rarity.class);
        for (var entry : rarities) if (!seen.add(entry.rarity())) throw new IllegalArgumentException("Duplicate rarity in tier");
        if (rankCredits < 0 || rankCredits > Milestones.MAX_LEVEL / Milestones.STEP)
            throw new IllegalArgumentException("Rank credits must be 0-10, as for a player");
    }

    public int totalWeight() { return rarities.stream().mapToInt(RarityWeight::weight).sum(); }

    public Rarity rollRarity(RandomGenerator random) {
        int ticket = random.nextInt(totalWeight());
        for (var entry : rarities) {
            ticket -= entry.weight();
            if (ticket < 0) return entry.rarity();
        }
        throw new IllegalStateException("Unreachable weighted draw");
    }
}
