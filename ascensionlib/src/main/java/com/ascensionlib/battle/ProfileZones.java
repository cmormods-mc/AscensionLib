package com.ascensionlib.battle;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * The dimensions where Ascension profiles act on wild Pokemon, each with a tier (1 or more). Anywhere else an ordinary wild fight is
 * native for both sides; raids and armed tower encounters are unaffected by this list because they are recognised by the battle itself.
 * The tier is how deep or dangerous the zone is: a wild Pokemon there is rated at that tier (see {@code RankedProgression.rateWild}),
 * and the Pokemon a player captures there is rated at the same tier, so a Scouter preview equals the capture.
 *
 * <p>A mod adds a fixed dimension with {@link #allow} (tier 1) or {@link #open}, and a dimension that exists only for a while (a dungeon run's
 * level) with {@link #open} when it is created and {@link #close} when it is deleted. The list is kept in memory and rebuilt at each start,
 * so a mod must register again after every restart. Other mods reach this through {@code AscensionZones}.
 */
public final class ProfileZones {
    /** The deepest tier a zone can have. */
    public static final int MAX_TIER = 10;

    /** CobbleTowers' dimension; its encounters are armed anyway, so this is only a safety net. */
    public static final ResourceLocation TOWER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tower");

    private static final Map<ResourceLocation, Integer> ZONES = new ConcurrentHashMap<>();

    static {
        ZONES.put(TOWER, 1);
    }

    private ProfileZones() {}

    /** Adds a dimension as a zone of tier 1. */
    public static void allow(ResourceLocation dimension) { open(dimension, 1); }

    /** Adds or changes a zone. Throws for a null dimension or a tier outside 1..{@link #MAX_TIER}. */
    public static void open(ResourceLocation dimension, int tier) {
        java.util.Objects.requireNonNull(dimension, "dimension");
        if (tier < 1 || tier > MAX_TIER) throw new IllegalArgumentException("Zone tier must be 1-" + MAX_TIER);
        ZONES.put(dimension, tier);
    }

    /** Removes a zone; safe when it is not one. The tower dimension cannot be closed. */
    public static void close(ResourceLocation dimension) {
        if (dimension != null && !dimension.equals(TOWER)) ZONES.remove(dimension);
    }

    /** The zone's tier, or 0 when the dimension is not a zone. */
    public static int tierOf(ResourceLocation dimension) {
        return dimension == null ? 0 : ZONES.getOrDefault(dimension, 0);
    }

    public static boolean isZone(ResourceLocation dimension) { return tierOf(dimension) > 0; }

    public static Map<ResourceLocation, Integer> zones() { return Collections.unmodifiableMap(ZONES); }
}
