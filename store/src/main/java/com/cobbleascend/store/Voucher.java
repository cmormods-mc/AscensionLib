package com.cobbleascend.store;

import com.cobbleascend.domain.v1.MaterialId;
import java.util.UUID;

/**
 * One batch of wallet currency minted as tradable items (a withdrawal). {@code outstanding} is how many of the batch's units are still out in
 * the world: a redemption can never credit more than that, so a duplicated stack cannot inflate the currency, only race the original for what
 * is left. {@code delivered} is false until the server has handed the items over, so a crash between the debit and the hand-over is recoverable.
 */
public record Voucher(UUID batchId, UUID playerId, MaterialId material, long minted, long outstanding, boolean delivered, long createdAt) {}
