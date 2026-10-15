package com.ascensionlib.scout;

import java.util.List;
import java.util.Optional;

/**
 * What the scouting and inspection pages show (plain data; {@code InspectWeb} and {@code HudWeb} turn it into JSON for the pages).
 * The server builds one {@link State} whenever something changes for a player; the page only ever shows the latest. An unscouted entry
 * carries a name and nothing else, so what is sent cannot leak a rarity, a modifier, a Unique or even a level (the enemy's name is what
 * the battle already shows).
 */
public final class ScoutPayloads {
    private ScoutPayloads() {}

    /** One modifier row of a scouted enemy; {@code category} is {@code prefix} or {@code suffix}. */
    public record Slot(String category, String name, int rank, int value, int min, int max, String condition, String theme) {
        /** A row with no range or effect text and no theme. */
        public Slot(String category, String name, int rank, int value) { this(category, name, rank, value, -1, -1, "", ""); }

        /** A row with range and effect text and no theme. */
        public Slot(String category, String name, int rank, int value, int min, int max, String condition) {
            this(category, name, rank, value, min, max, condition, "");
        }
    }

    /**
     * One theme's resonance on a Pokemon: how many of its slots belong to the theme, and the bonus percent those slots get (0 below two
     * pieces, 15 for two, 30 for three or more; see {@code Resonance}).
     */
    public record Tier(String theme, int pieces, int bonusPercent) {}

    /**
     * What a reveal shows. {@code baseStats} is HP, Attack, Defence, Sp. Atk, Sp. Def, Speed (empty when unknown);
     * {@code uniqueName} is empty when the enemy has none. Never IVs, EVs or moves.
     */
    public record Detail(int level, List<String> types, List<Integer> baseStats, String rarityId, String uniqueName, List<Slot> slots,
                         List<Tier> resonance) {
        /** The resonance tiers follow from the slots' themes, so every place that builds a detail gets them without computing them. */
        public Detail(int level, List<String> types, List<Integer> baseStats, String rarityId, String uniqueName, List<Slot> slots) {
            this(level, types, baseStats, rarityId, uniqueName, slots, tiers(slots));
        }

        static List<Tier> tiers(List<Slot> slots) {
            var counts = new java.util.LinkedHashMap<String, Integer>();
            for (var slot : slots) if (!slot.theme().isEmpty() && slot.value() > 0) counts.merge(slot.theme(), 1, Integer::sum);
            var out = new java.util.ArrayList<Tier>();
            counts.forEach((theme, pieces) -> out.add(new Tier(theme, pieces, com.cobbleascend.domain.v1.Resonance.multiplierPercent(pieces) - 100)));
            return List.copyOf(out);
        }
    }

    public record Entry(String subjectId, String name, Optional<Detail> detail) {}

    /** The player's current encounter (empty id and entries when there is none) and Scouter count. */
    public record State(String encounterId, int scouters, List<Entry> entries) {
        public static State none(int scouters) { return new State("", scouters, List.of()); }
    }
}
