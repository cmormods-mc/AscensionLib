package com.cobbleascend.domain.v1;

import java.util.Locale;

public enum Category {
    PREFIX, SUFFIX;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Category fromId(String id) {
        return switch (id) {
            case "prefix" -> PREFIX;
            case "suffix" -> SUFFIX;
            default -> throw new IllegalArgumentException("Unknown slot category: " + id);
        };
    }
}
