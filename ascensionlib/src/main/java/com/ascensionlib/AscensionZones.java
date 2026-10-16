package com.ascensionlib;

import com.ascensionlib.battle.ProfileZones;
import net.minecraft.resources.ResourceLocation;

/**
 * The contract for a mod that owns dimensions where Ascension profiles act on wild Pokemon (ascensionDungeons, whose run levels exist
 * only for the length of a run). Signatures use only {@code java.*}, so a caller can reach it by reflection when the library is optional
 * for it (see {@link AscensionRewards}). Call on the server thread.
 *
 * <p>Open a zone when its dimension is created and close it when it is deleted. While it is open: wild Pokemon in it fight with their
 * rating at the zone's tier, a Pokemon captured in it gets a profile rated at the same tier (so a Scouter preview equals the capture), and
 * nothing else about ordinary wild fights elsewhere changes. The registry lives in memory only; register again after every restart.
 */
public final class AscensionZones {
    /** Bumped only when a method is removed or changes meaning; a caller may refuse to integrate with a newer major. */
    public static final int CONTRACT_VERSION = 1;

    private AscensionZones() {}

    /** This contract's version, for a caller that wants to check before using it. */
    public static int contractVersion() { return CONTRACT_VERSION; }

    /**
     * Makes a dimension a profile zone of this tier, or changes its tier.
     *
     * @param dimensionId a dimension id such as {@code fantasy:run_ab12}
     * @param tier        1 (shallowest, the ordinary rarity table) to {@code 10}; a deeper tier rates wild and trainer Pokemon rarer. The owner can override it per dimension in config/ascensionlib.json
     * @return {@code true} when the zone is open; {@code false} for an invalid id or tier (nothing changes)
     */
    public static boolean openZone(String dimensionId, int tier) {
        var id = dimensionId == null ? null : ResourceLocation.tryParse(dimensionId);
        if (id == null || tier < 1 || tier > ProfileZones.MAX_TIER) return false;
        ProfileZones.open(id, tier);
        return true;
    }

    /** Closes a zone. Safe to call twice, for an unknown id, or with {@code null}. */
    public static void closeZone(String dimensionId) {
        var id = dimensionId == null ? null : ResourceLocation.tryParse(dimensionId);
        if (id != null) ProfileZones.close(id);
    }

    /** The dimension's tier: a tier the owner configured for it, else one a mod opened, else the default tier (1 unless changed); 0 where profiles do not act. */
    public static int zoneTier(String dimensionId) {
        var id = dimensionId == null ? null : ResourceLocation.tryParse(dimensionId);
        return id == null ? 0 : ProfileZones.tierOf(id);
    }
}
