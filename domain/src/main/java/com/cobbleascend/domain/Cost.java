package com.cobbleascend.domain;

public record Cost(int dust, int facets, int cores) {
    public Cost {
        if (dust < 0 || facets < 0 || cores < 0) throw new IllegalArgumentException("Negative cost");
    }
}
