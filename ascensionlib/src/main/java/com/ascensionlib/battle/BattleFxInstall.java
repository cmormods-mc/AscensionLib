package com.ascensionlib.battle;

import java.io.InputStream;

/** Registers the simulator module and the format-field provider with the host. Called once, from pre-launch. */
public final class BattleFxInstall {
    /** The extension id; becomes {@code showdown/ext-ascensionlib-fx.js}. */
    public static final String MODULE_ID = "ascensionlib-fx";
    static final String RESOURCE = "/assets/ascensionlib/showdown/ascension-fx.js";

    private BattleFxInstall() {}

    public static void register() {
        var host = RaidsShowdownHost.find();
        if (host.isEmpty()) return;
        host.get().registerModule(MODULE_ID, BattleFxInstall::module);
        // Resolved lazily: the first battle loads AscensionBattles and its Cobblemon classes, not this early call.
        host.get().registerFormatFields((battleId, playerIds) -> AscensionBattles.fieldsFor(battleId, playerIds));
        System.out.println("[AscensionLib] Registered the Showdown module with " + host.get().name() + ".");
    }

    static InputStream module() {
        return BattleFxInstall.class.getResourceAsStream(RESOURCE);
    }
}
