package com.cobbleascend.store;

import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.MaterialWallet;
import com.cobbleascend.domain.v1.ProfileV1;
import java.util.Map;
import java.util.UUID;

/**
 * One operation the store has just committed, as reported to {@link ProgressionStore#onCommit}: who, what, which materials left and entered the
 * wallet, and how long the transaction took. {@code profile} and {@code wallet} are the state after it (null when it did not touch them);
 * {@code detail} is the reason a grant, spend or exchange was given, empty for the rest. Nothing here is persisted.
 */
public record Committed(Kind kind, UUID operationId, UUID playerId, UUID pokemonId, String detail, Map<MaterialId, Long> spent,
                        Map<MaterialId, Long> gained, int creditsSpent, ProfileV1 profile, MaterialWallet wallet, long nanos) {}
