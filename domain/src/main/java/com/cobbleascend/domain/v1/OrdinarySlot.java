package com.cobbleascend.domain.v1;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A stable ordinary affix position. The slot ID and rank outlive the affix occupying it, so reforging
 * never erases level investment. Never address a slot by list index.
 *
 * <p>{@code floor} is the slot's raised minimum roll, bought with Perfecting (see {@code RankedProgression.perfect}): 0 means none, and a
 * floor exists only on a rank V slot, at or above its band's minimum and never above its value. A Refine rolls between the floor and the
 * band maximum, so a floor is never undone; a Reforge replaces the affix and clears it.
 *
 * <p>{@code sealed} marks a slot that has had its one Overreach attempt (see {@code RankedProgression.overreach}), win or lose: it can no longer be
 * Refined or Perfected, only Reforged. Only a sealed slot may hold a value above its band maximum (a successful Overreach), and a sealed slot has no floor.
 */
public record OrdinarySlot(String slotId, Category category, int rank, String affixId,
                           Map<String, String> parameters, int rolledValue, int definitionVersion, int floor, boolean sealed) {
    /** A slot with a floor and no seal. */
    public OrdinarySlot(String slotId, Category category, int rank, String affixId, Map<String, String> parameters, int rolledValue,
                        int definitionVersion, int floor) {
        this(slotId, category, rank, affixId, parameters, rolledValue, definitionVersion, floor, false);
    }

    /** A slot with no raised floor. */
    public OrdinarySlot(String slotId, Category category, int rank, String affixId, Map<String, String> parameters, int rolledValue,
                        int definitionVersion) {
        this(slotId, category, rank, affixId, parameters, rolledValue, definitionVersion, 0);
    }

    private static final Pattern ID = Pattern.compile("(prefix|suffix):(0|[1-5])");

    public OrdinarySlot {
        Objects.requireNonNull(slotId);
        Objects.requireNonNull(category);
        Objects.requireNonNull(affixId);
        parameters = Map.copyOf(parameters);
        if (!ID.matcher(slotId).matches() || !slotId.startsWith(category.id() + ":"))
            throw new IllegalArgumentException("Invalid slot ID: " + slotId);
        if (rank < 1 || rank > RankedAffix.RANKS) throw new IllegalArgumentException("Rank outside I-V");
        if (rolledValue < 0 || rolledValue > 100 || definitionVersion < 1)
            throw new IllegalArgumentException("Invalid slot roll or definition version");
        if (floor < 0 || floor > rolledValue) throw new IllegalArgumentException("A floor cannot be negative or above the roll");
        if (floor > 0 && rank != RankedAffix.RANKS) throw new IllegalArgumentException("Only a rank V slot can have a floor");
        if (sealed && rank != RankedAffix.RANKS) throw new IllegalArgumentException("Only a rank V slot can be sealed");
        if (sealed && floor > 0) throw new IllegalArgumentException("A sealed slot has no floor");
    }

    public static String slotId(Category category, int index) { return category.id() + ":" + index; }

    public int index() { return Integer.parseInt(slotId.substring(slotId.indexOf(':') + 1)); }

    /** Selected move type for typed affixes, otherwise null. */
    public String type() { return parameters.get("type"); }
}
