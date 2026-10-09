package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.Rarity;
import java.util.*;

/**
 * Production profile schema 1 (specification section 4.1). Structural invariants are enforced here;
 * invariants that need catalog data (bands, families, slot counts per rarity) live in {@link RankedRules}.
 * No wallet writes or battle counters belong in this value.
 */
public record ProfileV1(int schemaVersion, UUID profileId, UUID pokemonId, UUID authorityId, long revision,
                        Rarity rarity, Rarity initialRarity, Origin origin, int catalogVersion,
                        int attunement, int highestLevelObserved, Set<Integer> awardedMilestones,
                        int spentUpgradeCredits, List<OrdinarySlot> ordinarySlots, UniqueInstance unique) {
    public static final int SCHEMA = 1;
    public static final int MAX_SLOTS = 6;

    public ProfileV1 {
        Objects.requireNonNull(profileId);
        Objects.requireNonNull(pokemonId);
        Objects.requireNonNull(authorityId);
        Objects.requireNonNull(rarity);
        Objects.requireNonNull(initialRarity);
        Objects.requireNonNull(origin);
        awardedMilestones = Collections.unmodifiableSortedSet(new TreeSet<>(awardedMilestones));
        ordinarySlots = List.copyOf(ordinarySlots);
        if (schemaVersion != SCHEMA) throw new IllegalArgumentException("Unsupported profile schema");
        if (revision < 1 || catalogVersion < 1 || attunement < 0)
            throw new IllegalArgumentException("Invalid profile counters");
        if (initialRarity.ordinal() > rarity.ordinal())
            throw new IllegalArgumentException("Rarity cannot be below its initial value");
        if (highestLevelObserved < 1 || highestLevelObserved > Milestones.MAX_LEVEL)
            throw new IllegalArgumentException("Highest observed level outside 1-100");
        for (int milestone : awardedMilestones) {
            if (!Milestones.isMilestone(milestone) || milestone > highestLevelObserved)
                throw new IllegalArgumentException("Invalid awarded milestone: " + milestone);
        }
        if (spentUpgradeCredits < 0 || spentUpgradeCredits > awardedMilestones.size())
            throw new IllegalArgumentException("Spent credits exceed awarded milestones");
        if (ordinarySlots.size() > MAX_SLOTS) throw new IllegalArgumentException("Too many ordinary slots");
        int invested = 0;
        var indexes = new EnumMap<Category, Set<Integer>>(Category.class);
        for (var category : Category.values()) indexes.put(category, new TreeSet<>());
        for (var slot : ordinarySlots) {
            if (!indexes.get(slot.category()).add(slot.index()))
                throw new IllegalArgumentException("Duplicate slot ID: " + slot.slotId());
            invested += slot.rank() - 1;
        }
        for (var entry : indexes.entrySet()) {
            int expected = 0;
            for (int index : entry.getValue()) {
                if (index != expected++) throw new IllegalArgumentException("Slot IDs must be contiguous per category");
            }
        }
        if (invested != spentUpgradeCredits)
            throw new IllegalArgumentException("Spent credits must equal invested slot ranks");
    }

    /** Earned milestone choices not yet assigned to a slot. Never negative. */
    public int pendingCredits() { return awardedMilestones.size() - spentUpgradeCredits; }

    public Optional<OrdinarySlot> slot(String slotId) {
        return ordinarySlots.stream().filter(s -> s.slotId().equals(slotId)).findFirst();
    }

    public int slotCount(Category category) {
        return (int) ordinarySlots.stream().filter(s -> s.category() == category).count();
    }

    /** The next committed revision with the supplied mutable state; identity and provenance carry over. */
    ProfileV1 advance(Rarity nextRarity, int nextHighestLevel, Set<Integer> nextMilestones, int nextSpent,
                      List<OrdinarySlot> nextSlots, UniqueInstance nextUnique) {
        return new ProfileV1(schemaVersion, profileId, pokemonId, authorityId, Math.addExact(revision, 1),
                nextRarity, initialRarity, origin, catalogVersion, attunement, nextHighestLevel,
                nextMilestones, nextSpent, nextSlots, nextUnique);
    }

    /** The next revision with nothing else changed: a Pokemon whose canonical state moved elsewhere (a fusion) announces it so caches refresh. */
    public ProfileV1 bumped() {
        return advance(rarity, highestLevelObserved, awardedMilestones, spentUpgradeCredits, ordinarySlots, unique);
    }

    /** The next committed revision with {@code points} more lifetime attunement; everything else carries over. */
    ProfileV1 advanceAttunement(int points) {
        return new ProfileV1(schemaVersion, profileId, pokemonId, authorityId, Math.addExact(revision, 1), rarity, initialRarity,
                origin, catalogVersion, Math.addExact(attunement, points), highestLevelObserved, awardedMilestones,
                spentUpgradeCredits, ordinarySlots, unique);
    }

    /** Test support only: sets attunement directly with no revision change. */
    ProfileV1 withAttunement(int value) {
        return new ProfileV1(schemaVersion, profileId, pokemonId, authorityId, revision, rarity, initialRarity,
                origin, catalogVersion, value, highestLevelObserved, awardedMilestones, spentUpgradeCredits,
                ordinarySlots, unique);
    }
}
