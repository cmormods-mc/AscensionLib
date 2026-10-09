package com.ascensionlib;

import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.Optional;

/**
 * Which Pokemon may not be upgraded, refined, reforged, promoted or given a Unique. A mod that lends a player a Pokemon it will take
 * back (CobbleTowers' rentals) marks it with a persistent boolean named {@link #TAG}; the library then refuses to open the upgrade
 * screen for it, refuses every craft on it, and tells the client so its Upgrade button reads locked. Inspecting it stays allowed.
 *
 * <p>The tag is the whole contract: a persistent-data boolean, no class to call, so the mod that sets it needs no dependency on the
 * library and the library needs none on that mod.
 */
public final class CraftLocks {
    /** The persistent-data key (a boolean) that locks a Pokemon's upgrades. Part of the contract: never rename it in place. */
    public static final String TAG = "ascensionlib_craft_locked";

    public static final String REASON = "This Pokemon is on loan and cannot be upgraded.";

    private CraftLocks() {}

    public static boolean locked(Pokemon pokemon) {
        return pokemon.getPersistentData().getBoolean(TAG);
    }

    /** Why this Pokemon cannot be crafted on, or empty when it can. */
    public static Optional<String> reason(Pokemon pokemon) {
        return locked(pokemon) ? Optional.of(REASON) : Optional.empty();
    }
}
