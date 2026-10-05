package com.cobbleascend.domain;

import java.util.Objects;

public record AffixDefinition(String id, String name, String slot, String family,
                              String channel, int min, int max, int weight,
                              String parameter, String condition) {
    public AffixDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(family);
        Objects.requireNonNull(condition);
        if (!id.matches("[a-z][a-z0-9_]{0,63}") || family.isBlank())
            throw new IllegalArgumentException("Invalid affix identity");
        if (!"prefix".equals(slot) && !"suffix".equals(slot))
            throw new IllegalArgumentException("Invalid slot: " + slot);
        if (min < 0 || max < min || max > 100 || weight < 1 || weight > 10000)
            throw new IllegalArgumentException("Invalid affix range/weight: " + id);
        if (parameter != null && !parameter.equals("one_current_species_type")
                && !parameter.equals("one_uniform_standard_type"))
            throw new IllegalArgumentException("Unknown parameter rule: " + parameter);
    }
}
