package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.Rarity;
import com.cobbleascend.domain.Rules;
import com.cobbleascend.domain.v1.CraftException.Reason;
import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Pure schema-1 transitions. Each returns the next committed profile (revision + 1) and what the operation
 * costs; debiting a wallet, locking and persistence belong to the future transaction service.
 */
public final class RankedProgression {
    /** {@code cost} lists materials; {@code creditsSpent} lists pending milestone credits consumed. */
    public record CraftResult(ProfileV1 profile, Map<MaterialId, Long> cost, int creditsSpent) {}
    public record LevelResult(ProfileV1 profile, List<Integer> newlyAwarded) {}

    private final RankedRules rules;

    public RankedProgression(RankedRules rules) { this.rules = Objects.requireNonNull(rules); }

    /** One-time acquisition: rarity-sized slots at rank I and every milestone already reached by {@code level}. */
    public ProfileV1 create(UUID pokemonId, UUID authorityId, Rarity rarity, Origin origin, int level,
                            List<String> types, RandomGenerator random) {
        return build(pokemonId, authorityId, rarity, origin, level, rollInitialSlots(rarity, types, random));
    }

    /**
     * What a wild Pokemon will be when caught: its rarity, rank-I slots and the upgrades it would start with.
     * Fixed for that Pokemon (derived from its ID and a server-only secret), so fleeing and re-encountering
     * cannot reroll it and a Scouter preview equals the capture result exactly.
     */
    public record WildRating(Rarity rarity, List<OrdinarySlot> slots, int upgradesOnCapture) {
        public WildRating { slots = List.copyOf(slots); }
    }

    public WildRating rateWild(byte[] secret, UUID pokemonId, List<String> types, int level) {
        if (types.isEmpty() || !com.cobbleascend.domain.Rules.TYPES.containsAll(types))
            throw new IllegalArgumentException("Species types must be one or more standard types");
        int upgrades = Milestones.reachedThrough(level).size();
        var random = WildSeed.random(secret, rules.catalogVersion(), pokemonId);
        Rarity rarity = rules.base().rollRarity(random);
        var slots = rollInitialSlots(rarity, types, random);
        return new WildRating(rarity, slots, upgrades);
    }

    /** Captures a wild Pokemon using exactly the rating {@link #rateWild} previews. */
    public ProfileV1 createWild(byte[] secret, UUID pokemonId, UUID authorityId, Origin origin, int level,
                                List<String> types) {
        var rating = rateWild(secret, pokemonId, types, level);
        return build(pokemonId, authorityId, rating.rarity(), origin, level, rating.slots());
    }

    private ProfileV1 build(UUID pokemonId, UUID authorityId, Rarity rarity, Origin origin, int level,
                            List<OrdinarySlot> slots) {
        var profile = new ProfileV1(ProfileV1.SCHEMA, UUID.randomUUID(), pokemonId, authorityId, 1, rarity, rarity,
                origin, rules.catalogVersion(), 0, Math.min(level, Milestones.MAX_LEVEL),
                Milestones.reachedThrough(level), 0, slots, null);
        rules.validate(profile);
        return profile;
    }

    /**
     * A full set of rank-I slots for {@code rarity}, drawn with the same weights and family exclusions as a
     * capture. Shared by player acquisition and enemy generation so both follow identical rules.
     */
    public List<OrdinarySlot> rollInitialSlots(Rarity rarity, List<String> types, RandomGenerator random) {
        var slots = new ArrayList<OrdinarySlot>();
        for (var category : Category.values()) {
            for (int i = 0; i < rules.slotCount(rarity, category); i++)
                slots.add(draw(OrdinarySlot.slotId(category, i), category, slots, 1, types, random));
        }
        return List.copyOf(slots);
    }

    /** Monotonic catch-up: awards only milestones absent from the ledger. Lowering a level changes nothing. */
    public LevelResult observeLevel(ProfileV1 profile, int level) {
        rules.validate(profile);
        if (level < 1) throw new CraftException(Reason.INVALID_LEVEL, "Level must be at least 1");
        int observed = Math.min(level, Milestones.MAX_LEVEL);
        var awarded = new TreeSet<>(profile.awardedMilestones());
        var added = new ArrayList<Integer>();
        for (int milestone : Milestones.reachedThrough(observed)) if (awarded.add(milestone)) added.add(milestone);
        int highest = Math.max(profile.highestLevelObserved(), observed);
        if (added.isEmpty() && highest == profile.highestLevelObserved()) return new LevelResult(profile, List.of());
        var next = profile.advance(profile.rarity(), highest, awarded, profile.spentUpgradeCredits(),
                profile.ordinarySlots(), profile.unique());
        rules.validate(next);
        return new LevelResult(next, List.copyOf(added));
    }

