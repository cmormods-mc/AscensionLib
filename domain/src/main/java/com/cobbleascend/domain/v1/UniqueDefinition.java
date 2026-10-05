package com.cobbleascend.domain.v1;

import java.util.Objects;

/** A named Unique power. Unique powers have fixed tuning and no ordinary rank. */
public record UniqueDefinition(String id, String name, int definitionVersion) {
    public UniqueDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        if (!id.matches("[a-z][a-z0-9_]{0,63}") || name.isBlank() || definitionVersion < 1)
            throw new IllegalArgumentException("Invalid unique definition");
    }
}
