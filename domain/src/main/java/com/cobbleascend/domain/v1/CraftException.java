package com.cobbleascend.domain.v1;

/** A transition refused by the domain. The reason is a stable code for the future preview/confirm protocol. */
public final class CraftException extends IllegalArgumentException {
    public enum Reason {
        NO_PENDING_CREDIT, MAX_RANK, UNKNOWN_SLOT, NO_ELIGIBLE_AFFIX, INSUFFICIENT_ATTUNEMENT,
        MAX_RARITY, UNKNOWN_UNIQUE, UNIQUE_PRESENT, NO_UNIQUE, SAME_UNIQUE, INVALID_LEVEL,
        INSUFFICIENT_FUNDS, WALLET_OVERFLOW, INVALID_AMOUNT, UNKNOWN_SPECIES
    }

    private final Reason reason;

    public CraftException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
