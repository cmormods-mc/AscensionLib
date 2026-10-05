package com.cobbleascend.store;

/** Provisional economy settings (specification section 9.2: starting tuning values, not approved rates). */
public record StoreConfig(int catalystFragments) {
    public StoreConfig {
        if (catalystFragments < 1) throw new IllegalArgumentException("Catalyst threshold must be at least 1");
    }

    public static StoreConfig defaults() { return new StoreConfig(100); }
}
