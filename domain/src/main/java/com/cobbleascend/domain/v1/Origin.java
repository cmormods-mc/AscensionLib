package com.cobbleascend.domain.v1;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Provenance. Kinds are the prototype's origin strings plus validated external grants. */
public record Origin(String kind, UUID acquisitionId) {
    public static final List<String> KINDS =
            List.of("wild_capture", "hatch", "legacy", "admin", "unknown", "external_grant", "sigil");

    public Origin {
        Objects.requireNonNull(kind);
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("Unknown acquisition origin");
    }

    public static Origin of(String kind) { return new Origin(kind, null); }
}