    /** Spends one pending credit to advance the selected slot one rank and roll in the new band. */
    public CraftResult upgrade(ProfileV1 profile, String slotId, RandomGenerator random) {
        rules.validate(profile);
        var slot = requireSlot(profile, slotId);
        if (profile.pendingCredits() < 1)
            throw new CraftException(Reason.NO_PENDING_CREDIT, "No pending upgrade credit");
        if (slot.rank() >= RankedAffix.RANKS)
            throw new CraftException(Reason.MAX_RANK, "Slot is already at maximum rank");
        int rank = slot.rank() + 1;
        var value = rules.affix(slot.affixId()).band(rank).roll(random);
        var upgraded = new OrdinarySlot(slot.slotId(), slot.category(), rank, slot.affixId(), slot.parameters(),
                value, slot.definitionVersion());
        var next = profile.advance(profile.rarity(), profile.highestLevelObserved(), profile.awardedMilestones(),
                profile.spentUpgradeCredits() + 1, replace(profile, upgraded), profile.unique());
        rules.validate(next);
        return new CraftResult(next, Map.of(), 1);
    }

    /** Replaces the selected slot's affix and value. Slot ID, rank, other slots and credits are preserved. */
    public CraftResult reforge(ProfileV1 profile, String slotId, List<String> types, RandomGenerator random) {
        rules.validate(profile);
        var old = requireSlot(profile, slotId);
        var others = profile.ordinarySlots().stream().filter(s -> !s.slotId().equals(slotId)).toList();
        var replacement = draw(slotId, old.category(), others, old.rank(), types, random);
        var next = profile.advance(profile.rarity(), profile.highestLevelObserved(), profile.awardedMilestones(),
                profile.spentUpgradeCredits(), replace(profile, replacement), profile.unique());
        rules.validate(next);
        return new CraftResult(next, MaterialId.of(rules.base().craftCost("reforge")), 0);
    }

    /** Rerolls only the selected slot's value within its current rank band. */
    public CraftResult refine(ProfileV1 profile, String slotId, RandomGenerator random) {
        rules.validate(profile);
        var old = requireSlot(profile, slotId);
        int value = rules.affix(old.affixId()).band(old.rank()).roll(random);
        var refined = new OrdinarySlot(old.slotId(), old.category(), old.rank(), old.affixId(), old.parameters(),
                value, old.definitionVersion());
        var next = profile.advance(profile.rarity(), profile.highestLevelObserved(), profile.awardedMilestones(),
                profile.spentUpgradeCredits(), replace(profile, refined), profile.unique());
        rules.validate(next);
        return new CraftResult(next, MaterialId.of(rules.base().craftCost("refine")), 0);
    }

    /** Adds lifetime attunement (a Pokemon's battle-earned progress toward promotion). Free; the caller guarantees once-per-encounter. */
    public CraftResult awardAttunement(ProfileV1 profile, int points) {
        if (points < 1) throw new CraftException(Reason.INVALID_AMOUNT, "Attunement points must be positive");
        rules.validate(profile);
        return new CraftResult(profile.advanceAttunement(points), Map.of(), 0);
    }

    /** Adds exactly one rank-I slot. Credits, ranks, Unique and attunement are retained. */
    public CraftResult promote(ProfileV1 profile, List<String> types, RandomGenerator random) {
        rules.validate(profile);
        if (profile.rarity() == Rarity.MYTHICAL)
            throw new CraftException(Reason.MAX_RARITY, "Already at maximum rarity");
        Rules.Promotion promotion = rules.base().promotion(profile.rarity());
        if (profile.attunement() < promotion.attunement())
            throw new CraftException(Reason.INSUFFICIENT_ATTUNEMENT, "Insufficient attunement");
        Category added = null;
        for (var category : Category.values()) {
            if (rules.slotCount(promotion.to(), category) > rules.slotCount(profile.rarity(), category)) added = category;
        }
        if (added == null) throw new IllegalStateException("Promotion adds no slot");
        var slots = new ArrayList<>(profile.ordinarySlots());
        slots.add(draw(OrdinarySlot.slotId(added, profile.slotCount(added)), added, slots, 1, types, random));
        var next = profile.advance(promotion.to(), profile.highestLevelObserved(), profile.awardedMilestones(),
                profile.spentUpgradeCredits(), slots, profile.unique());
        rules.validate(next);
        return new CraftResult(next, MaterialId.of(promotion.cost()), 0);
    }

