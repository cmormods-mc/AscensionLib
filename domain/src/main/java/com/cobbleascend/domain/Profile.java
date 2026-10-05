package com.cobbleascend.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Prototype schema. No wallet writes or battle counters belong in this value. */
public record Profile(int schemaVersion, UUID profileId, UUID pokemonId, long revision,
                      Rarity rarity, Rarity initialRarity, int attunement, String origin,
                      List<AffixRoll> affixes) {
    public static final int SCHEMA = 0;

    public Profile {
        Objects.requireNonNull(profileId);
        Objects.requireNonNull(pokemonId);
        Objects.requireNonNull(rarity);
        Objects.requireNonNull(initialRarity);
        Objects.requireNonNull(origin);
        affixes = List.copyOf(affixes);
        if (schemaVersion != SCHEMA || revision < 1 || attunement < 0 || affixes.size() > 6)
            throw new IllegalArgumentException("Unsupported or invalid profile");
        if (!List.of("wild_capture", "legacy", "hatch", "admin", "unknown").contains(origin))
            throw new IllegalArgumentException("Unknown acquisition origin");
        if (initialRarity.ordinal() > rarity.ordinal())
            throw new IllegalArgumentException("Rarity cannot be below its initial value");
    }

    public Profile withProgress(Rarity next, List<AffixRoll> rolls) {
        return new Profile(schemaVersion, profileId, pokemonId, Math.addExact(revision, 1),
                next, initialRarity, attunement, origin, rolls);
    }
}
