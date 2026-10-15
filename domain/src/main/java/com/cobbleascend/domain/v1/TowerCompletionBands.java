package com.cobbleascend.domain.v1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * What clearing a tower's F10 champion pays (docs/TOWER-COMPLETION-REWARDS.md, numbers approved 2026-10-15; tune from live counters). A completion
 * is about an hour of play, so it always ends in a large payout: a <b>pile</b> that is guaranteed, a <b>quality tier</b> earned by how hard and
 * how clean the run was (never how fast), and a <b>crown</b> of one to three rolls from a weighted table, so a completion never ends without a notable
 * item. Flat per player except for the seeded rolls, which are seeded by (encounter, player), so a retried settlement recomputes the same chest.
 */
public record TowerCompletionBands(int dust, int facets, int fragments, int scouters, int marks, int goldMarkBonus, int mythicMarkBonus,
                                   int fragmentBundle, List<TierSpec> tiers) {
    /** The crown items. {@link #ARMOR} and {@link #TICKET} are CobbleTowers' own tables, so they come back as counts for it to hand over. */
    public enum Crown { CORE, FRAGMENT_BUNDLE, SIGIL, CATALYST, ARMOR, TICKET }

    public enum Tier { BRONZE, SILVER, GOLD, MYTHIC }

    /** @param minScore the lowest score in this tier; @param multiplierPercent percent of the pile; @param weights per {@link Crown}, in enum order */
    public record TierSpec(Tier tier, int minScore, int multiplierPercent, int crownSlots, int[] weights) {
        public TierSpec {
            if (weights.length != Crown.values().length) throw new IllegalArgumentException("One weight per crown item");
            int total = 0;
            for (int weight : weights) { if (weight < 0) throw new IllegalArgumentException("Negative weight"); total += weight; }
            if (total < 1 || crownSlots < 1 || multiplierPercent < 1) throw new IllegalArgumentException("A tier needs weight, a slot and a multiplier");
            weights = weights.clone();
        }
    }

    /**
     * The result of one completion.
     * @param pile the guaranteed part; {@code materials} is the pile plus what the crown rolls paid (what the wallet receives);
     * @param crowns the crown rolls in reveal order; {@code armorPieces} and {@code tickets} count CobbleTowers' own crown items
     */
    public record Completion(Tier tier, int score, Map<MaterialId, Long> pile, Map<MaterialId, Long> materials, List<Crown> crowns, int armorPieces, int tickets) {}

    public static final TowerCompletionBands DEFAULTS = new TowerCompletionBands(800, 6, 3, 4, 3, 1, 1, 12, List.of(
            new TierSpec(Tier.BRONZE, 0, 70, 1, new int[] {30, 40, 10, 0, 14, 6}),
            new TierSpec(Tier.SILVER, 2, 100, 1, new int[] {36, 30, 12, 2, 14, 6}),
            new TierSpec(Tier.GOLD, 5, 150, 2, new int[] {36, 22, 14, 4, 16, 8}),
            new TierSpec(Tier.MYTHIC, 8, 225, 3, new int[] {36, 14, 14, 8, 18, 10})));

    public TowerCompletionBands {
        if (dust < 0 || facets < 0 || fragments < 0 || scouters < 0 || marks < 0 || goldMarkBonus < 0 || mythicMarkBonus < 0 || fragmentBundle < 1)
            throw new IllegalArgumentException("Amounts must be nonnegative and a bundle at least 1");
        if (tiers.size() != Tier.values().length || tiers.get(0).minScore() != 0)
            throw new IllegalArgumentException("One spec per tier, the first starting at score 0");
        for (int i = 0; i < tiers.size(); i++)
            if (tiers.get(i).tier() != Tier.values()[i] || (i > 0 && tiers.get(i).minScore() <= tiers.get(i - 1).minScore()))
                throw new IllegalArgumentException("Tiers must be in order with rising scores");
        tiers = List.copyOf(tiers);
    }

    /** Skill, not speed: challenge cards taken, plus two per Ascension cycle, plus two when nobody was knocked out. */
    public static int score(int challengeCards, int cycle, boolean flawless) {
        if (challengeCards < 0 || cycle < 0) throw new IllegalArgumentException("Cards and cycle must be nonnegative");
        return challengeCards + 2 * cycle + (flawless ? 2 : 0);
    }

    public TierSpec tierFor(int score) {
        var found = tiers.get(0);
        for (var spec : tiers) if (score >= spec.minScore()) found = spec;
        return found;
    }

    public Completion payout(String encounterId, UUID player, int challengeCards, int cycle, boolean flawless) {
        int score = score(challengeCards, cycle, flawless);
        var spec = tierFor(score);
        var random = new Random(RewardSeed.of("tower-completion", encounterId, player));
        var materials = new EnumMap<MaterialId, Long>(MaterialId.class);
        add(materials, MaterialId.RESONANCE_DUST, scaled(dust, spec.multiplierPercent()));
        add(materials, MaterialId.FACET, scaled(facets, spec.multiplierPercent()));
        add(materials, MaterialId.UNIQUE_FRAGMENT, scaled(fragments, spec.multiplierPercent()));
        add(materials, MaterialId.SCOUTER, scouters);
        add(materials, MaterialId.TOWER_MARK, marks + (spec.tier().ordinal() >= Tier.GOLD.ordinal() ? goldMarkBonus : 0)
                + (spec.tier() == Tier.MYTHIC ? mythicMarkBonus : 0));
        var pile = Collections.unmodifiableMap(new EnumMap<>(materials));
        var crowns = new ArrayList<Crown>();
        int armor = 0, tickets = 0;
        for (int slot = 0; slot < spec.crownSlots(); slot++) {
            var crown = pick(spec.weights(), random);
            crowns.add(crown);
            switch (crown) {
                case CORE -> add(materials, MaterialId.ASCENSION_CORE, 1);
                case FRAGMENT_BUNDLE -> add(materials, MaterialId.UNIQUE_FRAGMENT, fragmentBundle);
                case SIGIL -> add(materials, MaterialId.ASCENSION_SIGIL, 1);
                case CATALYST -> add(materials, MaterialId.UNIQUE_CATALYST, 1);
                case ARMOR -> armor++;
                case TICKET -> tickets++;
            }
        }
        return new Completion(spec.tier(), score, pile, Collections.unmodifiableMap(materials), List.copyOf(crowns), armor, tickets);
    }

    private static long scaled(int base, int percent) { return Math.round(base * percent / 100.0); }

    private static void add(Map<MaterialId, Long> into, MaterialId material, long amount) {
        if (amount > 0) into.merge(material, amount, Long::sum);
    }

    private static Crown pick(int[] weights, Random random) {
        int total = 0;
        for (int weight : weights) total += weight;
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) return Crown.values()[i];
        }
        throw new IllegalStateException("unreachable");
    }
}
