package com.cobbleascend.domain;

import java.util.Locale;

public enum Rarity {
    COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHICAL;

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public static Rarity fromId(String id) { return valueOf(id.toUpperCase(Locale.ROOT)); }
}
