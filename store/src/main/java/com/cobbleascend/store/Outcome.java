package com.cobbleascend.store;

import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.MaterialWallet;
import com.cobbleascend.domain.v1.ProfileV1;
import java.util.Map;
import java.util.UUID;

/**
 * A committed result. {@code profile} and {@code wallet} are the snapshots produced by that operation (either
 * may be null when the operation did not touch it), so a replay returns exactly what the first call returned.
 */
public record Outcome(UUID operationId, Kind kind, ProfileV1 profile, MaterialWallet wallet,
                      Map<MaterialId, Long> cost, int creditsSpent, boolean replayed) {
    public Outcome {
        cost = Map.copyOf(cost);
    }

    Outcome asReplay() { return new Outcome(operationId, kind, profile, wallet, cost, creditsSpent, true); }
}
