package com.cobbleascend.domain.v1;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A stable ordinary affix position. The slot ID and rank outlive the affix occupying it, so reforging
 * never erases level investment. Never address a slot by list index.
 */
public record OrdinarySlot(String slotId, Category category, int rank, String affixId,
                           Map<String, String> parameters, int rolledValue, int definitionVersion) {
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
    }

    public static String slotId(Category category, int index) { return category.id() + ":" + index; }

    public int index() { return Integer.parseInt(slotId.substring(slotId.indexOf(':') + 1)); }

    /** Selected move type for typed affixes, otherwise null. */
    public String type() { return parameters.get("type"); }
}
