package com.ascensionlib.client;

import com.ascensionlib.craft.CraftPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Client side of the upgrade channel: asks for a Pokemon's view, and routes what the server answers to the open screen (or opens it
 * when the server asked, e.g. after {@code /ascend craft}). The client holds no state that matters: every number on the screen is the
 * server's latest view, and nothing is spent until the server accepts a confirmation.
 */
final class CraftClient {
    private static Screen pendingParent;
    private static boolean waitingToOpen;

    private CraftClient() {}

    /** Asks for the view; the screen opens when it arrives, returning to {@code parent} when closed. */
    static void open(Screen parent, String pokemonId) {
        if (!ClientPlayNetworking.canSend(CraftPayloads.Open.TYPE)) return;
        pendingParent = parent;
        waitingToOpen = true;
        ClientPlayNetworking.send(new CraftPayloads.Open(pokemonId));
    }

    static void onView(CraftPayloads.View view) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof CraftScreen screen && screen.pokemonId().equals(view.pokemonId())) {
            screen.accept(view);
        } else if (view.open() || waitingToOpen) {
            // Never over a battle or a screen we did not ask for; a command-opened view waits for a free screen like a reveal does.
            if (mc.screen == null || waitingToOpen || mc.screen instanceof AscendInspectScreen) {
                mc.setScreen(new CraftScreen(waitingToOpen ? pendingParent : mc.screen, view));
            }
        }
        waitingToOpen = false;
        pendingParent = null;
    }

    static void onDone(CraftPayloads.Done done) {
        if (Minecraft.getInstance().screen instanceof CraftScreen screen) screen.done(done);
    }
}
