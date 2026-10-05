package com.ascensionlib;

import com.ascensionlib.battle.BattleFxInstall;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Runs before any mod initializes, to register the library's Showdown module. It cannot wait for
 * {@link AscensionLib#onInitialize()}: Cobblemon builds its simulator while mods are still initializing, and a module
 * registered later is installed but not loaded until the next boot (found live by CobbleTowers).
 *
 * <p>Plain Java on purpose, and guarded: an exception here would stop the server over an optional feature.
 */
public final class AscensionPreLaunch implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        try {
            BattleFxInstall.register();
        } catch (RuntimeException | LinkageError ex) {
            System.err.println("[AscensionLib] Could not register the Showdown module; ascension effects are off: " + ex);
        }
    }
}
