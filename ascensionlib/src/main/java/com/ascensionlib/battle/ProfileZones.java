package com.ascensionlib.battle;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * The dimensions where Ascension profiles act on wild Pokemon (the tower dimension, plus any dimension another mod such as ascensionDungeons registers). Anywhere
 * else an ordinary wild fight is native for both sides; raids and armed tower encounters are unaffected by this list
 * because they are recognised by the battle itself.
 *
 * <p>Another mod can add its own dimension with {@link #allow}. The list is kept in memory and rebuilt at each start.
 */
public final class ProfileZones {
    /** CobbleTowers' dimension; its encounters are armed anyway, so this is only a safety net. */
    public static final ResourceLocation TOWER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tower");

    private static final Set<ResourceLocation> ALLOWED = ConcurrentHashMap.newKeySet();

    static {
        ALLOWED.add(TOWER);
    }

    private ProfileZones() {}

    public static void allow(ResourceLocation dimension) {
        ALLOWED.add(java.util.Objects.requireNonNull(dimension, "dimension"));
    }

    public static boolean isZone(ResourceLocation dimension) {
        return dimension != null && ALLOWED.contains(dimension);
    }

    public static Set<ResourceLocation> zones() {
        return Collections.unmodifiableSet(ALLOWED);
    }
}
