package com.cobbleascend.domain;

import java.util.*;
import java.util.random.RandomGenerator;

/** Pure transitions only. Applying a craft and debiting its cost requires a future transaction service. */
public final class Progression {
    public record CraftResult(Profile profile, Cost cost) {}
    private final Rules rules;
    public Progression(Rules rules) { this.rules = Objects.requireNonNull(rules); }

    public Profile create(UUID pokemonId, Rarity rarity, String origin, List<String> types, RandomGenerator random) {
        var rolls = new ArrayList<AffixRoll>();
        var tier = rules.tier(rarity);
        for (int i = 0; i < tier.prefixSlots(); i++) rolls.add(draw("prefix", rolls, types, random));
        for (int i = 0; i < tier.suffixSlots(); i++) rolls.add(draw("suffix", rolls, types, random));
        var profile = new Profile(Profile.SCHEMA, UUID.randomUUID(), pokemonId, 1, rarity, rarity, 0, origin, rolls);
        rules.validate(profile);
        return profile;
    }

    public CraftResult promote(Profile profile, List<String> types, RandomGenerator random) {
        rules.validate(profile);
        var promotion = rules.promotion(profile.rarity());
        if (profile.attunement() < promotion.attunement()) throw new IllegalArgumentException("Insufficient attunement");
        var next = rules.tier(promotion.to());
        var previous = rules.tier(profile.rarity());
        String slot = next.prefixSlots() > previous.prefixSlots() ? "prefix" : "suffix";
        var rolls = new ArrayList<>(profile.affixes());
        rolls.add(draw(slot, rolls, types, random));
        var result = profile.withProgress(promotion.to(), rolls);
        rules.validate(result);
        return new CraftResult(result, promotion.cost());
    }

    public CraftResult reforge(Profile profile, int index, List<String> types, RandomGenerator random) {
        rules.validate(profile);
        var old = profile.affixes().get(index);
        var other = new ArrayList<>(profile.affixes());
        other.remove(index);
        var replacement = draw(rules.affix(old.id()).slot(), other, types, random);
        var rolls = new ArrayList<>(profile.affixes());
        rolls.set(index, replacement);
        var result = profile.withProgress(profile.rarity(), rolls);
        rules.validate(result);
        return new CraftResult(result, rules.craftCost("reforge"));
    }

    public CraftResult refine(Profile profile, int index, RandomGenerator random) {
        rules.validate(profile);
        var old = profile.affixes().get(index);
        var definition = rules.affix(old.id());
        var rolls = new ArrayList<>(profile.affixes());
        rolls.set(index, new AffixRoll(old.id(), old.type(), random.nextInt(definition.min(), definition.max() + 1)));
        return new CraftResult(profile.withProgress(profile.rarity(), rolls), rules.craftCost("refine"));
    }

    private AffixRoll draw(String slot, List<AffixRoll> existing, List<String> types, RandomGenerator random) {
        var families = new HashSet<String>();
        existing.forEach(roll -> families.add(rules.affix(roll.id()).family()));
        var eligible = rules.affixes().stream().filter(a -> a.slot().equals(slot) && !families.contains(a.family())).toList();
        int total = eligible.stream().mapToInt(AffixDefinition::weight).sum();
        if (total == 0) throw new IllegalArgumentException("No eligible affix family");
        int ticket = random.nextInt(total);
        AffixDefinition selected = null;
        for (var definition : eligible) {
            ticket -= definition.weight();
            if (ticket < 0) { selected = definition; break; }
        }
        Objects.requireNonNull(selected);
        String type = null;
        if (selected.parameter() != null) {
            var typePool = selected.parameter().equals("one_current_species_type") ? types.stream().distinct().toList() : Rules.TYPES;
            if (typePool.isEmpty() || !Rules.TYPES.containsAll(typePool)) throw new IllegalArgumentException("Invalid species type pool");
            type = typePool.get(random.nextInt(typePool.size()));
        }
        return new AffixRoll(selected.id(), type, random.nextInt(selected.min(), selected.max() + 1));
    }
}
