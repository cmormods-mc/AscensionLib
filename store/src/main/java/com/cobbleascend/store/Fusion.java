package com.cobbleascend.store;

import java.util.UUID;

/** A committed fusion: the host kept, the donor consumed, and the inputs the Transcendent is resolved from. */
public record Fusion(UUID hostId, UUID donorId, String hostSpecies, String donorSpecies, String hostUnique, String donorUnique,
                     UUID operationId, long fusedAt) {}
