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
    public record Slot(String category, String name, int rank, int value, int min, int max, String condition) {
        /** A row with no range or effect text (a scouted enemy's: the reveal shows what it always did). */
        public Slot(String category, String name, int rank, int value) { this(category, name, rank, value, -1, -1, ""); }
    }

    /**
     * What a reveal shows. {@code baseStats} is HP, Attack, Defence, Sp. Atk, Sp. Def, Speed (empty when unknown);
     * {@code uniqueName} is empty when the enemy has none. Never IVs, EVs or moves.
     */
    public record Detail(int level, List<String> types, List<Integer> baseStats, String rarityId, String uniqueName, List<Slot> slots) {}

    public record Entry(String subjectId, String name, Optional<Detail> detail) {}

    /** The player's current encounter (empty id and entries when there is none) and Scouter count. */
    public record State(String encounterId, int scouters, List<Entry> entries) {
        public static State none(int scouters) { return new State("", scouters, List.of()); }
    }
}
