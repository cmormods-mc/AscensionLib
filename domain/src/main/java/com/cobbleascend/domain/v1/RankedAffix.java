package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.AffixDefinition;
import java.util.List;
import java.util.Objects;

/** An ordinary affix definition with one validated roll band per rank I-V. */
public record RankedAffix(AffixDefinition base, int definitionVersion, List<RankBand> bands) {
    public static final int RANKS = 5;

    public RankedAffix {
        Objects.requireNonNull(base);
        bands = List.copyOf(bands);
        if (definitionVersion < 1) throw new IllegalArgumentException("Invalid definition version: " + base.id());
        if (bands.size() != RANKS)
            throw new IllegalArgumentException("Affix must define exactly " + RANKS + " rank bands: " + base.id());
        for (int i = 1; i < bands.size(); i++) {
            if (bands.get(i).min() <= bands.get(i - 1).max())
                throw new IllegalArgumentException("Rank bands must strictly increase without overlap: " + base.id());
        }
    }

    public String id() { return base.id(); }
    public String family() { return base.family(); }
    public Category category() { return Category.fromId(base.slot()); }
    public int weight() { return base.weight(); }

    public RankBand band(int rank) {
        if (rank < 1 || rank > RANKS) throw new IllegalArgumentException("Rank outside I-V: " + rank);
        return bands.get(rank - 1);
    }
}