    /** Powers a Catalyst could currently install or swap in. Empty means no Catalyst may be consumed. */
    public List<UniqueDefinition> eligibleUniques(ProfileV1 profile) {
        rules.validate(profile);
        return rules.uniques().stream()
                .filter(u -> profile.unique() == null || !u.id().equals(profile.unique().uniqueId())).toList();
    }

    public CraftResult installUnique(ProfileV1 profile, String uniqueId, UUID operationId) {
        rules.validate(profile);
        if (profile.unique() != null) throw new CraftException(Reason.UNIQUE_PRESENT, "A Unique is already installed");
        return setUnique(profile, uniqueId, operationId);
    }

    public CraftResult replaceUnique(ProfileV1 profile, String uniqueId, UUID operationId) {
        rules.validate(profile);
        if (profile.unique() == null) throw new CraftException(Reason.NO_UNIQUE, "No Unique to replace");
        if (profile.unique().uniqueId().equals(uniqueId))
            throw new CraftException(Reason.SAME_UNIQUE, "Replacement must be a different Unique");
        return setUnique(profile, uniqueId, operationId);
    }

    private CraftResult setUnique(ProfileV1 profile, String uniqueId, UUID operationId) {
        var definition = rules.unique(uniqueId)
                .orElseThrow(() -> new CraftException(Reason.UNKNOWN_UNIQUE, "Unknown Unique: " + uniqueId));
        var instance = new UniqueInstance(definition.id(), definition.definitionVersion(), operationId);
        var next = profile.advance(profile.rarity(), profile.highestLevelObserved(), profile.awardedMilestones(),
                profile.spentUpgradeCredits(), profile.ordinarySlots(), instance);
        rules.validate(next);
        return new CraftResult(next, Map.of(MaterialId.UNIQUE_CATALYST, 1L), 0);
    }

    private static OrdinarySlot requireSlot(ProfileV1 profile, String slotId) {
        return profile.slot(slotId)
                .orElseThrow(() -> new CraftException(Reason.UNKNOWN_SLOT, "Unknown ordinary slot: " + slotId));
    }

    private static List<OrdinarySlot> replace(ProfileV1 profile, OrdinarySlot replacement) {
        var slots = new ArrayList<>(profile.ordinarySlots());
        slots.replaceAll(s -> s.slotId().equals(replacement.slotId()) ? replacement : s);
        return slots;
    }

    private OrdinarySlot draw(String slotId, Category category, Collection<OrdinarySlot> others, int rank,
                              List<String> types, RandomGenerator random) {
        var families = new HashSet<String>();
        others.forEach(slot -> families.add(rules.affix(slot.affixId()).family()));
        var eligible = rules.affixes().stream()
                .filter(a -> a.category() == category && !families.contains(a.family())).toList();
        int total = eligible.stream().mapToInt(RankedAffix::weight).sum();
        if (total == 0) throw new CraftException(Reason.NO_ELIGIBLE_AFFIX, "No eligible affix family");
        int ticket = random.nextInt(total);
        RankedAffix selected = null;
        for (var affix : eligible) {
            ticket -= affix.weight();
            if (ticket < 0) { selected = affix; break; }
        }
        Objects.requireNonNull(selected);
        Map<String, String> parameters = Map.of();
        var rule = selected.base().parameter();
        if (rule != null) {
            var pool = rule.equals("one_current_species_type") ? types.stream().distinct().toList() : Rules.TYPES;
            if (pool.isEmpty() || !Rules.TYPES.containsAll(pool))
                throw new IllegalArgumentException("Invalid species type pool");
            parameters = Map.of("type", pool.get(random.nextInt(pool.size())));
        }
        return new OrdinarySlot(slotId, category, rank, selected.id(), parameters,
                selected.band(rank).roll(random), selected.definitionVersion());
    }
}
