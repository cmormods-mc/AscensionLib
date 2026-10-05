package com.cobbleascend.domain;

/** Arithmetic only; durable atomic updates are the responsibility of the future store. */
public record Wallet(int dust, int facets, int cores) {
    public Wallet {
        if (dust < 0 || facets < 0 || cores < 0) throw new IllegalArgumentException("Negative wallet");
    }
    public Wallet debit(Cost cost) {
        if (dust < cost.dust() || facets < cost.facets() || cores < cost.cores())
            throw new IllegalArgumentException("Insufficient materials");
        return new Wallet(dust - cost.dust(), facets - cost.facets(), cores - cost.cores());
    }
    public Wallet credit(Cost reward) {
        return new Wallet(Math.addExact(dust, reward.dust()), Math.addExact(facets, reward.facets()),
                Math.addExact(cores, reward.cores()));
    }
}
