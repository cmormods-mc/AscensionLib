package com.cobbleascend.domain;

import java.util.Objects;

public record AffixRoll(String id, String type, int value) {
    public AffixRoll {
        Objects.requireNonNull(id);
        if (value < 0 || value > 100) throw new IllegalArgumentException("Invalid affix value");
    }
}
