package com.cobbleascend.domain.v1;

import java.util.Objects;

/**
 * A named Unique power. Unique powers have fixed tuning and no ordinary rank. {@code benefit} and {@code drawback} are the
 * player-facing text of what it does and what it costs (every Unique has a drawback); the simulator module owns the actual tuning.
 */
public record UniqueDefinition(String id, String name, String benefit, String drawback, int definitionVersion) {
    public UniqueDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(benefit);
        Objects.requireNonNull(drawback);
        if (!id.matches("[a-z][a-z0-9_]{0,63}") || name.isBlank() || definitionVersion < 1)
            throw new IllegalArgumentException("Invalid unique definition");
    }

    /** A Unique with no player-facing text (tests, tooling). */
    public UniqueDefinition(String id, String name, int definitionVersion) {
        this(id, name, "", "", definitionVersion);
    }
}
