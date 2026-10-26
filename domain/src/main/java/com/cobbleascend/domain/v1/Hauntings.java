package com.cobbleascend.domain.v1;

import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The Hauntings of the Halloween 2026 event (docs/HALLOWEEN-EVENT-DESIGN.md): one event-only power per Pokemon, held in a slot of its own that
 * never competes with the ordinary affixes. Pure rules: what exists, how a roll is drawn, what a roll costs. The effects themselves are in the
 * Showdown module (ascension-fx.js), which owns every condition and number; this only names them.
 *
 * <p>A roll first draws a Rare with a {@value #RARE_PERMILLE} in 1000 chance (Headless Rider 25, Reaper's Toll 15), and otherwise one of the eight
 * Commons by weight. The draw is a function of a seed, so a retried operation lands on the same Haunting.
 */
public final class Hauntings {
    /** Out of 1000: the chance one roll is a Rare Haunting. */
    public static final int RARE_PERMILLE = 40;
    /** The cost of the 1st, 2nd, 3rd and later rolls on one Pokemon, in Spooky Essence; a re-roll replaces the Haunting it had. */
    private static final long[] COSTS = {1, 3, 5, 8};

    /** One Haunting. {@code weight} is its share among the Commons (they sum to 100) or its permille among the Rares. */
    public record Haunting(String id, String name, boolean rare, int weight, String effect) {}

    private static final List<Haunting> ALL = List.of(
            new Haunting("wisp", "Wisp", false, 18, "20% chance on hit that the target flinches."),
            new Haunting("hex", "Hex", false, 16, "At the end of each turn, foes with a status or a lowered stat lose 1/16 of their max HP."),
            new Haunting("spectral_veil", "Spectral Veil", false, 16, "Takes 10% less damage from Normal and Fighting moves."),
            new Haunting("pumpkin_lantern", "Pumpkin Lantern", false, 14, "Restores 1/8 of its max HP when it enters the field."),
            new Haunting("trick_or_treat", "Trick or Treat", false, 12, "The first time it is hit each battle, it restores 1/8 of its max HP."),
            new Haunting("cobweb", "Cobweb", false, 10, "When it enters the field, lowers each foe's Speed by one stage."),
            new Haunting("graveyard_chill", "Graveyard Chill", false, 8, "A foe that makes contact with it loses 1/16 of its max HP."),
            new Haunting("midnight_howl", "Midnight Howl", false, 6, "Its first attack each battle deals 15% more damage."),
            new Haunting("headless_rider", "Headless Rider", true, 25, "Once per battle, a hit that would faint it leaves 1 HP, and it gains +1 Attack and +1 Sp. Atk."),
            new Haunting("reapers_toll", "Reaper's Toll", true, 15, "When it faints a foe it restores 1/3 of its max HP and gains +1 in its higher attacking stat."));

    private Hauntings() {}

    /** Every Haunting, Commons first. */
    public static List<Haunting> all() { return ALL; }

    public static Optional<Haunting> byId(String id) {
        for (var haunting : ALL) if (haunting.id().equals(id)) return Optional.of(haunting);
        return Optional.empty();
    }

    /** The Haunting a seed draws: deterministic, so a replayed operation gets the same result. */
    public static Haunting roll(long seed) {
        var random = new SplittableRandom(seed);
        int draw = random.nextInt(1000);
        if (draw < RARE_PERMILLE) {
            int within = draw;
            for (var haunting : ALL) {
                if (!haunting.rare()) continue;
                if (within < haunting.weight()) return haunting;
                within -= haunting.weight();
            }
        }
        int pick = random.nextInt(100);
        for (var haunting : ALL) {
            if (haunting.rare()) continue;
            if (pick < haunting.weight()) return haunting;
            pick -= haunting.weight();
        }
        throw new IllegalStateException("The Common weights do not add up to 100");
    }

    /** A stable seed for one roll, from the operation that asks for it and the Pokemon it is for. */
    public static long seed(UUID operationId, UUID pokemonId) {
        long a = operationId.getMostSignificantBits() ^ Long.rotateLeft(operationId.getLeastSignificantBits(), 21);
        long b = pokemonId.getMostSignificantBits() ^ Long.rotateLeft(pokemonId.getLeastSignificantBits(), 43);
        return a * 0x9E3779B97F4A7C15L + b;
    }

    /**
     * The number the Showdown module reads from the effect's percent field: Wisp's flinch chance, Spectral Veil's and Midnight Howl's percent of
     * damage. The other Hauntings are fixed in the module and travel as a plain "on" flag (1).
     */
    public static int effectPercent(String id) {
        return switch (id) {
            case "wisp" -> 20;
            case "spectral_veil" -> 10;
            case "midnight_howl" -> 15;
            default -> 1;
        };
    }

    /** What the next roll costs, given how many times this Pokemon has been rolled already (0 for its first). */
    public static long cost(int rollsSoFar) {
        if (rollsSoFar < 0) throw new IllegalArgumentException("rolls cannot be negative");
        return COSTS[Math.min(rollsSoFar, COSTS.length - 1)];
    }
}
