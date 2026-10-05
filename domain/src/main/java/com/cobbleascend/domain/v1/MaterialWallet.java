package com.cobbleascend.domain.v1;

import com.cobbleascend.domain.v1.CraftException.Reason;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable material balances (specification section 4.2). Every material is always present, balances are
 * never negative, arithmetic is checked, and any change that moves a balance increments the revision so
 * stale confirmations can be detected. Zero-cost operations return the same instance.
 */
public record MaterialWallet(long revision, Map<MaterialId, Long> balances) {
    public static final MaterialWallet EMPTY = new MaterialWallet(0, Map.of());

    public MaterialWallet {
        if (revision < 0) throw new IllegalArgumentException("Negative wallet revision");
        var full = new EnumMap<MaterialId, Long>(MaterialId.class);
        for (var id : MaterialId.values()) full.put(id, 0L);
        for (var entry : balances.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)
                throw new IllegalArgumentException("Invalid balance");
            full.put(entry.getKey(), entry.getValue());
        }
        balances = Collections.unmodifiableMap(full);
    }

    public long balance(MaterialId id) { return balances.get(id); }

    public boolean canAfford(Map<MaterialId, Long> cost) {
        for (var entry : cost.entrySet()) {
            requireAmount(entry.getValue());
            if (balance(entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    public MaterialWallet credit(Map<MaterialId, Long> amounts) {
        var next = new EnumMap<>(balances);
        boolean changed = false;
        for (var entry : amounts.entrySet()) {
            long amount = requireAmount(entry.getValue());
            if (amount == 0) continue;
            try {
                next.put(entry.getKey(), Math.addExact(next.get(entry.getKey()), amount));
            } catch (ArithmeticException overflow) {
                throw new CraftException(Reason.WALLET_OVERFLOW, "Balance would overflow: " + entry.getKey().id());
            }
            changed = true;
        }
        return changed ? new MaterialWallet(Math.addExact(revision, 1), next) : this;
    }

    public MaterialWallet debit(Map<MaterialId, Long> cost) {
        var next = new EnumMap<>(balances);
        boolean changed = false;
        for (var entry : cost.entrySet()) {
            long amount = requireAmount(entry.getValue());
            if (amount == 0) continue;
            if (next.get(entry.getKey()) < amount)
                throw new CraftException(Reason.INSUFFICIENT_FUNDS, "Insufficient " + entry.getKey().id());
            next.put(entry.getKey(), next.get(entry.getKey()) - amount);
            changed = true;
        }
        return changed ? new MaterialWallet(Math.addExact(revision, 1), next) : this;
    }

    /** Consumes {@code threshold} Unique fragments and credits one Catalyst in a single revision. */
    public MaterialWallet assembleCatalyst(long threshold) {
        if (threshold < 1) throw new IllegalArgumentException("Catalyst threshold must be at least 1");
        var next = new EnumMap<>(balances);
        if (next.get(MaterialId.UNIQUE_FRAGMENT) < threshold)
            throw new CraftException(Reason.INSUFFICIENT_FUNDS, "Insufficient unique_fragment");
        next.put(MaterialId.UNIQUE_FRAGMENT, next.get(MaterialId.UNIQUE_FRAGMENT) - threshold);
        try {
            next.put(MaterialId.UNIQUE_CATALYST, Math.addExact(next.get(MaterialId.UNIQUE_CATALYST), 1L));
        } catch (ArithmeticException overflow) {
            throw new CraftException(Reason.WALLET_OVERFLOW, "Balance would overflow: unique_catalyst");
        }
        return new MaterialWallet(Math.addExact(revision, 1), next);
    }

    private static long requireAmount(Long amount) {
        if (amount == null || amount < 0) throw new CraftException(Reason.INVALID_AMOUNT, "Amounts must be nonnegative");
        return amount;
    }
}
