package com.ascensionlib;

import com.cobbleascend.domain.v1.RankedRules;
import com.cobbleascend.store.ProgressionStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the canonical store's lifecycle for the running world: opens it when the server starts and closes it when
 * the server stops, and registers the Cobblemon capture/hatch/level wiring and the {@code /ascend} commands.
 * Mods built on the library never open the database themselves; they call {@link AscensionApi}.
 */
public final class AscensionLib implements ModInitializer {
    public static final String MOD_ID = "ascensionlib";
    private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    private RankedRules rules;
    private AscensionRuntime runtime;

    @Override public void onInitialize() {
        rules = RankedRules.defaults();
        try {
            LOG.info("SQLite driver OK (engine {}); native library loaded in this runtime", ProgressionStore.driverVersion());
        } catch (RuntimeException exception) {
            LOG.error("SQLite driver is NOT usable; progression will stay disabled", exception);
        }
        com.ascensionlib.scout.ScoutNet.register();
        ServerLifecycleEvents.SERVER_STARTING.register(this::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::stop);
        new AscendWiring().register();
        LOG.info("AscensionLib: shared progression store ready to open with the world.");
    }

    private void start(MinecraftServer server) {
        runtime = AscensionRuntime.open(server, rules);
        AscensionApi.attach(runtime, runtime.enabled() ? new ProfileService(runtime, rules) : null, server);
        com.ascensionlib.scout.ScoutEncounters.attach(server);
    }

    private void stop(MinecraftServer server) {
        com.ascensionlib.scout.ScoutEncounters.detach();
        AscensionApi.detach();
        if (runtime != null) runtime.close();
        runtime = null;
    }
}
