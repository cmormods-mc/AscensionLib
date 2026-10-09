package com.cobbleascend.store;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A confirmed fusion: the left Pokemon (host) is kept and becomes a Transcendent, the right one (donor) is consumed. Carries the
 * revisions the player saw. The species ids are what {@code Transcendence.resolve} needs later; the Uniques come from the profiles.
 */
public record FuseRequest(UUID operationId, UUID playerId, UUID hostId, UUID donorId, String hostSpecies, String donorSpecies,
                          long expectedHostRevision, long expectedDonorRevision, long expectedWalletRevision) {
    private static final Pattern SPECIES = Pattern.compile("[a-z0-9_]{1,64}");

    public FuseRequest {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(hostId);
        Objects.requireNonNull(donorId);
        if (hostId.equals(donorId)) throw new IllegalArgumentException("A Pokemon cannot be fused with itself");
        if (!SPECIES.matcher(hostSpecies == null ? "" : hostSpecies).matches() || !SPECIES.matcher(donorSpecies == null ? "" : donorSpecies).matches())
            throw new IllegalArgumentException("Invalid species id");
        if (expectedHostRevision < 1 || expectedDonorRevision < 1) throw new IllegalArgumentException("Profile revisions are required");
        if (expectedWalletRevision < CraftRequest.ANY_REVISION) throw new IllegalArgumentException("Invalid wallet revision");
    }

    String canonical() {
        return String.join("|", "FUSE", playerId.toString(), hostId.toString(), donorId.toString(), hostSpecies, donorSpecies,
                Long.toString(expectedHostRevision), Long.toString(expectedDonorRevision), Long.toString(expectedWalletRevision));
    }
}
